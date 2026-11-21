package egovframework.haengjeongnuli.approval.service;

import java.util.List;

/** 시험·안내용: 기본 서식 목록을 [이름, 종류, 보존기간, 제목, 본문] 으로 */
public final class BuiltInTemplatesAccess {

    private BuiltInTemplatesAccess() {}

    public static List<String[]> all() {
        return BuiltInTemplates.ALL.stream().map(t -> new String[] {t.name(), t.kind(), t.retention(), t.title(), t.body()}).toList();
    }
}
