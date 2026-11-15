package egovframework.haengjeongnuli.finance.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import egovframework.haengjeongnuli.finance.service.ContractVO;

@Mapper
public interface ContractMapper {

    String COLS = "CONTRACT_ID, CONTRACT_NO, CONTRACT_YEAR, TITLE, CONTRACT_KIND, METHOD, PRIVATE_REASON, PARTY, PARTY_BIZ_NO, AMOUNT, "
            + "DEPOSIT, CONTRACT_DATE, START_DATE, END_DATE, BUDGET_ID, STATUS, STATUS_NOTE, DEPT_NAME, CREATED_BY, CREATED_NAME, "
            + "CREATED_AT, UPDATED_AT ";

    @Select("SELECT SEQ_CONTRACT.NEXTVAL FROM DUAL")
    long nextId();

    @Insert("INSERT INTO CONTRACT (" + COLS + ") VALUES (#{contractId}, #{contractNo}, #{contractYear}, #{title}, #{contractKind}, "
            + "#{method}, #{privateReason}, #{party}, #{partyBizNo}, #{amount}, #{deposit, jdbcType=NUMERIC}, #{contractDate}, "
            + "#{startDate, jdbcType=DATE}, #{endDate, jdbcType=DATE}, #{budgetId, jdbcType=NUMERIC}, 'ACTIVE', NULL, #{deptName}, "
            + "#{createdBy}, #{createdName}, #{createdAt}, NULL)")
    void insert(ContractVO c);

    @Select("<script>SELECT " + COLS + "FROM CONTRACT WHERE CONTRACT_YEAR = #{year} "
            + "<if test='dept != null'>AND DEPT_NAME = #{dept}</if> ORDER BY CONTRACT_DATE DESC, CONTRACT_ID DESC</script>")
    List<ContractVO> selectList(@Param("year") int year, @Param("dept") String dept);

    @Select("SELECT " + COLS + "FROM CONTRACT WHERE CONTRACT_ID = #{id}")
    ContractVO selectById(@Param("id") long id);

    @Update("UPDATE CONTRACT SET STATUS = #{status}, STATUS_NOTE = #{note}, UPDATED_AT = #{at} WHERE CONTRACT_ID = #{id} AND STATUS = 'ACTIVE'")
    int close(@Param("id") long id, @Param("status") String status, @Param("note") String note, @Param("at") LocalDateTime at);
}
