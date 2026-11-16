package egovframework.haengjeongnuli.cmm.file;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.springframework.web.multipart.MultipartFile;

/** 메모리에 있는 파일(예: 전자민원 연동으로 받은 첨부)을 업로드 파일처럼 다루기 위한 감싸개 */
public class BytesFile implements MultipartFile {

    private final String originalName;
    private final byte[] bytes;

    public BytesFile(String originalName, byte[] bytes) {
        this.originalName = originalName;
        this.bytes = bytes;
    }

    @Override public String getName() { return "file"; }
    @Override public String getOriginalFilename() { return originalName; }
    @Override public String getContentType() { return "application/octet-stream"; }
    @Override public boolean isEmpty() { return bytes.length == 0; }
    @Override public long getSize() { return bytes.length; }
    @Override public byte[] getBytes() { return bytes.clone(); }
    @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }

    @Override
    public void transferTo(File dest) throws IOException {
        Files.write(dest.toPath(), bytes);
    }
}
