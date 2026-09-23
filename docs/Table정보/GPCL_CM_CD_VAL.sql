CREATE TABLE `GPCL_CM_CD_VAL` (
  `GRP_CD_ID` varchar(40) NOT NULL COMMENT '그룹코드ID',
  `CD_VAL` varchar(40) NOT NULL COMMENT '코드값',
  `CD_VAL_NM` varchar(80) DEFAULT NULL COMMENT '코드값명',
  `CD_VAL_ENG_NM` varchar(80) DEFAULT NULL COMMENT '코드값영문명',
  `CD_VAL_DSC` varchar(2000) DEFAULT NULL COMMENT '코드값설명',
  `UP_CD_VAL` varchar(40) DEFAULT NULL COMMENT '상위코드값',
  `SORT_ORD` int(11) DEFAULT NULL COMMENT '정렬순서',
  `CD_ADD_INFO_VAL1` varchar(100) DEFAULT NULL COMMENT '코드추가정보값1',
  `CD_ADD_INFO_VAL2` varchar(100) DEFAULT NULL COMMENT '코드추가정보값2',
  `CD_ADD_INFO_VAL3` varchar(300) DEFAULT NULL COMMENT '코드추가정보값3',
  `CD_ADD_INFO_NO1` decimal(18,5) DEFAULT NULL COMMENT '코드추가정보번호1',
  `CD_ADD_INFO_NO2` decimal(18,5) DEFAULT NULL COMMENT '코드추가정보번호2',
  `CD_ADD_INFO_NO3` decimal(18,5) DEFAULT NULL COMMENT '코드추가정보번호3',
  `USE_YN` varchar(1) NOT NULL COMMENT '사용여부',
  `REG_DDTM` varchar(14) DEFAULT NULL COMMENT '등록일시',
  `REG_ID` varchar(20) DEFAULT NULL COMMENT '등록자ID',
  `LST_ADJ_DDTM` varchar(14) DEFAULT NULL COMMENT '최종수정일시',
  `LST_ADJPRN_ID` varchar(20) DEFAULT NULL COMMENT '최종수정자ID',
  PRIMARY KEY (`GRP_CD_ID`,`CD_VAL`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='공통코드값';

INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_CONN_INFO','LOCAL_SRC','로컬 테스트 원천 IQ','DBA','com.sybase.jdbc4.jdbc.SybDriver','LOCAL',1,'ENC(v1:W6p8apIfXEn+W09f:A8mKEW79RqFJ9AREJrhad2/R/xjxN5OwnL2x)','DWDB','jdbc:sybase:Tds:192.168.0.122:2640/iqutf8?ServiceName=iqutf8&charset=utf8',NULL,NULL,NULL,'Y','20260921162016','SYSTEM','20260921164751','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_CONN_INFO','LOCAL_TGT','로컬 테스트 타겟 IQ','DBA','com.sybase.jdbc4.jdbc.SybDriver','LOCAL',2,'ENC(v1:W6p8apIfXEn+W09f:A8mKEW79RqFJ9AREJrhad2/R/xjxN5OwnL2x)','MIGTGT','jdbc:sybase:Tds:192.168.0.122:2640/iqutf8?ServiceName=iqutf8&charset=utf8',NULL,NULL,NULL,'Y','20260921162016','SYSTEM','20260921164751','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_LOCAL','native-load.pull.allowed-remote-ip','[환경B] 허용 원격 IP',NULL,'[환경B] 허용 원격 IP','LOCAL',1,'app.migration.native-load.pull.allowed-remote-ip',NULL,'192.168.0.122',NULL,NULL,NULL,'Y','20260921162800','SYSTEM','20260921164538','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_LOCAL','native-load.pull.enabled','[환경B] pull 사용',NULL,'[환경B] pull 사용','LOCAL',2,'app.migration.native-load.pull.enabled',NULL,'true',NULL,NULL,NULL,'Y','20260921162800','SYSTEM','20260921164538','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_LOCAL','native-load.pull.http-port','[환경B] 임시 파일서버 포트',NULL,'[환경B] 임시 파일서버 포트','LOCAL',3,'app.migration.native-load.pull.http-port',NULL,'18080',NULL,NULL,NULL,'Y','20260921162800','SYSTEM','20260921164538','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_LOCAL','native-load.pull.http-port-range','[환경B] 임시 파일서버 포트 범위(K-33)',NULL,'[환경B] 임시 파일서버 포트 범위(K-33)','LOCAL',4,'app.migration.native-load.pull.http-port-range',NULL,'18080-18089',NULL,NULL,NULL,'Y','20260921162800','SYSTEM','20260921164538','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_LOCAL','native-load.pull.local-ip','[환경B] 배치서버 IP',NULL,'[환경B] 배치서버 IP','LOCAL',5,'app.migration.native-load.pull.local-ip',NULL,'192.168.0.80',NULL,NULL,NULL,'Y','20260921162800','SYSTEM','20260921164538','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_LOCAL','native-load.pull.remote-dir','[환경B] 타겟서버 임시경로',NULL,'[환경B] 타겟서버 임시경로','LOCAL',6,'app.migration.native-load.pull.remote-dir',NULL,'/tmp/kbadw_migration',NULL,NULL,NULL,'Y','20260921162800','SYSTEM','20260921164538','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_LOCAL','native-load.pull.retry','[환경B] 크기대사 재시도',NULL,'[환경B] 크기대사 재시도','LOCAL',7,'app.migration.native-load.pull.retry',NULL,'1',NULL,NULL,NULL,'Y','20260921162800','SYSTEM','20260921164538','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_LOCAL','server-extract.ctl-sha256','.ctl 에 SHA256 기록',NULL,'.ctl 에 SHA256 기록','LOCAL',8,'app.migration.server-extract.ctl-sha256',NULL,'true',NULL,NULL,NULL,'Y','20260921162800','SYSTEM','20260921164538','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_LOCAL','server-extract.enabled','서버사이드 추출 사용',NULL,'서버사이드 추출 사용','LOCAL',9,'app.migration.server-extract.enabled',NULL,'true',NULL,NULL,NULL,'Y','20260921162800','SYSTEM','20260921164538','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_LOCAL','server-extract.file-size-kb','추출 파일 분할 크기(KB)',NULL,NULL,'LOCAL',10,'app.migration.server-extract.file-size-kb',NULL,'1000000',NULL,NULL,NULL,'Y','20260921162800','SYSTEM','20260921164538','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_LOCAL','server-extract.max-parallel-degree','빈 값 = 옵션 미적용',NULL,'빈 값 = 옵션 미적용','LOCAL',11,'app.migration.server-extract.max-parallel-degree',NULL,'',NULL,NULL,NULL,'Y','20260921162800','SYSTEM','20260921164538','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_LOCAL','server-extract.server-dir','IQ 가 쓸 추출 디렉토리',NULL,'IQ 가 쓸 추출 디렉토리','LOCAL',12,'app.migration.server-extract.server-dir',NULL,'/tmp/kbadw_extract',NULL,NULL,NULL,'Y','20260921162800','SYSTEM','20260921164538','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_LOCAL','server-extract.split-mode','추출 파일 분할 방식',NULL,NULL,'LOCAL',13,'app.migration.server-extract.split-mode',NULL,'NAME',NULL,NULL,NULL,'Y','20260921162800','SYSTEM','20260921164538','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_LOCAL','server-extract.verify-record-count','추출 건수 대사',NULL,'추출 건수 대사','LOCAL',14,'app.migration.server-extract.verify-record-count',NULL,'true',NULL,NULL,NULL,'Y','20260921162800','SYSTEM','20260921164538','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_LOCAL','stale-running-minutes','RUNNING 방치 판정 분',NULL,NULL,'LOCAL',15,'app.migration.stale-running-minutes',NULL,'120',NULL,NULL,NULL,'Y','20260921162800','SYSTEM','20260921164538','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_LOCAL','work-dir','추출 파일 임시 저장 경로',NULL,'추출 파일 임시 저장 경로','LOCAL',16,'app.migration.work-dir',NULL,'D:/workspace/iteyes_dev/kb-adw-data-migration/work',NULL,NULL,NULL,'Y','20260921162800','SYSTEM','20260921164538','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_PROD','disk-min-free-bytes','최소 여유 디스크(byte)',NULL,'기본값 빈 값. 운영 work 볼륨 용량에 맞춰 정할 것.','PROD',42,'app.migration.disk-min-free-bytes',NULL,'<최소 여유 바이트>',NULL,NULL,NULL,'N','20260921180837','SYSTEM','20260921182030','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_PROD','load.bad-limit','적재 허용 불량 건수',NULL,'기본값 빈 값. 운영은 0 권장(불량 1건이라도 멈춤).','PROD',41,'app.migration.load.bad-limit',NULL,'<허용 불량 건수>',NULL,NULL,NULL,'N','20260921180837','SYSTEM','20260921182030','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_PROD','max-parallel','동시 실행 테이블 수',NULL,'기본값 빈 값. GODIS 배치그룹 동시실행과 함께 볼 것.','PROD',40,'app.migration.max-parallel',NULL,'<운영 동시 실행 수>',NULL,NULL,NULL,'N','20260921180837','SYSTEM','20260921182030','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_PROD','native-load.lob.roll-bytes','[LOB] 적재 롤링 크기(byte)',NULL,'비우면 native-load.roll-bytes 를 따른다.','PROD',32,'app.migration.native-load.lob.roll-bytes',NULL,'<LOB 롤링 바이트>',NULL,NULL,NULL,'N','20260921180837','SYSTEM','20260921182030','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_PROD','native-load.log-dir','native load 로그 디렉토리',NULL,'기본값 빈 값. 비우면 로그 파일을 남기지 않는다.','PROD',13,'app.migration.native-load.log-dir',NULL,'<운영 load 로그 경로>',NULL,NULL,NULL,'N','20260921180837','SYSTEM','20260921182030','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_PROD','native-load.pull.enabled','[환경A] pull 미사용(공유볼륨)',NULL,'운영은 공유볼륨(환경A) 전제. pull 전송은 개발·테스트 전용이라 false 로 고정한다. LOAD 로그의 경고 참고.','PROD',1,'app.migration.native-load.pull.enabled',NULL,'false',NULL,NULL,NULL,'Y','20260921180837','SYSTEM','20260921182030','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_PROD','native-load.roll-bytes','적재 롤링 크기(byte)',NULL,'기본값 빈 값.','PROD',33,'app.migration.native-load.roll-bytes',NULL,'<롤링 바이트>',NULL,NULL,NULL,'N','20260921180837','SYSTEM','20260921182030','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_PROD','native-load.server-dir','타겟 IQ 가 읽을 적재 디렉토리',NULL,'기본값 빈 값. 환경A 공유볼륨 경로.','PROD',12,'app.migration.native-load.server-dir',NULL,'<공유볼륨 적재 경로>',NULL,NULL,NULL,'N','20260921180837','SYSTEM','20260921182030','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_PROD','purge-after','추출 파일 정리 시점',NULL,'기본값 VERIFY. 검증까지 끝나면 추출 파일을 지운다.','PROD',44,'app.migration.purge-after',NULL,'VERIFY',NULL,NULL,NULL,'N','20260921180837','SYSTEM','20260921182030','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_PROD','server-extract.ctl-sha256','.ctl 에 SHA256 기록',NULL,'기본값 true.','PROD',26,'app.migration.server-extract.ctl-sha256',NULL,'true',NULL,NULL,NULL,'N','20260921180837','SYSTEM','20260921182030','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_PROD','server-extract.enabled','서버사이드 추출 사용',NULL,'기본값 false. 운영에서 서버사이드 추출을 쓸지 정할 것.','PROD',20,'app.migration.server-extract.enabled',NULL,'true',NULL,NULL,NULL,'N','20260921180837','SYSTEM','20260921182030','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_PROD','server-extract.fetch-enabled','추출파일 xp_read_file 회수',NULL,'기본값 true. ★헤더 [검토 필요] 참고. 환경A 면 false 가 안전판이 된다.','PROD',21,'app.migration.server-extract.fetch-enabled',NULL,'false',NULL,NULL,NULL,'N','20260921180837','SYSTEM','20260921182030','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_PROD','server-extract.file-size-kb','추출 파일 분할 크기(KB)',NULL,'기본값 500000. 비우면 기본값.','PROD',23,'app.migration.server-extract.file-size-kb',NULL,'<운영 분할 크기 KB>',NULL,NULL,NULL,'N','20260921180837','SYSTEM','20260921182030','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_PROD','server-extract.lob.file-size-kb','[LOB] 추출 파일 분할 크기(KB)',NULL,'비우면 server-extract.file-size-kb 를 따른다.','PROD',30,'app.migration.server-extract.lob.file-size-kb',NULL,'<LOB 분할 크기 KB>',NULL,NULL,NULL,'N','20260921180837','SYSTEM','20260921182030','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_PROD','server-extract.lob.max-parallel-degree','[LOB] 추출 병렬도',NULL,'비우면 server-extract.max-parallel-degree 를 따른다. CD_VAL 38자로 여유 2자.','PROD',31,'app.migration.server-extract.lob.max-parallel-degree',NULL,'',NULL,NULL,NULL,'N','20260921180837','SYSTEM','20260921182030','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_PROD','server-extract.max-parallel-degree','추출 병렬도',NULL,'기본값 빈 값(옵션 미적용). IQ 부하와 함께 정할 것.','PROD',24,'app.migration.server-extract.max-parallel-degree',NULL,'',NULL,NULL,NULL,'N','20260921180837','SYSTEM','20260921182030','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_PROD','server-extract.server-dir','IQ 가 쓸 추출 디렉토리',NULL,'기본값 빈 값. 환경A 는 IQ 와 배치서버가 함께 보는 공유볼륨 경로.','PROD',11,'app.migration.server-extract.server-dir',NULL,'<공유볼륨 추출 경로>',NULL,NULL,NULL,'N','20260921180837','SYSTEM','20260921182030','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_PROD','server-extract.split-mode','추출 파일 분할 방식',NULL,'기본값 DIRECTORY, local 은 NAME. ★헤더 [검토 필요] 참고.','PROD',22,'app.migration.server-extract.split-mode',NULL,'DIRECTORY',NULL,NULL,NULL,'N','20260921180837','SYSTEM','20260921182030','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_PROD','server-extract.verify-record-count','추출 건수 대사',NULL,'기본값 true. 끄면 추출 건수 검증을 건너뛴다. 운영은 true 권장.','PROD',25,'app.migration.server-extract.verify-record-count',NULL,'true',NULL,NULL,NULL,'N','20260921180837','SYSTEM','20260921182030','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_PROD','stale-running-minutes','RUNNING 방치 판정 분',NULL,'기본값 빈 값. local 은 120. 운영 최장 수행시간보다 길게 잡을 것.','PROD',43,'app.migration.stale-running-minutes',NULL,'<방치 판정 분>',NULL,NULL,NULL,'N','20260921180837','SYSTEM','20260921182030','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_PROD','verify.max-target-columns','검증 대상 최대 컬럼 수',NULL,'기본값 20.','PROD',45,'app.migration.verify.max-target-columns',NULL,'20',NULL,NULL,NULL,'N','20260921180837','SYSTEM','20260921182030','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_ENV_PROP_PROD','work-dir','추출 파일 임시 저장 경로',NULL,'기본값 /data/migration/work. 배치서버 로컬 경로.','PROD',10,'app.migration.work-dir',NULL,'<운영 work 경로>',NULL,NULL,NULL,'N','20260921180837','SYSTEM','20260921182030','SYSTEM');
INSERT INTO `GPCL_CM_CD_VAL` VALUES ('MIG_EXE_ORD_CD','1','테스트실행',NULL,'착수 전 테스트',NULL,1,'차수',NULL,NULL,NULL,NULL,NULL,'Y','20260908201149','260160','20260908201149','260160');
