/*
DESCRIBE information_schema.tables;
  
DESCRIBE information_schema.columns;

select ta.*
  from information_schema.tables ta
 where 1=1
   and ta.TABLE_NAME = ''
;
select *
  from information_schema.columns co
 where co.TABLE_NAME = ''
;  
*/

-- 1. 스키마(데이터베이스) 생성
-- Charset은 시스템 요구사항에 맞춰 설정합니다. (기본 utf8mb4 / As-Is euc-kr 호환 필요시 euc-kr 적용)
CREATE DATABASE IF NOT EXISTS `MIG_TEST`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

-- 2. 마이그레이션 전용 계정(MigUser) 생성
-- '%' (외부 접속용: ETL 서버 및 배치 서버, DBeaver) / 'localhost' (내부 접속용)
CREATE USER IF NOT EXISTS 'MigUser'@'%' IDENTIFIED BY 'ChoUm6315!';
CREATE USER IF NOT EXISTS 'MigUser'@'localhost' IDENTIFIED BY 'ChoUm6315!';

-- 3. MIG_TEST 스키마에 대한 MigUser Owner 권한 부여 (ALL PRIVILEGES)
GRANT ALL PRIVILEGES ON `MIG_TEST`.* TO 'MigUser'@'%';
GRANT ALL PRIVILEGES ON `MIG_TEST`.* TO 'MigUser'@'localhost';

-- 4. 권한 반영
FLUSH PRIVILEGES;

-- 5. 스키마 전환
USE `MIG_TEST`;



DROP TABLE IF EXISTS GPCL_CM_CD;
CREATE TABLE GPCL_CM_CD (
    GRP_CD_ID        VARCHAR(30)    NOT NULL                  COMMENT '코드그룹ID',
    GRP_NM           VARCHAR(100)   NOT NULL                  COMMENT '코드그룹명',
    GRP_CD_DSC       VARCHAR(200)   NULL     DEFAULT NULL     COMMENT '코드그룹설명',
    VALID_STRT_DD    CHAR(8)        NULL     DEFAULT NULL     COMMENT '유효시작일자',
    VALID_END_DD     CHAR(8)        NULL     DEFAULT NULL     COMMENT '유효종료일자',
    GRP_TYPE_CD      CHAR(2)        NULL     DEFAULT NULL     COMMENT '코드그룹유형코드',
    MEM_CREAT_OBJ_YN CHAR(1)        NULL     DEFAULT 'N'      COMMENT '메모리생성대상여부 (캐시 적재 대상)',
    PRIMARY KEY (GRP_CD_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='공통코드 그룹';

DROP TABLE IF EXISTS GPCL_CM_CD_VAL;
CREATE TABLE GPCL_CM_CD_VAL (
    GRP_CD_ID        VARCHAR(30)    NOT NULL                  COMMENT '코드그룹ID',
    CD_VAL           VARCHAR(10)    NOT NULL                  COMMENT '코드값',
    UP_CD_VAL        VARCHAR(10)    NULL     DEFAULT NULL     COMMENT '상위코드값 (자기참조, 업무영역→주제영역 계층 필터링)',
    CD_VAL_NM        VARCHAR(100)   NOT NULL                  COMMENT '코드명',
    SORT_ORD         DECIMAL(5,0)   NULL     DEFAULT 0        COMMENT '정렬순서',
    CD_VAL_ENG_NM    VARCHAR(100)   NULL     DEFAULT NULL     COMMENT '코드영문명',
    CD_VAL_DSC       VARCHAR(200)   NULL     DEFAULT NULL     COMMENT '코드설명',
    CD_ADD_INFO_VAL1 VARCHAR(100)   NULL     DEFAULT NULL     COMMENT '코드부가정보문자1',
    CD_ADD_INFO_VAL2 VARCHAR(100)   NULL     DEFAULT NULL     COMMENT '코드부가정보문자2',
    CD_ADD_INFO_VAL3 VARCHAR(100)   NULL     DEFAULT NULL     COMMENT '코드부가정보문자3',
    CD_ADD_INFO_NO1  DECIMAL(15,0)  NULL     DEFAULT NULL     COMMENT '코드부가정보숫자1',
    CD_ADD_INFO_NO2  DECIMAL(15,0)  NULL     DEFAULT NULL     COMMENT '코드부가정보숫자2',
    PRIMARY KEY (GRP_CD_ID, CD_VAL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='공통코드 값';


DROP TABLE IF EXISTS GPCL_MIG_TABLE;
CREATE TABLE GPCL_MIG_TABLE (
    TABLE_SCHEMA     VARCHAR(64)     NOT NULL                   COMMENT '테이블이 속한 스키마(데이터베이스) 이름',
    TABLE_NAME       VARCHAR(64)     NOT NULL                   COMMENT '테이블 이름',
    PROC_ORD         DECIMAL(5,0)    NOT NULL                   COMMENT '처리순서(JOB_ID 내 실행 우선순위, 10단위 권장)',
    TABLE_COMMENT    VARCHAR(2048)   NULL     DEFAULT NULL      COMMENT '테이블 설명 및 코멘트',
    JOB_ID           VARCHAR(30)     NULL     DEFAULT NULL      COMMENT '배치JOB_ID',
    SPLIT_YN         CHAR(1)         NOT NULL DEFAULT 'N'       COMMENT '분할처리여부 (Y/N)',
    MIG_COND         VARCHAR(4000)   NULL     DEFAULT NULL      COMMENT '이관조건 (WHERE절 본문, 공란=전체 추출)',
    MNGR_ID          VARCHAR(20)     NULL     DEFAULT NULL      COMMENT '담당자',
    BIZ_AREA_CD      CHAR(2)         NULL     DEFAULT NULL      COMMENT '업무영역코드',
    SUBJ_AREA_CD     CHAR(2)         NULL     DEFAULT NULL      COMMENT '주제영역코드',
    GRP1_CD          VARCHAR(10)     NULL     DEFAULT NULL      COMMENT '그룹1코드',
    GRP2_CD          VARCHAR(10)     NULL     DEFAULT NULL      COMMENT '그룹2코드',
    GRP3_CD          VARCHAR(10)     NULL     DEFAULT NULL      COMMENT '그룹3코드',
    GRP4_CD          VARCHAR(10)     NULL     DEFAULT NULL      COMMENT '그룹4코드',
    GRP5_CD          VARCHAR(10)     NULL     DEFAULT NULL      COMMENT '그룹5코드',
    REG_ID           VARCHAR(20)     NULL     DEFAULT NULL      COMMENT '등록자',
    REG_DTM          DATETIME        NULL     DEFAULT NULL      COMMENT '등록일시',
    LST_ADJPRN_ID    VARCHAR(20)     NULL     DEFAULT NULL      COMMENT '최종수정자',
    LST_ADJ_DTM      DATETIME        NULL     DEFAULT NULL      COMMENT '최종수정일시',
    PRIMARY KEY (TABLE_SCHEMA, TABLE_NAME, PROC_ORD)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='이관대상 테이블 및 작업단위 관리';

DROP TABLE IF EXISTS GPCL_MIG_VRF_TARGET;
CREATE TABLE GPCL_MIG_VRF_TARGET (
    TABLE_SCHEMA     VARCHAR(64)     NOT NULL                   COMMENT '테이블이 속한 스키마(데이터베이스) 이름',
    TABLE_NAME       VARCHAR(64)     NOT NULL                   COMMENT '테이블 이름',
    COLUMN_NAME      VARCHAR(64)     NOT NULL                   COMMENT '컬럼명',
    ORDINAL_POSITION DECIMAL(3,0)    NOT NULL DEFAULT 0         COMMENT '컬럼순서',
    DATA_TYPE        VARCHAR(64)     NULL     DEFAULT NULL      COMMENT '데이터타입 (정밀도 포함) ,예: varchar(30)',
    SUM_YN           CHAR(1)         NOT NULL DEFAULT 'N'       COMMENT '합계검증여부',
    MIN_YN           CHAR(1)         NOT NULL DEFAULT 'N'       COMMENT '최소검증여부',
    MAX_YN           CHAR(1)         NOT NULL DEFAULT 'N'       COMMENT '최대검증여부',
    AVG_YN           CHAR(1)         NOT NULL DEFAULT 'N'       COMMENT '평균검증여부',
    REG_ID           VARCHAR(20)     NULL     DEFAULT NULL      COMMENT '등록자',
    REG_DTM          DATETIME        NULL     DEFAULT NULL      COMMENT '등록일시',
    LST_ADJPRN_ID    VARCHAR(20)     NULL     DEFAULT NULL      COMMENT '최종수정자',
    LST_ADJ_DTM      DATETIME        NULL     DEFAULT NULL      COMMENT '최종수정일시',
    PRIMARY KEY (TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='이관대상 테이블의 컬럼정보 및 검증할 컬럼 정보';


DROP TABLE IF EXISTS GPCL_MIG_VRF_RESULT;
CREATE TABLE GPCL_MIG_VRF_RESULT (
    EXEC_PHASE       VARCHAR(10)     NOT NULL                   COMMENT '실행회차 (PK1, GPCL_CM_CD_VAL의 CD_VAL과 연계)',
    TABLE_SCHEMA     VARCHAR(64)     NOT NULL                   COMMENT '테이블이 속한 스키마(데이터베이스) 이름',
    TABLE_NAME       VARCHAR(64)     NOT NULL                   COMMENT '테이블 또는 뷰의 이름',
    PROC_ORD         DECIMAL(5,0)    NOT NULL                   COMMENT '처리순서(JOB_ID 내 실행 우선순위, 10단위 권장)',
    COLUMN_NAME      VARCHAR(64)     NOT NULL                   COMMENT '컬럼명',
    UNLOAD_CNT       VARCHAR(20)     NULL     DEFAULT NULL      COMMENT '추출건수',
    LOAD_CNT         VARCHAR(20)     NULL     DEFAULT NULL      COMMENT '적재건수',
    UNLOAD_SUM       VARCHAR(20)     NULL     DEFAULT NULL      COMMENT '추출합계',
    LOAD_SUM         VARCHAR(20)     NULL     DEFAULT NULL      COMMENT '적재합계',
    UNLOAD_MIN       VARCHAR(20)     NULL     DEFAULT NULL      COMMENT '추출최소값',
    LOAD_MIN         VARCHAR(20)     NULL     DEFAULT NULL      COMMENT '적재최소값',
    UNLOAD_MAX       VARCHAR(20)     NULL     DEFAULT NULL      COMMENT '추출최대값',
    LOAD_MAX         VARCHAR(20)     NULL     DEFAULT NULL      COMMENT '적재최대값',
    UNLOAD_AVG       VARCHAR(20)     NULL     DEFAULT NULL      COMMENT '추출평균값',
    LOAD_AVG         VARCHAR(20)     NULL     DEFAULT NULL      COMMENT '적재평균값',
    ACT_CONTN        VARCHAR(500)    NULL     DEFAULT NULL      COMMENT '조치내용/보류사유',
    CONF_ID          VARCHAR(20)     NULL     DEFAULT NULL      COMMENT '확인자',
    CONF_DTM         DATETIME        NULL     DEFAULT NULL      COMMENT '확인일시',
    VRF_STAT_CD      CHAR(2)         NULL     DEFAULT '30'      COMMENT '검증상태코드 (10 정상/20 오류/30 미검증/40 미대상)',
    CONF_STAT_CD     CHAR(2)         NULL     DEFAULT '10'      COMMENT '확인상태코드 (10 대기/20 보류/30 확인완료)',
    RVRF_RSLT_CD     CHAR(2)         NULL     DEFAULT '10'      COMMENT '재검증결과코드 (10 대기/20 진행중/30 완료/40 보류)',
    VRF_ERR_CD       VARCHAR(20)     NULL     DEFAULT NULL      COMMENT '검증오류코드 (VRF-01~05, VRF-99)',
    REG_DTM          DATETIME        NULL     DEFAULT NULL      COMMENT '등록일시',
    LST_ADJ_DTM      DATETIME        NULL     DEFAULT NULL      COMMENT '최종수정일시',
    PRIMARY KEY (EXEC_PHASE, TABLE_SCHEMA, TABLE_NAME, PROC_ORD, COLUMN_NAME)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='추출 및 적재 컬럼 검증용 건수 추출';
