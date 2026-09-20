첨부된 프롬프트를 기준으로 보면, 현재 문서는 **프로젝트 초기환경과 클래스 목록은 잘 정의되어 있지만**, 실제 코드 생성 단계에서 LLM이 임의로 해석할 여지가 몇 군데 있습니다. 특히 **Pure Java + MyBatis + 3 Connection + Sybase IQ Native SQL + Batch Group 단위 처리**를 명확히 고정하는 것이 중요합니다. 

아래처럼 프롬프트를 재구성하는 것을 권장합니다.

# [ROLE]

당신은 **대용량 데이터 이관, Sybase IQ, Java/JDBC, MyBatis, 배치 아키텍처 및 데이터 검증**에 전문성을 가진 Senior Java/DBA Engineer이다.

아래 요구사항과 제약사항을 임의로 변경하거나 생략하지 말고 준수하여,
**Sybase IQ 16.0 → Sybase IQ 16.2 대용량 데이터 이관용 Pure Java 기반 경량 Batch Worker**의 실행 가능한 프로젝트 전체 소스를 생성한다.

---

# 1. 프로젝트 목표

HP-UX에서 운영 중인 **Sybase IQ 16.0(AS-IS)**의 대용량 데이터를
Linux 환경의 **Sybase IQ 16.2(TO-BE)**로 이관한다.

## 규모

* 대상 테이블: 약 13,000개
* 총 데이터: 약 250TB
* 공유 볼륨: 50TB
* AS-IS / TO-BE는 동일 네트워크에 존재
* AS-IS와 TO-BE 테이블/컬럼 구조는 기본적으로 동일
* 이관 대상과 검증 대상은 MariaDB 메타데이터로 관리

## 실행 구조

```text
GODIS
  │
  │ Shell 실행
  ▼
MigrationWorkerApplication
  │
  ▼
MigrationWorker
  │
  ├── Batch Group
  │     ├── Table 1
  │     ├── Table 2
  │     ├── Table 3
  │     └── ...
  │
  ├── AS-IS Sybase IQ 16.0
  │
  ├── Shared Volume 50TB
  │
  └── TO-BE Sybase IQ 16.2
```

**하나의 Batch Group에서 여러 테이블을 순차적으로 처리할 수 있도록 설계한다.**

---

# 2. 기술 스택 및 절대 제약사항

## 2.1 Pure Java

Spring Boot, Spring Framework, Spring Batch를 사용하지 않는다.

사용 가능한 구성:

* Java SE
* JDBC
* MyBatis
* SLF4J
* Logback

다음 라이브러리는 사용하지 않는다.

* Spring Boot
* Spring Framework
* Spring Batch
* HikariCP
* 기타 Connection Pool
* 무거운 DI Framework

프로그램 시작점은 반드시 다음 형태로 한다.

```java
public static void main(String[] args)
```

클래스:

```text
com.migration.MigrationWorkerApplication
```

---

# 3. 실행 인터페이스

Shell에서 다음과 같이 실행한다.

```bash
java -cp ... com.migration.MigrationWorkerApplication \
    <EXEC_SEQ> \
    <TASK_ID> \
    <EXEC_USER>
```

## Arguments

| index | 이름        | 필수  | 설명               |
| ----- | --------- | --- | ---------------- |
| 0     | EXEC_SEQ  | YES | 배치 실행 차수         |
| 1     | TASK_ID   | YES | GODIS 배치 TASK_ID |
| 2     | EXEC_USER | NO  | 실행자. 기본값 SYSTEM  |

잘못된 인자가 전달되면 명확한 오류 메시지를 출력하고 비정상 Exit Code를 반환한다.

---

# 4. Connection 관리 원칙

Connection Pool을 사용하지 않는다.

MigrationWorker의 작업 단위에서 다음 **3개의 Connection**을 명시적으로 생성하고 관리한다.

```text
META Connection
 └── MariaDB / GODIS

ASIS Connection
 └── Sybase IQ 16.0

TOBE Connection
 └── Sybase IQ 16.2
```

## 중요

Connection은 매 테이블마다 생성하지 않는다.

Batch Group 또는 Worker의 작업 주기 동안 재사용한다.

잘못된 구현:

```java
for (TableInfo table : tables) {
    Connection conn = createConnection();
    ...
    conn.close();
}
```

권장 구현:

```java
Connection asisConn = createConnection();
Connection tobeConn = createConnection();

for (TableInfo table : tables) {
    process(table);
}

close(asIsConn);
close(toBeConn);
```

단, 장시간 장애/timeout 등으로 Connection이 유효하지 않을 경우를 고려하여 명확한 예외 처리 및 재연결 전략을 설계한다.

---

# 5. Statement / PreparedStatement 사용 원칙

SQL 종류에 따라 적절한 JDBC 객체를 선택한다.

## Statement 사용

다음 SQL은 `Statement`를 기본으로 사용한다.

```sql
SET TEMPORARY OPTION ...
```

```sql
LOAD TABLE ...
```

```sql
UNLOAD / EXTRACT 관련 Sybase IQ Native SQL
```

테이블명, 컬럼명, 파일 경로 등 SQL Identifier가 동적으로 변경되는 SQL에는 `PreparedStatement`의 `?`를 사용하지 않는다.

예:

```sql
LOAD TABLE DBA.CUSTOMER ...
```

여기서 다음과 같은 SQL은 생성하지 않는다.

```sql
LOAD TABLE DBA.?
```

---

## PreparedStatement 사용

다음 조건에 해당하면 `PreparedStatement`를 사용한다.

* 동일 SQL 구조를 반복 실행
* WHERE 조건값만 변경
* 일반적인 데이터값을 bind parameter로 전달
* 반복 검증 SQL의 parameter binding이 가능한 경우

예:

```sql
SELECT COUNT(*),
       SUM(AMOUNT),
       MIN(AMOUNT),
       MAX(AMOUNT),
       AVG(AMOUNT)
FROM DBA.CUSTOMER
WHERE BUSINESS_DATE >= ?
  AND BUSINESS_DATE < ?
```

---

# 6. SQL Injection 및 동적 SQL 규칙

테이블명, 컬럼명, 스키마명, 파일 경로 등 Identifier는 단순 문자열 입력으로 취급하지 않는다.

동적 SQL 생성 시 다음을 수행한다.

1. 메타데이터에서 값 조회
2. 허용 문자 검증
3. 필요한 경우 Identifier quoting
4. SQL 생성
5. 실행

특히 다음 값은 검증한다.

```text
schemaName
tableName
columnName
filePath
fileName
```

사용자가 직접 전달한 임의 SQL을 실행하는 구조는 구현하지 않는다.

---

# 7. MariaDB Metadata

모든 환경설정은 Local Property File에 하드코딩하지 않는다.

최초 META Connection을 이용하여:

```text
GPCL_CM_CD_VAL
```

에서 다음 정보를 조회한다.

```text
GRP_CD_ID = 'MIGRATION_PROPERTIES'
```

조회 대상:

* MariaDB JDBC URL
* MariaDB User
* MariaDB Password
* MariaDB Driver
* AS-IS JDBC URL
* AS-IS User
* AS-IS Password
* AS-IS Driver
* TO-BE JDBC URL
* TO-BE User
* TO-BE Password
* TO-BE Driver
* LOG_LEVEL
* Shared Volume 기본 경로
* 파일명 생성 규칙
* Binary 임시 저장 경로
* 기타 Migration Property

Password 등 민감정보는 로그에 출력하지 않는다.

---

# 8. Migration Metadata

## GPCL_MIG_TABLE

`TASK_ID`를 기준으로 이관 대상 테이블을 조회한다.

다음과 같은 정보를 Java 객체로 관리한다.

```text
schemaName
tableName
executionOrder
extractOption
splitYn
blobYn
fileName
filePath
whereCondition
기타 이관 관련 속성
```

실제 컬럼명은 제공된 Metadata 정의를 우선한다.

정의가 제공되지 않은 컬럼은 임의로 만들어내지 말고 TODO 또는 명확한 확장 지점으로 표시한다.

---

## GPCL_MIG_VRF_TARGET

테이블별 검증 대상 컬럼과 검증 함수를 관리한다.

예:

```text
TABLE_NAME
COLUMN_NAME
VERIFY_FUNCTION
EXECUTION_ORDER
```

지원 검증 함수:

```text
COUNT
SUM
AVG
MIN
MAX
```

검증 대상 컬럼이 여러 개일 수 있음을 고려한다.

---

# 9. 전체 처리 Pipeline

전체 처리는 반드시 다음 Step 구조를 따른다.

```text
Step 0. Initialize
      ↓
Step 1. AS-IS Extract
      ↓
Step 2. AS-IS Verify
      ↓
Step 3. TO-BE Load
      ↓
Step 4. TO-BE Verify & Finish
```

---

# 10. Step 0 - Initialize

다음 순서로 처리한다.

```text
1. CLI Arguments 검증
2. 최소 META Connection 생성
3. GPCL_CM_CD_VAL 조회
4. Runtime Configuration 생성
5. Log Level 적용
6. ASIS Connection 생성
7. TOBE Connection 생성
8. GPCL_MIG_TABLE 조회
9. GPCL_MIG_VRF_TARGET 조회
10. Batch Group 구성
11. MariaDB 작업 상태 RUNNING 기록
```

초기화 실패 시:

```text
FAILED
Exit Code != 0
```

으로 종료한다.

---

# 11. Step 1 - AS-IS Extract

각 테이블에 대해 다음 작업을 수행한다.

```text
Table Start
    ↓
Extract Option 설정
    ↓
Sybase IQ Native Extract / Unload
    ↓
Shared Volume 파일 생성
    ↓
파일 존재 여부 확인
    ↓
파일 크기 확인
```

Sybase IQ 16.0의 Native Extract 기능을 사용한다.

예:

```sql
SET TEMPORARY OPTION TEMP_EXTRACT_NAME1 = ...;
SET TEMPORARY OPTION TEMP_EXTRACT_BINARY = 'ON';
```

추출 SQL은 Sybase IQ 문법에 맞게 생성한다.

---

# 12. Step 2 - AS-IS Verification

추출 후 AS-IS DB에서 검증값을 계산한다.

예:

```sql
SELECT
    COUNT(*) AS ROW_COUNT,
    SUM(AMOUNT) AS SUM_AMOUNT,
    MIN(AMOUNT) AS MIN_AMOUNT,
    MAX(AMOUNT) AS MAX_AMOUNT,
    AVG(AMOUNT) AS AVG_AMOUNT
FROM DBA.CUSTOMER
WHERE ...
```

검증 SQL은 `GPCL_MIG_VRF_TARGET`에 정의된 컬럼과 함수를 기준으로 동적으로 생성한다.

결과:

```text
ASIS Validation Result
```

를 메모리에 보관하고 필요한 실행 이력/검증 테이블에 기록한다.

NULL 처리 규칙을 명확하게 구현한다.

예:

```text
COUNT
SUM
AVG
MIN
MAX
```

각 aggregate 결과가 NULL일 수 있음을 고려한다.

---

# 13. Step 3 - TO-BE Load

TO-BE Sybase IQ 16.2에서 `LOAD TABLE`을 실행한다.

예:

```sql
LOAD TABLE DBA.CUSTOMER
(
    COL1,
    COL2,
    COL3
)
FROM '/migration/CUSTOMER/data_001.dat'
FORMAT BINARY;
```

파일 분할이 활성화된 경우 여러 파일을 처리할 수 있도록 설계한다.

---

# 14. 파일 삭제 규칙

**LOAD 성공을 확인한 이후에만 파일을 삭제한다.**

```text
LOAD 성공
   ↓
파일 존재 확인
   ↓
파일 삭제
   ↓
삭제 성공 여부 기록
```

LOAD 실패:

```text
LOAD 실패
   ↓
파일 유지
   ↓
ERROR 로그
   ↓
FAILED 상태 기록
   ↓
Exit Code != 0
```

파일 삭제 실패는 반드시 상세 로그로 남긴다.

50TB Shared Volume을 순환 사용해야 하므로 파일 삭제 시점은 중요하다.

---

# 15. Step 4 - TO-BE Verification

TO-BE에서 Step 2와 동일한 검증 SQL을 실행한다.

검증 결과:

```text
TOBE Validation Result
```

을 생성한다.

이후 AS-IS와 TO-BE를 비교한다.

---

# 16. Validation 비교 규칙

단순 문자열 비교를 하지 않는다.

데이터 타입별 비교 정책을 정의한다.

예:

```text
COUNT → 정확히 일치
SUM   → 정확히 일치 또는 Numeric Precision 정책 적용
MIN   → 값 비교
MAX   → 값 비교
AVG   → Precision / Scale 허용오차 적용 가능
```

AVG와 Floating Point 계열 데이터의 경우 무조건 `equals()`로 비교하지 않는다.

검증 결과는 최소한 다음 정보를 포함한다.

```text
tableName
columnName
functionName
asisValue
tobeValue
matchYn
difference
message
```

---

# 17. 최종 상태

검증 결과에 따라 MariaDB 상태를 갱신한다.

```text
SUCCESS
```

또는

```text
VERIFY_MISMATCH
```

또는

```text
FAILED
```

권장 상태 흐름:

```text
READY
  ↓
RUNNING
  ↓
EXTRACTING
  ↓
EXTRACTED
  ↓
ASIS_VERIFYING
  ↓
ASIS_VERIFIED
  ↓
LOADING
  ↓
LOADED
  ↓
TOBE_VERIFYING
  ↓
SUCCESS
```

오류 발생 시:

```text
FAILED
```

로 변경한다.

---

# 18. Batch Group 처리

하나의 TASK_ID에 여러 테이블이 존재할 수 있다.

예:

```text
TASK_ID = TASK001

01 CUSTOMER
02 ACCOUNT
03 TRANSACTION
04 PRODUCT
...
```

각 테이블을 독립적인 Migration Task로 취급한다.

권장 구조:

```text
BatchGroup
 │
 ├── TableMigrationTask #1
 ├── TableMigrationTask #2
 ├── TableMigrationTask #3
 └── ...
```

각 테이블의 성공/실패 결과를 개별적으로 기록한다.

한 테이블 실패 시 이후 테이블 처리 정책을 명확히 구현한다.

기본값은:

```text
현재 테이블 FAILED
→ Batch Group 중단
→ GODIS에 비정상 종료 반환
```

으로 한다.

단, 향후 `CONTINUE_ON_ERROR` 정책을 추가할 수 있도록 확장 가능한 구조로 작성한다.

---

# 19. Connection / Statement Lifecycle

다음 원칙을 반드시 준수한다.

```text
Connection
    → Batch Group / Worker 단위 재사용

Statement
    → SQL 작업 단위에서 생성/사용/close

PreparedStatement
    → 동일 SQL 구조 반복 실행 시 재사용 가능

ResultSet
    → 조회 직후 사용 후 즉시 close
```

모든 JDBC 리소스는 반드시 `try-with-resources`를 사용한다.

예:

```java
try (Statement stmt = conn.createStatement()) {
    stmt.execute(sql);
}
```

또는:

```java
try (
    PreparedStatement pstmt = conn.prepareStatement(sql);
    ResultSet rs = pstmt.executeQuery()
) {
    ...
}
```

---

# 20. Transaction 정책

Sybase IQ의 Extract / Load 특성을 고려하여 Transaction 경계를 명확히 정의한다.

특히 다음을 구분한다.

```text
Metadata Transaction
Extract Transaction
Load Transaction
Validation Transaction
Status Update Transaction
```

`LOAD TABLE`의 Commit 처리 방식과 JDBC `autoCommit` 정책을 명시적으로 구현한다.

Transaction이 필요하지 않은 작업까지 장시간 Transaction으로 유지하지 않는다.

---

# 21. MyBatis 사용 원칙

MyBatis XML을 사용한다.

## MetaMapper.xml

다음 SQL을 관리한다.

```text
GPCL_CM_CD_VAL 조회
GPCL_MIG_TABLE 조회
GPCL_MIG_VRF_TARGET 조회
작업 상태 INSERT
작업 상태 UPDATE
검증 결과 INSERT
검증 결과 UPDATE
```

## SybaseIQMapper.xml

Sybase IQ 관련 SQL을 관리한다.

단, 테이블명/컬럼명/파일 경로가 동적인 SQL은 MyBatis `${}` 사용 시 SQL Injection 가능성을 고려하여 반드시 사전 검증된 Metadata만 허용한다.

가능한 경우 값은 `#{}` parameter binding을 사용한다.

---

# 22. 클래스 구조

다음 클래스를 기본 구조로 구현한다.

```text
com.migration
│
├── MigrationWorkerApplication
├── MigrationWorker
│
├── config
│   ├── MigrationConfig
│   └── ConnectionFactory
│
├── metadata
│   ├── MetaMapper
│   ├── TableInfo
│   └── ValidationTarget
│
├── sybase
│   ├── SybaseIQUnloadWorker
│   ├── SybaseIQLoadWorker
│   └── SybaseIQSqlBuilder
│
├── validation
│   ├── MigrationValidator
│   └── ValidationResult
│
├── util
│   ├── FileUtil
│   ├── LogUtil
│   └── SqlUtil
│
└── exception
    ├── MigrationException
    ├── ExtractException
    ├── LoadException
    └── ValidationException
```

필요한 DTO/Enum은 추가할 수 있다.

---

# 23. 핵심 클래스 책임

## MigrationWorkerApplication

책임:

* main()
* CLI argument parsing
* Worker 생성
* Worker 실행
* 예외 처리
* System.exit()

---

## MigrationWorker

책임:

* 전체 Pipeline orchestration
* Step 순서 제어
* Batch Group 제어
* Table 단위 처리
* 상태 변경
* 오류 처리

---

## ConnectionFactory

책임:

* META Connection 생성
* ASIS Connection 생성
* TOBE Connection 생성
* Driver loading
* Connection lifecycle 관리

Connection Pool은 구현하지 않는다.

---

## SybaseIQUnloadWorker

책임:

* Extract Option 설정
* Extract SQL 생성
* AS-IS 실행
* 파일 생성 확인

---

## SybaseIQLoadWorker

책임:

* LOAD TABLE SQL 생성
* TO-BE 실행
* LOAD 성공 여부 확인
* 파일 삭제 호출

---

## MigrationValidator

책임:

* 검증 SQL 생성/실행
* AS-IS 검증
* TO-BE 검증
* 결과 비교
* mismatch 상세정보 생성

---

## SybaseIQSqlBuilder

책임:

```text
buildExtractOptionSql()
buildExtractSql()
buildLoadSql()
buildValidationSql()
```

SQL 문자열 생성만 담당한다.

DB Connection을 직접 관리하지 않는다.

---

# 24. FileUtil

다음 기능을 제공한다.

```text
exists()
getFileSize()
isReadable()
deleteSafely()
```

파일 삭제 전에 존재 여부와 파일 크기를 확인한다.

삭제 실패 시 Exception을 숨기지 않는다.

---

# 25. Logging

SLF4J + Logback을 사용한다.

로그에는 최소한 다음 Context를 포함한다.

```text
EXEC_SEQ
TASK_ID
EXEC_USER
TABLE_NAME
EXECUTION_ORDER
STEP
```

예:

```text
[EXEC_SEQ=1001]
[TASK_ID=TASK001]
[TABLE=CUSTOMER]
[STEP=LOAD]
LOAD TABLE started
```

Password, Connection String의 비밀번호 등 민감정보는 절대 출력하지 않는다.

---

# 26. Exit Code

최소한 다음을 정의한다.

```text
0  = SUCCESS
1  = INVALID_ARGUMENT
2  = INITIALIZATION_ERROR
3  = EXTRACT_ERROR
4  = LOAD_ERROR
5  = VALIDATION_MISMATCH
6  = VALIDATION_ERROR
9  = SYSTEM_ERROR
```

GODIS가 Shell의 Exit Code를 정상적으로 받을 수 있도록 한다.

---

# 27. 예외 처리

다음 원칙을 따른다.

```text
Exception 발생
    ↓
현재 Table 상태 FAILED
    ↓
MariaDB 상태 기록
    ↓
ERROR 로그
    ↓
필요한 파일 보존
    ↓
Connection / Statement / ResultSet close
    ↓
상위 Worker 전달
    ↓
MigrationWorkerApplication에서 Exit Code 결정
```

예외를 단순히:

```java
catch (Exception e) {
    e.printStackTrace();
}
```

로 처리하지 않는다.

---

# 28. 성능 요구사항

250TB / 13,000 tables 규모를 고려한다.

다음 원칙을 적용한다.

1. Connection을 테이블마다 생성하지 않는다.
2. Statement를 불필요하게 장기간 공유하지 않는다.
3. 동일 SQL 반복 시 PreparedStatement 재사용을 고려한다.
4. ResultSet을 즉시 close한다.
5. 대용량 데이터를 Java 메모리에 적재하지 않는다.
6. Sybase IQ의 Native Extract / LOAD TABLE을 사용한다.
7. Extract 파일 전체를 Java byte[]로 읽지 않는다.
8. Shared Volume 파일은 LOAD 성공 후 즉시 삭제한다.
9. 로그에 대량 데이터를 출력하지 않는다.
10. 테이블별 처리시간과 건수를 측정한다.

---

# 29. 메모리 사용 원칙

250TB 전체 데이터를 JVM Heap에 적재하지 않는다.

Java 프로그램은 다음 역할에 집중한다.

```text
Metadata 관리
+
SQL 생성
+
DB Command 실행
+
File 상태 확인
+
Validation
+
상태 관리
```

실제 데이터 이동은:

```text
Sybase IQ
    ↓
Native Extract
    ↓
Shared Volume
    ↓
LOAD TABLE
    ↓
Sybase IQ
```

로 처리한다.

---

# 30. 동시성

초기 구현에서는 **하나의 Worker 내부 테이블 처리를 순차 실행**한다.

```text
Table 1
  ↓
Table 2
  ↓
Table 3
```

GODIS의 멀티스레드/복수 Job 실행을 통해 상위 수준의 병렬 처리를 수행할 수 있도록 설계한다.

따라서 MigrationWorker 내부에서 임의로 대규모 Thread Pool을 생성하지 않는다.

향후 Parallel Table Migration을 추가할 수 있도록 인터페이스를 확장 가능하게 설계한다.

---

# 31. 생성해야 하는 파일

최종 결과물은 최소 다음 구조를 갖는다.

```text
migration-worker/
│
├── pom.xml
│
├── src/main/java/
│   └── com/migration/
│       ├── MigrationWorkerApplication.java
│       ├── MigrationWorker.java
│       ├── config/
│       ├── metadata/
│       ├── sybase/
│       ├── validation/
│       ├── util/
│       └── exception/
│
├── src/main/resources/
│   ├── mapper/
│   │   ├── MetaMapper.xml
│   │   └── SybaseIQMapper.xml
│   ├── mybatis-config.xml
│   └── logback.xml
│
└── bin/
    └── run_migration.sh
```

---

# 32. Maven 의존성

불필요한 의존성을 추가하지 않는다.

필요한 경우:

```text
MyBatis
MariaDB JDBC Driver
Sybase IQ JDBC Driver
SLF4J
Logback
```

만 사용한다.

각 Dependency의 Version은 임의로 최신 버전을 선택하지 말고 프로젝트에서 지정한 호환 버전을 사용한다.

버전 정보가 제공되지 않은 경우:

```text
TODO: 프로젝트 표준 버전 확인 필요
```

라고 명시한다.

---

# 33. 코드 생성 규칙

코드는 다음 기준으로 작성한다.

* Java 8 이상에서 동작하도록 작성
* 명확한 package 구조
* 의미 있는 변수명
* Magic Number 최소화
* 상수는 별도 관리
* public method에는 JavaDoc 작성
* 예외 메시지는 실행 Context 포함
* SQL은 읽기 쉽게 formatting
* 한 메서드가 지나치게 많은 책임을 갖지 않도록 한다
* DB Connection을 static global singleton으로 만들지 않는다
* ThreadLocal Connection을 사용하지 않는다
* Connection Pool을 구현하지 않는다

---

# 34. 중요: 구현 전 검증

코드를 생성하기 전에 먼저 다음을 수행한다.

## 1단계

요구사항에서 모순/누락/불명확한 부분을 찾아 표로 정리한다.

```text
번호
요구사항
문제점
영향
권장 해결방법
```

## 2단계

확정된 가정과 실제 제공된 요구사항을 분리한다.

```text
[확정 요구사항]
...

[추가 가정]
...

[확인 필요]
...
```

## 3단계

확인 필요 항목이 있어도 코드 생성이 가능한 경우에는 임의로 사실을 만들어내지 말고 명확한 TODO/Extension Point로 처리한다.

---

# 35. 최종 산출물

다음 순서로 결과를 출력한다.

## 1. Architecture

전체 구조와 데이터 흐름을 Mermaid로 제시한다.

## 2. Project Tree

전체 프로젝트 구조를 제시한다.

## 3. Requirement Issues

모호하거나 충돌하는 요구사항을 정리한다.

## 4. Source Code

각 Java/XML/Shell 파일의 전체 소스를 제공한다.

생략부호:

```text
...
```

로 핵심 코드를 생략하지 않는다.

## 5. Build

```bash
mvn clean package
```

등 실제 빌드 방법을 제공한다.

## 6. Execution

```bash
./run_migration.sh EXEC_SEQ TASK_ID EXEC_USER
```

형태의 실행 방법을 제공한다.

## 7. Error Handling

대표적인 오류 상황과 처리 방식을 설명한다.

## 8. Test

최소 다음 테스트 시나리오를 제공한다.

```text
정상 단일 테이블
정상 다중 테이블
Extract 실패
파일 생성 실패
Load 실패
파일 삭제 실패
ASIS 검증 실패
TOBE 검증 실패
Validation mismatch
Connection 실패
Metadata 조회 실패
잘못된 CLI Argument
```

---

# 36. 최종 구현 원칙

가장 중요한 원칙은 다음과 같다.

```text
GODIS
  ↓
Shell
  ↓
Pure Java MigrationWorker
  ↓
Metadata
  ↓
┌─────────────────────────────┐
│ Batch Group                 │
│                             │
│ Table                       │
│  ├─ Extract                 │
│  ├─ ASIS Verify             │
│  ├─ Load                    │
│  ├─ TOBE Verify             │
│  └─ Result                  │
└─────────────────────────────┘
  ↓
GODIS Result
```

Java는 **데이터 자체를 이동하는 프로그램이 아니라 Sybase IQ Native Extract/Load를 orchestration하고 이관 결과를 검증·관리하는 경량 Worker**로 구현한다.

특히 다음을 반드시 지킨다.

> **Connection은 작업 단위에서 재사용한다.**

> **Statement는 Sybase IQ Native Command 실행에 사용한다.**

> **PreparedStatement는 반복 SQL 및 값 Parameter Binding이 가능한 경우에 사용한다.**

> **대용량 데이터 자체를 Java Heap으로 가져오지 않는다.**

> **AS-IS와 TO-BE의 검증 결과를 정량적으로 비교한다.**

> **LOAD 성공 전에는 공유 볼륨 파일을 삭제하지 않는다.**

> **코드 생성 시 제공되지 않은 DB 컬럼명, SQL 문법, 설정값을 임의로 만들어내지 않는다.**

### 이번 최적화에서 특히 중요한 변경점

원본은 이미 **4-Step Pipeline, 3개 Connection, MyBatis, Sybase IQ Native Extract/Load**가 잘 정의되어 있습니다. 

다만 실제 LLM에게 코드를 생성시키기에는 다음 부분을 더 강하게 고정하는 것이 좋습니다.

1. **Batch Group → Table Migration Task** 계층을 명시했습니다.
2. 앞서 질문하신 내용을 반영해 **Connection / Statement / PreparedStatement의 lifecycle**을 명시했습니다.
3. `LOAD TABLE DBA.?` 같은 잘못된 PreparedStatement 사용을 방지했습니다.
4. `GPCL_MIG_TABLE`에 없는 컬럼을 LLM이 임의로 만들어내지 못하도록 했습니다.
5. **250TB 데이터를 Java Heap으로 가져오지 않는다는 원칙**을 명시했습니다.
6. Extract 실패 / Load 실패 / 파일 삭제 실패 / Validation mismatch를 각각 구분했습니다.
7. **코드 생성 전에 요구사항의 모순·누락을 먼저 분석하도록** 했습니다.
8. 실제 소스 생성 시 `...`으로 코드를 생략하지 못하도록 했습니다.
9. 초기에는 **Worker 내부 순차 처리 + GODIS 레벨 병렬화**로 고정했습니다.
10. 가장 중요한 부분인 **Sybase IQ Native Extract/Load를 Java가 직접 데이터 이동하는 것이 아니라 orchestration하는 구조**를 명시했습니다.

원본에서 이미 정의한 주요 클래스들도 그대로 살렸습니다. 예를 들어 `MigrationWorker`, `ConnectionFactory`, `SybaseIQUnloadWorker`, `SybaseIQLoadWorker`, `MigrationValidator`, `SybaseIQSqlBuilder`, `FileUtil`, `LogUtil`, `SqlUtil` 등이 요청 산출물로 정의되어 있습니다. 

**실제로 Claude Code / Qwen Code / Codex에 넣을 목적이라면**, 위 프롬프트를 한 번에 코드 전체를 생성하도록 사용하는 것보다 **① 프로젝트 골격 생성 → ② Metadata/MyBatis → ③ Connection/SQL → ④ Extract/Load → ⑤ Validation → ⑥ 통합 테스트**의 6개 프롬프트로 분리하는 것이 훨씬 안정적입니다. 특히 현재처럼 13,000개 테이블/250TB 규모에서는 한 번의 거대한 코드 생성보다 단계별 검증을 넣는 편이 좋습니다.
