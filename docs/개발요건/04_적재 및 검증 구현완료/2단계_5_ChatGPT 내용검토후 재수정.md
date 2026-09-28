# Sybase IQ 16.0 → 16.2 대용량 데이터 이관 Pure Java Worker 개발 Master Prompt

## 0. 작업 지시

당신은 **Sybase IQ, Java/JDBC, MyBatis, 대용량 데이터 이관, 배치 시스템 및 데이터 검증**에 전문성을 가진 Senior Java/DBA Engineer이다.

아래 요구사항과 첨부된 프로젝트 문서를 **구현의 기준(Source of Truth)**으로 사용하여 실행 가능한 **Pure Java 기반 Sybase IQ Migration Worker**를 개발한다.

임의의 DB 컬럼, 테이블, 설정값, SQL 문법, 메타데이터를 만들어내지 않는다.

요구사항이 서로 충돌하거나 실제 DB 구조가 확인되지 않는 경우:

1. 충돌 내용을 먼저 식별한다.
2. 확정된 요구사항과 추정 사항을 분리한다.
3. 실제 DB Metadata 또는 제공된 Schema를 우선한다.
4. 확인할 수 없는 부분은 임의 구현하지 않고 TODO 또는 Extension Point로 남긴다.

---

# 1. 시스템 목적

HP-UX 환경의:

```text
Sybase IQ 16.0
AS-IS
```

에서

```text
Linux 환경의
Sybase IQ 16.2
TO-BE
```

로 대용량 데이터를 이관한다.

전체 규모:

```text
대상 테이블 : 약 13,000개
전체 데이터 : 약 250TB
공유 볼륨   : 약 50TB
```

AS-IS와 TO-BE는 동일 네트워크에 존재하며 공유 볼륨을 데이터 전달 매체로 사용한다.

데이터 자체를 Java Heap으로 가져와 이동하지 않는다.

전체 데이터 흐름은 다음을 기본으로 한다.

```text
AS-IS Sybase IQ
      │
      │ Native Extract / Unload
      ▼
Shared Volume
      │
      │ LOAD TABLE
      ▼
TO-BE Sybase IQ
```

Java 프로그램은 데이터 이동 프로그램이 아니라 다음 역할의 **Orchestrator**이다.

```text
Metadata 조회
SQL 생성
Sybase IQ Native Command 실행
Extract / Load 제어
파일 상태 관리
검증값 산출
AS-IS / TO-BE 검증
결과 저장
작업상태 관리
Exit Code 반환
```

---

# 2. 실행 구조

```text
GODIS
   │
   │ Shell
   ▼
MigrationWorkerApplication
   │
   ▼
MigrationWorker
   │
   ├── Batch Group
   │     │
   │     ├── TableMigrationTask
   │     ├── TableMigrationTask
   │     └── TableMigrationTask
   │
   ├── MariaDB / GODIS
   │
   ├── Sybase IQ 16.0
   │
   ├── Shared Volume
   │
   └── Sybase IQ 16.2
```

하나의 `JOB_ID`는 하나의 Batch Group으로 취급한다.

`JOB_ID`에 속한 여러 테이블을 `PROC_ORD` 순서대로 처리한다.

초기 구현에서는 Worker 내부에서 테이블을 순차 처리한다.

병렬 실행은 GODIS의 Job/Group 수준에서 담당한다.

Worker 내부에서 임의의 Thread Pool을 생성하지 않는다.

---

# 3. 기술 스택

## 반드시 사용

```text
Java SE
JDBC
MyBatis
SLF4J
Logback
Maven
```

## 사용하지 않음

```text
Spring Boot
Spring Framework
Spring Batch
HikariCP
Connection Pool
무거운 DI Framework
```

프로그램 시작점:

```java
public static void main(String[] args)
```

Main class:

```text
com.migration.MigrationWorkerApplication
```

---

# 4. 실행 방식

GODIS Shell에서 다음 형태로 실행한다.

```bash
java -cp ... com.migration.MigrationWorkerApplication \
    <EXEC_SEQ> \
    <JOB_ID> \
    <EXEC_USER>
```

Arguments:

| Index | Name      | Required | Description        |
| ----- | --------- | -------- | ------------------ |
| 0     | EXEC_SEQ  | YES      | 배치 실행 차수           |
| 1     | JOB_ID    | YES      | GODIS Batch Job ID |
| 2     | EXEC_USER | NO       | 실행자, 기본값 SYSTEM    |

잘못된 argument가 전달되면 즉시 오류 처리하고 Exit Code `1`을 반환한다.

---

# 5. Source of Truth 우선순위

구현 시 다음 우선순위를 반드시 적용한다.

```text
1. 실제 AS-IS / TO-BE DB Schema
2. 실제 MariaDB Metadata Table DDL
3. 제공된 샘플 데이터
4. 본 Prompt의 명시 요구사항
5. 기존 프로젝트 코드
6. 개발자의 일반적인 관행
```

특히 실제 DB에 존재하지 않는 컬럼을 임의로 Java DTO나 SQL에 추가하지 않는다.

---

# 6. 중요: Metadata와 Physical Schema를 구분한다

다음 두 종류의 Schema를 반드시 구분한다.

## 6.1 Migration Metadata Schema

MariaDB에 존재하는:

```text
GPCL_CM_CD_VAL
GPCL_MIG_TABLE
GPCL_MIG_VRF_TARGET
GPCL_MIG_VRF_RESULT
```

등의 metadata table.

## 6.2 Physical Table Schema

실제로 이관되는 Sybase IQ의:

```text
HANKUKERP.TBL_CERT_WAY
HANKUKERP.TBL_DEPARTMENT
HANKUKERP.TBL_PROJECT
...
```

의 실제 컬럼 구조
`GPCL_MIG_VRF_TARGET`에 존재하는 컬럼 목록은 이관대상 테이블의 전체 컬럼 목록을 포함한다.
- TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, DATA_TYPE(컬럼의 타입과 자릿수,정밀도 등 포함)

즉, `GPCL_MIG_VRF_TARGET` 테이블을 이용하여 AS-IS와 TO-BE Physical Schema를 구성할 수 있다.
(Sybase IQ Catalog 나JDBC `DatabaseMetaData`를 사용하지 말것.)

---

# 7. GPCL_MIG_TABLE

## 목적

`JOB_ID`를 기준으로 이관 대상 테이블을 조회한다.

Java에서는 다음 정보를 객체로 관리한다.

```java
public class TableInfo {
    private String tableSchema;
    private String tableName;
    private int procOrd;
    private String tableComment;
    private String jobId;
    private String splitYn;
    private String migCond;
    private String mngrId;

    // 실제 DB Schema에 존재하는 추가 Metadata가 확인되는 경우에만 추가
}
```

기본 DDL:

```sql
CREATE TABLE GPCL_MIG_TABLE (
    TABLE_SCHEMA     VARCHAR(64)     NOT NULL,
    TABLE_NAME       VARCHAR(64)     NOT NULL,
    PROC_ORD         DECIMAL(5,0)    NOT NULL,
    TABLE_COMMENT    VARCHAR(2048)   NULL DEFAULT NULL,
    JOB_ID           VARCHAR(30)     NULL DEFAULT NULL,
    SPLIT_YN         CHAR(1)         NOT NULL DEFAULT 'N',
    MIG_COND         VARCHAR(4000)   NULL DEFAULT NULL,
    MNGR_ID          VARCHAR(20)     NULL DEFAULT NULL,
    PRIMARY KEY (TABLE_SCHEMA, TABLE_NAME, PROC_ORD)
)
```

조회 조건:

```sql
WHERE JOB_ID = #{jobId}
```

정렬:

```sql
ORDER BY PROC_ORD
       , TABLE_SCHEMA
       , TABLE_NAME
```

`PROC_ORD`를 Batch Group 내부의 처리순서로 사용한다.

---

# 8. GPCL_MIG_TABLE 샘플 데이터

다음 데이터를 실제 개발 테스트 Fixture로 사용한다.

| TABLE_SCHEMA | TABLE_NAME     | PROC_ORD | TABLE_COMMENT | JOB_ID | SPLIT_YN | MIG_COND |
| ------------ | -------------- | -------: | ------------- | ------ | -------- | -------- |
| HANKUKERP    | TBL_CERT_WAY   |       10 | 검증방법          | 300    | N        |          |
| HANKUKERP    | TBL_DEPARTMENT |       10 | 부서관리          | 200    | N        |          |
| HANKUKERP    | TBL_PROJECT    |       10 | 프로젝트 테이블      | 100    | N        |          |

따라서 다음과 같은 테스트가 가능해야 한다.

```text
JOB_ID=100
 → HANKUKERP.TBL_PROJECT

JOB_ID=200
 → HANKUKERP.TBL_DEPARTMENT

JOB_ID=300
 → HANKUKERP.TBL_CERT_WAY
```

---

# 9. GPCL_MIG_TABLE Schema 불일치 주의

제공된 샘플에는 다음 컬럼도 표시되어 있지만, 사용하지 말것.

```text
BIZ_AREA_CD
SUBJ_AREA_CD
GRP1_CD
GRP2_CD
GRP3_CD
GRP4_CD
GRP5_CD
REG_ID
REG_DTM
LST_ADJPRN_ID
LST_ADJ_DTM
```

제공된 CREATE TABLE 정의에 포함된 컬럼을 대상으로 DTO 작성이나 SQL에서 사용한다.

---

# 10. GPCL_MIG_VRF_TARGET

목적:

```text
테이블별 검증 대상 컬럼
+
검증 함수
```

를 관리한다.

주요 컬럼:

```text
TABLE_SCHEMA
TABLE_NAME
COLUMN_NAME
ORDINAL_POSITION
DATA_TYPE
SUM_YN
MIN_YN
MAX_YN
AVG_YN
```

Java 객체:

```java
public class ValidationTarget {
    private String tableSchema;
    private String tableName;
    private String columnName;
    private int ordinalPosition;
    private String dataType;
    private boolean sumYn;
    private boolean minYn;
    private boolean maxYn;
    private boolean avgYn;
}
```

---

# 11. GPCL_MIG_VRF_TARGET 샘플

다음 샘플을 테스트 기준으로 사용한다.

```text
HANKUKERP.TBL_CERT_WAY

ID
INSPECT_ID
TEST_WAY
USE_YN
```

```text
HANKUKERP.TBL_DEPARTMENT

ID
NAME
```

```text
HANKUKERP.TBL_PROJECT

CODE
CONTRACT_YEAR
DETAIL_CODE
ID
NAME
NOTE
REG_DATE
SERVICE
SERVICE_LOB
```

검증 함수 설정 예:

```text
INSPECT_ID
SUM=Y
MIN=Y
MAX=Y
AVG=Y
```

```text
CONTRACT_YEAR
SUM=Y
MIN=Y
MAX=Y
AVG=Y
```

```text
NAME
MIN=Y
MAX=Y
```

```text
REG_DATE
MIN=Y
MAX=Y
```

---

# 12. Validation Target 정렬

`GPCL_MIG_VRF_TARGET` 조회 결과의 입력 순서를 신뢰하지 않는다.

반드시:

```sql
ORDER BY ORDINAL_POSITION
```

을 적용한다.

예를 들어 다음 데이터:

```text
3 CODE
4 CONTRACT_YEAR
7 DETAIL_CODE
1 ID
2 NAME
6 NOTE
5 REG_DATE
8 SERVICE
9 SERVICE_LOB
```

는 다음 순서로 처리해야 한다.

```text
1 ID
2 NAME
3 CODE
4 CONTRACT_YEAR
5 REG_DATE
6 NOTE
7 DETAIL_CODE
8 SERVICE
9 SERVICE_LOB
```

---

# 13. BLOB 처리

샘플에는:

```text
HANKUKERP.TBL_PROJECT.SERVICE_LOB
DATA_TYPE = BLOB
```

가 존재한다.

따라서 BLOB을 일반 숫자/문자 컬럼과 동일하게 취급하지 않는다.

BLOB 관련 처리는 다음을 분리한다.

```text
일반 데이터 Extract/Load
+
Binary/BLOB 파일 처리
```

BLOB의 실제 Sybase IQ 표현 및 Native Extract/Load 방식은 실제 Sybase IQ Schema/환경에서 확인한 후 구현한다.

확인되지 않은 BLOB SQL 문법을 임의로 생성하지 않는다.

---

# 14. Physical Schema 조회

실제 이관 대상 테이블에 대해 필요하면 다음 정보를 AS-IS / TO-BE 각각 조회한다.

```text
TABLE_SCHEMA
TABLE_NAME
COLUMN_NAME
ORDINAL_POSITION
DATA_TYPE
COLUMN_SIZE
DECIMAL_DIGITS
NULLABLE
```

필요한 경우:

```text
PRECISION
SCALE
CHARACTER_LENGTH
NUMERIC_PRECISION
```

등도 조회한다.

이를 `TableSchema`, `ColumnSchema` 객체로 관리한다.

---

# 15. Schema Validation

이관 시작 전에 필요하면 다음을 비교한다.

```text
AS-IS table 존재
TO-BE table 존재

column count
column name
ordinal position
data type
length
precision
scale
nullable
```

불일치가 발생하면:

```text
SCHEMA_MISMATCH
```

로 처리한다.

Java 코드가 임의로 AS-IS와 TO-BE의 구조 차이를 보정하지 않는다.

---

# 16. GPCL_MIG_VRF_RESULT

검증 결과는 다음 metadata table에 저장한다.

```text
GPCL_MIG_VRF_RESULT
```

주요 결과:

```text
UNLOAD_CNT
LOAD_CNT

UNLOAD_SUM
LOAD_SUM

UNLOAD_MIN
LOAD_MIN

UNLOAD_MAX
LOAD_MAX

UNLOAD_AVG
LOAD_AVG

VRF_STAT_CD
VRF_ERR_CD
ACT_CONTN
```

결과 저장 객체를 별도로 구현한다.

---

# 17. 공통 설정

MariaDB:

```text
GPCL_CM_CD_VAL
```

에서 Migration 설정을 조회한다.

기본 조회 기준은 제공된 metadata 정의에 따른다.

예:

```text
ASIS_JDBC_URL
ASIS_USER
ASIS_PWD

TOBE_JDBC_URL
TOBE_USER
TOBE_PWD

LOG_LEVEL

EXPORT_DATA_PATH
EXPORT_BLOB_PATH
```

비밀번호는 로그에 출력하지 않는다.

---

# 18. Connection 구조

정확히 3개의 논리적인 Connection을 관리한다.

```text
META
 └── MariaDB

ASIS
 └── Sybase IQ 16.0

TOBE
 └── Sybase IQ 16.2
```

Connection Pool은 사용하지 않는다.

Connection은 Table마다 생성하지 않는다.

잘못된 구현:

```java
for (...) {
    Connection conn = createConnection();
}
```

권장:

```java
Connection metaConn;
Connection asisConn;
Connection tobeConn;

for (TableInfo table : tables) {
    process(table);
}
```

장애로 Connection이 유효하지 않은 경우에만 명시적인 재연결 정책을 적용한다.

---

# 19. JDBC Statement 정책

## Statement

다음과 같이 SQL 구조 자체가 동적으로 생성되는 경우 `Statement`를 사용한다.

```text
SET TEMPORARY OPTION
Native Extract
LOAD TABLE
동적 Identifier SQL
```

예:

```sql
LOAD TABLE HANKUKERP.TBL_PROJECT
(
    ID,
    NAME,
    CODE
)
FROM '/migration/TBL_PROJECT/data_001.dat'
FORMAT BINARY;
```

다음은 생성하지 않는다.

```sql
LOAD TABLE ?.?
```

또는:

```sql
LOAD TABLE HANKUKERP.?
```

---

# 20. PreparedStatement 정책

"## 6.1 Migration Metadata Schema" 처리할 때만 적용한다.
값 Parameter가 존재하는 반복 SQL에는 `PreparedStatement`를 사용한다.
"## 6.2 Physical Table Schema" 처리시는 Statement를 사용한다.

예:

```sql
SELECT ...
FROM GPCL_MIG_TABLE
WHERE JOB_ID = ?
```

즉:

```text
Identifier
→ 안전하게 검증한 후 SQL 문자열 생성

Value
→ PreparedStatement parameter
```

로 구분한다.

---

# 21. MIG_COND 처리

`MIG_COND`는:

```text
WHERE 절 본문
```

으로 사용한다.

예:

```text
REG_DATE >= '2025-01-01'
```

빈 값:

```text
NULL
''
공백
```

이면 전체 데이터를 추출한다.

다음처럼 임의 SQL 전체를 허용하지 않는다.

```text
DROP TABLE ...
SELECT ...
INSERT ...
UPDATE ...
```

`MIG_COND`는 metadata에 등록된 Migration 조건이라는 전제에서 사용한다.

가능하면 허용되는 SQL expression/identifier에 대한 validation을 수행한다.

---

# 22. Pipeline

각 Table에 대해 반드시 다음 순서로 처리한다.

```text
Step 0 Initialize
       ↓
Step 1 AS-IS Extract
       ↓
Step 2 AS-IS Verify
       ↓
Step 3 TO-BE Load
       ↓
Step 4 TO-BE Verify
       ↓
Step 5 Compare
       ↓
Step 6 Result / Cleanup
```

---

# 23. Step 1 AS-IS Extract

Sybase IQ 16.0 Native Extract 기능을 사용한다.

Extract Option을 먼저 설정한다.

예:

```java/sql
    // 1. 해당 테이블용 추출 경로 지정 (DB 서버 Local 디스크 또는 High-speed SAN/NFS)
    String metaFile = "/data/export/" + tableName + "_meta.dat";
    String blobFile = "/data/export/" + tableName + "_blob.bin";

    stmt.execute("SET TEMPORARY OPTION Temp_Extract_Name1 = '" + metaFile + "'");
    stmt.execute("SET TEMPORARY OPTION Temp_Extract_Name2 = '" + blobFile + "'");
    stmt.execute("SET TEMPORARY OPTION Temp_Extract_Binary = 'ON'");
    
    // 2. Query 실행 -> Sybase IQ 엔진이 DB 서버 디스크에 직접 대량 파일 생성
    System.out.println("Start Unload Table: " + tableName);
    stmt.executeQuery("SELECT * FROM " + tableName);
    System.out.println("Complete Unload Table: " + tableName);

    // 3. Reset
    stmt.execute("SET TEMPORARY OPTION Temp_Extract_Name1 = ''");
    stmt.execute("SET TEMPORARY OPTION Temp_Extract_Name2 = ''");
```

단, 실제 Sybase IQ 16.0에서 지원되는 정확한 Native Extract syntax는 실제 DB 환경 또는 제공된 SQL specification을 기준으로 검증한다.

검증되지 않은 SQL 문법을 임의 생성하지 않는다.

Extract 완료 후:

```text
파일 존재 여부
파일 크기
파일 접근 가능 여부
```

를 확인한다.

---

# 24. Step 2 AS-IS Verification

AS-IS에서 검증 SQL을 실행한다.

검증 대상은:

```text
GPCL_MIG_VRF_TARGET
```

으로 결정한다.

가능한 함수:

```text
COUNT
SUM
MIN
MAX
AVG
```

예:

```sql
SELECT
       COUNT(*),
       SUM(CONTRACT_YEAR),
       MIN(CONTRACT_YEAR),
       MAX(CONTRACT_YEAR),
       AVG(CONTRACT_YEAR)
FROM HANKUKERP.TBL_PROJECT
WHERE ...
```

---

# 25. Validation Function과 Data Type

모든 컬럼에 모든 aggregate를 적용하지 않는다.

예:

```text
INT
DECIMAL
NUMERIC
BIGINT
...
```

에는:

```text
SUM
AVG
MIN
MAX
```

적용 가능 여부를 검토한다.

문자열:

```text
VARCHAR
CHAR
```

에는 일반적으로:

```text
MIN
MAX
```

만 metadata 설정에 따라 수행한다.

DATE 계열:

```text
MIN
MAX
```

를 지원한다.

BLOB에는:
(BLOB과 같이 취급할 타입들은 LONG BINARY,VARBINARY, BINARY, IMAGE)
```text
SUM
AVG
MIN
MAX
```

를 임의 적용하지 않는다.

잘못된 함수가 metadata에 설정되어 있으면:

```text
INVALID_VALIDATION_FUNCTION
```

으로 처리한다.

---

# 26. NULL 처리

다음 Aggregate 결과가 NULL일 수 있음을 고려한다.

```text
SUM
AVG
MIN
MAX
```

AS-IS와 TO-BE가 모두 NULL인 경우:

```text
MATCH
```

로 처리할 수 있도록 비교 정책을 명시한다.

한쪽만 NULL인 경우:

```text
MISMATCH
```

이다.

단, 실제 비교 정책은 `ValidationComparator` 등 별도 클래스로 분리한다.

---

# 27. Validation 비교

다음 항목을 비교한다.

```text
COUNT
SUM
MIN
MAX
AVG
```

비교 결과 객체:

```java
public class ValidationResult {
    private String tableSchema;
    private String tableName;
    private String columnName;
    private String functionName;

    private String asisValue;
    private String tobeValue;

    private boolean match;
    private String difference;
    private String message;
}
```

문자열 단순 비교에 의존하지 않는다.

Numeric:

```text
BigDecimal
```

기반 비교를 우선 고려한다.

AVG:

```text
Precision
Scale
Tolerance
```

정책을 명시적으로 관리한다.

---

# 28. Step 3 TO-BE Load

TO-BE Sybase IQ 16.2에서 `LOAD TABLE`을 실행한다.

기본 형태:

```sql
LOAD TABLE HANKUKERP.TBL_PROJECT
(
    ID,
    NAME,
    CODE,
    CONTRACT_YEAR,
    REG_DATE,
    NOTE,
    DETAIL_CODE,
    SERVICE,
    SERVICE_LOB
)
FROM '/migration/TBL_PROJECT/data_001.dat'
FORMAT BINARY;
```

단, 실제 column list와 file format은 **실제 Physical Schema 및 Extract 결과에 따라 동적으로 생성**한다.

---

# 29. SPLIT_YN

`GPCL_MIG_TABLE.SPLIT_YN`:

```text
Y
N
```

을 지원한다.

`N`:

```text
table → single or standard extract file
```

`Y`:

```text
table → multiple extract files
```

해당 컬럼은 참고용으로만 사용한다.

테이블 추출/적재의 분할이나 병렬처리는 GODIS웹에서 개발자(설계자)가 직접판단해서 설정한다.

---

# 30. 파일 삭제

가장 중요한 규칙:

```text
LOAD 성공 확인
        ↓
파일 존재 확인
        ↓
파일 삭제
```

LOAD 실패 시:

```text
파일 유지
```

한다.

파일 삭제 실패 시:

```text
ERROR 로그
cleanup failure 기록
```

을 남긴다.

파일 삭제 실패를 데이터 이관 성공과 동일하게 처리할지 여부는 상태 정책에서 명시한다.

---

# 31. Batch Group 실패 정책

기본 정책:

```text
Table #1 SUCCESS
Table #2 SUCCESS
Table #3 FAILED
        ↓
Batch Group 중단
        ↓
GODIS abnormal exit
```

향후:

```text
CONTINUE_ON_ERROR
```

를 추가할 수 있도록 정책을 enum 또는 configuration으로 분리한다.

---

# 32. 상태 관리

상태 흐름:

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

오류:

```text
FAILED
```

검증 불일치:

```text
VERIFY_MISMATCH
```

등으로 명확히 구분한다.

실제 MariaDB 상태 코드가 별도 metadata에 정의되어 있다면 해당 코드를 우선한다.

---

# 33. Transaction

다음 Transaction을 독립적으로 관리한다.

```text
Metadata
Extract
Load
Validation
Status Update
```

특히 MariaDB와 Sybase IQ의 Connection이 서로 다르므로 하나의 Global Transaction처럼 처리하지 않는다.

```text
MariaDB Transaction
≠
AS-IS Transaction
≠
TO-BE Transaction
```

Sybase IQ `LOAD TABLE`의 실제 Commit 동작은 사용하는 JDBC Driver와 DB 설정을 기준으로 확인한다.

---

# 34. JDBC Resource Lifecycle

원칙:

```text
Connection
→ Worker/Batch Group 단위 재사용

Statement
→ 작업 단위 생성/실행/close

PreparedStatement
→ "## 6.2 Physical Table Schema" 테이블 처리에서는 사용하지 않는다. PreparedStatement 필요한 경우 Statement를 사용한다.

ResultSet
→ 사용 즉시 close
```

반드시:

```java
try-with-resources
```

를 사용한다.

---

# 35. MyBatis

MyBatis XML을 사용한다.

## MetaMapper.xml

관리 SQL:

```text
GPCL_CM_CD_VAL 조회
GPCL_MIG_TABLE 조회
GPCL_MIG_VRF_TARGET 조회
GPCL_MIG_VRF_RESULT INSERT
GPCL_MIG_VRF_RESULT UPDATE
작업 상태 INSERT
작업 상태 UPDATE
```

## SybaseIQMapper.xml

Sybase IQ metadata 또는 필요한 정적 SQL이 존재하는 경우 관리한다.

단, Native Extract / LOAD TABLE처럼 SQL 자체가 동적으로 생성되는 부분은 `SybaseIQSqlBuilder`가 담당하도록 한다.

---

# 36. `${}`와 `#{}`

값:

```text
#{value}
```

를 기본으로 한다.

Identifier:

```text
${identifier}
```

가 불가피한 경우 반드시 사전에 검증된 Metadata만 사용한다.

사용자 입력을 그대로 `${}`에 넣지 않는다.

가능하면 동적 Sybase SQL은 Java의 `SybaseIQSqlBuilder`에서 명확하게 생성한다.

---

# 37. Class Architecture

```text
com.migration
│
├── MigrationWorkerApplication
├── MigrationWorker
│
├── config
│   ├── MigrationConfig
│   ├── ConnectionFactory
│   └── ExitCode
│
├── metadata
│   ├── MetaMapper
│   ├── TableInfo
│   ├── TableSchema
│   ├── ColumnSchema
│   └── ValidationTarget
│
├── sybase
│   ├── SybaseIQUnloadWorker
│   ├── SybaseIQLoadWorker
│   ├── SybaseIQSchemaReader
│   └── SybaseIQSqlBuilder
│
├── validation
│   ├── MigrationValidator
│   ├── ValidationResult
│   └── ValidationComparator
│
├── file
│   └── FileUtil
│
├── util
│   ├── LogUtil
│   └── SqlUtil
│
└── exception
    ├── MigrationException
    ├── MetadataException
    ├── SchemaException
    ├── ExtractException
    ├── LoadException
    └── ValidationException
```

---

# 38. MigrationWorkerApplication

책임:

```text
main()
CLI parsing
Worker 생성
Worker 실행
Exception 처리
System.exit()
```

Main은 Business Logic을 포함하지 않는다.

---

# 39. MigrationWorker

책임:

```text
전체 Pipeline orchestration
Batch Group 조회
TableMigrationTask 실행
Step 제어
상태 관리
Exception 처리
Exit Code 결정
```

---

# 40. SybaseIQSqlBuilder

다음 메서드를 기본으로 구현한다.

```java
buildExtractOptionSql()
buildExtractSql()
buildLoadSql()
buildValidationSql()
```

SQL 생성만 담당한다.

Connection을 직접 관리하지 않는다.

---

# 41. SybaseIQSchemaReader

책임:

```text
AS-IS Physical Schema 조회
TO-BE Physical Schema 조회
Table 존재 여부
Column 존재 여부
Column Type
Column Length
Precision
Scale
Ordinal
Nullable
```

등을 담당한다.

---

# 42. MigrationValidator

책임:

```text
Validation Target 조회
Validation SQL 생성
AS-IS 검증
TO-BE 검증
결과 비교
Mismatch 생성
GPCL_MIG_VRF_RESULT 저장
```

---

# 43. FileUtil

다음 기능을 제공한다.

```text
exists()
getFileSize()
isReadable()
deleteSafely()
```

대용량 파일 전체를 Java Heap으로 읽지 않는다.

---

# 44. Logging

SLF4J + Logback을 사용한다.

최소 Context:

```text
EXEC_SEQ
JOB_ID
EXEC_USER
TABLE_SCHEMA
TABLE_NAME
PROC_ORD
STEP
```

예:

```text
[EXEC_SEQ=1001]
[JOB_ID=100]
[TABLE=HANKUKERP.TBL_PROJECT]
[PROC_ORD=10]
[STEP=LOAD]
LOAD TABLE started
```

다음은 로그에 출력하지 않는다.

```text
Password
DB Password
Credential
Secret
대량 데이터
```

---

# 45. Exit Code

```text
0 = SUCCESS
1 = INVALID_ARGUMENT
2 = INITIALIZATION_ERROR
3 = EXTRACT_ERROR
4 = LOAD_ERROR
5 = VALIDATION_MISMATCH
6 = VALIDATION_ERROR
7 = SCHEMA_ERROR
8 = METADATA_ERROR
9 = SYSTEM_ERROR
```

GODIS Shell에서 Exit Code를 정확하게 수신할 수 있어야 한다.

---

# 46. Exception 처리

Exception 발생:

```text
Exception
 ↓
현재 Table FAILED
 ↓
오류 로그
 ↓
필요 파일 보존
 ↓
Resource close
 ↓
Worker에 전달
 ↓
Exit Code 결정
```

다음과 같이 단순 처리하지 않는다.

```java
catch (Exception e) {
    e.printStackTrace();
}
```

Exception은 원인과 실행 Context를 포함해야 한다.

---

# 47. 성능

250TB / 13,000 Tables 환경을 고려한다.

절대 하지 않는다.

```text
250TB를 Java Heap으로 읽기
List<Row> 형태로 전체 데이터 적재
byte[]로 전체 Extract File 읽기
ResultSet 전체를 메모리에 유지
테이블마다 Connection 생성
불필요한 Thread Pool
대량 데이터 Logging
```

반드시:

```text
Native Extract
Shared Volume
LOAD TABLE
```

구조를 사용한다.

---

# 48. 동시성

MigrationWorker 내부:

```text
Sequential
```

처리.

예:

```text
TBL_PROJECT
   ↓
TBL_DEPARTMENT
   ↓
TBL_CERT_WAY
```

병렬화 및 처리순서는 GODIS 레벨에서 담당한다.

---

# 49. 실제 샘플 기반 테스트

최소 다음 테스트를 구현한다.

## JOB_ID=100

```text
HANKUKERP.TBL_PROJECT
```

Validation Target:

```text
ID
NAME
CODE
CONTRACT_YEAR
REG_DATE
NOTE
DETAIL_CODE
SERVICE
SERVICE_LOB
```

특히:

```text
CONTRACT_YEAR → SUM/MIN/MAX/AVG
REG_DATE       → MIN/MAX
SERVICE_LOB    → BLOB
```

처리를 검증한다.

## JOB_ID=200

```text
HANKUKERP.TBL_DEPARTMENT
```

## JOB_ID=300

```text
HANKUKERP.TBL_CERT_WAY
```

---

# 50. 반드시 테스트할 오류 상황

```text
정상 단일 Table
정상 다중 Table

JOB_ID 미존재
Metadata 조회 실패
잘못된 Table Schema
AS-IS Table 없음
TO-BE Table 없음
Schema mismatch

Extract 실패
Extract 파일 미생성
Extract 파일 크기 0
파일 접근 불가

AS-IS Validation 실패

Load 실패
Load 중 파일 없음

파일 삭제 실패

TO-BE Validation 실패
Validation mismatch

MariaDB Connection 실패
AS-IS Connection 실패
TO-BE Connection 실패

잘못된 CLI Argument
```

---

# 51. 구현 전 반드시 수행할 분석

코드를 작성하기 전에 먼저 다음 표를 작성한다.

| No | 요구사항 | 확인된 사실 | 문제/모호성 | 구현 영향 | 처리방안 |
| -- | ---- | ------ | ------ | ----- | ---- |

특히 다음을 반드시 확인한다.

```text
1. GPCL_MIG_TABLE 실제 컬럼
2. GPCL_MIG_VRF_TARGET 실제 컬럼
3. GPCL_MIG_VRF_RESULT 실제 컬럼
4. Physical Sybase IQ Schema 조회 방법
5. Sybase IQ 16.0 Native Extract 정확한 syntax
6. Sybase IQ 16.2 LOAD TABLE syntax
7. BLOB 처리 방식
8. SPLIT_YN 실제 분할 기준
9. MIG_COND 허용 범위
10. Validation tolerance
11. 상태코드
12. EXEC_PHASE와 EXEC_SEQ 관계
```

확인되지 않은 사항은 임의 구현하지 않는다.

---

# 52. 구현 단계

한 번에 전체 프로젝트를 생성하지 말고 다음 단계로 구현한다.

## Phase 1 - Project Skeleton

생성:

```text
pom.xml
MigrationWorkerApplication
MigrationWorker
config
exception
util
```

컴파일 확인.

---

## Phase 2 - Metadata

구현:

```text
MetaMapper
MetaMapper.xml
TableInfo
ValidationTarget
MigrationConfig
```

테스트:

```text
JOB_ID=100
JOB_ID=200
JOB_ID=300
```

---

## Phase 3 - Physical Schema

구현:

```text
TableSchema
ColumnSchema
SybaseIQSchemaReader
```

AS-IS / TO-BE Schema 비교 테스트.

---

## Phase 4 - SQL Builder

구현:

```text
SybaseIQSqlBuilder
```

테스트:

```text
Extract SQL
Load SQL
Validation SQL
```

SQL 문자열을 실제 DB에 실행하기 전에 Unit Test로 먼저 검증한다.

---

## Phase 5 - Extract / Load

구현:

```text
SybaseIQUnloadWorker
SybaseIQLoadWorker
FileUtil
```

작은 테스트 테이블로 실제 Sybase IQ 연동 테스트.

---

## Phase 6 - Validation

구현:

```text
MigrationValidator
ValidationComparator
ValidationResult
```

다음 테스트:

```text
COUNT MATCH
COUNT MISMATCH
SUM MATCH
SUM MISMATCH
AVG tolerance
NULL vs NULL
NULL vs VALUE
BLOB 제외
```

---

## Phase 7 - Orchestration

전체 연결:

```text
Initialize
 → Extract
 → ASIS Verify
 → Load
 → TOBE Verify
 → Compare
 → Result
 → Cleanup
```

---

## Phase 8 - Integration Test

최종적으로:

```text
GODIS
 ↓
Shell
 ↓
Java
 ↓
MariaDB
 ↓
Sybase IQ 16.0
 ↓
Shared Volume
 ↓
Sybase IQ 16.2
```

전체 흐름을 검증한다.

---

# 53. 생성 파일

최종 구조:

```text
migration-worker/
│
├── pom.xml
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/migration/
│   │   │
│   │   └── resources/
│   │       ├── mapper/
│   │       │   ├── MetaMapper.xml
│   │       │   └── SybaseIQMapper.xml
│   │       ├── mybatis-config.xml
│   │       └── logback.xml
│   │
│   └── test/
│       └── java/
│
└── bin/
    └── run_migration.sh
```

---

# 54. Maven Dependency

불필요한 dependency를 추가하지 않는다.

필요:

```text
MyBatis
MariaDB JDBC Driver
Sybase IQ JDBC Driver
SLF4J
Logback
```

Version이 제공되지 않았다면 임의의 최신 Version을 선택하지 않는다.

다음처럼 TODO로 표시한다.

```text
TODO: 프로젝트 표준 Dependency Version 확인
```

---

# 55. Coding Rule

## 55.1 Connection Lifecycle

Migration Worker는 하나의 Batch Group을 처리하는 동안 다음 3개의 DB Connection을 생성하여 재사용한다.

```text
Batch Group 시작
    │
    ├── META Connection
    │      └── MariaDB
    │
    ├── ASIS Connection
    │      └── Sybase IQ 16.0
    │
    └── TOBE Connection
           └── Sybase IQ 16.2
    │
    ▼
Batch Group의 모든 Table 처리
    │
    ▼
Batch Group 종료
    │
    └── 3개 Connection close
```

즉:

> **Connection의 기본 생명주기는 Batch Group이다.**

Table마다 Connection을 생성하지 않는다.

잘못된 구현:

```java
for (TableInfo table : tables) {
    Connection conn = connectionFactory.createConnection();
    process(table, conn);
    conn.close();
}
```

권장 구현:

```java
public class MigrationWorker {

    private Connection metaConnection;
    private Connection asisConnection;
    private Connection tobeConnection;

    public void execute(String jobId) {

        initializeConnections();

        try {
            List<TableInfo> tables =
                metaMapper.selectMigrationTables(jobId);

            for (TableInfo table : tables) {
                processTable(table);
            }

        } finally {
            closeConnections();
        }
    }
}
```

---

## 55.2 Connection 역할

### META Connection

MariaDB metadata 및 결과 관리:

```text
GPCL_CM_CD_VAL
GPCL_MIG_TABLE
GPCL_MIG_VRF_TARGET
GPCL_MIG_VRF_RESULT
```

### ASIS Connection

Sybase IQ 16.0:

```text
Physical Schema 조회
Native Extract
AS-IS Validation
```

### TOBE Connection

Sybase IQ 16.2:

```text
Physical Schema 조회
LOAD TABLE
TO-BE Validation
```

---

## 55.3 Connection 소유권

Connection은 `MigrationWorker`가 소유한다.

권장:

```text
MigrationWorker
 ├── metaConnection
 ├── asisConnection
 └── tobeConnection
```

Connection을 애플리케이션 전체에서 공유하는 `static global Connection`으로 선언하지 않는다.

비권장:

```java
public static Connection metaConnection;
public static Connection asisConnection;
public static Connection tobeConnection;
```

즉,

> **Batch Group 동안 재사용하되, JVM 전역 static 객체로 만들지는 않는다.**

---

## 55.4 Connection Pool

현재 Worker는 GODIS가 Job 단위로 실행하는 독립적인 배치 프로그램이므로 Connection Pool을 사용하지 않는다.

```text
Connection Pool
→ 사용하지 않음
```

다음 구조를 사용한다.

```text
Worker 시작
    ↓
3 Connections 생성
    ↓
Batch Group 처리
    ↓
3 Connections close
    ↓
Worker 종료
```

---

## 55.5 Connection 장애

Batch Group 처리 중 Connection이 끊어질 가능성을 고려한다.

```text
Connection 정상
    ↓
계속 재사용

Connection 장애
    ↓
Connection 상태 확인
    ↓
필요한 경우 재연결
    ↓
현재 작업의 Retry / Fail 정책 적용
```

재연결 정책은 `ConnectionFactory` 또는 별도의 `ConnectionManager`에서 관리한다.

단순히 모든 Table 처리마다 Connection을 검사하고 무조건 재생성하지 않는다.

---

## 55.6 Statement Lifecycle

Connection과 달리 Statement는 Batch Group 전체에서 전역으로 공유하지 않는다.

기본 생명주기:

```text
Connection
    │
    ├── Statement 생성
    │       ↓
    │    SQL 실행
    │       ↓
    │    ResultSet 처리
    │       ↓
    │    Statement close
    │
    ├── Statement 생성
    │       ↓
    │      ...
    │
    └── ...
```

Native Extract / LOAD TABLE 등의 작업 단위별 Statement를 생성하고 사용 후 close한다.

---

## 55.7 PreparedStatement Lifecycle

반복적으로 동일 SQL을 실행하면서 값 Parameter만 변경되는 경우 `PreparedStatement`를 사용할 수 있다.

예:

```sql
SELECT COUNT(*)
FROM HANKUKERP.TBL_PROJECT
WHERE REG_DATE >= ?
```

반면 다음과 같이 Identifier 자체가 변경되는 SQL에는 `PreparedStatement`의 `?`를 사용하지 않는다.

```sql
FROM ?
```

또는:

```sql
LOAD TABLE ?.?
```

Identifier는 검증 후 SQL 문자열로 생성한다.

---

## 55.8 금지사항

다음 구조는 사용하지 않는다.

```text
static global Connection
ThreadLocal Connection
Connection Pool
불필요한 Global Singleton
Table마다 Connection 생성
Statement를 Batch Group 전체에서 무조건 공유
ResultSet 장시간 유지
```

핵심 원칙:

```text
Connection
→ Batch Group 생명주기

Statement
→ 작업 생명주기

ResultSet
→ 조회 작업 생명주기
```


---

# 56. 최종 출력 순서

코드를 생성할 때 다음 순서를 반드시 따른다.

## 1. Requirement Analysis

확정사항 / 불확실사항 / 충돌사항을 정리한다.

## 2. Architecture

Mermaid로 전체 Architecture를 표시한다.

## 3. Data Flow

```text
GODIS
 ↓
MigrationWorker
 ↓
Metadata
 ↓
AS-IS Extract
 ↓
AS-IS Verify
 ↓
Shared Volume
 ↓
TO-BE Load
 ↓
TO-BE Verify
 ↓
Compare
 ↓
Result
```

## 4. Project Tree

전체 프로젝트 구조.

## 5. Source Code

모든 파일의 전체 내용을 제공한다.

다음처럼 생략하지 않는다.

```text
...
```

핵심 코드 생략 금지.

## 6. SQL

MyBatis XML과 Dynamic SQL을 별도로 표시한다.

## 7. Build

```bash
mvn clean package
```

## 8. Execution

```bash
./bin/run_migration.sh 1001 100 SYSTEM
```

## 9. Test

정상/오류/검증불일치 테스트를 제공한다.

---

# 57. 최종 금지사항

다음 사항을 절대 하지 않는다.

```text
Spring Boot 추가
Spring Batch 추가
HikariCP 추가
Connection Pool 추가

250TB 데이터를 Java Heap으로 이동

실제 Schema에 없는 컬럼 생성

실제 제공되지 않은 DB 값 생성

Sybase IQ 문법을 다른 DB 문법으로 대체

BLOB을 일반 컬럼처럼 처리

MIG_COND에 임의 SQL 전체 허용

사용자 입력을 검증 없이 ${}에 삽입

LOAD 성공 전에 파일 삭제

검증 결과를 문자열 단순 비교만으로 판정

오류를 catch 후 무시

Thread Pool을 Worker 내부에 임의 생성
```

---

# 58. 가장 중요한 설계 원칙

최종 구현은 다음 원칙을 반드시 만족해야 한다.

```text
                GODIS
                  │
                  ▼
        MigrationWorkerApplication
                  │
                  ▼
          MigrationWorker
                  │
          ┌───────┴────────┐
          ▼                ▼
      Metadata          Batch Group
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
           Table #1      Table #2     Table #3
              │
       ┌──────┼──────┐
       ▼      ▼      ▼
    Extract Verify  Load
                     │
                     ▼
                  Verify
                     │
                     ▼
                  Compare
                     │
                     ▼
                  Result
```

핵심은 다음이다.

> Java는 대용량 데이터를 직접 이동하지 않는다.

> Java는 Sybase IQ Native Extract/Load를 orchestration한다.

> `GPCL_MIG_TABLE`은 JOB_ID와 PROC_ORD를 기준으로 Batch Group의 실행대상을 결정한다.

> `GPCL_MIG_VRF_TARGET`은 검증 대상과 검증 함수를 결정한다.

> GPCL_MIG_VRF_TARGET 테이블/컬럼 정보를 기준으로 Physical Table SQL을 생성한다.

> Metadata Schema와 Physical Schema를 혼동하지 않는다.

> Identifier와 Value Parameter를 구분한다.

> Connection은 Worker/Batch Group 수준에서 재사용한다.

> Statement는 Native/Dynamic SQL 작업 단위로 사용한다.

> 대용량 데이터는 JVM Heap에 올리지 않는다.

> LOAD 성공 전에는 Extract 파일을 삭제하지 않는다.

> AS-IS와 TO-BE 검증값을 정량적으로 비교한다.

> 확인되지 않은 DB 구조나 Sybase IQ 문법은 임의로 만들어내지 않는다.

> 구현은 Phase 1 → Phase 8의 단계적 방식으로 수행하고 각 단계마다 Compile/Test를 통과한 후 다음 단계로 진행한다.
