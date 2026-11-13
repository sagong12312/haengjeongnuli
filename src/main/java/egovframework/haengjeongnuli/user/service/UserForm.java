package egovframework.haengjeongnuli.user.service;

import egovframework.haengjeongnuli.cmm.LoginUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 관리자가 직원 계정을 만들 때 입력값 */
public class UserForm {

    @NotBlank(message = "아이디를 입력하세요.")
    @Pattern(regexp = "[a-zA-Z0-9._-]{3,50}", message = "아이디는 영문·숫자·.-_ 3~50자로 입력하세요.")
    private String loginId;

    @NotBlank(message = "이름을 입력하세요.")
    @Size(max = 100)
    private String userName;

    @Size(max = 50)
    private String positionName;

    @Size(max = 100)
    private String deptName;

    @NotNull(message = "권한을 고르세요.")
    private LoginUser.Role role = LoginUser.Role.GENERAL;

    @NotBlank(message = "초기 비밀번호를 입력하세요.")
    private String password;

    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getPositionName() { return positionName; }
    public void setPositionName(String positionName) { this.positionName = positionName; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public LoginUser.Role getRole() { return role; }
    public void setRole(LoginUser.Role role) { this.role = role; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
