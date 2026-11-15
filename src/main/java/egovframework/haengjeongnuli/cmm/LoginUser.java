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
    /** 세입 담당 직원 (Y/N) */
    private String revenueYn = "N";
    /** 사회복지 담당 직원 (Y/N) */
    private String welfareYn = "N";
    /** 처리과 기록물관리책임자 (Y/N) */
    private String recordsManagerYn = "N";
    // 인사정보
    private String empNo;
    private String jobSeries;
    private java.time.LocalDate hiredOn;
    private String extension;

    // 화면에서 쓰는 이름들
    public String name() { return userName; }
    public String position() { return positionName == null ? "" : positionName; }
    public String dept() { return deptName == null ? "" : deptName; }
    public boolean isCounterStaff() { return role == Role.COUNTER; }
    public boolean isAdmin() { return role == Role.ADMIN; }
    /** 세입 화면을 쓸 수 있는 세입 담당 직원 */
    public boolean isRevenueStaff() { return "Y".equals(revenueYn); }
    /** 복지 급여 신청(민감한 개인정보)을 다루는 사회복지 담당 직원 */
    public boolean isWelfareStaff() { return "Y".equals(welfareYn); }
    public String getWelfareYn() { return welfareYn; }
    /** 처리과 기록물관리책임자 (등록정보 수정을 확인한다) */
    public boolean isRecordsManager() { return "Y".equals(recordsManagerYn); }
    public String getRecordsManagerYn() { return recordsManagerYn; }
    public void setRecordsManagerYn(String recordsManagerYn) { this.recordsManagerYn = recordsManagerYn; }
    public void setWelfareYn(String welfareYn) { this.welfareYn = welfareYn; }
    /** 잠기는 시간(분). 지나면 저절로 풀린다 (LoginPolicy 가 설정값으로 바꾼다) */
    private static volatile int lockMinutes = 15;
    public static void setLockMinutes(int minutes) { lockMinutes = Math.max(1, minutes); }
    public static int getLockMinutes() { return lockMinutes; }

    /** 비밀번호를 MAX_FAILED 번 틀린 뒤 lockMinutes 분이 지나지 않았으면 잠김 (잠긴 시각이 없는 예전 기록은 풀린 것으로 본다) */
    public boolean isLocked() {
        return failedCount >= MAX_FAILED && lockedAt != null && lockedAt.isAfter(LocalDateTime.now().minusMinutes(lockMinutes));
    }

    /** 권한·상태가 바뀔 때마다 올라가는 번호 (로그인 중인 세션이 최신인지 확인) */
    private long securityVersion;
    private LocalDateTime lockedAt;
    public long getSecurityVersion() { return securityVersion; }
    public void setSecurityVersion(long securityVersion) { this.securityVersion = securityVersion; }
    public LocalDateTime getLockedAt() { return lockedAt; }
    public void setLockedAt(LocalDateTime lockedAt) { this.lockedAt = lockedAt; }

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
    public String getEmpNo() { return empNo; }
    public void setEmpNo(String empNo) { this.empNo = empNo; }
    public String getJobSeries() { return jobSeries; }
    public void setJobSeries(String jobSeries) { this.jobSeries = jobSeries; }
    public java.time.LocalDate getHiredOn() { return hiredOn; }
    public void setHiredOn(java.time.LocalDate hiredOn) { this.hiredOn = hiredOn; }
    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }
    public String getRevenueYn() { return revenueYn; }
    public void setRevenueYn(String revenueYn) { this.revenueYn = revenueYn; }
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
