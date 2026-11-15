package egovframework.haengjeongnuli.main.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 로그인 연장: 요청만으로 세션의 마지막 사용 시각이 바뀐다 */
@Controller
public class SessionController {

    @GetMapping("/session/ping")
    public ResponseEntity<Void> ping() {
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
