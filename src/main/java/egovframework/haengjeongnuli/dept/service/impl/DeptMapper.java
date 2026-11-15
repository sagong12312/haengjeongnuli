package egovframework.haengjeongnuli.dept.service.impl;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import egovframework.haengjeongnuli.dept.service.DeptVO;

@Mapper
public interface DeptMapper {
    List<DeptVO> selectAll();
    DeptVO selectById(long deptId);
    DeptVO selectByName(String deptName);
    long nextId();
    void insert(DeptVO d);
    void updateUse(@Param("deptId") long deptId, @Param("useYn") String useYn);
    void updateInfo(@Param("deptId") long deptId, @Param("parentId") Long parentId, @Param("deptCode") String deptCode,
                    @Param("sortOrder") int sortOrder);
    int countMembers(String deptName);
    int countActiveChildren(long deptId);
}
