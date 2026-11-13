package egovframework.haengjeongnuli.cmm.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileStorageTest {

    @TempDir
    Path dir;

    @Test
    void 경로를_떼고_이름만_남긴다() {
        assertThat(FileStorage.cleanName("C:\\Users\\a\\보고서.hwp")).isEqualTo("보고서.hwp");
        assertThat(FileStorage.cleanName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(FileStorage.cleanName("")).isEqualTo("file");
    }

    @Test
    void 허용_확장자만_받는다() throws Exception {
        FileStorage s = new FileStorage(dir.toString(), "hwp,pdf");
        assertThat(s.isAllowed("a.HWP")).isTrue();
        assertThat(s.isAllowed("a.pdf")).isTrue();
        assertThat(s.isAllowed("a.exe")).isFalse();
        assertThat(s.isAllowed("a.pdf.exe")).isFalse();
        assertThat(s.isAllowed("noext")).isFalse();
    }

    @Test
    void 저장_이름이_아니면_경로를_만들지_않는다() throws Exception {
        FileStorage s = new FileStorage(dir.toString(), "hwp");
        assertThatThrownBy(() -> s.path("../secret")).isInstanceOf(IllegalArgumentException.class);
        assertThat(s.path("0123456789abcdef0123456789abcdef")).startsWithRaw(dir.toAbsolutePath().normalize());
    }
}
