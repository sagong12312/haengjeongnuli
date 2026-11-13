package egovframework.haengjeongnuli.civil.service;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 민원 접수 화면 입력값 */
public class CivilForm {

    @NotNull(message = "민원 종류를 고르세요.")
    private CivilKind kind;
    private boolean lawInterpretation = true;
    private LegalAffair affair;
    @NotBlank
    private String route = "방문";

    @NotBlank(message = "성명을 입력하세요.")
    @Size(max = 100)
    private String name;
    private boolean noMobile;
    @Size(max = 20)
    private String mobile;
    private boolean noHome;
    @Size(max = 20)
    private String homePhone;
    @Email(message = "이메일 형식이 아닙니다.")
    @Size(max = 200)
    private String email;
    @Size(max = 500)
    private String address;
    @NotBlank
    private String notify = "문자";

    @NotBlank(message = "제목을 입력하세요.")
    @Size(max = 300)
    private String title;
    @NotBlank(message = "내용을 입력하세요.")
    private String content;

    private String dept;
    private String staff;
    private boolean privacyConsent;

    @AssertTrue(message = "법정민원은 민원 사무를 골라야 합니다.")
    public boolean isAffairChosen() {
        return kind != CivilKind.LEGAL || affair != null;
    }

    @AssertTrue(message = "휴대전화 번호를 입력하거나 '없음'을 고르세요.")
    public boolean isMobileOk() {
        return noMobile || (mobile != null && !mobile.isBlank());
    }

    @AssertTrue(message = "집전화 번호를 입력하거나 '없음'을 고르세요.")
    public boolean isHomeOk() {
        return !noMobile || noHome || (homePhone != null && !homePhone.isBlank());
    }

    @AssertTrue(message = "휴대전화가 없으면 문자로 통지할 수 없습니다.")
    public boolean isNotifyOk() {
        return !(noMobile && "문자".equals(notify));
    }

    @AssertTrue(message = "개인정보 수집·이용 동의를 확인하세요.")
    public boolean isConsentOk() {
        return privacyConsent;
    }

    public CivilKind getKind() { return kind; }
    public void setKind(CivilKind kind) { this.kind = kind; }
    public boolean isLawInterpretation() { return lawInterpretation; }
    public void setLawInterpretation(boolean lawInterpretation) { this.lawInterpretation = lawInterpretation; }
    public LegalAffair getAffair() { return affair; }
    public void setAffair(LegalAffair affair) { this.affair = affair; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isNoMobile() { return noMobile; }
    public void setNoMobile(boolean noMobile) { this.noMobile = noMobile; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public boolean isNoHome() { return noHome; }
    public void setNoHome(boolean noHome) { this.noHome = noHome; }
    public String getHomePhone() { return homePhone; }
    public void setHomePhone(String homePhone) { this.homePhone = homePhone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getNotify() { return notify; }
    public void setNotify(String notify) { this.notify = notify; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getDept() { return dept; }
    public void setDept(String dept) { this.dept = dept; }
    public String getStaff() { return staff; }
    public void setStaff(String staff) { this.staff = staff; }
    public boolean isPrivacyConsent() { return privacyConsent; }
    public void setPrivacyConsent(boolean privacyConsent) { this.privacyConsent = privacyConsent; }
}
