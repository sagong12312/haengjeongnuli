package egovframework.haengjeongnuli.cmm.security;

import java.security.SecureRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.user.service.UserForm;
import egovframework.haengjeongnuli.user.service.UserService;

/**
 * 계정이 하나도 없으면 첫 관리자 계정(admin)을 만든다.
 * 비밀번호는 환경변수 APP_INIT_ADMIN_PASSWORD 로 정할 수 있고, 없으면 무작위로 만들어 파일(app.init-admin.password-file)에
 * 적는다. 서버 로그에는 비밀번호를 남기지 않는다 (로그는 여러 사람이 보거나 오래 보관될 수 있다).
 */
@Component
public class InitialAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminRunner.class);
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final UserService userService;
    private final String configuredPassword;
    private final boolean requirePassword;
    private final String passwordFile;

    public InitialAdminRunner(UserService userService,
                              @Value("${app.init-admin.password:}") String configuredPassword,
                              @Value("${app.init-admin.require-password:false}") boolean requirePassword,
                              @Value("${app.init-admin.password-file:./data/initial-admin-password.txt}") String passwordFile) {
        this.userService = userService;
        this.configuredPassword = configuredPassword;
        this.requirePassword = requirePassword;
        this.passwordFile = passwordFile;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userService.hasAnyUser()) {
            return;
        }
        boolean generated = configuredPassword == null || configuredPassword.isBlank();
        if (generated && requirePassword) {
            throw new IllegalStateException("계정이 하나도 없습니다. 운영 환경에서는 환경변수 APP_INIT_ADMIN_PASSWORD 로 "
                    + "첫 관리자 비밀번호를 정해서 시작하세요 (첫 로그인 때 바꾸게 됩니다).");
        }
        if (!generated) {
            String problem = egovframework.haengjeongnuli.user.service.PasswordPolicy.check(configuredPassword, "admin");
            if (problem != null) {
                throw new IllegalStateException("APP_INIT_ADMIN_PASSWORD 가 비밀번호 규칙에 맞지 않습니다: " + problem);
            }
        }
        String password = generated ? randomPassword() : configuredPassword;

        UserForm form = new UserForm();
        form.setLoginId("admin");
        form.setUserName("시스템 관리자");
        form.setRole(LoginUser.Role.ADMIN);
        form.setPassword(password);
        userService.create(form);

        if (generated) {
            java.nio.file.Path file = java.nio.file.Path.of(passwordFile).toAbsolutePath().normalize();
            try {
                java.nio.file.Files.createDirectories(file.getParent());
                java.nio.file.Files.writeString(file, "아이디: admin\n비밀번호: " + password
                        + "\n\n처음 로그인하면 비밀번호를 바꾸게 됩니다. 로그인한 뒤 이 파일을 지우세요.\n",
                        java.nio.charset.StandardCharsets.UTF_8);
                try {
                    java.nio.file.Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
                } catch (UnsupportedOperationException ignored) {
                    // Windows: 파일 권한은 폴더 권한을 따른다
                }
            } catch (java.io.IOException e) {
                throw new IllegalStateException("첫 관리자 비밀번호를 파일에 쓰지 못했습니다: " + file
                        + " (APP_INIT_ADMIN_PASSWORD 로 정해서 시작할 수도 있습니다)", e);
            }
            log.warn("\n\n==========================================================\n"
                    + "  첫 관리자 계정(admin)을 만들었습니다.\n"
                    + "  비밀번호는 이 파일에 적었습니다: {}\n"
                    + "  로그인해서 비밀번호를 바꾼 뒤 파일을 지우세요.\n"
                    + "==========================================================\n", file);
        } else {
            log.warn("첫 관리자 계정(admin)을 APP_INIT_ADMIN_PASSWORD 로 만들었습니다.");
        }
    }

    /** 영문·숫자 14자 + 특수문자·숫자 (비밀번호 규칙 충족) */
    static String randomPassword() {
        SecureRandom r = new SecureRandom();
        while (true) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 14; i++) {
                sb.append(CHARS.charAt(r.nextInt(CHARS.length())));
            }
            String pw = sb.append('!').append(r.nextInt(10)).toString();
            // 우연히 흔한 낱말·이어지는 숫자가 나오면 다시 만든다
            if (egovframework.haengjeongnuli.user.service.PasswordPolicy.check(pw, "admin") == null) {
                return pw;
            }
        }
    }
}
