package egovframework.haengjeongnuli.cmm.notification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReminderMapper {

    /** 처리 중인 민원 중 처리기한이 지났거나 until 전에 끝나는 것 */
    @Select("SELECT COMPLAINT_ID AS \"id\", RECEIPT_NO AS \"receiptNo\", TITLE AS \"title\", DEPT AS \"dept\", "
            + "ASSIGNEE_ID AS \"assigneeId\", REGISTER_ID AS \"registerId\", DEADLINE_AT AS \"deadlineAt\" "
            + "FROM CIVIL_COMPLAINT WHERE STATUS <> 'DONE' AND DEADLINE_AT IS NOT NULL AND DEADLINE_AT < #{until} ORDER BY DEADLINE_AT")
    List<Map<String, Object>> civilDue(@Param("until") LocalDateTime until);

    /** before 이전에 상신된 뒤 아직 결재 중인 문서의 지금 차례 */
    @Select("SELECT D.DOC_ID AS \"docId\", D.TITLE AS \"title\", D.SUBMITTED_AT AS \"submittedAt\", L.APPROVER_ID AS \"approverId\", "
            + "L.APPROVER_NAME AS \"approverName\", L.ROLE AS \"role\" FROM APPROVAL_DOC D JOIN APPROVAL_LINE L ON L.DOC_ID = D.DOC_ID "
            + "WHERE D.STATUS = 'PROG' AND D.DELETED_YN = 'N' AND L.STATUS = 'PENDING' AND D.SUBMITTED_AT < #{before} ORDER BY D.SUBMITTED_AT")
    List<Map<String, Object>> approvalWaiting(@Param("before") LocalDateTime before);
}
