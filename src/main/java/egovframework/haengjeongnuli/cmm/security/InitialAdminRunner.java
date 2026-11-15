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
 * 비밀번호는 환경변수 APP_INIT_ADMIN_PASSWORD 로 정할 수 있고, 없으면 무작위로 만들어 서버 창에 한 번만 보여준다.
 */
@Component
public class InitialAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminRunner.class);
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final UserService userService;
    private final String configuredPassword;
    private final boolean requirePassword;

    public InitialAdminRunner(UserService userService,
                              @Value("${app.init-admin.password:}") String configuredPassword,
                              @Value("${app.init-admin.require-password:false}") boolean requirePassword) {
        this.userService = userService;
        this.configuredPassword = configuredPassword;
        this.requirePassword = requirePassword;
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
            log.warn("\n\n==========================================================\n"
                    + "  첫 관리자 계정을 만들었습니다. 지금 한 번만 보여줍니다.\n"
                    + "    아이디   : admin\n"
                    + "    비밀번호 : {}\n"
                    + "  로그인한 뒤 '내 정보 > 비밀번호 변경'에서 바꾸세요.\n"
                    + "==========================================================\n", password);
        } else {
            log.warn("첫 관리자 계정(admin)을 APP_INIT_ADMIN_PASSWORD 로 만들었습니다.");
        }
    }

    /** 영문·숫자 14자 + 특수문자·숫자 (비밀번호 규칙 충족) */
    static String randomPassword() {
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 14; i++) {
            sb.append(CHARS.charAt(r.nextInt(CHARS.length())));
        }
        return sb.append('!').append(r.nextInt(10)).toString();
    }
}
