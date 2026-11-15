package egovframework.haengjeongnuli.user.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

@Service("userService")
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String loginId) {
        LoginUser u = loginId == null ? null : userMapper.selectByLoginId(loginId.trim());
        if (u == null) {
            throw new UsernameNotFoundException("계정 없음");
        }
        return u;
    }

    @Override
    @Transactional
    public LoginUser create(UserForm form) {
        String loginId = form.getLoginId().trim();
        if (userMapper.selectByLoginId(loginId) != null) {
            throw new IllegalStateException("이미 있는 아이디입니다: " + loginId);
        }
        LoginUser u = new LoginUser();
        u.setUserId(userMapper.nextId());
        u.setLoginId(loginId);
        u.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        u.setUserName(form.getUserName().trim());
        u.setPositionName(blankToNull(form.getPositionName()));
        u.setDeptName(blankToNull(form.getDeptName()));
        u.setRole(form.getRole());
        u.setEnabledYn("Y");
        u.setCreatedAt(LocalDateTime.now());
        userMapper.insertUser(u);
        return u;
    }

    @Override
    public List<LoginUser> list() {
        return userMapper.selectAll();
    }

    @Override
    public boolean hasAnyUser() {
        return userMapper.countUsers() > 0;
    }

    @Override
    @Transactional
    public void unlock(long userId) {
        userMapper.resetFailedCount(userId);
    }

    @Override
    @Transactional
    public void setEnabled(long userId, boolean enabled) {
        userMapper.updateEnabled(userId, enabled ? "Y" : "N");
    }

    @Override
    @Transactional
    public void resetPassword(long userId, String newPassword) {
        // 관리자가 정해 준 비밀번호는 다음 로그인 때 본인이 바꾸게 한다
        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword), null);
        userMapper.resetFailedCount(userId);
    }

    @Override
    @Transactional
    public boolean changeOwnPassword(long userId, String currentPassword, String newPassword) {
        LoginUser u = userMapper.selectById(userId);
        if (u == null || currentPassword == null || !passwordEncoder.matches(currentPassword, u.getPasswordHash())) {
            return false;
        }
        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword), LocalDateTime.now());
        return true;
    }

    @Override
    @Transactional
    public void recordFailure(String loginId) {
        if (loginId != null) {
            userMapper.increaseFailedCount(loginId.trim());
        }
    }

    @Override
    @Transactional
    public void recordSuccess(String loginId) {
        userMapper.markLoginSuccess(loginId, LocalDateTime.now());
    }

    @Override
    @Transactional
    public boolean moveDept(long userId, String newDept, String movedBy) {
        LoginUser u = userMapper.selectById(userId);
        if (u == null) {
            return false;
        }
        String to = blankToNull(newDept);
        String from = u.getDeptName();
        if (java.util.Objects.equals(from, to)) {
            return true;
        }
        userMapper.updateDept(userId, to);
        userMapper.insertDeptHistory(userId, from, to, LocalDateTime.now(), movedBy);
        return true;
    }

    @Override
    public List<egovframework.haengjeongnuli.user.service.DeptMoveVO> recentDeptMoves(int limit) {
        return userMapper.selectRecentDeptMoves(Math.max(1, Math.min(limit, 200)));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
