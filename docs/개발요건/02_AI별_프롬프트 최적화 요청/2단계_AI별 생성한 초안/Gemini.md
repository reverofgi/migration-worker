제공해주신 요구사항 문서는 **250TB 대용량 데이터 환경에서 Java를 '컨트롤러(Orchestrator)'로만 사용하고 실제 I/O는 DB 서버 네이티브(Unload/Load)로 처리**하는 매우 타당한 아키텍처를 담고 있습니다.

이 프롬프트를 AI(LLM)에 입력했을 때 **환각(Hallucination) 없이 즉시 실행 가능한 프로덕션 코드**를 얻을 수 있도록 주요 약점을 보완한 검토 의견과 **최적화된 마스터 프롬프트**를 제시합니다.

---

## 1. 요구사항 검토 및 보완 의견

| 구분 | 주요 보완 필요 항목 | 보완 이유 및 가이드 |
| --- | --- | --- |
| **Sybase IQ 세션 관리** | `Temp_Extract` 세션 옵션 초기화 보장 | Unload 중 에러 발생 시 `Temp_Extract_Name1 = ''` 리셋이 안 되면 **해당 커넥션의 후속 `SELECT` 결과가 파일로 덮어씌워지는 대형 장애** 발생. `finally` 블록에서 강제 초기화하는 규칙 명시 필요. |
| **BLOB 컬럼 지원** | 바이너리 추출 옵션 자동 분기 | `GPCL_MIG_TABLE`에 BLOB 포함 여부(`IS_BLOB_INC`)가 있을 경우, `TEMP_EXTRACT_BINARY = 'ON'` 옵션이 자동으로 붙도록 `SybaseIQSqlBuilder`에 명시적인 분기 로직 추가 필요. |
| **메타데이터 스키마** | 컬럼명 표준 정의 부족 | 스키마 명세가 모호하면 AI가 컬럼명(예: `TABLE_NAME` vs `TBL_NM`)을 임의로 생성하여 기존 GODIS DB 및 DTO와 불일치 발생. 프롬프트 내에 **표준 DDL/컬럼명 명시** 필요. |
| **예외 및 Exit Code** | GODIS 스케줄러 연동 | Shell 스크립트가 성공(`0`)과 실패(`1` 이상)를 명확히 수신하도록 `MigrationWorkerApplication`에서 `System.exit()` 호출 지침 강화. |

---

## 2. 최적화된 마스터 프롬프트 (Copy & Paste용)

아래 박스 전체를 AI 코드 생성 모델(GPT-4o, Claude 3.5 Sonnet 등)에 그대로 전달하시면 됩니다.

```markdown
# [Prompt] Sybase IQ 대용량 데이터 이관용 Pure Java 배치 프로그램 생성

당신은 대용량 DB 마이그레이션 및 고성능 백엔드 아키텍처 전문 시니어 Java/DBA 엔지니어입니다.
아래에 정의된 인프라 제약조건, 메타데이터 스키마 표준, 4단계 파이프라인 및 클래스 구조를 엄격히 준수하여 **즉시 컴파일 및 실행 가능한 Pure Java 기반 이관 배치 프로그램 전체 소스 코드**를 작성해 주세요.

---

## 1. 프로젝트 핵심 제약사항
1. **Pure Java (Lightweight)**:
   - Spring, Spring Batch, HikariCP, JPA 등 외부 프레임워크를 일체 배제합니다.
   - pure JDBC, MyBatis(SqlSessionFactory), SLF4J/Logback, Commons-IO 등 최소한의 라이브러리만 사용합니다.
2. **3개 Connection 독립 관리**:
   - `META Connection`: GODIS MariaDB (설정 조회, 상태/검증 결과 기록)
   - `ASIS Connection`: Sybase IQ 16.0 (AS-IS Unload & 원천 검증 Query)
   - `TOBE Connection`: Sybase IQ 16.2 (TO-BE Load & 타겟 검증 Query)
3. **Sybase IQ 세션 안전성**:
   - `TEMP_EXTRACT_NAME1` 옵션 사용 후 **예외 발생 여부와 상관없이 `finally` 블록에서 반드시 `SET TEMPORARY OPTION Temp_Extract_Name1 = ''`을 실행**하여 세션을 복구해야 합니다.
4. **공유 볼륨(NFS 50TB) 용량 순환**:
   - TO-BE `LOAD TABLE` 성공 직후, 공유 볼륨의 추출 파일(.dat, .csv, .bin 등)을 **즉시 삭제(`FileUtil.deleteQuietly`)**합니다.

---

## 2. 메타데이터 테이블 스키마 표준 (MariaDB)

MyBatis Mapper 및 DTO 작성 시 아래 컬럼명을 엄격히 사용하세요.

```sql
-- 1. 공통 코드/프로퍼티 테이블
CREATE TABLE GPCL_CM_CD_VAL (
    GRP_CD_ID   VARCHAR(50)  NOT NULL, -- 'MIGRATION_PROPERTIES'
    CD_ID       VARCHAR(50)  NOT NULL, -- 예: 'ASIS_JDBC_URL', 'LOG_LEVEL', 'SHARE_VOLUME_PATH'
    CD_VAL      VARCHAR(500) NOT NULL,
    PRIMARY KEY (GRP_CD_ID, CD_ID)
);

-- 2. 이관 대상 테이블 메타
CREATE TABLE GPCL_MIG_TABLE (
    TASK_ID      VARCHAR(50) NOT NULL PRIMARY KEY,
    ASIS_OWNER   VARCHAR(30) NOT NULL,
    ASIS_TBL_NM  VARCHAR(50) NOT NULL,
    TOBE_OWNER   VARCHAR(30) NOT NULL,
    TOBE_TBL_NM  VARCHAR(50) NOT NULL,
    IS_BLOB_INC  CHAR(1)     DEFAULT 'N', -- 'Y'일 경우 Temp_Extract_Binary='ON' 적용
    EXTRACT_OPTS VARCHAR(500),            -- 추가 추출 옵션
    LOAD_OPTS    VARCHAR(500)             -- 추가 로드 옵션
);

-- 3. 검증 대상 컬럼 메타
CREATE TABLE GPCL_MIG_VRF_TARGET (
    TASK_ID     VARCHAR(50) NOT NULL,
    VRF_SEQ     INT         NOT NULL,
    COL_NM      VARCHAR(50) NOT NULL,
    VRF_FUNC    VARCHAR(10) NOT NULL, -- 'COUNT', 'SUM', 'AVG', 'MIN', 'MAX'
    PRIMARY KEY (TASK_ID, VRF_SEQ)
);

-- 4. 이관 실행 이력 및 검증 결과 테이블
CREATE TABLE GPCL_MIG_EXEC_LOG (
    EXEC_SEQ    BIGINT      NOT NULL,
    TASK_ID     VARCHAR(50) NOT NULL,
    STATUS      VARCHAR(20) NOT NULL, -- 'RUNNING', 'SUCCESS', 'FAILED', 'VERIFY_MISMATCH'
    EXTRACT_CNT BIGINT,
    LOAD_CNT    BIGINT,
    ASIS_VRF_VAL VARCHAR(4000),       -- JSON 또는 GRP_CONCAT 형태 저장
    TOBE_VRF_VAL VARCHAR(4000),
    ERROR_MSG   TEXT,
    START_TIME  DATETIME,
    END_TIME    DATETIME,
    EXEC_USER   VARCHAR(50),
    PRIMARY KEY (EXEC_SEQ, TASK_ID)
);

```

---

## 3. 프로그램 실행 파이프라인 (4-Step Pipeline)

CLI 호출: `java -cp ... com.migration.MigrationWorkerApplication <EXEC_SEQ> <TASK_ID> [EXEC_USER]`

* **[Step 0: 초기화]**
* MariaDB 접속 후 `GPCL_CM_CD_VAL`에서 설정값 로드 및 `LogUtil`로 Logback 동적 레벨 변경.
* `ConnectionFactory`를 통해 ASIS, TOBE `SqlSessionFactory` 및 `Connection` 생성.
* `GPCL_MIG_EXEC_LOG`에 STATUS = `'RUNNING'`, START_TIME 기록.


* **[Step 1: AS-IS Extract]**
* `SybaseIQSqlBuilder`로 Unload 옵션 SQL 생성 (`Temp_Extract_Name1 = '/nfs/path/{TASK_ID}_{EXEC_SEQ}.dat'`).
* `IS_BLOB_INC = 'Y'`인 경우 `Temp_Extract_Binary = 'ON'` 세팅.
* AS-IS DB에서 `SELECT * FROM {ASIS_OWNER}.{ASIS_TBL_NM}` 실행.


* **[Step 2: AS-IS Verify]**
* `GPCL_MIG_VRF_TARGET` 정보로 동적 집계 Query 생성 (`SELECT COUNT(*), SUM(COL1), MAX(COL2) FROM ...`).
* 집계 결과를 `GPCL_MIG_EXEC_LOG.ASIS_VRF_VAL`에 기록.


* **[Step 3: TO-BE Load & Clean]**
* TO-BE IQ에서 `LOAD TABLE {TOBE_OWNER}.{TOBE_TBL_NM} FROM '/nfs/path/{TASK_ID}_{EXEC_SEQ}.dat' ...` 실행.
* **성공 시**: NFS 상의 추출 파일 삭제 (`FileUtil.deleteFile`).
* **실패 시**: 에러 로그 기록, 파일 보존, STATUS = `'FAILED'`, System Exit Code = `1` 종료.


* **[Step 4: TO-BE Verify & Finish]**
* TO-BE DB에서 Step 2와 동일한 동적 집계 Query 실행 및 `TOBE_VRF_VAL` 기록.
* AS-IS vs TO-BE 검증값 비교: 일치 시 `'SUCCESS'`, 불일치 시 `'VERIFY_MISMATCH'`.
* END_TIME, 처리 소요시간 갱신 후 정상 종료 (`Exit Code = 0`).



---

## 4. 요청 산출물 목록

아래 클래스 및 XML을 축약(주석/TODO 생략) 없이 **완전한 프로덕션 코드**로 생성하세요.

### 1. MyBatis Mapper XML (2개)

* `MetaMapper.xml`: MariaDB 설정 조회, 메타 조회, 이력/검증 결과 저장 및 갱신.
* `SybaseIQMapper.xml`: ASIS/TOBE 공용. 세션 옵션 설정/해제, `LOAD TABLE` 문 실행, 동적 검증 Query 실행.

### 2. Java Source Code (10개)

1. `MigrationWorkerApplication.java`: CLI 인자 파싱, Global 예외 처리, `System.exit()` 제어.
2. `MigrationWorker.java`: Step 0~4 전체 흐름을 제어하는 Main Orchestrator.
3. `ConnectionFactory.java`: MariaDB 공통코드를 읽어 3개 DB(META, ASIS, TOBE)용 `SqlSessionFactory` 구축.
4. `SybaseIQUnloadWorker.java`: AS-IS Unload 실행 및 `finally` 세션 리셋 보장 로직.
5. `SybaseIQLoadWorker.java`: TO-BE `LOAD TABLE` 실행 및 성공 시 `FileUtil` 연동 파일 삭제.
6. `MigrationValidator.java`: AS-IS/TO-BE 동적 검증 값 비교 및 결과 판정.
7. `SybaseIQSqlBuilder.java`: `TEMP_EXTRACT` 옵션 SQL, `LOAD TABLE` DDL, 동적 `SELECT COUNT/SUM...` SQL 생성기.
8. `FileUtil.java`: 파일 존재 검증, 용량 확인, 안전한 파일 삭제 기능.
9. `LogUtil.java`: MariaDB 프로퍼티 문자열(DEBUG/INFO/WARN/ERROR)을 읽어 Logback `LoggerContext` 레벨 동적 변경.
10. `SqlUtil.java`: JDBC Connection/Statement/ResultSet 안전 닫기(Close) 유틸리티.

### 3. Shell Script (1개)

* `run_migration.sh`: Classpath 설정, JVM Memory 옵션(`-Xms1g -Xmx2g`), CLI 인자 전달 및 Exit Code를 GODIS로 전달하는 실행 스크립트.

```

---

## 3. 이관 실행 프로세스 구조 요약


```

[GODIS / Shell Script]
│
▼ (CLI: EXEC_SEQ, TASK_ID)
[MigrationWorkerApplication] ─── (Step 0) ──► [MariaDB (META)] : 공통코드/메타 로드
│
├─── (Step 1) ──► [AS-IS Sybase IQ 16.0] ──(Unload Direct)──► [50TB NFS Volume]
│                                                                  │
├─── (Step 2) ──► [AS-IS Sybase IQ 16.0] (원천 집계 검증)          │
│                                                                  │
├─── (Step 3) ──► [TO-BE Sybase IQ 16.2] ◄──(LOAD TABLE Direct)────┘
│                      │
│                      └─► [FileUtil] : 적재 성공 시 NFS 파일 즉시 삭제
│
└─── (Step 4) ──► [TO-BE Sybase IQ 16.2] (타겟 집계 검증 및 최종 판정)

```

이 프롬프트를 사용하시면 AI가 **시판 배치 솔루션에 준하는 완성도 높은 Java 코드**와 **Sybase IQ 네이티브 언로드/로드 연동 구성 요소**를 누락 없이 출력합니다.

```