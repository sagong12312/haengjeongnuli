package egovframework.haengjeongnuli.cmm.text;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** 엑셀에서 바로 열리는 CSV (UTF-8 BOM, CRLF, 수식으로 읽힐 값 막기) */
public final class Csv {

    private final StringBuilder sb = new StringBuilder("﻿");

    private Csv() {}

    public static Csv of(String... headers) {
        return new Csv().row((Object[]) headers);
    }

    public Csv row(Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cell(values[i]));
        }
        sb.append("\r\n");
        return this;
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    public ResponseEntity<byte[]> download(String fileName) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 쉼표·따옴표·줄바꿈은 감싸고, 글자 값이 =,+,-,@ 로 시작하면 앞에 ' 를 붙인다 (숫자는 그대로) */
    public static String cell(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof Number) {
            return v.toString();
        }
        String t = v.toString();
        if (!t.isEmpty() && "=+-@\t\r".indexOf(t.charAt(0)) >= 0) {
            t = "'" + t;
        }
        if (t.contains(",") || t.contains("\"") || t.contains("\n") || t.contains("\r")) {
            t = "\"" + t.replace("\"", "\"\"") + "\"";
        }
        return t;
    }
}
