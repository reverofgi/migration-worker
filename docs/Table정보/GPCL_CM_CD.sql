CREATE TABLE `GPCL_CM_CD` (
  `GRP_CD_ID` varchar(40) NOT NULL COMMENT '그룹코드ID',
  `GRP_NM` varchar(80) DEFAULT NULL COMMENT '그룹명',
  `GRP_CD_DSC` varchar(1000) DEFAULT NULL COMMENT '그룹코드설명',
  `GRP_TYPE_CD` varchar(1) NOT NULL COMMENT '그룹유형코드',
  `MEM_CREAT_OBJ_YN` varchar(1) NOT NULL COMMENT '메모리생성대상여부',
  `VALID_STRT_DD` varchar(8) DEFAULT NULL COMMENT '유효개시일자',
  `VALID_END_DD` varchar(8) DEFAULT NULL COMMENT '유효종료일자',
  `REG_DDTM` varchar(14) DEFAULT NULL COMMENT '등록일시',
  `REG_ID` varchar(20) DEFAULT NULL COMMENT '등록자ID',
  `LST_ADJ_DDTM` varchar(14) DEFAULT NULL COMMENT '최종수정일시',
  `LST_ADJPRN_ID` varchar(20) DEFAULT NULL COMMENT '최종수정자ID',
  PRIMARY KEY (`GRP_CD_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='공통코드';

INSERT INTO `GPCL_CM_CD` VALUES ('MIG_CONN_INFO','이행 DB 접속정보','이행 원천/타겟 DB 접속정보. CD_VAL={프로파일}_{연결구분}, UP_CD_VAL=프로파일. ENG_NM=접속계정, DSC=드라이버클래스, ADD1=비밀번호(ENC), ADD2=스키마OWNER, ADD3=JDBC URL. 비밀번호를 담으므로 메모리생성 제외(MEM_CREAT_OBJ_YN=N).','S','N','20260921','99991231','20260921162007','SYSTEM','20260921164736','SYSTEM');
INSERT INTO `GPCL_CM_CD` VALUES ('MIG_ENV_PROP_ALL','이행 배치 환경설정(공통)','이행 배치 환경설정 key-value. 전 프로파일 공통값. CD_VAL=app.migration. 을 뗀 키, UP_CD_VAL=프로파일, ADD1=전체 프로퍼티 키(주입에 사용), ADD3=값, USE_YN=N 이면 주입 제외(yml 유지). ENC 암호문이 들어올 수 있어 메모리생성 제외(MEM_CREAT_OBJ_YN=N).','S','N','20260921','99991231','20260921162417','SYSTEM','20260921164525','SYSTEM');
INSERT INTO `GPCL_CM_CD` VALUES ('MIG_ENV_PROP_LOCAL','이행 배치 환경설정(local)','이행 배치 환경설정 key-value. local 프로파일. CD_VAL=app.migration. 을 뗀 키, UP_CD_VAL=프로파일, ADD1=전체 프로퍼티 키(주입에 사용), ADD3=값, USE_YN=N 이면 주입 제외(yml 유지). ENC 암호문이 들어올 수 있어 메모리생성 제외(MEM_CREAT_OBJ_YN=N).','S','N','20260921','99991231','20260921162417','SYSTEM','20260921164525','SYSTEM');
INSERT INTO `GPCL_CM_CD` VALUES ('MIG_ENV_PROP_PROD','이행 배치 환경설정(prod)','이행 배치 환경설정 key-value. prod 프로파일. CD_VAL=app.migration. 을 뗀 키, UP_CD_VAL=프로파일, ADD1=전체 프로퍼티 키(주입에 사용), ADD3=값, USE_YN=N 이면 주입 제외(yml 유지). ENC 암호문이 들어올 수 있어 메모리생성 제외(MEM_CREAT_OBJ_YN=N).','S','N','20260921','99991231','20260921180837','SYSTEM','20260921182030','SYSTEM');
INSERT INTO `GPCL_CM_CD` VALUES ('MIG_EXE_ORD_CD','실행차수코드','KB MIG 실행차수코드','B','Y','20260908','99991231','20260908100504','260160','20260908100504','260160');
INSERT INTO `GPCL_CM_CD` VALUES ('MIG_TARGET_HIER_TEST','이관 대상 분류 테스트','업무영역-주제영역-그룹1~5 테스트 데이터','B','Y','20000101','99991231','20260826182640','260159','20260826182640','260159');
