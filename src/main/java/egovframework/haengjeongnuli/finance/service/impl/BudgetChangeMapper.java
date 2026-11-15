package egovframework.haengjeongnuli.finance.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import egovframework.haengjeongnuli.finance.service.BudgetChangeVO;

@Mapper
public interface BudgetChangeMapper {

    String COLS = "C.CHANGE_ID, C.BUDGET_ID, C.CHANGE_TYPE, C.ROUND_NO, C.CARRY_KIND, C.DELTA, C.REASON, C.STATUS, C.REQUESTER_ID, "
            + "C.REQUESTER_NAME, C.REQUESTED_AT, C.REVIEWER_ID, C.REVIEWER_NAME, C.REVIEWED_AT, C.REVIEW_NOTE, C.NEW_BUDGET_ID, "
            + "B.DEPT_NAME, B.CODE, B.FISCAL_YEAR ";

    @Select("SELECT SEQ_FIN_BUDGET_CHANGE.NEXTVAL FROM DUAL")
    long nextId();

    @Insert("INSERT INTO FIN_BUDGET_CHANGE (CHANGE_ID, BUDGET_ID, CHANGE_TYPE, ROUND_NO, CARRY_KIND, DELTA, REASON, STATUS, "
            + "REQUESTER_ID, REQUESTER_NAME, REQUESTED_AT) VALUES (#{changeId}, #{budgetId}, #{changeType}, "
            + "#{roundNo, jdbcType=NUMERIC}, #{carryKind}, #{delta}, #{reason}, 'REQUESTED', #{requesterId}, #{requesterName}, #{requestedAt})")
    void insert(BudgetChangeVO c);

    @Select("SELECT " + COLS + "FROM FIN_BUDGET_CHANGE C JOIN FIN_BUDGET B ON B.BUDGET_ID = C.BUDGET_ID WHERE C.BUDGET_ID = #{budgetId} ORDER BY C.CHANGE_ID")
    List<BudgetChangeVO> selectByBudget(@Param("budgetId") long budgetId);

    @Select("SELECT " + COLS + "FROM FIN_BUDGET_CHANGE C JOIN FIN_BUDGET B ON B.BUDGET_ID = C.BUDGET_ID WHERE C.CHANGE_ID = #{id}")
    BudgetChangeVO selectById(@Param("id") long changeId);

    @Select("SELECT " + COLS + "FROM FIN_BUDGET_CHANGE C JOIN FIN_BUDGET B ON B.BUDGET_ID = C.BUDGET_ID WHERE C.STATUS = 'REQUESTED' ORDER BY C.CHANGE_ID")
    List<BudgetChangeVO> selectRequested();

    @Select("SELECT COUNT(*) FROM FIN_BUDGET_CHANGE WHERE BUDGET_ID = #{budgetId} AND STATUS = 'REQUESTED'")
    int countOpen(@Param("budgetId") long budgetId);

    @Update("UPDATE FIN_BUDGET_CHANGE SET STATUS = #{status}, REVIEWER_ID = #{by}, REVIEWER_NAME = #{byName}, REVIEWED_AT = #{at}, "
            + "REVIEW_NOTE = #{note}, NEW_BUDGET_ID = #{newBudgetId, jdbcType=NUMERIC} WHERE CHANGE_ID = #{id} AND STATUS = 'REQUESTED'")
    int close(@Param("id") long changeId, @Param("status") String status, @Param("by") String by, @Param("byName") String byName,
              @Param("at") LocalDateTime at, @Param("note") String note, @Param("newBudgetId") Long newBudgetId);

    @Update("UPDATE FIN_BUDGET SET AMOUNT = AMOUNT + #{delta}, UPDATED_AT = #{at} WHERE BUDGET_ID = #{budgetId} AND STATUS = 'APPROVED'")
    int addAmount(@Param("budgetId") long budgetId, @Param("delta") long delta, @Param("at") LocalDateTime at);

    /** 이월로 다음 해에 만드는 승인된 예산 */
    @Insert("INSERT INTO FIN_BUDGET (BUDGET_ID, KIND, FISCAL_YEAR, DETAIL_ID, CODE, AMOUNT, BASIS, STATUS, DEPT_NAME, REQUESTER_ID, "
            + "REQUESTER_NAME, REQUESTED_AT, REVIEWER_ID, REVIEWER_NAME, REVIEWED_AT, CREATED_AT, DELETED_YN) "
            + "SELECT #{newId}, KIND, FISCAL_YEAR + 1, DETAIL_ID, CODE, #{amount}, #{basis}, 'APPROVED', DEPT_NAME, #{requesterId}, "
            + "#{requesterName}, #{at}, #{reviewerId}, #{reviewerName}, #{at}, #{at}, 'N' FROM FIN_BUDGET WHERE BUDGET_ID = #{budgetId}")
    int insertCarried(@Param("newId") long newId, @Param("budgetId") long budgetId, @Param("amount") long amount,
                      @Param("basis") String basis, @Param("requesterId") String requesterId,
                      @Param("requesterName") String requesterName, @Param("reviewerId") String reviewerId,
                      @Param("reviewerName") String reviewerName, @Param("at") LocalDateTime at);
}
