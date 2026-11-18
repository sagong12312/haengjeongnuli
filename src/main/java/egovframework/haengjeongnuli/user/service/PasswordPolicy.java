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
        String weak = weakness(password);
        if (weak != null) {
            return weak;
        }
        return null;
    }

    /** 흔해서 쉽게 맞힐 수 있는 낱말 (대소문자·숫자·기호를 빼고 비교) */
    static final java.util.List<String> COMMON = java.util.List.of(
            "password", "passwd", "qwerty", "qwer", "asdf", "zxcv", "1q2w3e", "q1w2e3", "admin", "welcome", "iloveyou",
            "letmein", "abcd", "korea", "seoul", "dragon", "monkey", "master", "login", "test", "guest", "haengjeong", "nuri");

    /**
     * 흔한 비밀번호 꼴이면 이유, 아니면 null.
     * 흔한 낱말, 1234·4321 같은 4자리 이상 연속 숫자, 같은 글자 4번 이상 반복을 막는다.
     */
    public static String weakness(String password) {
        if (password == null) {
            return null;
        }
        String low = password.toLowerCase();
        for (String w : COMMON) {
            if (low.contains(w)) {
                return "'" + w + "'처럼 흔한 낱말이 들어간 비밀번호는 쓸 수 없습니다.";
            }
        }
        if (password.matches(".*(.)\\1\\1\\1.*")) {
            return "같은 글자를 4번 넘게 잇달아 쓸 수 없습니다.";
        }
        int up = 1, down = 1;
        for (int i = 1; i < password.length(); i++) {
            char a = password.charAt(i - 1), b = password.charAt(i);
            boolean digits = Character.isDigit(a) && Character.isDigit(b);
            up = digits && b - a == 1 ? up + 1 : 1;
            down = digits && a - b == 1 ? down + 1 : 1;
            if (up >= 4 || down >= 4) {
                return "1234·4321처럼 4자리 넘게 이어지는 숫자는 쓸 수 없습니다.";
            }
        }
        return null;
    }
}
