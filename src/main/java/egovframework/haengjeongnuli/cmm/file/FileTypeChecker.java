package egovframework.haengjeongnuli.cmm.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 파일 내용이 확장자와 맞는지 확인한다 (확장자만 바꾼 파일을 막는다).
 * <ul>
 *   <li>pdf·png·jpg·gif: 파일 앞부분의 형식 표지(시그니처)</li>
 *   <li>hwp·doc·xls·ppt: OLE2 복합 문서 표지 (HWP 3.0은 자체 표지)</li>
 *   <li>zip: ZIP 표지 / docx·xlsx·pptx·hwpx: ZIP 이면서 형식에 꼭 있는 내부 항목이 있어야 한다</li>
 *   <li>txt·csv: NUL 등 제어 문자가 없고 UTF-8 또는 EUC-KR(MS949)로 읽히는 글자여야 한다</li>
 * </ul>
 */
public final class FileTypeChecker {

    private static final byte[] PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF87 = "GIF87a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] GIF89 = "GIF89a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] OLE2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final byte[] HWP3 = "HWP Document File".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP = {'P', 'K', 0x03, 0x04};
    private static final byte[] ZIP_EMPTY = {'P', 'K', 0x05, 0x06};

    /** 글자 파일은 앞부분 이만큼을 검사한다 */
    private static final int TEXT_SAMPLE = 256 * 1024;
    /** ZIP 안 항목은 이만큼까지만 살펴본다 */
    private static final int MAX_ZIP_ENTRIES = 2000;

    private FileTypeChecker() {}

    public interface Source {
        InputStream open() throws IOException;
    }

    /** 확장자(소문자)에 맞는 내용이면 true */
    public static boolean matches(String ext, Source src) throws IOException {
        byte[] head;
        try (InputStream in = src.open()) {
            head = in.readNBytes(TEXT_SAMPLE);
        }
        if (head.length == 0) {
            return false;
        }
        return switch (ext) {
            case "pdf" -> starts(head, PDF);
            case "png" -> starts(head, PNG);
            case "jpg", "jpeg" -> starts(head, JPG);
            case "gif" -> starts(head, GIF87) || starts(head, GIF89);
            case "doc", "xls", "ppt" -> starts(head, OLE2);
            case "hwp" -> starts(head, OLE2) || starts(head, HWP3);
            case "zip" -> starts(head, ZIP) || starts(head, ZIP_EMPTY);
            case "docx" -> starts(head, ZIP) && zipHas(src, "[Content_Types].xml", "word/");
            case "xlsx" -> starts(head, ZIP) && zipHas(src, "[Content_Types].xml", "xl/");
            case "pptx" -> starts(head, ZIP) && zipHas(src, "[Content_Types].xml", "ppt/");
            case "hwpx" -> starts(head, ZIP) && zipHas(src, "mimetype", "Contents/");
            case "txt", "csv" -> isText(head);
            default -> false;
        };
    }

    private static boolean starts(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(Arrays.copyOf(data, prefix.length), prefix);
    }

    /** ZIP 안에 정확한 이름의 항목(exact)과 이름이 prefix 로 시작하는 항목이 모두 있는지 */
    private static boolean zipHas(Source src, String exact, String prefix) throws IOException {
        Set<String> found = new HashSet<>();
        boolean prefixFound = false;
        try (ZipInputStream zin = new ZipInputStream(src.open())) {
            ZipEntry e;
            int n = 0;
            while ((e = zin.getNextEntry()) != null && n++ < MAX_ZIP_ENTRIES) {
                String name = e.getName();
                if (name.equals(exact)) {
                    found.add(exact);
                }
                if (name.startsWith(prefix)) {
                    prefixFound = true;
                }
                if (found.contains(exact) && prefixFound) {
                    return true;
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
        return false;
    }

    /** 사람이 읽는 글자 파일인지: 탭·줄바꿈 외 제어 문자가 없고 UTF-8 이나 MS949 로 읽혀야 한다 */
    static boolean isText(byte[] data) {
        int len = data.length;
        for (int i = 0; i < len; i++) {
            int b = data[i] & 0xFF;
            if (b < 0x20 && b != '\t' && b != '\n' && b != '\r' && b != '\f') {
                return false;
            }
            if (b == 0x7F) {
                return false;
            }
        }
        int start = len >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF ? 3 : 0;
        // 앞부분만 잘라 읽었으면 끝의 글자가 잘렸을 수 있으므로 마지막 3바이트는 빼고 확인한다
        int end = len == TEXT_SAMPLE ? Math.max(start, len - 3) : len;
        return decodes(data, start, end, StandardCharsets.UTF_8) || decodes(data, start, end, Charset.forName("MS949"));
    }

    private static boolean decodes(byte[] data, int from, int to, Charset cs) {
        try {
            cs.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data, from, to - from));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
