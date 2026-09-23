
public class SybaseIQUnloadWorker {

    private final String strDbUrl;
    private final String strUser;
    private final String strPassword;

    private final String strDataDir;
    private final String strBlobDir;
    private final String strNullToken;
    private final String strDelimiter;

    public SybaseIQUnloadWorker(
            String strDbUrl,
            String strUser,
            String strPassword,
            String strDataDir,
            String strBlobDir,
            String strNullToken,
            String strDelimiter) {

        this.strDbUrl = strDbUrl;
        this.strUser = strUser;
        this.strPassword = strPassword;

        this.strDataDir = strDataDir;
        this.strBlobDir = strBlobDir;

        this.strNullToken = strNullToken;
        this.strDelimiter = strDelimiter;
    }

    public void execute(
            String strTableName,
            String strCondition)
            throws Exception {

        String strSafeTableName =
                SqlUtil.validateIdentifier(
                        strTableName);

        String strSimpleTableName =
                getSimpleTableName(
                        strSafeTableName);

        String strMetaFile =
                this.strDataDir
                + "/"
                + strSimpleTableName
                + "_meta.dat";

        String strBlobPrefix =
                this.strBlobDir
                + "/"
                + strSimpleTableName
                + "_";

        try (
            Connection conn =
                DriverManager.getConnection(
                    this.strDbUrl,
                    this.strUser,
                    this.strPassword);

            Statement stmt =
                conn.createStatement()
        ) {

            conn.setAutoCommit(true);

            configureExtract(
                    stmt,
                    strMetaFile);

            String strSql =
                    buildExtractSql(
                            strSafeTableName,
                            strBlobPrefix,
                            strCondition);

            System.out.println(
                    "[UNLOAD][START] "
                    + strSafeTableName);

            System.out.println(
                    "[UNLOAD][META FILE] "
                    + strMetaFile);

            /*
             * 결과는 JDBC ResultSet으로 가져오지 않는다.
             *
             * TEMP_EXTRACT_NAME1이 설정되어 있으므로
             * Sybase IQ 16.0 Server가 SELECT 결과를
             * Shared Volume의 Meta 파일로 직접 기록한다.
             *
             * 동시에 BFILE()은 BLOB 데이터를
             * 서버 파일시스템에 직접 생성한다.
             */
            stmt.execute(strSql);

            System.out.println("[UNLOAD][SUCCESS] " + strSafeTableName);

            clearExtract(stmt);

        } catch (Exception ex) {
System.err.println("[UNLOAD][ERROR] "+ strSafeTableName + " : "+ ex.getMessage());
            throw ex;
        }
    }

    private void configureExtract(Statement stmt, String strMetaFile) throws Exception {

        String strSafeMetaFile = SqlUtil.escapeLiteral(strMetaFile);

        //Meta 파일은 ASCII Delimited 파일이다.
        stmt.execute("SET TEMPORARY OPTION TEMP_EXTRACT_NAME1 = '" + strSafeMetaFile + "'");
        stmt.execute("SET TEMPORARY OPTION TEMP_EXTRACT_BINARY = 'OFF'");

        // 중요: NULL을 empty로 변환하지 않는다.
        // 다만 이번 SELECT에서는 nullable 컬럼을 CASE로 직접 {NULL} 토큰으로 변환하므로 이 설정은 방어적인 의미도 있다.
        stmt.execute("SET TEMPORARY OPTION TEMP_EXTRACT_NULL_AS_EMPTY = 'OFF'");
        stmt.execute("SET TEMPORARY OPTION TEMP_EXTRACT_NULL_AS_ZERO = 'OFF'");
        stmt.execute("SET TEMPORARY OPTION TEMP_EXTRACT_COLUMN_DELIMITER = '"
            + SqlUtil.escapeLiteral(this.strDelimiter) + "'");

        // UNIX에서는 기본 Row Delimiter도 newline이지만 명시적으로 지정한다.
        stmt.execute("SET TEMPORARY OPTION TEMP_EXTRACT_ROW_DELIMITER = '\\n'");

        // 값에 delimiter가 존재할 가능성을 고려.
        stmt.execute("SET TEMPORARY OPTION TEMP_EXTRACT_QUOTES = 'ON'");
    }

    private String buildExtractSql( String strTableName, String strBlobPrefix, String strCondition) {

        String strSafeBlobPrefix = SqlUtil.escapeLiteral(strBlobPrefix);
        String strSafeNullToken = SqlUtil.escapeLiteral(this.strNullToken);

        StringBuilder strSql = new StringBuilder();

        strSql.append("SELECT ");

        /*
         * ---------------------------------------------------------
         * col1_id
         * NOT NULL
         * ---------------------------------------------------------
         */
        strSql.append("col1_id, ");

        /*
         * ---------------------------------------------------------
         * col2_name VARCHAR(5) NULL
         *
         * NULL  -> {NULL}
         * ''    -> ''
         * DATA  -> DATA
         *
         * empty string과 NULL을 명확히 분리한다.
         * ---------------------------------------------------------
         */
        strSql.append(
            "CASE "
            + "WHEN col2_name IS NULL "
            + "THEN '"
            + strSafeNullToken
            + "' "
            + "ELSE col2_name "
            + "END, "
        );

        /*
         * ---------------------------------------------------------
         * col3_file BLOB NOT NULL
         *
         * BFILE 성공 -> 파일 경로
         * BFILE 실패 -> {BFILE_ERROR}
         * ---------------------------------------------------------
         */

        appendBlobColumn(
                strSql,
                "col3_file",
                strSafeBlobPrefix,
                false);

        strSql.append(", ");

        /*
         * ---------------------------------------------------------
         * col4_file BLOB NULL
         * ---------------------------------------------------------
         */

        appendBlobColumn(
                strSql,
                "col4_file",
                strSafeBlobPrefix,
                true);

        strSql.append(", ");

        /*
         * ---------------------------------------------------------
         * col5_file BLOB NULL
         * ---------------------------------------------------------
         */

        appendBlobColumn(
                strSql,
                "col5_file",
                strSafeBlobPrefix,
                true);

        strSql.append(" FROM ");
        strSql.append(strTableName);

        if (strCondition != null
                && !strCondition.trim().isEmpty()) {

            strSql.append(" WHERE ");
            strSql.append(strCondition);
        }

        return strSql.toString();
    }

    private void appendBlobColumn(
            StringBuilder strSql,
            String strColumnName,
            String strBlobPrefix,
            boolean boolNullable) {

        String strNullToken =
                SqlUtil.escapeLiteral(
                        this.strNullToken);

        String strBlobFileExpression =
                "'"
                + strBlobPrefix
                + "' || col1_id || '_"
                + strColumnName
                + ".bin'";

        if (boolNullable) {

            strSql.append(
                "CASE "
            );

            /*
             * NULL BLOB
             *
             * BFILE() 자체도 NULL을 반환하고
             * 파일을 생성하지 않지만,
             * Meta 파일에도 명시적인 NULL Token을 기록한다.
             */
            strSql.append(
                "WHEN "
                + strColumnName
                + " IS NULL "
                + "THEN '"
                + strNullToken
                + "' "
            );

            /*
             * Non-NULL
             */
            strSql.append(
                "WHEN BFILE("
                + strBlobFileExpression
                + ", "
                + strColumnName
                + ") = 1 "
                + "THEN "
                + strBlobFileExpression
                + " "
            );

            /*
             * File Open/Write 실패
             */
            strSql.append(
                "ELSE '{BFILE_ERROR}' "
            );

            strSql.append("END");

        } else {

            /*
             * NOT NULL BLOB
             */
            strSql.append(
                "CASE "
                + "WHEN BFILE("
                + strBlobFileExpression
                + ", "
                + strColumnName
                + ") = 1 "
                + "THEN "
                + strBlobFileExpression
                + " "
                + "ELSE '{BFILE_ERROR}' "
                + "END"
            );
        }
    }

    private void clearExtract(
            Statement stmt) {

        try {

            stmt.execute( "SET TEMPORARY OPTION TEMP_EXTRACT_NAME1 = ''");
            stmt.execute( "SET TEMPORARY OPTION TEMP_EXTRACT_BINARY = 'OFF'");

        } catch (Exception ex) {

            System.err.println("[UNLOAD][RESET WARNING] " + ex.getMessage());
        }
    }

    private String getSimpleTableName( String strTableName) {

        int intPos = strTableName.lastIndexOf('.');

        if (intPos >= 0) {
            return strTableName.substring(intPos + 1);
        }

        return strTableName;
    }
}