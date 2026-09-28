# Codex 응답 언어

- 사용자에게 보내는 설명, 진행 상황, 질문, 최종 답변은 기본적으로 한국어로 작성한다.
- 사용자가 다른 언어로 답변해 달라고 명시한 경우에만 해당 언어를 사용한다.
- 코드, 명령어, 파일명, API 이름, 오류 메시지 등 기술적으로 원문 유지가 필요한 내용은 그대로 표기한다.

# 배치시스템 아키텍처

배치실행 구조는
ASIS데이터베이스(HP-unix, SybaseIQ16.0)에서 공유볼륨에 파일을 추출하고,
TOBE베이터베이스(Linux, SybaseIQ16.2)에서 공유볼륨에 생성된 파일을 적재합니다.
이때 GODIS웹의 배치프래임워크는 TOBE베이터베이스가 설치된 시스템에 docker 위에서 운영됩니다.
즉, TOBE시스템의 shell을 이용하여 배치java를 실행하는 구조입니다.

## Sybase IQ SQL에 사용하는 Linux 서버 경로
DB_EXPORT_DATA_PATH=/opt/sap/iq161/IQ-16_1/demo/data
DB_EXPORT_BLOB_PATH=/opt/sap/iq161/IQ-16_1/demo/blob

## MigrationWorkerApplication이 접근하는 경로
WORKER_EXPORT_DATA_PATH=C:/migrationTemp/data
WORKER_EXPORT_BLOB_PATH=C:/migrationTemp/blob


# Project Coding Rule
- ./codingRules.md 참고