package egovframework.haengjeongnuli.cmm.mark;

import java.nio.charset.StandardCharsets;

import org.springframework.core.MethodParameter;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 모든 엑셀(CSV) 내려받기에 내려받은 사람마다 다른 보이지 않는 추적 번호를 심는다.
 * 화면마다 따로 고칠 필요 없이, text/csv 로 나가는 응답을 여기서 한 번에 처리한다.
 */
@ControllerAdvice
public class CsvMarkAdvice implements ResponseBodyAdvice<Object> {

    private final DownloadMarkService marks;

    public CsvMarkAdvice(DownloadMarkService marks) {
        this.marks = marks;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (!(body instanceof byte[] bytes) || contentType == null || !"text".equals(contentType.getType())
                || !"csv".equals(contentType.getSubtype())) {
            return body;
        }
        String fileName = null;
        String cd = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        if (cd != null) {
            try {
                fileName = ContentDisposition.parse(cd).getFilename();
            } catch (IllegalArgumentException e) {
                fileName = null;
            }
        }
        String code = marks.issue("CSV", fileName);
        if (code == null) {
            return body;
        }
        byte[] marked = DownloadMark.markCsv(new String(bytes, StandardCharsets.UTF_8), code).getBytes(StandardCharsets.UTF_8);
        response.getHeaders().setContentLength(marked.length);
        return marked;
    }
}
