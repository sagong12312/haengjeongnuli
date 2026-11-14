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
