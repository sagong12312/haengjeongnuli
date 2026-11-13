package egovframework.haengjeongnuli.user.service;

/**
 * 비밀번호 규칙: 9자 이상, 영문·숫자·특수문자를 모두 포함, 아이디를 포함하지 않음.
 * 기관 보안 지침이 정해지면 여기만 고친다.
 */
public final class PasswordPolicy {

    public static final String DESCRIPTION = "9자 이상, 영문·숫자·특수문자를 모두 섞어야 하며 아이디를 포함할 수 없습니다.";

    private PasswordPolicy() {}

    /** 규칙에 맞으면 null, 아니면 이유 */
    public static String check(String password, String loginId) {
        if (password == null || password.length() < 9) {
            return "비밀번호는 9자 이상이어야 합니다.";
        }
        if (password.length() > 64) {
            return "비밀번호는 64자 이하로 입력하세요.";
        }
        boolean letter = password.chars().anyMatch(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'));
        boolean digit = password.chars().anyMatch(Character::isDigit);
        boolean special = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c));
        if (!(letter && digit && special)) {
            return "비밀번호는 영문·숫자·특수문자를 모두 포함해야 합니다.";
        }
        if (loginId != null && !loginId.isBlank()
                && password.toLowerCase().contains(loginId.toLowerCase())) {
            return "비밀번호에 아이디를 넣을 수 없습니다.";
        }
        return null;
    }
}
