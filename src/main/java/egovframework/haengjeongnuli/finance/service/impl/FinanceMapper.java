package egovframework.haengjeongnuli.finance.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.haengjeongnuli.finance.service.BudgetVO;
import egovframework.haengjeongnuli.finance.service.FinCodeVO;
import egovframework.haengjeongnuli.finance.service.ProgramVO;
import egovframework.haengjeongnuli.finance.service.TxnVO;

@Mapper
public interface FinanceMapper {

    // 과목 코드
    int countCodes(@Param("codeType") String codeType);
    void insertCode(FinCodeVO code);
    List<FinCodeVO> selectCodes(@Param("codeType") String codeType);

    // 사업
    long nextProgramId();
    void insertProgram(ProgramVO p);
    List<ProgramVO> selectPrograms(@Param("year") int year);
    ProgramVO selectProgram(@Param("progId") long progId);
    int countChildren(@Param("progId") long progId);
    int countBudgetsOfDetail(@Param("progId") long progId);
    int deleteProgram(@Param("progId") long progId);

    // 예산
    long nextBudgetId();
    void insertBudget(BudgetVO b);
    int updateBudgetContent(BudgetVO b);
    BudgetVO selectBudget(@Param("budgetId") long budgetId);
    /** 같은 세부사업·과목의 예산이 이미 있는지 (자기 자신 제외) */
    int countSame(@Param("kind") String kind, @Param("year") int year, @Param("detailId") Long detailId,
                  @Param("code") String code, @Param("exceptId") Long exceptId);
    List<BudgetVO> selectBudgets(@Param("kind") String kind, @Param("year") int year,
                                 @Param("dept") String dept, @Param("status") String status);
    int updateStatus(@Param("budgetId") long budgetId, @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus, @Param("at") LocalDateTime at,
                     @Param("reviewerId") String reviewerId, @Param("reviewerName") String reviewerName,
                     @Param("note") String note);
    int markBudgetDeleted(@Param("budgetId") long budgetId);
    /** 집행 전에 예산 줄을 잠근다 (잔액 확인과 집행 사이에 다른 집행이 끼지 않게) */
    BudgetVO lockBudget(@Param("budgetId") long budgetId);

    // 집행·수납
    long nextTxnId();
    void insertTxn(TxnVO t);
    TxnVO selectTxn(@Param("txnId") long txnId);
    List<TxnVO> selectTxnsOfBudget(@Param("budgetId") long budgetId);
    List<TxnVO> selectRecentTxns(@Param("kind") String kind, @Param("year") int year, @Param("limit") int limit);
    long sumUsed(@Param("budgetId") long budgetId);
    /** 세입 목별 수납액 합계 */
    List<TxnVO> sumReceiptsByCode(@Param("year") int year);
    long sumReserved(@Param("budgetId") long budgetId, @Param("exceptDocId") Long exceptDocId);
    List<java.util.Map<String, Object>> selectPendingSpends(@Param("budgetId") long budgetId);
    int cancelTxn(@Param("txnId") long txnId, @Param("by") String by, @Param("at") LocalDateTime at);

    long sumCommitted(@Param("budgetId") long budgetId);
    long sumPendingOnCommit(@Param("commitDocId") long commitDocId, @Param("exceptDocId") Long exceptDocId);
    long nextCommitId();
    void insertCommit(egovframework.haengjeongnuli.finance.service.CommitVO c);
    egovframework.haengjeongnuli.finance.service.CommitVO selectCommitByDoc(@Param("docId") long docId);
    egovframework.haengjeongnuli.finance.service.CommitVO lockCommitByDoc(@Param("docId") long docId);
    List<egovframework.haengjeongnuli.finance.service.CommitVO> selectOpenCommits(@Param("dept") String dept);
    List<egovframework.haengjeongnuli.finance.service.CommitVO> selectCommitsByBudget(@Param("budgetId") long budgetId);
    int addCommitSpent(@Param("docId") long docId, @Param("amount") long amount);
    int closeCommit(@Param("docId") long docId, @Param("by") String by, @Param("at") java.time.LocalDateTime at,
                    @Param("note") String note);
    List<java.util.Map<String, Object>> selectCommitSpends(@Param("docId") long docId);
}
