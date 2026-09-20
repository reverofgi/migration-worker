# [Request] Sybase IQ 대용량 이관용 경량 Java 배치 — 1단계: 초기환경 · 아키텍처 · 소스 생성

## 0. 수행 규칙 (반드시 준수)

1. **역할**: 대용량 마이그레이션과 Sybase IQ 운영 경험이 있는 시니어 Java/DBA 엔지니어.
2. **표기 규칙**
   - `[확정]` : 그대로 구현한다.
   - `[가정]` : 명시된 기본값으로 구현하되, 코드 주석에 `ASSUMPTION-<번호>`를 남기고 값은 공통코드 또는 상수로 변경 가능하게 만든다.
   - `[PoC]` : 실제 환경에서 검증이 필요한 항목. 코드 주석에 `TODO(POC-<번호>)`를 남긴다.
3. **추측 금지**: 명세에 없고 Sybase IQ 16.0 / 16.2 공식 문서로 확신할 수 없는 구문·옵션명은 임의로 만들지 않는다. `TODO(VERIFY-IQ)` 주석을 남기고, 문자열을 공통코드로 교체할 수 있게 작성한다.
4. **출력 방식**: 8장의 Phase 단위로 **한 번에 하나의 Phase만** 출력한다. 파일은 완결된 상태로 출력하며 `...`, "동일하게 구현" 같은 생략은 금지한다. 각 파일은 `경로`를 제목으로 한 코드블록으로 제시한다.
5. **설명 최소화**: Phase 말미에 (a) 이번 Phase에서 추가된 `ASSUMPTION` / `TODO` 목록, (b) 다음 Phase 선행 조건만 10줄 이내로 요약한다.

---

## 1. 프로젝트 개요 `[확정]`

| 항목 | 내용 |
|---|---|
| 목적 | HP-UX Sybase IQ 16.0(AS-IS) → Linux Sybase IQ 16.2(TO-BE) ADW 데이터 이관 |
| 규모 | 테이블 약 13,000개 / 총 약 250TB |
| 전달 매체 | AS-IS·TO-BE가 함께 마운트한 **50TB 공유 볼륨(NFS/공유스토리지)** |
| 실행 방식 | GODIS 웹(MariaDB 기반 배치 프레임워크)이 스케줄링·멀티스레드로 Shell Script를 호출. **프로세스 1개 = 1 TASK_ID 처리**이며, 병렬성은 GODIS가 여러 JVM을 동시에 띄우는 방식으로 확보 |
| 구조 | AS-IS/TO-BE 테이블·컬럼 구조가 동일하며 단일 메타데이터 세트로 관리 |

---

## 2. 아키텍처 결정 사항

### 2.1 기술 스택 · 빌드 `[확정]`
- **Pure Java 경량 앱**: Spring, Spring Batch, HikariCP 등 프레임워크와 커넥션 풀 라이브러리를 사용하지 않는다.
- 사용 라이브러리는 MyBatis, MariaDB JDBC, Sybase IQ JDBC(`jconn4` 또는 SQL Anywhere JDBC — 드라이버 클래스명은 공통코드로 주입), SLF4J, Logback으로 한정한다. Lombok은 사용하지 않는다.
- 빌드는 **Maven** `[가정]`, 소스 레벨은 **Java 8** `[가정]`이다. 배포는 `lib/` 디렉터리에 의존 JAR를 복사하는 방식이며 실행 스크립트가 classpath를 구성한다. Sybase JDBC 드라이버는 Maven Central에 없으므로 `system` scope 또는 로컬 설치 방식으로 처리하고 pom에 안내 주석을 남긴다.
- 기본 패키지는 `com.migration`이며, 하위 패키지는 `worker`, `db`, `verify`, `util`, `model`, `exception`으로 구성한다.

### 2.2 부트스트랩 (Meta 접속정보 전달) `[가정]`
- 로컬 프로퍼티 파일은 사용하지 않는다(`logback.xml`은 패키징 리소스로 허용).
- MariaDB **최소 접속정보**는 환경변수 `MIG_META_URL`, `MIG_META_USER`, `MIG_META_PWD`로 받는다. 비밀번호가 `ps`에 노출되지 않도록 `-D` 옵션은 쓰지 않는다.
- 이후 모든 설정은 `GPCL_CM_CD_VAL`(`GRP_CD_ID='MIGRATION_PROPERTIES'`)에서 조회하여 인메모리 `Map`으로 보관한다. 필수 키가 누락되면 Exit Code 2로 즉시 종료한다.

### 2.3 Connection 관리 `[확정 + 보강]`
- 프로세스당 **정확히 3개**(META / ASIS / TOBE)의 Connection만 사용한다. MyBatis 내장 `UnpooledDataSource`로 `SqlSessionFactory`를 3개 만들고, `ConnectionFactory`가 단일 생성·제공·반환 창구가 된다.
- `SybaseIQMapper`는 ASIS·TOBE 두 Factory에 모두 등록한다.
- **트랜잭션 분리** `[가정]`: META 세션은 상태 기록마다 즉시 commit한다(이관 트랜잭션과 독립). ASIS는 읽기 전용 용도이고, TOBE는 `autoCommit=false` 상태에서 명시적으로 commit/rollback한다.
- 장시간 쿼리를 고려하여 `queryTimeout`은 기본 무제한(0)이며 공통코드로 변경할 수 있다. 각 Step 진입 전에 `isValid()`를 점검하고, 끊긴 경우 재연결을 1회 시도한다(횟수는 공통코드).
- 종료 시 close 순서는 TOBE → ASIS → META이며, META는 최종 상태를 기록한 뒤 마지막에 닫는다. `finally` 또는 try-with-resources로 **모든 종료 경로에서 누수가 없도록** 한다. `SIGTERM` 수신 시 Shutdown Hook에서 상태를 `FAILED`(`ERR_CD=ABORTED`)로 기록하고 자원을 정리한다.

### 2.4 SQL · MyBatis 규칙 `[확정 + 보강]`
- Meta 접근은 Mapper 인터페이스 + XML로, 정적/동적 SQL을 분리한다.
- **`SET TEMPORARY OPTION`, `LOAD TABLE` 등 DDL·옵션 구문은 바인드 파라미터를 쓸 수 없으므로** `${}` 치환 + `statementType="STATEMENT"`로 선언한다.
- `${}`로 들어가는 스키마·테이블·컬럼·경로 값은 `SybaseIQSqlBuilder`에서 **화이트리스트 검증**을 통과한 값만 사용한다 (식별자는 `^[A-Za-z0-9_#$]+$`, 경로는 허용 prefix 검사, 따옴표·세미콜론 거부). 검증에 실패하면 `MigrationException`을 던진다.

### 2.5 로깅 `[확정 + 보강]`
- SLF4J + Logback을 사용하고, 로그 레벨(DEBUG/INFO/WARN/ERROR)은 `LogUtil`이 공통코드 값으로 런타임에 반영한다(Step 0 직후 적용). 그 이전 구간은 INFO로 동작한다.
- MDC로 `execSeq`, `taskId`, `table`을 주입하고, 로그 파일은 `EXEC_SEQ`·`TASK_ID` 단위로 분리한다(경로는 실행 스크립트가 `-Dlog.dir`로 전달).
- **비밀번호, 접속 URL의 credential은 어떤 레벨에서도 로그에 노출하지 않는다**(마스킹).

### 2.6 보안 `[가정]`
- `GPCL_CM_CD_VAL`의 비밀번호 값은 암호문일 수 있으므로 복호화 훅(`interface PasswordDecoder`, 기본 구현은 평문 패스스루)을 둔다.

---

## 3. 실행 인터페이스 `[확정 + 보강]`

`java -cp "lib/*:app.jar" com.migration.MigrationWorkerApplication <EXEC_SEQ> <TASK_ID> [EXEC_USER]`

| 인자 | 설명 |
|---|---|
| `args[0]` | `EXEC_SEQ` 배치실행차수 (필수) |
| `args[1]` | `TASK_ID` (필수) |
| `args[2]` | `EXEC_USER` (선택, 기본 `SYSTEM`) |

**Exit Code 표** (`ExitCode` enum으로 구현, `System.exit`는 `MigrationWorkerApplication`에서만 호출)

| 코드 | 의미 |
|---|---|
| 0 | 대상 테이블 전체 SUCCESS |
| 1 | 인자 오류 |
| 2 | 초기화 오류 (환경변수 / 공통코드 / 커넥션 / 메타 0건) |
| 3 | Extract 실패 |
| 4 | AS-IS 검증값 산출 실패 |
| 5 | Load 실패 (적재건수 불일치 포함) |
| 6 | 검증 불일치 (`VERIFY_MISMATCH`) |
| 7 | TO-BE 검증 쿼리 실패 |
| 9 | 예기치 못한 오류 |

한 TASK에 테이블이 여러 개일 경우 `TABLE_ORD` 순으로 순차 처리한다. 기본은 **첫 실패 시 중단**(`STOP_ON_ERROR=Y`)이고 `N`이면 계속 진행하되, 종료 코드는 **최초 실패 코드**로 한다 `[가정]`.

---

## 4. 메타데이터 스키마

> **아래 컬럼명은 [가정]입니다. 실제 DDL이 있으면 이 장을 교체하십시오.** 컬럼명이 다를 경우 Mapper XML의 컬럼 매핑만 수정하면 되도록 작성합니다.

### 4.1 `GPCL_CM_CD_VAL` (`GRP_CD_ID='MIGRATION_PROPERTIES'`, `USE_YN='Y'`)
컬럼: `GRP_CD_ID`, `CD_ID`(키), `CD_VAL`(값)

| CD_ID | 설명 | 기본값 |
|---|---|---|
| `ASIS_JDBC_URL` / `_USER` / `_PWD` / `_DRIVER` | AS-IS 접속 | (필수) |
| `TOBE_JDBC_URL` / `_USER` / `_PWD` / `_DRIVER` | TO-BE 접속 | (필수) |
| `LOG_LEVEL` | 로그 레벨 | `INFO` |
| `EXPORT_DIR_ASIS` | AS-IS **IQ 서버 기준** 공유 볼륨 경로 | (필수) |
| `EXPORT_DIR_TOBE` | TO-BE **IQ 서버 기준** 공유 볼륨 경로 | (필수) |
| `EXPORT_DIR_LOCAL` | **배치 실행 호스트 기준** 마운트 경로 (FileUtil 용) | (필수) |
| `FILE_NAME_PATTERN` | 예: `{EXEC_SEQ}/{SCHEMA}.{TABLE}/{SCHEMA}.{TABLE}_{SEQ}.dat` | 좌측 예시 |
| `EXTRACT_FORMAT` | `DELIMITED` \| `BINARY` `[PoC]` | `DELIMITED` |
| `COL_DELIM` / `ROW_DELIM` | 구분자 | `\|` / `\n` |
| `FILE_SPLIT_SIZE_MB` | 파일 분할 크기 (0=분할 안 함) | `0` |
| `BLOB_TMP_DIR` | Blob 임시 경로 | — |
| `MIN_FREE_SPACE_GB` | Extract 전 공유 볼륨 최소 여유공간 | `500` |
| `SPACE_WAIT_SEC` / `SPACE_WAIT_MAX` | 여유공간 부족 시 대기 간격(초) / 최대 횟수 | `60` / `30` |
| `TRUNCATE_BEFORE_LOAD` | 적재 전 TO-BE 테이블 TRUNCATE 여부 | `N` |
| `DELETE_AFTER_LOAD` | 적재 성공 후 파일 삭제 | `Y` |
| `STOP_ON_ERROR` | 테이블 실패 시 TASK 중단 | `Y` |
| `NUM_TOLERANCE` | 부동소수·AVG 상대 허용오차 (0=완전일치) | `0` |
| `QUERY_TIMEOUT_SEC` | 쿼리 타임아웃 (0=무제한) | `0` |
| `RECONNECT_RETRY` | 커넥션 재연결 횟수 | `1` |

### 4.2 `GPCL_MIG_TABLE` (`TASK_ID`로 조회)
`TASK_ID`, `TABLE_ORD`, `SCHEMA_NM`, `TABLE_NM`, `EXTRACT_WHERE`(선택), `SPLIT_YN`, `BLOB_YN`, `USE_YN`

### 4.3 `GPCL_MIG_VRF_TARGET`
`SCHEMA_NM`, `TABLE_NM`, `VRF_ORD`, `COL_NM`, `FUNC_CD`(`COUNT|SUM|AVG|MIN|MAX`), `USE_YN`
- `COUNT(*)` 검증은 **VRF_TARGET 정의와 무관하게 항상 수행**한다.
- `VRF_ORD`는 **(컬럼, 함수) 조합마다 유일**하게 정의한다. 한 컬럼에 함수가 여러 개일 수 있어, 원안의 키(실행회차·테이블명·실행순서·컬럼명)만으로는 충돌 여지가 있기 때문이다.

### 4.4 `GPCL_MIG_LOG` (테이블 단위 실행 이력)
PK `(EXEC_SEQ, TASK_ID, SCHEMA_NM, TABLE_NM)`
`STATUS`, `EXTRACT_ROWS`, `LOAD_ROWS`, `FILE_CNT`, `FILE_BYTES`, `START_DT`, `END_DT`, `ELAPSED_SEC`, `ERR_CD`, `ERR_MSG`(4000자 절단), `EXEC_USER`, `HOST_NM`, `PID`

### 4.5 `GPCL_MIG_VRF_RESULT` (검증 결과)
PK `(EXEC_SEQ, SCHEMA_NM, TABLE_NM, VRF_ORD)`
`COL_NM`, `FUNC_CD`, `ASIS_VAL`, `TOBE_VAL`(문자열 저장), `MATCH_YN`, `ASIS_DT`, `TOBE_DT`

### 4.6 상태값 (`MigStatus` enum)
`READY → RUNNING → EXTRACTED → ASIS_VERIFIED → LOADED → SUCCESS`
종료 상태는 `FAILED`, `VERIFY_MISMATCH`이며, 상태 전이마다 META에 즉시 기록한다.

---

## 5. 처리 파이프라인 (테이블 단위 4-Step)

### Step 0 — 초기화 · 메타 로드
1. 인자 검증 → 환경변수로 META 접속 → 공통코드 로드 → `LogUtil`로 로그 레벨 적용.
2. `ConnectionFactory`로 ASIS/TOBE 연결 후 `SELECT 1`로 확인한다.
3. `GPCL_MIG_TABLE`(`TABLE_ORD` 순)과 `GPCL_MIG_VRF_TARGET`을 조회한다. 대상이 0건이면 Exit 2.
4. **중복 실행 방지** `[가정]`: `GPCL_MIG_LOG`의 행을 `UPDATE … SET STATUS='RUNNING' WHERE STATUS IN ('READY','FAILED','VERIFY_MISMATCH')`(또는 신규 INSERT)로 **원자적으로 선점**한다. 영향 행이 0이면 다른 프로세스가 처리 중이거나 이미 SUCCESS이므로 해당 테이블은 건너뛴다.
5. **재실행 정책** `[가정]`: `SUCCESS` 테이블은 skip한다. 그 외 상태는 Step 1부터 전체 재수행하며, 이때 잔여 추출 파일은 삭제하고 TO-BE 대상 테이블은 `TRUNCATE_BEFORE_LOAD` 규칙을 따른다.

### Step 1 — AS-IS 추출 (Extract)
1. `FileUtil`로 공유 볼륨 **여유공간을 확인**한다. 부족하면 `SPACE_WAIT_SEC` 간격으로 최대 `SPACE_WAIT_MAX`회 대기하고 그래도 부족하면 실패 처리한다. 동시에 여러 JVM이 볼륨을 소비하므로 이 단계가 필수다.
2. 테이블별 하위 디렉터리를 생성하고 `FILE_NAME_PATTERN`에 따라 파일명을 생성한다.
3. `SybaseIQUnloadWorker`가 `SybaseIQSqlBuilder`가 만든 `TEMP_EXTRACT_*` 옵션군을 `SET TEMPORARY OPTION`으로 설정한 뒤, SELECT를 실행하여 언로드한다. 옵션은 **`finally`에서 반드시 원복**한다(커넥션 스코프 옵션이므로 누락 시 다음 테이블에 영향).
4. `FileUtil`이 파일 존재 여부, 크기, 분할 파일 목록을 검증한다. 0건 테이블은 파일이 비거나 없을 수 있으므로 Step 2의 COUNT=0과 교차 확인한 뒤 정상으로 본다.
5. 상태를 `EXTRACTED`로 기록한다.

### Step 2 — AS-IS 검증값 산출
1. `MigrationValidator`가 **테이블당 1회 스캔 쿼리**를 만든다. COUNT(*)와 VRF_TARGET의 모든 집계를 **하나의 SELECT에 별칭(`V_<VRF_ORD>`)으로 포함**한다. 컬럼별로 쿼리를 나누면 TB급 테이블을 N번 스캔하게 되므로 지양한다.
2. 결과를 `GPCL_MIG_VRF_RESULT`에 INSERT(`ASIS_VAL`)하고 상태를 `ASIS_VERIFIED`로 기록한다.
3. `[가정]` 이관 기간 중 AS-IS 대상 테이블은 변경되지 않는다(Extract와 검증의 스냅샷 일관성). 코드 주석에 명시한다.

### Step 3 — TO-BE 적재 (Load) · 파일 정리
1. 적재 전 TO-BE 대상 테이블 건수를 확인한다. 0이 아니면 `TRUNCATE_BEFORE_LOAD=Y`일 때 TRUNCATE하고, `N`이면 `TARGET_NOT_EMPTY`로 실패 처리한다(중복 적재 방지).
2. `SybaseIQLoadWorker`가 `LOAD TABLE`을 실행한다. 분할 파일은 한 구문에서 다건으로 적재하고, 파일 오류 시 롤백되는 옵션과 컬럼 매핑을 사용한다. 적재건수를 확보한 뒤 **명시적 COMMIT**한다.
3. **성공 판정 = LOAD 정상 종료 AND 적재건수 == Step 2의 AS-IS COUNT**. 판정이 성공이면 `FileUtil`이 해당 테이블 디렉터리의 추출 파일을 즉시 삭제한다(`DELETE_AFTER_LOAD=Y`). 삭제 실패는 WARN 로그와 `FILE_CLEAN_FAIL` 표시만 남기고 작업 성공은 유지한다. 상태는 `LOADED`.
4. **실패 시**: ROLLBACK, 상세 에러 로깅, **파일 보존**, 상태 `FAILED`(`ERR_CD`, `ERR_MSG` 기록), Exit 5. 건수 불일치도 동일하게 처리한다.

### Step 4 — TO-BE 검증 · 최종 판정
1. TO-BE에서 Step 2와 **동일한 쿼리**를 실행하고 `GPCL_MIG_VRF_RESULT`를 UPDATE한다(`TOBE_VAL`, `MATCH_YN`, `TOBE_DT`).
2. 비교 규칙: NULL == NULL은 일치, NULL과 값은 불일치. 정수/DECIMAL은 `BigDecimal.compareTo`(scale 무시). 부동소수와 AVG는 `NUM_TOLERANCE` 상대오차 내 일치. 문자/날짜는 문자열 `equals`.
3. 전건 일치이면 `SUCCESS`, 하나라도 불일치이면 `VERIFY_MISMATCH`(Exit 6)로 기록한다. `START/END`, `ELAPSED_SEC`, `EXTRACT_ROWS`, `LOAD_ROWS`를 최종 기록한다.

---

## 6. Sybase IQ 구현 유의사항

1. **Unload/Load 파일 I/O는 IQ 서버 프로세스가 수행**한다. SQL에 들어가는 경로(`EXPORT_DIR_ASIS`/`EXPORT_DIR_TOBE`)는 각 IQ 서버 OS 기준이고, Java의 `FileUtil`은 배치 실행 호스트의 마운트 경로(`EXPORT_DIR_LOCAL`)로 접근한다. 세 경로를 혼용하지 않는다. `[가정]` 배치 실행 호스트도 공유 볼륨을 마운트한다.
2. 추출 옵션은 `TEMP_EXTRACT_*` 계열(파일명, 구분자, 분할 크기 등)을 사용한다. 옵션명을 확신할 수 없는 항목은 `TODO(VERIFY-IQ)`로 표시하고 문자열을 공통코드로 뺄 수 있게 한다.
3. `[PoC]` **HP-UX ↔ Linux 간 엔디안·문자셋 차이**로 BINARY 포맷의 교차 호환성과 문자 인코딩이 영향받을 수 있다. 기본은 `DELIMITED`이고, `EXTRACT_FORMAT=BINARY` 분기는 구현하되 `TODO(POC-1)`로 표시한다.
4. 언로드 SELECT는 결과셋이 비어 있다. Mapper 선언 방식(`select` vs `update` + `STATEMENT`)을 선택한 이유를 주석으로 남긴다.
5. `[PoC]` 분할 파일의 실제 명명 규칙은 IQ 동작을 기준으로 확인해야 하므로, 파일 수집은 이름 추측 대신 **디렉터리 + prefix 스캔**으로 구현한다.
6. Blob(`BLOB_YN='Y'`) 처리 `[가정]`: 이번 Phase 범위에서는 `BlobHandler` 인터페이스(확장 포인트)만 두고, Blob 테이블은 `FAILED(ERR_CD=BLOB_NOT_SUPPORTED)`로 처리한다.

---

## 7. 산출물: 클래스 · 매퍼 책임

### 7.1 지정 클래스 (필수)

| 파일 | 책임 |
|---|---|
| `MigrationWorkerApplication` | CLI 파싱, 라이프사이클, Shutdown Hook, Exit Code 제어 |
| `MigrationWorker` | Step 0~4 오케스트레이션, 테이블 루프, 상태 동기화, 에러 핸들링 |
| `ConnectionFactory` | 공통코드 기반 META/ASIS/TOBE `SqlSessionFactory` 및 Connection 생성·제공·반환·재연결 |
| `SybaseIQUnloadWorker` | AS-IS 언로드 (`TEMP_EXTRACT_*` 설정/원복, 언로드 실행) |
| `SybaseIQLoadWorker` | TO-BE `LOAD TABLE` 실행, 적재건수 반환, 커밋/롤백 |
| `MigrationValidator` | 검증 쿼리 실행, AS-IS vs TO-BE 비교 판정 |
| `SybaseIQSqlBuilder` | Unload/Load/집계 SQL 생성, 식별자·경로 검증 |
| `FileUtil` | 여유공간 조회, 존재·크기 검증, prefix 스캔, 안전 삭제 |
| `LogUtil` | Logback 레벨 동적 적용, MDC 헬퍼, 마스킹 |
| `SqlUtil` | 자원 해제, 예외 래핑 |

### 7.2 허용되는 보조 클래스
`model` 패키지의 DTO(`MigTable`, `VrfTarget`, `VrfResult`, `MigLog`)와 enum(`MigStatus`, `ExitCode`, `VrfFunc`), `exception.MigrationException`(Step, ExitCode 보유), `PasswordDecoder`, `BlobHandler`, 그리고 Mapper 인터페이스(`MetaMapper`, `SybaseIQMapper`).

### 7.3 매퍼 / 스크립트
- `MetaMapper.xml`: 공통코드 조회, 대상 테이블·검증 대상 조회, 이력 선점·상태 변경, 검증 결과 INSERT/UPDATE
- `SybaseIQMapper.xml` (ASIS/TOBE 공용): 추출 옵션 설정/해제, Unload 실행, `LOAD TABLE` 실행, 건수 조회, 동적 집계 쿼리 실행, `TRUNCATE`, `SELECT 1`
- `run_migration.sh`: 인자 전달, 환경변수 검증, classpath, JVM 옵션(`-Xms/-Xmx`, `-Dlog.dir`, `-Dfile.encoding=UTF-8`), Exit Code 수신 및 GODIS 반환, 인자 누락 시 사용법 출력
- `ddl_meta.sql`: 4장 가정 DDL (`GPCL_MIG_LOG`, `GPCL_MIG_VRF_RESULT` 포함)

---

## 8. 출력 Phase (한 번에 하나씩, "다음" 입력 시 진행)

| Phase | 내용 |
|---|---|
| 1 | `pom.xml`, 디렉터리 트리, model/enum/exception, `LogUtil`, `SqlUtil`, `FileUtil`, `logback.xml`, `ddl_meta.sql`, 처리 흐름 요약(텍스트 시퀀스) |
| 2 | Mapper 인터페이스 2종 + `MetaMapper.xml` + `SybaseIQMapper.xml` |
| 3 | `ConnectionFactory`, `SybaseIQSqlBuilder`, `SybaseIQUnloadWorker`, `SybaseIQLoadWorker`, `MigrationValidator` |
| 4 | `MigrationWorker`, `MigrationWorkerApplication`, `run_migration.sh` |
| 5 | 단위 테스트(`SybaseIQSqlBuilder`, `MigrationValidator` 비교 규칙, `FileUtil`; DB 불필요) + README(빌드·배포·운영 체크리스트) |

---

## 9. 완료 기준 (Phase 4 종료 시 자체 점검표로 출력)

- [ ] Spring/Hikari 등 금지 의존성이 pom에 없다.
- [ ] 모든 종료 경로(정상, 예외, SIGTERM)에서 Connection·Statement·Session 누수가 없다.
- [ ] 모든 실패 경로가 3장의 Exit Code 표와 일치한다. `System.exit`는 Application 클래스에서만 호출한다.
- [ ] 스키마·테이블·컬럼·경로가 `${}`로 들어가기 전에 화이트리스트 검증을 거친다.
- [ ] 로그와 예외 메시지에 비밀번호가 노출되지 않는다.
- [ ] 파일 삭제는 "LOAD 성공 AND 적재건수 == AS-IS COUNT"일 때만 일어나고, 실패 시 파일이 보존된다.
- [ ] 0건 테이블, 분할 파일, 재실행(중복 적재 방지), 중복 실행(선점 실패) 시나리오가 처리된다.
- [ ] 검증 쿼리가 테이블당 1회 스캔이다.
- [ ] 모든 상태 전이가 META에 기록되고 `ERR_CD`/`ERR_MSG`가 남는다.
- [ ] 모든 `ASSUMPTION` / `TODO(VERIFY-IQ)` / `TODO(POC)` 항목이 최종 목록으로 정리된다.

---

## 부록. 프롬프트 투입 전 확정 체크리스트 (작성자용 — 투입 시 삭제 가능)

아래 항목을 확정하면 본문의 `[가정]`을 `[확정]`으로 바꾸고 값을 교체하십시오. 미확정이어도 기본값으로 진행됩니다.

1. `GPCL_CM_CD_VAL`, `GPCL_MIG_TABLE`, `GPCL_MIG_VRF_TARGET`의 **실제 DDL** (컬럼명이 다르면 4장을 통째로 교체)
2. 이력·검증결과 테이블(`GPCL_MIG_LOG`, `GPCL_MIG_VRF_RESULT`)이 이미 존재하는지, 명칭·구조는 무엇인지
3. Java 버전(8 / 11 / 17)과 빌드 도구(Maven / Gradle), 사용 Sybase JDBC 드라이버 종류
4. Meta 접속정보 전달 방식(환경변수 vs GODIS가 제공하는 방식)
5. 배치 실행 호스트의 공유 볼륨 마운트 여부, 세 경로(AS-IS 서버 / TO-BE 서버 / 배치 호스트)의 실제 값
6. Blob 테이블의 범위와 처리 방식
7. 이관 중 AS-IS 테이블 변경 여부 (변경 가능하면 Step 2를 Step 1 앞으로 재배치 검토)
8. 재실행 정책 (TO-BE 선 TRUNCATE 허용 여부)
9. 추출 포맷(DELIMITED / BINARY)과 문자셋 — HP-UX ↔ Linux PoC 결과
10. 공유 볼륨 여유공간 임계치와 동시 실행 JVM 수 상한
