package egovframework.haengjeongnuli.finance.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import egovframework.haengjeongnuli.finance.service.AssetVO;
import egovframework.haengjeongnuli.finance.service.InspectionVO;
import egovframework.haengjeongnuli.finance.service.PurchaseVO;
import egovframework.haengjeongnuli.finance.service.QuoteVO;

@Mapper
public interface PurchaseMapper {

    String P_COLS = "P.PURCHASE_ID, P.PUR_NO, P.PUR_YEAR, P.TITLE, P.PUR_TYPE, P.DEPT_NAME, P.BUDGET_ID, P.ESTIMATED_AMOUNT, P.STATUS, "
            + "P.SELECTED_QUOTE_ID, P.SELECT_REASON, P.NOTE, P.CREATED_BY, P.CREATED_NAME, P.CREATED_AT, P.CANCELED_AT, P.CANCEL_REASON, "
            + "Q.VENDOR, Q.AMOUNT AS SELECTED_AMOUNT, "
            + "(SELECT COUNT(*) FROM PUR_QUOTE C WHERE C.PURCHASE_ID = P.PURCHASE_ID) AS QUOTE_COUNT ";
    String P_FROM = "FROM PURCHASE P LEFT JOIN PUR_QUOTE Q ON Q.QUOTE_ID = P.SELECTED_QUOTE_ID ";

    // ===== 구매 건 =====

    @Select("SELECT SEQ_PURCHASE.NEXTVAL FROM DUAL")
    long nextId();

    @Insert("INSERT INTO PURCHASE (PURCHASE_ID, PUR_NO, PUR_YEAR, TITLE, PUR_TYPE, DEPT_NAME, BUDGET_ID, ESTIMATED_AMOUNT, STATUS, NOTE, "
            + "CREATED_BY, CREATED_NAME, CREATED_AT) VALUES (#{purchaseId}, #{purNo}, #{purYear}, #{title}, #{purType}, #{deptName}, "
            + "#{budgetId, jdbcType=NUMERIC}, #{estimatedAmount}, 'QUOTING', #{note}, #{createdBy}, #{createdName}, #{createdAt})")
    void insert(PurchaseVO p);

    @Select("<script>SELECT " + P_COLS + P_FROM + "WHERE P.PUR_YEAR = #{year} <if test='dept != null'>AND P.DEPT_NAME = #{dept}</if> "
            + "ORDER BY P.PURCHASE_ID DESC</script>")
    List<PurchaseVO> selectList(@Param("year") int year, @Param("dept") String dept);

    String P_WHERE = "WHERE P.PUR_YEAR = #{year} <if test='dept != null'>AND P.DEPT_NAME = #{dept}</if> ";

    @Select("<script>SELECT COUNT(*) FROM PURCHASE P " + P_WHERE + "</script>")
    int countList(@Param("year") int year, @Param("dept") String dept);

    /** 한 쪽 (ROWNUM 으로 자른다: Oracle·Tibero·H2 공통) */
    @Select("<script>SELECT * FROM (SELECT X.*, ROWNUM AS RN FROM (SELECT " + P_COLS + P_FROM + P_WHERE
            + "ORDER BY P.PURCHASE_ID DESC) X WHERE ROWNUM &lt;= #{endRow}) WHERE RN &gt; #{startRow}</script>")
    List<PurchaseVO> selectPage(@Param("year") int year, @Param("dept") String dept, @Param("startRow") int startRow,
                                @Param("endRow") int endRow);

    @Select("SELECT " + P_COLS + P_FROM + "WHERE P.PURCHASE_ID = #{id}")
    PurchaseVO selectById(@Param("id") long id);

    /** 상태를 바꾸기 전에 잠근다 (같은 건을 동시에 고치지 않게) */
    @Select("SELECT PURCHASE_ID FROM PURCHASE WHERE PURCHASE_ID = #{id} FOR UPDATE")
    Long lock(@Param("id") long id);

    /** 결재·계약에 고를 수 있는 우리 부서의 진행 중인 구매 건 */
    @Select("SELECT " + P_COLS + P_FROM + "WHERE P.DEPT_NAME = #{dept} AND P.STATUS IN ('QUOTING', 'SELECTED', 'INSPECTED') "
            + "ORDER BY P.PURCHASE_ID DESC")
    List<PurchaseVO> selectOpenByDept(@Param("dept") String dept);

    @Update("UPDATE PURCHASE SET STATUS = 'SELECTED', SELECTED_QUOTE_ID = #{quoteId}, SELECT_REASON = #{reason} "
            + "WHERE PURCHASE_ID = #{id} AND STATUS = 'QUOTING'")
    int select(@Param("id") long id, @Param("quoteId") long quoteId, @Param("reason") String reason);

    /** 업체 선정을 되돌린다 (검수 전, 결재·계약에 아직 쓰지 않았을 때) */
    @Update("UPDATE PURCHASE SET STATUS = 'QUOTING', SELECTED_QUOTE_ID = NULL, SELECT_REASON = NULL "
            + "WHERE PURCHASE_ID = #{id} AND STATUS = 'SELECTED'")
    int unselect(@Param("id") long id);

    @Update("UPDATE PURCHASE SET STATUS = 'INSPECTED' WHERE PURCHASE_ID = #{id} AND STATUS = 'SELECTED'")
    int markInspected(@Param("id") long id);

    @Update("UPDATE PURCHASE SET STATUS = 'CANCELED', CANCELED_AT = #{at}, CANCEL_REASON = #{reason} "
            + "WHERE PURCHASE_ID = #{id} AND STATUS IN ('QUOTING', 'SELECTED')")
    int cancel(@Param("id") long id, @Param("reason") String reason, @Param("at") LocalDateTime at);

    // ===== 견적 =====

    @Select("SELECT SEQ_PUR_QUOTE.NEXTVAL FROM DUAL")
    long nextQuoteId();

    @Insert("INSERT INTO PUR_QUOTE (QUOTE_ID, PURCHASE_ID, VENDOR, BIZ_NO, AMOUNT, QUOTE_DATE, NOTE, ORIGINAL_NAME, STORED_NAME, FILE_SIZE, "
            + "CREATED_BY, CREATED_AT) VALUES (#{quoteId}, #{purchaseId}, #{vendor}, #{bizNo}, #{amount}, #{quoteDate}, #{note}, "
            + "#{originalName}, #{storedName}, #{fileSize, jdbcType=NUMERIC}, #{createdBy}, #{createdAt})")
    void insertQuote(QuoteVO q);

    @Select("SELECT QUOTE_ID, PURCHASE_ID, VENDOR, BIZ_NO, AMOUNT, QUOTE_DATE, NOTE, ORIGINAL_NAME, STORED_NAME, FILE_SIZE, CREATED_BY, "
            + "CREATED_AT FROM PUR_QUOTE WHERE PURCHASE_ID = #{id} ORDER BY AMOUNT, QUOTE_ID")
    List<QuoteVO> selectQuotes(@Param("id") long purchaseId);

    @Select("SELECT QUOTE_ID, PURCHASE_ID, VENDOR, BIZ_NO, AMOUNT, QUOTE_DATE, NOTE, ORIGINAL_NAME, STORED_NAME, FILE_SIZE, CREATED_BY, "
            + "CREATED_AT FROM PUR_QUOTE WHERE QUOTE_ID = #{id}")
    QuoteVO selectQuote(@Param("id") long quoteId);

    @Delete("DELETE FROM PUR_QUOTE WHERE QUOTE_ID = #{id} AND PURCHASE_ID IN (SELECT PURCHASE_ID FROM PURCHASE WHERE STATUS = 'QUOTING')")
    int deleteQuote(@Param("id") long quoteId);

    // ===== 검수 =====

    @Select("SELECT SEQ_PUR_INSPECTION.NEXTVAL FROM DUAL")
    long nextInspectionId();

    @Insert("INSERT INTO PUR_INSPECTION (INSP_ID, PURCHASE_ID, NOTIFIED_DATE, INSPECTED_DATE, RESULT, NOTE, INSPECTOR_ID, INSPECTOR_NAME, "
            + "CREATED_AT) VALUES (#{inspId}, #{purchaseId}, #{notifiedDate}, #{inspectedDate}, #{result}, #{note}, #{inspectorId}, "
            + "#{inspectorName}, #{createdAt})")
    void insertInspection(InspectionVO i);

    @Select("SELECT INSP_ID, PURCHASE_ID, NOTIFIED_DATE, INSPECTED_DATE, RESULT, NOTE, INSPECTOR_ID, INSPECTOR_NAME, CREATED_AT "
            + "FROM PUR_INSPECTION WHERE PURCHASE_ID = #{id} ORDER BY INSP_ID")
    List<InspectionVO> selectInspections(@Param("id") long purchaseId);

    // ===== 흐름 (결재·계약) =====

    @Select("SELECT DOC_ID AS \"docId\", KIND AS \"kind\", TITLE AS \"title\", STATUS AS \"status\", PAY_AMOUNT AS \"payAmount\", "
            + "PAYEE AS \"payee\", DRAFTER_NAME AS \"drafterName\" FROM APPROVAL_DOC "
            + "WHERE PURCHASE_ID = #{id} AND DELETED_YN = 'N' ORDER BY DOC_ID")
    List<Map<String, Object>> selectDocs(@Param("id") long purchaseId);

    @Select("SELECT CONTRACT_ID AS \"contractId\", CONTRACT_NO AS \"contractNo\", TITLE AS \"title\", PARTY AS \"party\", "
            + "AMOUNT AS \"amount\", STATUS AS \"status\" FROM CONTRACT WHERE PURCHASE_ID = #{id} ORDER BY CONTRACT_ID")
    List<Map<String, Object>> selectContracts(@Param("id") long purchaseId);

    /** 해지하지 않은 계약 금액 합 (계약이 있으면 지급 한도는 계약 금액) */
    @Select("SELECT COALESCE(SUM(AMOUNT), 0) FROM CONTRACT WHERE PURCHASE_ID = #{id} AND STATUS <> 'TERMINATED'")
    long sumContract(@Param("id") long purchaseId);

    @Select("SELECT COUNT(*) FROM CONTRACT WHERE PURCHASE_ID = #{id} AND STATUS <> 'TERMINATED'")
    int countContract(@Param("id") long purchaseId);

    /** 이 구매 건으로 결재 중·완료된 지출결의 금액 합 */
    @Select("<script>SELECT COALESCE(SUM(PAY_AMOUNT), 0) FROM APPROVAL_DOC WHERE PURCHASE_ID = #{id} AND KIND = '지출결의' "
            + "AND STATUS IN ('PROG', 'DONE') AND DELETED_YN = 'N'<if test='except != null'> AND DOC_ID &lt;&gt; #{except}</if></script>")
    long sumPaid(@Param("id") long purchaseId, @Param("except") Long exceptDocId);

    /** 결재 중·완료된 문서(품의·결의)가 있는지: 있으면 업체 선정을 되돌리거나 취소할 수 없다 */
    @Select("SELECT COUNT(*) FROM APPROVAL_DOC WHERE PURCHASE_ID = #{id} AND STATUS IN ('PROG', 'DONE') AND DELETED_YN = 'N'")
    int countLiveDocs(@Param("id") long purchaseId);

    // ===== 물품대장 =====

    String A_COLS = "A.ASSET_ID, A.ASSET_NO, A.NAME, A.SPEC, A.QUANTITY, A.UNIT_PRICE, A.ACQUIRED_DATE, A.PURCHASE_ID, A.DEPT_NAME, "
            + "A.LOCATION, A.USER_NAME, A.STATUS, A.LAST_CHECKED_AT, A.LAST_CHECKED_BY, A.DISPOSED_AT, A.DISPOSE_REASON, A.CREATED_BY, "
            + "A.CREATED_AT, P.PUR_NO, A.IDLE_DOC_ID, A.DISPOSE_METHOD ";

    @Select("SELECT SEQ_ASSET.NEXTVAL FROM DUAL")
    long nextAssetId();

    @Insert("INSERT INTO ASSET (ASSET_ID, ASSET_NO, NAME, SPEC, QUANTITY, UNIT_PRICE, ACQUIRED_DATE, PURCHASE_ID, DEPT_NAME, LOCATION, "
            + "USER_NAME, STATUS, CREATED_BY, CREATED_AT) VALUES (#{assetId}, #{assetNo}, #{name}, #{spec}, #{quantity}, #{unitPrice}, "
            + "#{acquiredDate}, #{purchaseId, jdbcType=NUMERIC}, #{deptName}, #{location}, #{userName}, 'USE', #{createdBy}, #{createdAt})")
    void insertAsset(AssetVO a);

    String A_WHERE = "WHERE 1 = 1 "
            + "<if test='dept != null'>AND A.DEPT_NAME = #{dept} </if>"
            + "<if test='status != null'>AND A.STATUS = #{status} </if>"
            + "<if test='status == null'>AND A.STATUS &lt;&gt; 'DISPOSED' </if>"
            + "<if test='q != null'>AND (A.NAME LIKE #{q} ESCAPE '\\' OR A.ASSET_NO LIKE #{q} ESCAPE '\\' OR A.LOCATION LIKE #{q} ESCAPE '\\' "
            + "OR A.USER_NAME LIKE #{q} ESCAPE '\\') </if>";

    @Select("<script>SELECT " + A_COLS + "FROM ASSET A LEFT JOIN PURCHASE P ON P.PURCHASE_ID = A.PURCHASE_ID " + A_WHERE
            + "ORDER BY A.ASSET_ID DESC</script>")
    List<AssetVO> selectAssets(@Param("dept") String dept, @Param("status") String status, @Param("q") String likeQuery);

    @Select("<script>SELECT * FROM (SELECT X.*, ROWNUM AS RN FROM (SELECT " + A_COLS
            + "FROM ASSET A LEFT JOIN PURCHASE P ON P.PURCHASE_ID = A.PURCHASE_ID " + A_WHERE
            + "ORDER BY A.ASSET_ID DESC) X WHERE ROWNUM &lt;= #{endRow}) WHERE RN &gt; #{startRow}</script>")
    List<AssetVO> selectAssetPage(@Param("dept") String dept, @Param("status") String status, @Param("q") String likeQuery,
                                  @Param("startRow") int startRow, @Param("endRow") int endRow);

    /** 건수, 금액 합, 올해 재물조사한 건수 (쪽과 상관없이 조건 전체) */
    @Select("<script>SELECT COUNT(*) AS \"count\", COALESCE(SUM(A.QUANTITY * A.UNIT_PRICE), 0) AS \"total\", "
            + "COALESCE(SUM(CASE WHEN A.LAST_CHECKED_AT &gt;= #{yearStart} THEN 1 ELSE 0 END), 0) AS \"checked\" FROM ASSET A "
            + A_WHERE + "</script>")
    Map<String, Object> summarizeAssets(@Param("dept") String dept, @Param("status") String status, @Param("q") String likeQuery,
                                        @Param("yearStart") LocalDate yearStart);

    @Select("SELECT " + A_COLS + "FROM ASSET A LEFT JOIN PURCHASE P ON P.PURCHASE_ID = A.PURCHASE_ID WHERE A.ASSET_ID = #{id}")
    AssetVO selectAsset(@Param("id") long id);

    @Select("SELECT " + A_COLS + "FROM ASSET A LEFT JOIN PURCHASE P ON P.PURCHASE_ID = A.PURCHASE_ID WHERE A.PURCHASE_ID = #{id} "
            + "ORDER BY A.ASSET_ID")
    List<AssetVO> selectAssetsByPurchase(@Param("id") long purchaseId);

    @Select("SELECT COALESCE(SUM(QUANTITY * UNIT_PRICE), 0) FROM ASSET WHERE PURCHASE_ID = #{id}")
    long sumAssets(@Param("id") long purchaseId);

    @Update("UPDATE ASSET SET LOCATION = #{location}, USER_NAME = #{userName} WHERE ASSET_ID = #{id} AND STATUS <> 'DISPOSED'")
    int updatePlace(@Param("id") long id, @Param("location") String location, @Param("userName") String userName);

    /** 재물조사: 실물을 확인했다 */
    @Update("UPDATE ASSET SET LAST_CHECKED_AT = #{on}, LAST_CHECKED_BY = #{by} WHERE ASSET_ID = #{id} AND STATUS <> 'DISPOSED'")
    int check(@Param("id") long id, @Param("on") LocalDate on, @Param("by") String by);

    /** 불용을 되돌려 다시 쓴다 */
    @Update("UPDATE ASSET SET STATUS = 'USE', IDLE_DOC_ID = NULL WHERE ASSET_ID = #{id} AND STATUS = 'IDLE'")
    int reuse(@Param("id") long id);

    /** 결재가 끝난 불용 결정 문서를 반영한다 (쓰는 중인 그 부서 물품만) */
    @Update("UPDATE ASSET SET STATUS = 'IDLE', IDLE_DOC_ID = #{docId} WHERE ASSET_NO = #{assetNo} AND DEPT_NAME = #{dept} AND STATUS = 'USE'")
    int markIdle(@Param("assetNo") String assetNo, @Param("dept") String dept, @Param("docId") long docId);

    @Select("<script>SELECT " + A_COLS + "FROM ASSET A LEFT JOIN PURCHASE P ON P.PURCHASE_ID = A.PURCHASE_ID WHERE A.ASSET_NO IN "
            + "<foreach collection='nos' item='n' open='(' separator=',' close=')'>#{n}</foreach></script>")
    List<AssetVO> selectAssetsByNo(@Param("nos") java.util.Collection<String> assetNos);

    @Update("UPDATE ASSET SET STATUS = 'DISPOSED', DISPOSED_AT = #{on}, DISPOSE_METHOD = #{method}, DISPOSE_REASON = #{reason} "
            + "WHERE ASSET_ID = #{id} AND STATUS = 'IDLE' AND IDLE_DOC_ID IS NOT NULL")
    int dispose(@Param("id") long id, @Param("on") LocalDate on, @Param("method") String method, @Param("reason") String reason);
}
