첨부 파일을 다시 확인했습니다. 현재 이 대화에서 제가 실제로 읽을 수 있는 파일은 **`05_1단계_마스카라 수정요청응답_프로젝트 초기환경 및 아키텍처 생성용 프롬프트.md` 1개뿐이며, 118행까지 확인했지만 테이블/컬럼 스키마 정의가 추가되어 있지 않습니다.** 

따라서 **첨부된 스키마의 실제 컬럼명·데이터타입·PK·NULL 여부 등을 제가 임의로 만들어 넣지는 않겠습니다.** 대신 현재 프롬프트를 스키마가 포함된 경우에도 LLM이 정확하게 활용하도록 구조 자체를 크게 개선하는 것이 좋습니다.

특히 현재 프롬프트는 `GPCL_MIG_TABLE`, `GPCL_MIG_VRF_TARGET` 정도의 메타정보만 정의하고 있는데, 실제 **테이블 스키마 정보가 추가된다면 이것을 단순 참고자료가 아니라 `LOAD TABLE`, Extract, Validation SQL 생성의 기준정보로 승격**시키는 것이 핵심입니다. 

아래 형태를 권장합니다.

# Sybase IQ 16.0 → 16.2 대용량 데이터 이관 Batch Worker 개발 프롬프트

## 0. 역할

당신은 다음 분야의 Senior Engineer 역할을 수행한다.

* Sybase IQ 16.0 / 16.2
* Java JDBC
* MyBatis
* 대용량 데이터 Migration
* Batch Processing
* 데이터 검증 및 정합성 검증
* SQL Dynamic Generation
* Linux Shell
* MariaDB Metadata 기반 Batch Framework

목표는 **13,000개 테이블 / 약 250TB 규모의 Sybase IQ 16.0 → 16.2 데이터 이관을 수행하는 Pure Java 기반 경량 Migration Worker**를 구현하는 것이다.

---

# 1. 가장 중요한 구현 원칙

다음 원칙을 절대적으로 준수한다.

### 1.1 Java는 데이터 이동 엔진이 아니다

Java가 대용량 데이터를 직접 읽어서 메모리로 가져오거나 Row 단위 INSERT를 수행하지 않는다.

데이터 이동은 다음 구조를 사용한다.

```text
AS-IS Sybase IQ 16.0
        │
        │ Native Extract / UNLOAD
        ▼
Shared Volume
        │
        │ LOAD TABLE
        ▼
TO-BE Sybase IQ 16.2
```

Java는 다음 역할만 수행한다.

```text
Metadata 조회
      ↓
SQL 생성
      ↓
Sybase IQ Command 실행
      ↓
파일 상태 확인
      ↓
Validation 실행
      ↓
결과 비교
      ↓
GODIS/MariaDB 상태 기록
```

---

# 2. 시스템 규모

* AS-IS: HP-UX / Sybase IQ 16.0
* TO-BE: Linux / Sybase IQ 16.2
* 대상 테이블: 약 13,000개
* 전체 데이터: 약 250TB
* Shared Volume: 약 50TB
* AS-IS / TO-BE는 동일 네트워크
* GODIS가 Batch Group / Task를 관리
* GODIS가 Shell Script를 호출
* Shell Script가 Java Worker를 실행

---

# 3. 실행 구조

```text
GODIS
  │
  │ Batch Group / Job
  ▼
run_migration.sh
  │
  ▼
MigrationWorkerApplication
  │
  ▼
MigrationWorker
  │
  ├── TableMigrationTask
  │       │
  │       ├── Extract
  │       ├── AS-IS Verify
  │       ├── Load
  │       ├── TO-BE Verify
  │       └── Compare
  │
  └── MariaDB Status / Result
```

하나의 Batch Group은 여러 테이블을 처리할 수 있다.

초기 구현에서는 **하나의 Worker 내부에서는 테이블을 순차 처리**한다.

병렬 처리는 GODIS의 Batch Group / Job 실행 단위에서 담당할 수 있도록 설계한다.

---

# 4. 기술 제약

## 반드시 사용

* Pure Java
* JDBC
* MyBatis
* SLF4J
* Logback
* Maven
* Linux Shell

## 사용하지 않음

* Spring Boot
* Spring Framework
* Spring Batch
* HikariCP
* 별도 Connection Pool
* 불필요한 DI Framework

Java Application의 시작점:

```java
public static void main(String[] args)
```

---

# 5. CLI

다음 형식을 사용한다.

```bash
java -cp ... com.migration.MigrationWorkerApplication \
    <EXEC_SEQ> \
    <TASK_ID> \
    <EXEC_USER>
```

| Argument | 설명        | 필수  |
| -------- | --------- | --- |
| args[0]  | EXEC_SEQ  | YES |
| args[1]  | TASK_ID   | YES |
| args[2]  | EXEC_USER | NO  |

`EXEC_USER` 기본값:

```text
SYSTEM
```

---

# 6. Connection Architecture

총 3개의 논리적 Connection을 사용한다.

```text
META
 └── MariaDB / GODIS

ASIS
 └── Sybase IQ 16.0

TOBE
 └── Sybase IQ 16.2
```

Connection Pool을 사용하지 않는다.

Connection은 **Batch Group / Worker 작업 단위에서 재사용**한다.

테이블마다 Connection을 생성하지 않는다.

잘못된 구현:

```java
for (TableInfo table : tables) {
    Connection conn = createConnection();
    ...
    conn.close();
}
```

권장:

```java
Connection asisConn = createConnection();
Connection tobeConn = createConnection();

for (TableInfo table : tables) {
    process(table);
}

asisConn.close();
tobeConn.close();
```

단, Connection 오류/timeout에 대한 명확한 예외 처리를 구현한다.

---

# 7. Statement / PreparedStatement

## Statement

다음 작업은 `Statement`를 기본으로 사용한다.

```text
SET TEMPORARY OPTION
Sybase IQ Native Extract
LOAD TABLE
동적 Table / Column Identifier를 포함하는 SQL
```

예:

```java
try (Statement stmt = conn.createStatement()) {
    stmt.executeUpdate(sql);
}
```

---

## PreparedStatement

다음 조건에서 사용한다.

* 동일 SQL 구조 반복
* 값만 변경
* WHERE 조건 parameter binding
* 반복적인 검증 SQL

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

단,

```sql
FROM DBA.?
```

처럼 Table/Column Identifier를 `?`로 대체하지 않는다.

---

# 8. ★ 테이블 스키마 정보는 SQL 생성의 기준정보로 사용한다

첨부된 **테이블 스키마 정보**를 단순 참고용 문서로 취급하지 않는다.

스키마 정보는 다음 SQL 생성에 직접 사용한다.

```text
1. Extract SQL
2. LOAD TABLE SQL
3. Validation SQL
4. 데이터 타입별 Validation
5. Binary / BLOB 처리
6. NULL 처리
7. Column 순서 결정
8. 파일 Format 결정
```

---

# 9. Schema Metadata

첨부된 스키마 정보를 다음 논리적 구조로 해석한다.

```text
TableSchema
 ├── schemaName
 ├── tableName
 ├── tableComment
 ├── columns[]
 │     ├── ordinalPosition
 │     ├── columnName
 │     ├── dataType
 │     ├── dataLength
 │     ├── dataPrecision
 │     ├── dataScale
 │     ├── nullable
 │     ├── defaultValue
 │     └── comment
 ├── primaryKey[]
 └── 기타 Constraint / Attribute
```

**실제 첨부 스키마에 존재하는 필드만 사용한다.**

첨부자료에 존재하지 않는 필드를 사실처럼 만들어내지 않는다.

---

# 10. Schema와 Migration Metadata의 관계

다음 세 종류의 정보를 명확히 구분한다.

```text
A. Table Schema
   → 실제 DB 구조

B. Migration Metadata
   → 어떻게 이관할 것인가

C. Verification Metadata
   → 어떻게 검증할 것인가
```

관계:

```text
Table Schema
      │
      ├──────────────┐
      ▼              ▼
Migration        Validation
Metadata         Metadata
      │              │
      └──────┬───────┘
             ▼
      Migration SQL
```

---

# 11. GPCL_MIG_TABLE

`TASK_ID`를 기준으로 이관 대상 테이블을 조회한다.

다음 유형의 정보를 관리한다.

```text
TASK_ID
SCHEMA_NAME
TABLE_NAME
EXECUTION_ORDER
EXTRACT_OPTION
SPLIT_YN
BLOB_YN
FILE_PATH
FILE_NAME
WHERE_CONDITION
기타 Migration Option
```

**실제 테이블 정의에 없는 컬럼을 임의로 생성하지 않는다.**

---

# 12. GPCL_MIG_VRF_TARGET

검증 대상 정보를 관리한다.

예:

```text
TABLE_NAME
COLUMN_NAME
VERIFY_FUNCTION
EXECUTION_ORDER
```

지원 함수:

```text
COUNT
SUM
AVG
MIN
MAX
```

하나의 Table에 여러 Column과 여러 Validation Function이 존재할 수 있다.

---

# 13. Schema → LOAD TABLE 자동 생성

`LOAD TABLE`의 Column 목록은 임의로 작성하지 않는다.

반드시 Schema의 `ordinalPosition` 기준으로 생성한다.

예:

```text
Schema
  COL1 ordinal=1
  COL2 ordinal=2
  COL3 ordinal=3
```

이면:

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

Column 순서를 변경하지 않는다.

---

# 14. Schema → Extract

Extract 시에도 Schema의 Column 순서를 기준으로 한다.

특히 다음 데이터 타입을 별도로 처리한다.

```text
CHAR
VARCHAR
LONG VARCHAR
NUMERIC
DECIMAL
INTEGER
BIGINT
DATE
TIME
TIMESTAMP
BINARY
VARBINARY
LONG BINARY
BLOB
```

**실제 Sybase IQ 16.0에서 지원되는 데이터 타입만 사용한다.**

첨부 스키마의 타입이 위 목록과 다르면 해당 타입을 우선한다.

---

# 15. BINARY / BLOB Column

Binary 계열 컬럼이 존재하면 일반 문자/숫자 컬럼과 동일하게 처리하지 않는다.

다음 정보를 확인한다.

```text
BLOB_YN
Binary Column
Extract Option
Binary File Path
LOAD Option
```

Binary 데이터를 Java Heap에 전체 적재하지 않는다.

필요하면 파일 기반 처리 방식을 사용한다.

---

# 16. Validation SQL 생성

Validation SQL은 다음 두 종류의 Metadata를 조합하여 생성한다.

```text
Table Schema
+
GPCL_MIG_VRF_TARGET
```

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

---

# 17. Validation Function과 Data Type

Validation Function은 Column Data Type을 고려한다.

예:

```text
COUNT
 └── 모든 대상 Column 가능

SUM
 └── Numeric 계열 우선

AVG
 └── Numeric 계열 우선

MIN
 └── 비교 가능한 타입

MAX
 └── 비교 가능한 타입
```

문자열 Column에 `SUM()`을 생성하는 등 **Data Type에 맞지 않는 SQL은 생성하지 않는다.**

Schema와 Validation Metadata가 모순되면:

```text
VALIDATION_METADATA_ERROR
```

로 처리하고 임의 변환하지 않는다.

---

# 18. NULL 처리

Validation 결과에서 NULL을 정상적인 결과로 처리할 수 있어야 한다.

예:

```text
SUM() = NULL
AVG() = NULL
MIN() = NULL
MAX() = NULL
```

일 수 있다.

따라서 다음과 같이 단순 비교하지 않는다.

```java
asisValue.equals(tobeValue)
```

다음 개념을 사용한다.

```text
NULL == NULL → MATCH

NULL != 값 → MISMATCH

값 == 값 → 타입/Precision 정책에 따라 비교
```

---

# 19. Numeric Validation

특히:

```text
DECIMAL
NUMERIC
DOUBLE
FLOAT
```

등은 문자열 비교를 하지 않는다.

가능하면:

```java
BigDecimal
```

기반으로 비교한다.

AVG 등에서 Precision 차이가 발생할 수 있으므로 다음 정책을 분리한다.

```text
EXACT
SCALE
TOLERANCE
```

프로젝트 요구사항에 정의되지 않은 허용오차를 임의로 지정하지 않는다.

---

# 20. Date / Timestamp Validation

다음 타입은 문자열 포맷만 비교하지 않는다.

```text
DATE
TIME
TIMESTAMP
```

JDBC가 반환하는 타입 또는 명시적인 정규화 규칙을 사용한다.

Time Zone 차이가 존재할 가능성이 있으면 별도의 Normalization 정책을 구현한다.

정책이 제공되지 않은 경우 임의의 Time Zone을 선택하지 않는다.

---

# 21. Pipeline

전체 Pipeline:

```text
Step 0
Initialize
   ↓
Step 1
AS-IS Extract
   ↓
Step 2
AS-IS Verify
   ↓
Step 3
TO-BE Load
   ↓
Step 4
TO-BE Verify
   ↓
Compare
   ↓
Finish
```

---

# 22. Step 0 - Initialize

```text
CLI Argument 검증
      ↓
META Connection
      ↓
GPCL_CM_CD_VAL
      ↓
Runtime Configuration
      ↓
Log Level
      ↓
ASIS Connection
      ↓
TOBE Connection
      ↓
GPCL_MIG_TABLE
      ↓
GPCL_MIG_VRF_TARGET
      ↓
Schema Metadata
      ↓
Batch Group 구성
      ↓
RUNNING
```

Schema Metadata가 별도 테이블/파일/문서로 제공되는 경우 그 실제 구조를 우선한다.

---

# 23. Step 1 - AS-IS Extract

Table별:

```text
Table Start
    ↓
Schema 확인
    ↓
Extract Option 설정
    ↓
Native Extract
    ↓
파일 생성
    ↓
파일 존재 확인
    ↓
파일 크기 확인
```

Extract 실패 시 파일을 임의로 삭제하지 않는다.

---

# 24. Step 2 - AS-IS Validation

AS-IS DB에서 Validation SQL을 실행한다.

결과:

```text
ValidationResult
```

를 생성한다.

최소:

```text
EXEC_SEQ
TASK_ID
TABLE_NAME
COLUMN_NAME
FUNCTION
VALUE
```

를 관리한다.

---

# 25. Step 3 - TO-BE Load

Schema 기반으로 `LOAD TABLE` SQL을 생성한다.

예:

```sql
LOAD TABLE <SCHEMA>.<TABLE>
(
    <SCHEMA COLUMN ORDER>
)
FROM '<FILE_PATH>'
FORMAT BINARY;
```

실제 Sybase IQ 16.2 문법과 Schema에 맞게 생성한다.

---

# 26. 파일 삭제 정책

절대 다음 순서로 하지 않는다.

```text
Extract
 ↓
Delete
 ↓
Load
```

반드시:

```text
Extract
 ↓
ASIS Verify
 ↓
Load
 ↓
Load SUCCESS 확인
 ↓
File Delete
 ↓
TOBE Verify
```

순서로 처리한다.

LOAD 실패 시 파일을 보존한다.

---

# 27. Step 4 - TO-BE Validation

AS-IS와 동일한 기준으로 TO-BE Validation을 수행한다.

```text
ASIS Validation
       │
       │
       ▼
TOBE Validation
       │
       ▼
MigrationValidator
       │
   ┌───┴───┐
   ▼       ▼
MATCH   MISMATCH
```

---

# 28. Validation 결과

다음 정보를 저장한다.

```text
TABLE_NAME
COLUMN_NAME
FUNCTION
ASIS_VALUE
TOBE_VALUE
MATCH_YN
DIFFERENCE
MESSAGE
```

필요하면:

```text
DATA_TYPE
PRECISION
SCALE
```

도 함께 저장한다.

---

# 29. Table 단위 상태

Table별 상태를 다음과 같이 관리한다.

```text
READY
EXTRACTING
EXTRACTED
ASIS_VERIFYING
ASIS_VERIFIED
LOADING
LOADED
TOBE_VERIFYING
SUCCESS
VERIFY_MISMATCH
FAILED
```

상태 전이는 임의로 건너뛰지 않는다.

---

# 30. Batch Group

하나의 Batch Group:

```text
Batch Group
 │
 ├── CUSTOMER
 ├── ACCOUNT
 ├── PRODUCT
 ├── TRANSACTION
 └── ...
```

Table별로 독립적인 결과를 남긴다.

초기 정책:

```text
Table 실패
   ↓
해당 Table FAILED
   ↓
Batch Group 중단
   ↓
GODIS 비정상 종료
```

단, 향후 `CONTINUE_ON_ERROR` 옵션을 추가할 수 있도록 설계한다.

---

# 31. Java Class Architecture

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
│   ├── TableSchema
│   ├── ColumnSchema
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

# 32. 핵심 객체

## TableSchema

```java
class TableSchema {
    String schemaName;
    String tableName;
    List<ColumnSchema> columns;
}
```

## ColumnSchema

```java
class ColumnSchema {
    int ordinalPosition;
    String columnName;
    String dataType;
    Integer length;
    Integer precision;
    Integer scale;
    boolean nullable;
}
```

실제 Schema 정보에 존재하지 않는 속성은 임의로 생성하지 않는다.

---

# 33. SQL Builder

`SybaseIQSqlBuilder`는 다음 기능을 제공한다.

```text
buildExtractOptionSql()
buildExtractSql()
buildLoadSql()
buildValidationSql()
```

각 메서드는 Schema와 Migration Metadata를 입력으로 받는다.

예:

```java
String buildLoadSql(
    TableSchema schema,
    TableInfo tableInfo
)
```

---

# 34. SQL Builder의 책임 분리

SQL Builder는:

```text
SQL 생성
```

만 담당한다.

다음은 담당하지 않는다.

```text
Connection 생성
SQL 실행
Transaction
파일 삭제
상태 변경
Logging
```

---

# 35. MyBatis

`MetaMapper.xml`:

```text
GPCL_CM_CD_VAL 조회
GPCL_MIG_TABLE 조회
GPCL_MIG_VRF_TARGET 조회
Schema Metadata 조회
Batch 상태 INSERT/UPDATE
Validation 결과 INSERT/UPDATE
```

`SybaseIQMapper.xml`:

```text
SET TEMPORARY OPTION
Extract
LOAD TABLE
Validation
```

동적 Identifier는 반드시 검증된 Schema/Metadata 값만 사용한다.

가능한 일반 데이터값은:

```text
#{parameter}
```

를 사용한다.

---

# 36. Schema 검증

실행 전에 반드시 다음을 검증한다.

```text
Schema 존재
Table 존재
Column 존재
Column 순서
Data Type
Column Count
```

AS-IS와 TO-BE Schema가 동일해야 한다는 전제라면 실제 실행 전에 비교할 수 있도록 한다.

예:

```text
ASIS Schema
     │
     ├── Column Count
     ├── Column Name
     ├── Ordinal
     ├── Data Type
     ├── Length
     ├── Precision
     └── Scale
     │
     ▼
TOBE Schema
```

불일치 시 이관을 진행하지 않고:

```text
SCHEMA_MISMATCH
```

상태로 종료한다.

---

# 37. Schema Mismatch 상세 결과

다음 정보를 로그/결과에 남긴다.

```text
TABLE_NAME
COLUMN_NAME
ATTRIBUTE
ASIS_VALUE
TOBE_VALUE
MESSAGE
```

예:

```text
TABLE       : CUSTOMER
COLUMN      : AMOUNT
ATTRIBUTE   : DATA_TYPE
ASIS        : DECIMAL(18,2)
TOBE        : DECIMAL(20,2)
```

---

# 38. 파일 처리

`FileUtil`:

```text
exists()
getSize()
isReadable()
deleteSafely()
```

지원.

LOAD 성공 전에는 삭제하지 않는다.

파일 삭제 실패는 Migration 성공 여부와 별도로 명확히 기록한다.

단, Shared Volume 공간 부족으로 후속 이관에 영향을 줄 수 있으므로 운영 정책에 따라 Batch Group 결과에 반영할 수 있도록 한다.

---

# 39. Logging

SLF4J + Logback.

모든 주요 로그에는:

```text
EXEC_SEQ
TASK_ID
TABLE_NAME
EXECUTION_ORDER
STEP
```

를 포함한다.

Password는 절대 로그에 출력하지 않는다.

---

# 40. Performance

250TB 규모를 고려한다.

다음 사항을 반드시 지킨다.

```text
Connection
→ Worker 단위 재사용

Statement
→ SQL 작업 단위

PreparedStatement
→ 반복 SQL

ResultSet
→ 즉시 close

Data
→ JVM Heap에 전체 적재 금지

Extract
→ Sybase IQ Native

Load
→ Sybase IQ LOAD TABLE
```

Java에서:

```java
List<Row>
byte[]
InputStream 전체 데이터
```

형태로 대용량 이관 데이터를 메모리에 보관하지 않는다.

---

# 41. Transaction

Transaction 경계를 명확히 분리한다.

```text
META
ASIS
TOBE
```

각 DB Connection의 Transaction을 서로 하나의 Transaction으로 취급하지 않는다.

특히:

```text
Extract
Load
Validation
Status Update
```

의 실패가 서로에게 미치는 영향을 명확히 정의한다.

---

# 42. Error Code

```text
0  SUCCESS
1  INVALID_ARGUMENT
2  INITIALIZATION_ERROR
3  METADATA_ERROR
4  SCHEMA_ERROR
5  EXTRACT_ERROR
6  LOAD_ERROR
7  VALIDATION_ERROR
8  VERIFY_MISMATCH
9  SYSTEM_ERROR
```

---

# 43. 생성 전에 반드시 수행할 분석

코드 생성 전에 먼저 다음을 출력한다.

## A. 요구사항 분석

```text
Requirement
Interpretation
Implementation
```

## B. Schema 분석

첨부된 Schema에서 실제로 확인되는:

```text
Table
Column
Data Type
Length
Precision
Scale
Nullable
PK
기타 속성
```

을 표로 정리한다.

## C. Metadata 관계

다음을 매핑한다.

```text
GPCL_MIG_TABLE
        ↓
TableSchema
        ↓
GPCL_MIG_VRF_TARGET
        ↓
SQL Builder
        ↓
Extract / Load / Validation
```

## D. 불명확한 사항

임의로 결정하지 말고:

```text
[확인 필요]
```

로 명시한다.

---

# 44. 코드 생성 순서

한 번에 모든 코드를 생성하지 말고 다음 순서로 구현한다.

### Phase 1

```text
Maven Project
Main
Config
ConnectionFactory
```

### Phase 2

```text
MyBatis
MetaMapper
Metadata DTO
Schema DTO
```

### Phase 3

```text
SybaseIQSqlBuilder
Extract
Load
```

### Phase 4

```text
Validation
ValidationResult
MigrationValidator
```

### Phase 5

```text
MigrationWorker
Pipeline
Error Handling
State Management
```

### Phase 6

```text
Shell
Logging
Integration Test
```

각 Phase가 컴파일 가능한 상태인지 확인한 후 다음 Phase로 진행한다.

---

# 45. 반드시 생성해야 하는 테스트

최소 다음 테스트를 작성한다.

```text
1. 정상 단일 Table
2. 정상 다중 Table
3. Schema 정상
4. Schema Mismatch
5. Column Count Mismatch
6. Column Order Mismatch
7. Data Type Mismatch
8. Extract 실패
9. Extract File 생성 실패
10. ASIS Validation 실패
11. LOAD 실패
12. LOAD 후 File Delete 실패
13. TOBE Validation 실패
14. Validation Mismatch
15. NULL Validation
16. DECIMAL Validation
17. DATE/TIMESTAMP Validation
18. BINARY/BLOB Table
19. Metadata 조회 실패
20. Connection 실패
21. 잘못된 CLI Argument
```

---

# 46. 최종 산출물

다음 순서로 결과를 생성한다.

```text
1. 요구사항 분석
2. 첨부 Schema 분석
3. Metadata Mapping
4. Architecture
5. Mermaid Diagram
6. Project Tree
7. Maven pom.xml
8. Java 전체 소스
9. MyBatis XML 전체 소스
10. Logback 설정
11. Shell Script
12. Unit Test
13. Integration Test
14. Build 방법
15. 실행 방법
16. 오류 처리
17. 운영 시 고려사항
```

코드를:

```text
...
```

로 생략하지 않는다.

---

# 47. 절대 금지사항

다음 행동을 하지 않는다.

1. 존재하지 않는 Schema Column을 임의 생성
2. 존재하지 않는 Metadata Column을 임의 생성
3. Sybase IQ 문법을 일반 ANSI SQL로 임의 변환
4. `LOAD TABLE`을 일반 INSERT 방식으로 변경
5. 250TB 데이터를 Java Memory로 이동
6. Connection을 Table마다 생성
7. Connection Pool을 임의 추가
8. Spring Boot를 임의 추가
9. Schema와 다른 Column 순서 사용
10. Data Type에 맞지 않는 Validation Function 생성
11. NULL을 임의로 0 또는 빈 문자열로 변환
12. Numeric 비교를 단순 String 비교
13. LOAD 실패 후 파일 삭제
14. 오류를 `printStackTrace()`만으로 처리
15. 요구사항에 없는 운영 정책을 임의로 확정

---

# 48. 최종 목표

최종 프로그램은 다음 구조를 만족해야 한다.

```text
                 GODIS
                   │
                   ▼
          run_migration.sh
                   │
                   ▼
      MigrationWorkerApplication
                   │
                   ▼
          MigrationWorker
                   │
       ┌───────────┴───────────┐
       │                       │
   Metadata                 Schema
       │                       │
       └───────────┬───────────┘
                   │
                   ▼
             TableMigration
                   │
        ┌──────────┼──────────┐
        ▼          ▼          ▼
     Extract     Verify      Load
        │          │          │
        ▼          │          ▼
 Shared Volume     │       TO-BE IQ
        │          │
        └──────────┴──────────┐
                              ▼
                       TO-BE Verify
                              │
                              ▼
                           Compare
                              │
                 ┌────────────┴────────────┐
                 ▼                         ▼
               SUCCESS              VERIFY_MISMATCH
                 │
                 ▼
              GODIS
```

핵심적으로 이 프로그램은 **Schema + Migration Metadata + Verification Metadata를 결합하여 이관 SQL을 생성하고, Sybase IQ Native Extract/Load를 실행하며, AS-IS/TO-BE의 정량적 검증 결과를 비교하는 Orchestrator**로 구현한다.

### 특히 이번에는 프롬프트의 구조를 이렇게 바꾸는 것이 중요합니다

현재 파일의 기존 구조에서는 `GPCL_MIG_TABLE`이 테이블명·스키마·옵션·파일분할·BLOB 여부를 관리하고, `GPCL_MIG_VRF_TARGET`이 검증 컬럼과 함수를 관리하는 것으로 되어 있습니다. 

여기에 **실제 테이블 스키마**가 들어오면 LLM이 다음 3가지를 혼동하지 않도록 해야 합니다.

```text
                 ┌──────────────────────┐
                 │     Table Schema     │
                 │                      │
                 │ Column               │
                 │ Data Type            │
                 │ Length / Precision   │
                 │ Ordinal              │
                 └──────────┬───────────┘
                            │
             ┌──────────────┴──────────────┐
             ▼                             ▼
   ┌───────────────────┐       ┌───────────────────┐
   │ Migration Metadata │       │ Validation Metadata│
   │                   │       │                   │
   │ 어떻게 이관할지    │       │ 어떻게 검증할지    │
   └─────────┬─────────┘       └─────────┬─────────┘
             │                           │
             └─────────────┬─────────────┘
                           ▼
                 ┌──────────────────┐
                 │ SQL Builder      │
                 ├──────────────────┤
                 │ Extract SQL      │
                 │ LOAD TABLE SQL   │
                 │ Validation SQL   │
                 └──────────────────┘
```

이렇게 해야 **예를 들어 `DECIMAL(18,2)` 컬럼에 `AVG()`를 적용하거나, BLOB 컬럼을 일반 컬럼처럼 처리하거나, 스키마의 실제 컬럼 순서와 다른 `LOAD TABLE`을 생성하는 오류를 LLM 단계에서 줄일 수 있습니다.**

다만 **현재 제가 받은 첨부파일에는 말씀하신 실제 테이블 스키마가 들어 있지 않습니다.** 현재 파일은 8,121 bytes / 118 lines이고, 제가 확인한 전체 내용은 기존 프로젝트 요구사항까지만 포함합니다. 

**스키마가 들어 있는 파일을 다시 첨부해 주시면**, 이번에는 위 템플릿에 실제 `TABLE / COLUMN / DATA_TYPE / LENGTH / PRECISION / SCALE / NULL / PK` 등을 하나씩 반영해서, **Claude Code / Qwen Code / Codex에 바로 투입할 수 있는 최종 개발 프롬프트**로 만들어드리겠습니다.
