# Migration Worker Agent Guide

## 프로젝트 목적

이 저장소는 Sybase IQ 16.0(AS-IS)에서 16.2(TO-BE)로 대용량 데이터를 이관하는 Pure Java 오케스트레이터다. Java가 데이터를 메모리로 운반하지 않고 Sybase IQ Native Extract, 공유 볼륨, `LOAD TABLE`을 제어한다.

## 요구사항 우선순위

구현 판단은 다음 순서를 따른다.

1. `docs/Table정보/*.sql`
2. `docs/개발요건/대용량데이터_이관_개발 MasterPrompt .md`

충돌이나 불명확한 항목은 명시적으로 기록한다. 확인되지 않은 컬럼, SQL 문법, 상태 코드, 설정 규약은 만들지 말고 TODO 또는 확장 지점으로 남긴다.

## 기술 및 구조 제약

- Java SE 22, JDBC, MyBatis XML, SLF4J, Logback, Maven을 사용한다.
- Spring, Spring Boot, Spring Batch, HikariCP 및 Connection Pool을 도입하지 않는다.
- 진입점은 `com.migration.MigrationWorkerApplication`이다.
- `MigrationWorker`가 하나의 `TASK_ID` Batch Group을 순차 처리한다.
- GODIS, ASIS, TOBE 연결은 Worker 인스턴스가 소유하며 Batch Group 전체에서 재사용한다.
- 테이블별 연결 생성, 임의 Thread Pool을 금지한다.
- Statement와 ResultSet은 작업 단위에서 try-with-resources로 닫는다.

## 처리 및 안전 원칙

- 테이블 처리 순서는 Extract → AS-IS Verify → Load → TO-BE Verify → Compare → Result/Cleanup이다.
- `MIG_COND`는 WHERE 조건 본문만 허용하며 임의 SQL 전체를 허용하지 않는다.
- BLOB은 일반 컬럼처럼 추정 처리하지 않는다.
- 250TB 데이터를 JVM Heap, `List<Row>`, 대형 `byte[]`로 적재하지 않는다.
- 검증 성공 전 Extract 파일을 삭제하지 않는다.
- 예외를 무시하거나 단순 `printStackTrace()`로 처리하지 않는다. 실행 문맥과 원인을 보존하고 정의된 Exit Code로 전달한다.

## 코드 및 테스트 위치

- Java: `src/main/java/com/migration`
- MyBatis/설정: `src/main/resources`
- 테스트: `src/test/java`
- 요구사항: `docs/개발요건`

빌드와 검증은 `mvn clean test`, 패키징은 `mvn clean package`를 사용한다. 테스트 클래스는 `*Test`, 테스트 메서드는 관찰 가능한 동작을 이름으로 사용한다.