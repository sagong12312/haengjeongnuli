package egovframework.haengjeongnuli.cmm.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 첨부파일을 디스크에 저장하고 꺼낸다.
 * 저장 이름은 무작위로 만들고 확장자를 붙이지 않는다 (올린 사람이 정한 이름·경로를 디스크에 쓰지 않는다).
 */
@Component
public class FileStorage {

    private final Path root;
    private final Set<String> allowedExtensions;
    /** 한 번에 올릴 수 있는 파일 수 */
    private int maxCount = 10;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setMaxCount(@Value("${app.file.max-count:10}") int maxCount) {
        this.maxCount = maxCount;
    }

    public int getMaxCount() { return maxCount; }

    public FileStorage(@Value("${app.file.upload-dir:./uploads}") String uploadDir,
                       @Value("${app.file.allowed-extensions}") String allowed) throws IOException {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        this.allowedExtensions = Arrays.stream(allowed.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        Files.createDirectories(root);
    }

    /** 확장자 허용 여부 */
    public boolean isAllowed(String originalName) {
        String ext = extension(originalName);
        return ext != null && allowedExtensions.contains(ext);
    }

    public Set<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    /**
     * 올릴 수 있는 파일인지 확인한다: 허용 확장자이고, 내용이 그 확장자의 실제 형식과 맞아야 한다.
     *
     * @return 문제가 있으면 안내 문구, 없으면 null
     */
    public String check(MultipartFile file) {
        String name = cleanName(file.getOriginalFilename());
        String ext = extension(name);
        if (ext == null || !allowedExtensions.contains(ext)) {
            return "올릴 수 없는 형식입니다: " + name;
        }
        if (file.isEmpty()) {
            return "빈 파일은 올릴 수 없습니다: " + name;
        }
        try {
            if (!FileTypeChecker.matches(ext, file::getInputStream)) {
                return "파일 내용이 확장자(" + ext + ")와 맞지 않아 올릴 수 없습니다: " + name;
            }
        } catch (IOException e) {
            return "파일을 읽지 못했습니다: " + name;
        }
        return null;
    }

    /** 파일을 저장하고 저장 이름을 돌려준다. 형식 확인을 통과하지 못한 파일은 저장하지 않는다. */
    public String save(MultipartFile file) throws IOException {
        String problem = check(file);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        String storedName = UUID.randomUUID().toString().replace("-", "");
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
        }
        return storedName;
    }

    public Path path(String storedName) {
        return resolve(storedName);
    }

    public void deleteQuietly(String storedName) {
        try {
            Files.deleteIfExists(resolve(storedName));
        } catch (IOException | IllegalArgumentException ignored) {
            // 정리 실패는 무시 (다음 정리 작업에서 처리)
        }
    }

    /** 저장 폴더 밖을 가리키지 못하게 막는다 */
    private Path resolve(String storedName) {
        if (storedName == null || !storedName.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("잘못된 저장 파일 이름");
        }
        Path p = root.resolve(storedName).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("잘못된 저장 경로");
        }
        return p;
    }

    /** 올린 파일 이름에서 경로를 떼어 내고 길이를 제한한다 */
    public static String cleanName(String original) {
        if (original == null) {
            return "file";
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) {
            return "file";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    static String extension(String name) {
        if (name == null) {
            return null;
        }
        String clean = cleanName(name);
        int dot = clean.lastIndexOf('.');
        return (dot < 0 || dot == clean.length() - 1) ? null : clean.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
