package egovframework.haengjeongnuli.cmm;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** 로그인한 직원 (USER_ACCOUNT 테이블) */
public class LoginUser implements UserDetails {

    private static final long serialVersionUID = 1L;

    /** 비밀번호를 이 횟수만큼 틀리면 잠긴다 */
    public static final int MAX_FAILED = 5;

    public enum Role {
        GENERAL("일반 직원"), COUNTER("창구 직원"), ADMIN("시스템 관리자");

        private final String label;

        Role(String label) { this.label = label; }

        public String getLabel() { return label; }
    }

    private Long userId;
    private String loginId;
    private String passwordHash;
    private String userName;
    private String positionName;
    private String deptName;
    private Role role;
    private String enabledYn = "Y";
    private int failedCount;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime passwordChangedAt;

    // 화면에서 쓰는 이름들
    public String name() { return userName; }
    public String position() { return positionName == null ? "" : positionName; }
    public String dept() { return deptName == null ? "" : deptName; }
    public boolean isCounterStaff() { return role == Role.COUNTER; }
    public boolean isAdmin() { return role == Role.ADMIN; }
    public boolean isLocked() { return failedCount >= MAX_FAILED; }

    /**
     * 비밀번호를 바꿔야 하는지: 처음 받은(관리자가 정해 준) 비밀번호이거나 maxDays 일이 지났으면 true
     */
    public boolean isPasswordExpired(int maxDays) {
        return passwordChangedAt == null || passwordChangedAt.isBefore(LocalDateTime.now().minusDays(maxDays));
    }

    public String initial() {
        return (userName == null || userName.isBlank()) ? "?" : userName.substring(0, 1);
    }

    // UserDetails
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return loginId; }
    @Override public boolean isAccountNonLocked() { return !isLocked(); }
    @Override public boolean isEnabled() { return "Y".equals(enabledYn); }

    // getter / setter (MyBatis)
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getPositionName() { return positionName; }
    public void setPositionName(String positionName) { this.positionName = positionName; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getEnabledYn() { return enabledYn; }
    public void setEnabledYn(String enabledYn) { this.enabledYn = enabledYn; }
    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getPasswordChangedAt() { return passwordChangedAt; }
    public void setPasswordChangedAt(LocalDateTime passwordChangedAt) { this.passwordChangedAt = passwordChangedAt; }
}
