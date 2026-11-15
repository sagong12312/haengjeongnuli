package egovframework.haengjeongnuli.user.service;

import java.util.List;

import org.springframework.security.core.userdetails.UserDetailsService;

import egovframework.haengjeongnuli.cmm.LoginUser;

/** 직원 계정 서비스 (로그인 조회 포함) */
public interface UserService extends UserDetailsService {

    /** 계정을 만든다. 아이디가 이미 있으면 IllegalStateException */
    LoginUser create(UserForm form);

    List<LoginUser> list();

    boolean hasAnyUser();

    void unlock(long userId);

    void setEnabled(long userId, boolean enabled);

    /** 이름·직급·사번·직렬·임용일·내선번호를 고친다 (부서는 부서 이동으로만 바꾼다) */
    void updateProfile(LoginUser u);

    /** 아이디로 찾는다. 없으면 null */
    LoginUser findByLoginId(String loginId);

    /** 계정 번호로 찾는다. 없으면 null */
    LoginUser findById(long userId);

    /** 사회복지 담당 표시를 켜고 끈다 */
    void setWelfareStaff(long userId, boolean value);

    /** 처리과 기록물관리책임자 표시를 켜고 끈다 */
    void setRecordsManager(long userId, boolean value);

    /** 세입 담당 표시를 켜고 끈다 */
    void setRevenueStaff(long userId, boolean value);

    /** 관리자가 비밀번호를 새로 정해 준다 */
    void resetPassword(long userId, String newPassword);

    /** 본인이 비밀번호를 바꾼다. 현재 비밀번호가 틀리면 false */
    boolean changeOwnPassword(long userId, String currentPassword, String newPassword);

    void recordFailure(String loginId);

    void recordSuccess(String loginId);

    /** 부서를 옮기고 기록을 남긴다. 계정이 없으면 false, 같은 부서면 바꾸지 않고 true */
    boolean moveDept(long userId, String newDept, String movedBy);

    List<DeptMoveVO> recentDeptMoves(int limit);
}
