# Migration Worker Agent Guide

## 프로젝트 목적

이 저장소는 Sybase IQ 16.0(AS-IS)에서 16.2(TO-BE)로 대용량 데이터를 이관하는 Pure Java 오케스트레이터다. Java가 데이터를 메모리로 운반하지 않고 Sybase IQ Native Extract, 공유 볼륨, `LOAD TABLE`을 제어한다. 현재 구현 범위는 Phase 1(Project Skeleton)과 Phase 2(Metadata)다.

## 요구사항 우선순위

구현 판단은 다음 순서를 따른다.

1. 실제 AS-IS/TO-BE DB 스키마
2. 실제 MariaDB Metadata DDL
3. 제공된 샘플 데이터
4. `docs/개발요건/2단계_5_ChatGPT 내용검토후 재수정.md`
5. `docs/Table정보/테이블정보.sql`
6. 기존 코드
7. 일반적인 개발 관행

충돌이나 불명확한 항목은 명시적으로 기록한다. 확인되지 않은 컬럼, SQL 문법, 상태 코드, 설정 규약은 만들지 말고 TODO 또는 확장 지점으로 남긴다.

## 기술 및 구조 제약

- Java SE 22, JDBC, MyBatis XML, SLF4J, Logback, Maven을 사용한다.
- Spring, Spring Boot, Spring Batch, HikariCP 및 Connection Pool을 도입하지 않는다.
- 진입점은 `com.migration.MigrationWorkerApplication`이다.
- `MigrationWorker`가 하나의 `JOB_ID` Batch Group을 순차 처리한다.
- META, ASIS, TOBE 연결은 Worker 인스턴스가 소유하며 Batch Group 전체에서 재사용한다.
- static 전역 연결, 테이블별 연결 생성, 임의 Thread Pool을 금지한다.
- Statement와 ResultSet은 작업 단위에서 try-with-resources로 닫는다.

## Metadata 규칙

Migration Metadata와 실제 이관 테이블의 Physical Schema를 혼동하지 않는다. `GPCL_MIG_TABLE`은 `JOB_ID`로 조회하고 `PROC_ORD, TABLE_SCHEMA, TABLE_NAME` 순으로 정렬한다. `GPCL_MIG_VRF_TARGET`은 전체 컬럼과 검증 함수를 제공하며 반드시 `ORDINAL_POSITION`으로 정렬한다. 값은 MyBatis `#{...}`로 바인딩한다. `${...}` 또는 문자열 조합이 필요한 식별자는 사전에 검증된 metadata만 사용한다.

## 처리 및 안전 원칙

- 테이블 처리 순서는 Extract → AS-IS Verify → Load → TO-BE Verify → Compare → Result/Cleanup이다.
- `MIG_COND`는 WHERE 조건 본문만 허용하며 임의 SQL 전체를 허용하지 않는다.
- BLOB은 일반 컬럼처럼 추정 처리하지 않는다.
- 250TB 데이터를 JVM Heap, `List<Row>`, 대형 `byte[]`로 적재하지 않는다.
- 검증 성공 전 Extract 파일을 삭제하지 않는다.
- 비밀번호, 자격증명, 원본 데이터는 로그에 출력하지 않는다.
- 예외를 무시하거나 단순 `printStackTrace()`로 처리하지 않는다. 실행 문맥과 원인을 보존하고 정의된 Exit Code로 전달한다.

## 단계별 개발

Phase 3 Physical Schema, Phase 4 SQL Builder, Phase 5 Extract/Load, Phase 6 Validation, Phase 7 Orchestration, Phase 8 Integration Test 순서를 지킨다. 요청받은 Phase만 구현하고 다음 Phase 기능을 선행 구현하지 않는다. 각 단계에서 컴파일과 테스트를 통과한 후 진행한다.

## 코드 및 테스트 위치

- Java: `src/main/java/com/migration`
- MyBatis/설정: `src/main/resources`
- 테스트: `src/test/java`
- 요구사항: `docs/개발요건`

빌드와 검증은 `mvn clean test`, 패키징은 `mvn clean package`를 사용한다. 테스트 클래스는 `*Test`, 테스트 메서드는 관찰 가능한 동작을 이름으로 사용한다. Metadata 테스트에는 `JOB_ID=100`, `200`, `300`, 정렬, 빈 결과, 잘못된 입력, Y/N 변환을 포함한다.

## 변경 작업 체크리스트

1. 실제 요구사항과 추정 사항을 분리한다.
2. 기존 Phase 범위와 Connection 생명주기를 보존한다.
3. 동적 SQL의 값과 식별자를 구분한다.
4. 정상·실패·경계 조건 테스트를 추가한다.
5. `target/`, IDE 설정, 자격증명, 이관 산출물을 커밋하지 않는다.
