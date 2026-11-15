package egovframework.haengjeongnuli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.WebApplicationContext;

import egovframework.haengjeongnuli.cmm.file.FileStorage;

/** 업로드 파일 실제 형식 확인 (보안 점검: 7z 를 .txt 로 바꾸면 올라가던 문제) */
@SpringBootTest
class FileTypeCheckTest {

    @Autowired FileStorage storage;
    @Autowired WebApplicationContext ctx;

    private static MockMultipartFile f(String name, byte[] data) {
        return new MockMultipartFile("files", name, "application/octet-stream", data);
    }

    private static byte[] zip(String... entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (String e : entries) {
                z.putNextEntry(new ZipEntry(e));
                z.write("x".getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }

    /** 7z 파일 앞부분 (형식 표지 37 7A BC AF 27 1C 와 이진 자료) */
    private static final byte[] SEVEN_Z = {0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C, 0x00, 0x04,
            (byte) 0x8D, (byte) 0x9B, (byte) 0xD5, 0x0F, 0x00, 0x00, 0x00, 0x00};

    @Test
    void 확장자만_바꾼_파일은_막는다() throws Exception {
        assertThat(storage.check(f("압축.txt", SEVEN_Z))).contains("맞지 않아");
        assertThat(storage.check(f("보고서.pdf", SEVEN_Z))).contains("맞지 않아");
        assertThat(storage.check(f("사진.jpg", "MZ\u0090\u0000".getBytes(StandardCharsets.ISO_8859_1)))).contains("맞지 않아");
        assertThat(storage.check(f("문서.hwp", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)))).contains("맞지 않아");
        // ZIP 이지만 워드 문서 구조가 없으면 docx 가 아니다
        assertThat(storage.check(f("문서.docx", zip("a.txt")))).contains("맞지 않아");
        assertThat(storage.check(f("실행.exe", "MZ".getBytes()))).contains("올릴 수 없는 형식");
        assertThat(storage.check(f("빈.txt", new byte[0]))).contains("빈 파일");
    }

    @Test
    void 실제_형식이_맞으면_올린다() throws Exception {
        assertThat(storage.check(f("메모.txt", "안녕하세요\r\n두 번째 줄\t탭".getBytes(StandardCharsets.UTF_8)))).isNull();
        assertThat(storage.check(f("목록.csv", "이름,부서\n홍길동,총무과\n".getBytes(Charset.forName("MS949"))))).isNull();
        assertThat(storage.check(f("보고서.pdf", "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII)))).isNull();
        assertThat(storage.check(f("사진.png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0}))).isNull();
        assertThat(storage.check(f("문서.hwp", new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1}))).isNull();
        assertThat(storage.check(f("문서.docx", zip("[Content_Types].xml", "word/document.xml")))).isNull();
        assertThat(storage.check(f("문서.hwpx", zip("mimetype", "Contents/section0.xml")))).isNull();
        assertThat(storage.check(f("묶음.zip", zip("a.txt")))).isNull();
    }

    @Test
    void 저장할_때도_다시_확인한다() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> storage.save(f("압축.txt", SEVEN_Z)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 화면에서_올리면_안내와_함께_막힌다() throws Exception {
        TestUsers.mvcAs(ctx, TestUsers.general()).perform(multipart("/board/upload").file(f("압축.txt", SEVEN_Z))
                        .param("board", "archive").param("scope", "전체 공개").param("category", "업무 서식")
                        .param("title", "자료").param("content", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("파일 내용이 확장자(txt)와 맞지 않아")));
        MockMultipartFile[] many = new MockMultipartFile[11];
        var req = multipart("/board/upload");
        for (int i = 0; i < 11; i++) {
            req.file(f("메모" + i + ".txt", "글".getBytes(StandardCharsets.UTF_8)));
        }
        TestUsers.mvcAs(ctx, TestUsers.general()).perform(req.param("board", "archive").param("scope", "전체 공개")
                        .param("category", "업무 서식").param("title", "자료").param("content", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("10개까지")));
    }
}
