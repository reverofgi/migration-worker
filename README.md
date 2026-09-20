# migration-worker - Phase 2 Metadata

## Scope

This project implements **Phase 1 - Project Skeleton** and **Phase 2 - Metadata**.

Implemented:

- Pure Java entry point
- CLI argument validation
- `MigrationWorker`
- `MigrationConfig`
- `ConnectionFactory` extension point
- Exit code model
- Base migration exceptions
- SLF4J + Logback logging
- Basic utility extension points
- Unit tests
- Batch Group-scoped 3-Connection lifecycle structure
- MyBatis `MetaMapper` and XML mappings
- `GPCL_MIG_TABLE` lookup by `JOB_ID`
- `GPCL_MIG_VRF_TARGET` lookup ordered by `ORDINAL_POSITION`
- `TableInfo` and `ValidationTarget` metadata models
- Explicit Y/N aggregate flag conversion
- Integration-style metadata tests for JOB_ID 100, 200, and 300

Not implemented intentionally:

- Physical Sybase IQ schema access
- Native Extract
- `LOAD TABLE`
- Validation
- Result persistence
- GODIS integration

## CLI

```bash
java -cp ... com.migration.MigrationWorkerApplication <EXEC_SEQ> <JOB_ID> [EXEC_USER]
```

Example:

```bash
java -cp ... com.migration.MigrationWorkerApplication 1001 100 SYSTEM
```

## Connection lifecycle

One Worker instance owns three instance-level connections:

- META -> MariaDB
- ASIS -> Sybase IQ 16.0
- TOBE -> Sybase IQ 16.2

They are created at Batch Group initialization and closed in `finally`.

They are **not static global connections** and are **not created per table**.

## Build

```bash
mvn clean test
mvn clean package
```

## Important dependency note

The source Master Prompt does not provide enterprise-approved dependency versions.
The `pom.xml` therefore contains explicit baseline placeholders for Phase 1 only.

Before Phase 2 / production use, confirm:

1. Java/JDK version
2. Maven version
3. MyBatis version
4. MariaDB JDBC driver version
5. Sybase IQ JDBC driver version
6. SLF4J/Logback versions
7. Enterprise artifact repository coordinates

The production MariaDB and Sybase IQ JDBC drivers are intentionally not added until
their approved artifact coordinates/versions are known. Phase 2 tests use H2 only
as an in-memory SQL fixture; production metadata SQL remains MariaDB-compatible.
