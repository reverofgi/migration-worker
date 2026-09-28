CREATE TABLE `MIG_EXEC_DTL` (
  `EXE_ORD` decimal(3,0) NOT NULL COMMENT '실행차수(PK1)',
  `TABLE_OWNER` varchar(30) NOT NULL COMMENT '테이블OWNER(PK2)',
  `TABLE_ID` varchar(30) NOT NULL COMMENT '테이블ID(PK3)',
  `PROC_ORD` decimal(5,0) NOT NULL COMMENT '처리순서(PK4)',  
  `JOB_TP_CD` varchar(10) NOT NULL COMMENT '작업구분코드 (PK5. UNLOAD/UNLOAD_VRF/LOAD/LOAD_VRF'),
  `STRT_DTM` datetime DEFAULT NULL COMMENT '시작일시',
  `END_DTM` datetime DEFAULT NULL COMMENT '종료일시',
  `TOTAL_CNT` decimal(15,0) DEFAULT NULL COMMENT '전체건수 (VERIFY 행은 판정한 검증 대상 컬럼 수)',
  `EXEC_STAT_CD` char(2) DEFAULT '10' COMMENT '작업상태코드 (10 실행중 / 20 완료 / 30 오류)',
  `ERR_CD` varchar(20) DEFAULT NULL COMMENT '오류코드 (공통코드에서 관리)',
  `REG_ID` varchar(20) DEFAULT NULL COMMENT '등록자',
  `REG_DTM` datetime DEFAULT NULL COMMENT '등록일시',
  `LST_ADJPRN_ID` varchar(20) DEFAULT NULL COMMENT '최종수정자',
  `LST_ADJ_DTM` datetime DEFAULT NULL COMMENT '최종수정일시',
  PRIMARY KEY (`EXE_ORD`,`TABLE_OWNER`,`TABLE_ID`,`PROC_ORD`,`JOB_TP_CD`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='실행 이력 상세'
;