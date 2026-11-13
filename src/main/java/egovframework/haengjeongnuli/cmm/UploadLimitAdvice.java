package egovframework.haengjeongnuli.cmm;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 올린 파일이 크기 제한을 넘으면 오류 화면 대신 안내 문구와 함께 돌려보낸다 */
@ControllerAdvice
public class UploadLimitAdvice {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(RedirectAttributes ra) {
        ra.addFlashAttribute("message", "파일이 너무 큽니다. 파일당 20MB, 한 번에 100MB까지 올릴 수 있습니다.");
        return "redirect:/board?tab=archive";
    }
}
