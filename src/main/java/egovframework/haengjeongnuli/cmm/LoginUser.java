package egovframework.haengjeongnuli.cmm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로그인 기능이 생기기 전까지 쓰는 임시 사용자 정보 (application.yml 의 app.user).
 */
@ConfigurationProperties(prefix = "app.user")
public record LoginUser(String name, String position, String dept, Role role) {

    public enum Role { COUNTER, GENERAL }

    public boolean isCounterStaff() {
        return role == Role.COUNTER;
    }

    public String initial() {
        return (name == null || name.isBlank() || name.startsWith("[")) ? "홍" : name.substring(0, 1);
    }
}
