package egovframework.haengjeongnuli.records.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import egovframework.haengjeongnuli.approval.service.ApprovalForm;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.file.FileStorage;
import egovframework.haengjeongnuli.cmm.notification.NotificationService;
import egovframework.haengjeongnuli.records.service.impl.RecordMapper;
import egovframework.haengjeongnuli.user.service.UserService;

/**
 * 기록물 등록 (공공기록물법 시행령 제20조, 시행규칙 제4조·제5조).
 * <ul>
 *   <li>생산·접수 등록번호: 처리과명-연도별 등록일련번호. 전자결재와 같은 번호대를 쓴다.</li>
 *   <li>생산 문서는 결재가 끝난 뒤, 접수 문서는 접수와 동시에 번호를 부여한다.</li>
 *   <li>등록정보는 고치거나 지우지 않는다 (시행령 제20조 제2항). 잘못 등록했으면 비고에 사유를 적어 새로 등록한다.</li>
 * </ul>
 */
@Service
public class RecordService {

    public record Result(boolean ok, String message, Long id) {}

    private final RecordMapper mapper;
    private final FileStorage fileStorage;
    private final UserService userService;
    private final NotificationService notificationService;

    public RecordService(RecordMapper mapper, FileStorage fileStorage, UserService userService,
                         NotificationService notificationService) {
        this.mapper = mapper;
        this.fileStorage = fileStorage;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    /** 처리과 이름 (부서가 없으면 기관 이름) */
    public static String deptKey(String dept) {
        return dept == null || dept.isBlank() ? "행정누리" : dept.trim();
    }

    /** 처리과·연도별 다음 등록일련번호. 행 잠금으로 겹치지 않게 한다 (호출하는 쪽 트랜잭션 안에서) */
    @Transactional
    public int nextSeq(String dept, int year) {
        Integer last = mapper.lockCounter(dept, year);
        if (last == null) {
            try {
                mapper.insertCounter(dept, year, mapper.maxApprovalSeq(dept, year));
            } catch (DuplicateKeyException e) {
                // 다른 요청이 먼저 만들었다
            }
            last = mapper.lockCounter(dept, year);
        }
        int next = last + 1;
        mapper.updateCounter(dept, year, next);
        return next;
    }

    /** 전자결재로 생산·접수된 문서를 등록대장에 올린다 */
    @Transactional
    public void fromApproval(String type, String regNo, int year, String dept, String title, LocalDateTime at,
                             String counterpart, String counterpartNo, String retention, String disclosure, long docId,
                             String byId, String byName) {
        RecordVO r = new RecordVO();
        r.setRegId(mapper.nextId());
        r.setRegType(type);
        r.setRegNo(regNo);
        r.setRegYear(year);
        r.setDeptName(deptKey(dept));
        r.setTitle(title);
        r.setRegDate(at.toLocalDate());
        r.setCounterpart(counterpart);
        r.setCounterpartNo(counterpartNo);
        r.setMedium("전자문서");
        r.setRetention(retention);
        r.setDisclosure(disclosure);
        r.setSource("APPROVAL");
        r.setDocId(docId);
        r.setRegisteredBy(byId);
        r.setRegisteredName(byName);
        r.setRegisteredAt(at);
        mapper.insert(r);
    }

    public boolean canView(RecordVO r, LoginUser u) {
        return u.isAdmin() || deptKey(u.getDeptName()).equals(r.getDeptName()) || u.getLoginId().equals(r.getHandlerId())
                || u.getLoginId().equals(r.getRegisteredBy());
    }

    public RecordVO get(long id) {
        RecordVO r = mapper.select(id);
        if (r != null) {
            r.setFiles(mapper.selectFiles(id));
        }
        return r;
    }

    public List<RecordVO> list(LoginUser me, int year, String type, String q) {
        String t = RecordVO.PRODUCE.equals(type) || RecordVO.RECEIVE.equals(type) ? type : null;
        return mapper.selectList(me.isAdmin(), deptKey(me.getDeptName()), me.getLoginId(), year, t,
                q == null || q.isBlank() ? null : q.trim(), 500);
    }

    public RecordFileVO fileForDownload(long fileId, LoginUser me) {
        RecordFileVO f = mapper.selectFile(fileId);
        if (f == null) {
            return null;
        }
        RecordVO r = mapper.select(f.getRegId());
        return r != null && canView(r, me) ? f : null;
    }

    /** 시스템 밖에서 생산·접수한 문서를 직접 등록한다 (우리 부서가 처리과) */
    @Transactional
    public Result register(String type, String title, LocalDate regDate, String counterpart, String counterpartNo,
                           String medium, Integer pages, String retention, String disclosure, String handlerId,
                           String note, List<MultipartFile> files, LoginUser me) throws IOException {
        if (!RecordVO.PRODUCE.equals(type) && !RecordVO.RECEIVE.equals(type)) {
            return fail("생산·접수 구분을 고르세요.");
        }
        boolean receive = RecordVO.RECEIVE.equals(type);
        if (blank(title) || title.trim().length() > 300) {
            return fail("제목을 300자 이내로 입력하세요.");
        }
        if (regDate == null || regDate.isAfter(LocalDate.now()) || regDate.isBefore(LocalDate.now().minusYears(1))) {
            return fail((receive ? "접수일" : "생산일") + "을 최근 1년 안의 날짜로 입력하세요.");
        }
        if (receive && blank(counterpart)) {
            return fail("발신 기관을 입력하세요.");
        }
        if (len(counterpart) > 200 || len(counterpartNo) > 100 || len(note) > 1000) {
            return fail("입력 길이를 확인하세요.");
        }
        if (medium == null || !RecordVO.MEDIA.contains(medium)) {
            return fail("기록물 형태를 고르세요.");
        }
        if (pages != null && (pages < 1 || pages > 99999)) {
            return fail("쪽수를 확인하세요.");
        }
        if (retention == null || !ApprovalForm.RETENTIONS.contains(retention)) {
            return fail("보존기간을 고르세요.");
        }
        if (disclosure == null || !ApprovalForm.DISCLOSURES.contains(disclosure)) {
            return fail("공개 여부를 고르세요.");
        }
        LoginUser handler = null;
        if (!blank(handlerId)) {
            handler = userService.findByLoginId(handlerId.trim());
            if (handler == null || !handler.isEnabled()) {
                return fail("처리 담당자를 직원 목록에서 고르세요.");
            }
        }
        List<MultipartFile> real = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (real.size() > fileStorage.getMaxCount()) {
            return fail("파일은 한 번에 " + fileStorage.getMaxCount() + "개까지 올릴 수 있습니다.");
        }
        for (MultipartFile f : real) {
            String problem = fileStorage.check(f);
            if (problem != null) {
                return fail(problem);
            }
        }

        String dept = deptKey(me.getDeptName());
        int year = regDate.getYear();
        RecordVO r = new RecordVO();
        r.setRegId(mapper.nextId());
        r.setRegType(type);
        r.setRegNo(dept + "-" + nextSeq(dept, year));
        r.setRegYear(year);
        r.setDeptName(dept);
        r.setTitle(title.trim());
        r.setRegDate(regDate);
        r.setCounterpart(blank(counterpart) ? null : counterpart.trim());
        r.setCounterpartNo(blank(counterpartNo) ? null : counterpartNo.trim());
        r.setMedium(medium);
        r.setPages(pages);
        r.setRetention(retention);
        r.setDisclosure(disclosure);
        r.setSource("MANUAL");
        r.setHandlerId(handler == null ? null : handler.getLoginId());
        r.setHandlerName(handler == null ? null : handler.getUserName());
        r.setNote(blank(note) ? null : note.trim());
        r.setRegisteredBy(me.getLoginId());
        r.setRegisteredName(me.getUserName());
        r.setRegisteredAt(LocalDateTime.now());
        mapper.insert(r);

        for (MultipartFile mf : real) {
            String stored = fileStorage.save(mf);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        fileStorage.deleteQuietly(stored);
                    }
                }
            });
            RecordFileVO f = new RecordFileVO();
            f.setFileId(mapper.nextFileId());
            f.setRegId(r.getRegId());
            f.setOriginalName(FileStorage.cleanName(mf.getOriginalFilename()));
            f.setStoredName(stored);
            f.setFileSize(mf.getSize());
            f.setCreatedAt(r.getRegisteredAt());
            mapper.insertFile(f);
        }
        if (handler != null && !handler.getLoginId().equals(me.getLoginId())) {
            notificationService.notify(handler.getLoginId(), (receive ? "접수 문서 " : "문서 ") + r.getRegNo() + " '"
                    + r.getTitle() + "' 처리 담당으로 지정되었습니다.", "/records/" + r.getRegId());
        }
        return new Result(true, (receive ? "접수" : "생산") + " 등록했습니다. 등록번호 " + r.getRegNo()
                + " — 문서에 이 번호를 적어 두세요.", r.getRegId());
    }

    private static Result fail(String m) {
        return new Result(false, m, null);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static int len(String s) {
        return s == null ? 0 : s.length();
    }

    // ===== 등록정보 수정 (시행규칙 제6조: 기록물관리책임자 확인, 수정일자·내용·사유 관리) =====

    public List<RecordFixVO> fixes(long regId) {
        return mapper.selectFixes(regId);
    }

    /** 내가 확인할 수정 요청 (기록물관리책임자만) */
    public List<RecordFixVO> fixesToConfirm(LoginUser me) {
        return me.isRecordsManager() ? mapper.selectOpenFixes(deptKey(me.getDeptName())) : List.of();
    }

    /** 이 직원이 이 기록물의 수정을 요청할 수 있는지: 처리과 직원·등록자·처리 담당자 */
    public boolean canRequestFix(RecordVO r, LoginUser u) {
        return deptKey(u.getDeptName()).equals(r.getDeptName()) || u.getLoginId().equals(r.getRegisteredBy())
                || u.getLoginId().equals(r.getHandlerId());
    }

    /** 이 직원이 이 기록물의 수정을 확인할 수 있는지: 처리과의 기록물관리책임자 */
    public boolean canConfirmFix(RecordVO r, LoginUser u) {
        return u.isRecordsManager() && deptKey(u.getDeptName()).equals(r.getDeptName());
    }

    @Transactional
    public Result requestFix(long regId, String field, String newValue, String reason, LoginUser me) {
        RecordVO r = get(regId);
        if (r == null || !canView(r, me) || !canRequestFix(r, me)) {
            return fail("이 기록물의 수정을 요청할 수 없습니다.");
        }
        if (r.isCanceled()) {
            return fail("등록이 취소된 기록물입니다.");
        }
        if (field == null || !RecordFixVO.FIELDS.containsKey(field)) {
            return fail("고칠 항목을 고르세요.");
        }
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty() || why.length() > 500) {
            return fail("수정 사유를 500자 이내로 적으세요.");
        }
        String v = newValue == null || newValue.isBlank() ? null : newValue.trim();
        String problem = checkValue(field, v, r);
        if (problem != null) {
            return fail(problem);
        }
        String old = currentValue(r, field);
        if (java.util.Objects.equals(old, v)) {
            return fail("지금 값과 같습니다.");
        }
        if (mapper.countOpenFix(regId, field) > 0) {
            return fail("이 항목은 이미 확인을 기다리는 수정 요청이 있습니다.");
        }
        RecordFixVO f = new RecordFixVO();
        f.setFixId(mapper.nextFixId());
        f.setRegId(regId);
        f.setFieldName(field);
        f.setOldValue(old);
        f.setNewValue(v);
        f.setReason(why);
        f.setRequestedBy(me.getLoginId());
        f.setRequestedName(me.getUserName());
        f.setRequestedAt(LocalDateTime.now());
        mapper.insertFix(f);
        int managers = 0;
        for (LoginUser u : userService.list()) {
            if (u.isEnabled() && u.isRecordsManager() && r.getDeptName().equals(deptKey(u.getDeptName()))
                    && !u.getLoginId().equals(me.getLoginId())) {
                notificationService.notify(u.getLoginId(), me.getUserName() + "님이 기록물 " + r.getRegNo() + "의 "
                        + f.getFieldLabel() + " 수정을 요청했습니다. 확인해 주세요.", "/records/" + regId);
                managers++;
            }
        }
        String what = RecordFixVO.CANCEL.equals(field) ? "등록 취소를" : f.getFieldLabel() + " 수정을";
        return new Result(true, what + " 요청했습니다. " + (managers > 0
                ? "처리과 기록물관리책임자가 확인하면 반영됩니다."
                : "처리과(" + r.getDeptName() + ")에 기록물관리책임자가 지정되어 있지 않습니다. 시스템 관리자에게 지정을 요청하세요."), regId);
    }

    @Transactional
    public Result confirmFix(long fixId, boolean approve, LoginUser me) {
        RecordFixVO f = mapper.selectFix(fixId);
        if (f == null || !RecordFixVO.REQUESTED.equals(f.getStatus())) {
            return fail("이미 처리했거나 없는 요청입니다.");
        }
        RecordVO r = get(f.getRegId());
        if (!canConfirmFix(r, me)) {
            return fail("처리과 기록물관리책임자만 확인할 수 있습니다.");
        }
        if (me.getLoginId().equals(f.getRequestedBy())) {
            return fail("자기가 요청한 수정은 확인할 수 없습니다. 다른 기록물관리책임자가 확인해야 합니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (mapper.closeFix(fixId, approve ? RecordFixVO.APPROVED : RecordFixVO.REJECTED, me.getLoginId(),
                me.getUserName(), now) != 1) {
            return fail("이미 처리했거나 없는 요청입니다.");
        }
        if (approve) {
            // 요청 뒤에 같은 칸이 바뀌었으면 반영하지 않는다
            if (!java.util.Objects.equals(currentValue(r, f.getFieldName()), f.getOldValue())) {
                throw new IllegalStateException("등록정보가 요청 뒤에 바뀌었습니다");
            }
            mapper.updateField(r.getRegId(), f.getFieldName(), f.getNewValue(),
                    "pages".equals(f.getFieldName()) && f.getNewValue() != null ? Integer.valueOf(f.getNewValue()) : null);
        }
        notificationService.notify(f.getRequestedBy(), me.getUserName() + "님이 기록물 " + r.getRegNo() + "의 "
                + f.getFieldLabel() + " 수정 요청을 " + (approve ? "확인해 반영했습니다." : "반려했습니다."), "/records/" + r.getRegId());
        return new Result(true, f.getFieldLabel() + " 수정을 " + (approve ? "확인해 반영했습니다." : "반려했습니다."), r.getRegId());
    }

    private static String currentValue(RecordVO r, String field) {
        return switch (field) {
            case "title" -> r.getTitle();
            case "counterpart" -> r.getCounterpart();
            case "counterpartNo" -> r.getCounterpartNo();
            case "medium" -> r.getMedium();
            case "pages" -> r.getPages() == null ? null : String.valueOf(r.getPages());
            case "retention" -> r.getRetention();
            case "disclosure" -> r.getDisclosure();
            case "note" -> r.getNote();
            case RecordFixVO.CANCEL -> r.getCanceledYn();
            default -> null;
        };
    }

    private static String checkValue(String field, String v, RecordVO r) {
        return switch (field) {
            case "title" -> v == null || v.length() > 300 ? "제목을 300자 이내로 적으세요." : null;
            case "counterpart" -> v != null && v.length() > 200 ? "기관 이름은 200자 이내로 적으세요."
                    : (v == null && RecordVO.RECEIVE.equals(r.getRegType()) ? "접수 문서는 발신 기관을 비울 수 없습니다." : null);
            case "counterpartNo" -> v != null && v.length() > 100 ? "문서번호는 100자 이내로 적으세요." : null;
            case "medium" -> v == null || !RecordVO.MEDIA.contains(v) ? "형태를 목록에서 고르세요." : null;
            case "pages" -> v != null && !v.matches("[1-9]\\d{0,4}") ? "쪽수는 1~99999 사이 숫자로 적으세요." : null;
            case "retention" -> v == null || !egovframework.haengjeongnuli.approval.service.ApprovalForm.RETENTIONS.contains(v)
                    ? "보존기간을 목록에서 고르세요." : null;
            case "disclosure" -> v == null || !egovframework.haengjeongnuli.approval.service.ApprovalForm.DISCLOSURES.contains(v)
                    ? "공개 여부를 목록에서 고르세요." : null;
            case "note" -> v != null && v.length() > 1000 ? "비고는 1,000자 이내로 적으세요." : null;
            case RecordFixVO.CANCEL -> !"Y".equals(v) ? "등록 취소 요청이 올바르지 않습니다."
                    : (!r.isManual() ? "전자결재로 등록된 기록물은 취소할 수 없습니다. 문서 자체를 바로잡으려면 새로 기안하세요." : null);
            default -> "고칠 수 없는 항목입니다.";
        };
    }
}
