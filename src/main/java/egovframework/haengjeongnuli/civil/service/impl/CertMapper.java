package egovframework.haengjeongnuli.civil.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import egovframework.haengjeongnuli.civil.service.CertIssueVO;

@Mapper
public interface CertMapper {

    @Select("SELECT SEQ_CERT_ISSUE.NEXTVAL FROM DUAL")
    long nextId();

    @Insert("INSERT INTO CERT_ISSUE (ISSUE_ID, ISSUE_NO, ISSUE_YEAR, CERT_TYPE, APPLICANT_NAME, APPLICANT_TYPE, PURPOSE, COPIES, FEE, "
            + "EXEMPT_REASON, ID_CHECKED_YN, DEPT_NAME, ISSUED_BY, ISSUED_NAME, ISSUED_AT) VALUES (#{issueId}, #{issueNo}, #{issueYear}, "
            + "#{certType}, #{applicantName}, #{applicantType}, #{purpose}, #{copies}, #{fee}, #{exemptReason}, #{idCheckedYn}, "
            + "#{deptName}, #{issuedBy}, #{issuedName}, #{issuedAt})")
    void insert(CertIssueVO v);

    @Select("SELECT * FROM (SELECT ISSUE_ID, ISSUE_NO, ISSUE_YEAR, CERT_TYPE, APPLICANT_NAME, APPLICANT_TYPE, PURPOSE, COPIES, FEE, "
            + "EXEMPT_REASON, ID_CHECKED_YN, DEPT_NAME, ISSUED_BY, ISSUED_NAME, ISSUED_AT FROM CERT_ISSUE "
            + "WHERE ISSUED_AT >= #{from} AND ISSUED_AT < #{to} ORDER BY ISSUED_AT DESC, ISSUE_ID DESC) WHERE ROWNUM <= 20000")
    List<CertIssueVO> selectBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
