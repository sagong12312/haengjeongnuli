package egovframework.haengjeongnuli.approval.service.impl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import egovframework.haengjeongnuli.approval.service.ApprovalDocVO;
import egovframework.haengjeongnuli.approval.service.ApprovalFileVO;
import egovframework.haengjeongnuli.approval.service.ApprovalForm;
import egovframework.haengjeongnuli.approval.service.ApprovalLineVO;
import egovframework.haengjeongnuli.approval.service.ApprovalSearchVO;
import egovframework.haengjeongnuli.approval.service.ApprovalService;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.file.FileStorage;
import egovframework.haengjeongnuli.cmm.notification.NotificationService;
import egovframework.haengjeongnuli.user.service.UserService;

@Service("approvalService")
public class ApprovalServiceImpl implements ApprovalService {

    static final List<String> BOX_IDS = List.of("wait", "prog", "done", "back", "recv", "temp");

    private final ApprovalMapper mapper;
    private final UserService userService;
    private final FileStorage fileStorage;
    private final NotificationService notificationService;

    public ApprovalServiceImpl(ApprovalMapper mapper, UserService userService, FileStorage fileStorage,
                               NotificationService notificationService) {
        this.mapper = mapper;
        this.userService = userService;
        this.fileStorage = fileStorage;
        this.notificationService = notificationService;
    }

    @Override
    public Map<String, String> validate(ApprovalForm f, boolean submit, LoginUser me) {
        Map<String, String> err = new LinkedHashMap<>();
        if (blank(f.getTitle())) {
            err.put("title", "제목을 입력하세요.");
        } else if (f.getTitle().trim().length() > 300) {
            err.put("title", "제목은 300자 이내로 입력하세요.");
        }
        if (!ApprovalForm.KINDS.contains(f.getKind())) {
            err.put("kind", "문서 종류를 고르세요.");
        }
        if (!ApprovalForm.RETENTIONS.contains(f.getRetention())) {
            err.put("retention", "보존기간을 고르세요.");
        }
        if (!ApprovalForm.DISCLOSURES.contains(f.getDisclosure())) {
            err.put("disclosure", "공개 여부를 고르세요.");
        }
        if (!"internal".equals(f.getReceiverType()) && !"external".equals(f.getReceiverType())) {
            err.put("receiverType", "수신 구분을 고르세요.");
        }
        if (f.getReason() != null && !ApprovalForm.REASONS.containsAll(f.getReason())) {
            err.put("reason", "비공개 사유를 다시 고르세요.");
        }
        if (len(f.getReasonDetail()) > 500) {
            err.put("reasonDetail", "제한 범위·사유는 500자 이내로 입력하세요.");
        }
        for (String[] p : new String[][] {{"receiver", f.getReceiver()}, {"via", f.getVia()},
                {"reference", f.getReference()}, {"sender", f.getSender()}}) {
            if (len(p[1]) > 200) {
                err.put(p[0], "200자 이내로 입력하세요.");
            }
        }
        if (len(f.getBody()) > 100_000) {
            err.put("body", "본문이 너무 깁니다.");
        }

        List<String> reviewers = f.realReviewers();
        if (reviewers.size() > ApprovalForm.MAX_REVIEWERS) {
            err.put("reviewers", "검토자는 " + ApprovalForm.MAX_REVIEWERS + "명까지 지정할 수 있습니다.");
        }
        List<String> all = new ArrayList<>(reviewers);
        if (!blank(f.getFinalApprover())) {
            all.add(f.getFinalApprover().trim());
        }
        Set<String> candidates = approverCandidates(me).stream().map(LoginUser::getLoginId).collect(Collectors.toSet());
        if (all.stream().anyMatch(id -> !candidates.contains(id))) {
            err.put("reviewers", "결재선에는 사용 중인 다른 직원만 넣을 수 있습니다.");
        } else if (new HashSet<>(all).size() != all.size()) {
            err.put("reviewers", "같은 직원을 결재선에 두 번 넣을 수 없습니다.");
        }

        if (submit) {
            if (blank(f.getBody())) {
                err.put("body", "본문을 입력하세요.");
            }
            if (blank(f.getFinalApprover())) {
                err.putIfAbsent("finalApprover", "결재권자를 지정하세요.");
            }
            if (!"공개".equals(f.getDisclosure()) && (f.getReason() == null || f.getReason().isEmpty())) {
                err.put("reason", "비공개 사유를 하나 이상 고르세요.");
            }
            if ("external".equals(f.getReceiverType()) && blank(f.getReceiver())) {
                err.put("receiver", "수신자를 입력하세요.");
            }
        }
        return err;
    }

    @Override
    @Transactional
    public long create(ApprovalForm f, boolean submit, List<MultipartFile> files, LoginUser me) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        ApprovalDocVO d = new ApprovalDocVO();
        d.setDocId(mapper.nextDocId());
        fill(d, f);
        d.setStatus(ApprovalDocVO.DRAFT);
        d.setDrafterId(me.getLoginId());
        d.setDrafterName(me.getUserName());
        d.setDrafterPosition(me.getPositionName());
        d.setDrafterDept(me.getDeptName());
        d.setCreatedAt(now);
        mapper.insertDoc(d);
        saveLines(d.getDocId(), f, me);
        saveFiles(d.getDocId(), files, now);
        if (submit) {
            submit(d.getDocId(), now);
        }
        return d.getDocId();
    }

    @Override
    @Transactional
    public Result update(long docId, ApprovalForm f, boolean submit, List<MultipartFile> files,
                         List<Long> removeFileIds, LoginUser me) throws IOException {
        ApprovalDocVO d = get(docId);
        if (d == null) {
            return Result.NOT_FOUND;
        }
        if (!d.isDraftedBy(me)) {
            return Result.FORBIDDEN;
        }
        if (!ApprovalDocVO.DRAFT.equals(d.getStatus())) {
            return Result.BAD_STATE;
        }
        LocalDateTime now = LocalDateTime.now();
        fill(d, f);
        d.setUpdatedAt(now);
        if (mapper.updateDocContent(d) == 0) {
            return Result.BAD_STATE;
        }
        mapper.deleteLines(docId);
        saveLines(docId, f, me);

        List<ApprovalFileVO> toRemove = d.getFiles().stream()
                .filter(x -> removeFileIds != null && removeFileIds.contains(x.getFileId())).toList();
        for (ApprovalFileVO x : toRemove) {
            mapper.deleteFile(x.getFileId(), docId);
        }
        saveFiles(docId, files, now);
        List<String> stored = toRemove.stream().map(ApprovalFileVO::getStoredName).toList();
        if (!stored.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    stored.forEach(fileStorage::deleteQuietly);
                }
            });
        }
        if (submit) {
            submit(docId, now);
        }
        return Result.OK;
    }

    /** 상신: 첫 칸을 결재할 차례로 두고 그 사람에게 알린다 */
    private void submit(long docId, LocalDateTime now) {
        mapper.resetLines(docId);
        mapper.updateDocStatus(docId, ApprovalDocVO.DRAFT, ApprovalDocVO.PROG, now);
        mapper.setLineStatus(docId, 1, ApprovalLineVO.PENDING);
        ApprovalDocVO d = get(docId);
        notifyTurn(d, d.getLines().get(0));
    }

    @Override
    public ApprovalDocVO get(long docId) {
        ApprovalDocVO d = mapper.selectDoc(docId);
        if (d != null) {
            d.setLines(mapper.selectLines(docId));
            d.setFiles(mapper.selectFiles(docId));
        }
        return d;
    }

    @Override
    public List<ApprovalDocVO> list(ApprovalSearchVO search) {
        return mapper.selectList(search);
    }

    @Override
    public int count(ApprovalSearchVO search) {
        return mapper.selectCount(search);
    }

    @Override
    public Map<String, Integer> boxCounts(LoginUser me) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String box : BOX_IDS) {
            ApprovalSearchVO s = ApprovalSearchVO.of(box, me);
            if ("done".equals(box)) {
                s.setPeriod("1m");
            }
            out.put(box, mapper.selectCount(s));
        }
        return out;
    }

    @Override
    @Transactional
    public Result approve(long docId, String opinion, LoginUser me) {
        ApprovalDocVO d = get(docId);
        if (d == null) {
            return Result.NOT_FOUND;
        }
        if (!d.isMyTurn(me)) {
            return Result.NOT_YOUR_TURN;
        }
        ApprovalLineVO line = d.getPendingLine();
        LocalDateTime now = LocalDateTime.now();
        if (mapper.actLine(line.getLineId(), me.getLoginId(), ApprovalLineVO.APPROVED, trim(opinion, 1000), now) == 0) {
            return Result.NOT_YOUR_TURN;
        }
        ApprovalLineVO next = d.getLines().stream()
                .filter(l -> l.getStepNo() == line.getStepNo() + 1).findFirst().orElse(null);
        if (next != null) {
            mapper.setLineStatus(docId, next.getStepNo(), ApprovalLineVO.PENDING);
            notifyTurn(d, next);
            return Result.OK;
        }
        // 마지막 결재: 완료하고 문서번호를 붙인다
        if (mapper.updateDocStatus(docId, ApprovalDocVO.PROG, ApprovalDocVO.DONE, now) == 0) {
            throw new IllegalStateException("결재 상태가 바뀌었습니다");
        }
        String docNo = assignNumber(d, now.getYear());
        notificationService.notify(d.getDrafterId(),
                "'" + d.getTitle() + "' 결재가 끝났습니다. (문서번호 " + docNo + ")", link(docId));
        if (d.isExternal() && !blank(d.getReceiver())) {
            String dept = d.getReceiver().trim();
            for (LoginUser u : userService.list()) {
                if (u.isEnabled() && dept.equals(u.getDeptName()) && !u.getLoginId().equals(d.getDrafterId())) {
                    notificationService.notify(u.getLoginId(),
                            "'" + d.getTitle() + "' 문서가 " + dept + "(으)로 왔습니다. 접수해 주세요.", link(docId));
                }
            }
        }
        return Result.OK;
    }

    /**
     * 생산등록번호: 처리과명-연도별 등록일련번호.
     * 결재가 끝난 뒤 부여하고(공공기록물법 시행규칙 제4조 제1호), 문서에는 처리과 기관코드 대신
     * 처리과명을 표기한다(같은 규칙 제5조 제3항).
     */
    private String assignNumber(ApprovalDocVO d, int year) {
        String dept = deptKey(d.getDrafterDept());
        int seq = nextRegSeq(dept, year);
        String no = dept + "-" + seq;
        mapper.updateDocNumber(d.getDocId(), year, seq, no);
        return no;
    }

    /** 처리과·연도별 등록일련번호. 생산·접수 등록이 같은 번호대를 이어 쓴다. 행 잠금으로 겹치지 않게 한다. */
    private int nextRegSeq(String dept, int year) {
        Integer last = mapper.lockRegCounter(dept, year);
        if (last == null) {
            try {
                mapper.insertRegCounter(dept, year, mapper.nextDocSeq(year, dept) - 1);
            } catch (DuplicateKeyException e) {
                // 다른 요청이 먼저 만들었다
            }
            last = mapper.lockRegCounter(dept, year);
        }
        int next = last + 1;
        mapper.updateRegCounter(dept, year, next);
        return next;
    }

    private static String deptKey(String dept) {
        return blank(dept) ? "행정누리" : dept.trim();
    }

    @Override
    @Transactional
    public Result reject(long docId, String opinion, LoginUser me) {
        ApprovalDocVO d = get(docId);
        if (d == null) {
            return Result.NOT_FOUND;
        }
        if (!d.isMyTurn(me)) {
            return Result.NOT_YOUR_TURN;
        }
        LocalDateTime now = LocalDateTime.now();
        if (mapper.actLine(d.getPendingLine().getLineId(), me.getLoginId(), ApprovalLineVO.REJECTED,
                trim(opinion, 1000), now) == 0) {
            return Result.NOT_YOUR_TURN;
        }
        mapper.updateDocStatus(docId, ApprovalDocVO.PROG, ApprovalDocVO.REJECTED, now);
        notificationService.notify(d.getDrafterId(),
                me.getUserName() + "님이 '" + d.getTitle() + "' 문서를 반려했습니다.", link(docId));
        return Result.OK;
    }

    @Override
    @Transactional
    public Result withdraw(long docId, LoginUser me) {
        ApprovalDocVO d = get(docId);
        if (d == null) {
            return Result.NOT_FOUND;
        }
        if (!d.isDraftedBy(me)) {
            return Result.FORBIDDEN;
        }
        if (!d.canWithdraw(me)) {
            return Result.BAD_STATE;
        }
        // 결재자가 같은 순간 처리했다면 회수하지 않는다
        if (mapper.withdrawFirst(docId) == 0) {
            return Result.BAD_STATE;
        }
        mapper.resetLines(docId);
        mapper.updateDocStatus(docId, ApprovalDocVO.PROG, ApprovalDocVO.DRAFT, LocalDateTime.now());
        return Result.OK;
    }

    @Override
    @Transactional
    public Result receive(long docId, LoginUser me) {
        ApprovalDocVO d = get(docId);
        if (d == null) {
            return Result.NOT_FOUND;
        }
        if (!d.isReceiverDept(me)) {
            return Result.FORBIDDEN;
        }
        if (d.getReceivedAt() != null) {
            return Result.BAD_STATE;
        }
        // 접수와 동시에 접수등록번호를 부여한다 (공공기록물법 시행규칙 제4조 제5호)
        LocalDateTime now = LocalDateTime.now();
        String dept = deptKey(me.getDeptName());
        String receiptNo = dept + "-" + nextRegSeq(dept, now.getYear());
        if (mapper.markReceived(docId, me.getLoginId(), now, receiptNo) != 1) {
            throw new IllegalStateException("이미 접수된 문서입니다");
        }
        return Result.OK;
    }

    @Override
    @Transactional
    public Result deleteDraft(long docId, LoginUser me) {
        ApprovalDocVO d = get(docId);
        if (d == null) {
            return Result.NOT_FOUND;
        }
        if (!d.isDraftedBy(me)) {
            return Result.FORBIDDEN;
        }
        return mapper.markDeleted(docId, me.getLoginId()) == 1 ? Result.OK : Result.BAD_STATE;
    }

    @Override
    public ApprovalFileVO fileForDownload(long fileId, LoginUser me) {
        ApprovalFileVO f = mapper.selectFile(fileId);
        if (f == null) {
            return null;
        }
        ApprovalDocVO d = get(f.getDocId());
        return d != null && d.canView(me) ? f : null;
    }

    @Override
    public List<LoginUser> approverCandidates(LoginUser me) {
        return userService.list().stream()
                .filter(u -> u.isEnabled() && !u.getLoginId().equals(me.getLoginId()))
                .toList();
    }

    private void fill(ApprovalDocVO d, ApprovalForm f) {
        boolean open = "공개".equals(f.getDisclosure());
        boolean external = "external".equals(f.getReceiverType());
        d.setKind(f.getKind());
        d.setRetention(f.getRetention());
        d.setDisclosure(f.getDisclosure());
        d.setRestrictReasons(open || f.getReason() == null ? null
                : f.getReason().stream().distinct().sorted().collect(Collectors.joining(",")));
        d.setRestrictDetail(open ? null : trim(f.getReasonDetail(), 500));
        d.setReceiverType(f.getReceiverType());
        d.setReceiver(external ? trim(f.getReceiver(), 200) : null);
        d.setVia(external ? trim(f.getVia(), 200) : null);
        d.setReferenceTo(external ? trim(f.getReference(), 200) : null);
        d.setSender(trim(f.getSender(), 200));
        d.setEffectiveDate(f.getEffectiveDate());
        d.setTitle(f.getTitle().trim());
        d.setBody(f.getBody());
    }

    /** 검토자들 다음에 결재권자. 결재권자가 비어 있으면(임시저장) 검토자만 둔다. */
    private void saveLines(long docId, ApprovalForm f, LoginUser me) {
        Map<String, LoginUser> users = approverCandidates(me).stream()
                .collect(Collectors.toMap(LoginUser::getLoginId, Function.identity()));
        List<String> ids = new ArrayList<>(f.realReviewers());
        boolean hasFinal = !blank(f.getFinalApprover());
        if (hasFinal) {
            ids.add(f.getFinalApprover().trim());
        }
        for (int i = 0; i < ids.size(); i++) {
            LoginUser u = users.get(ids.get(i));
            ApprovalLineVO l = new ApprovalLineVO();
            l.setLineId(mapper.nextLineId());
            l.setDocId(docId);
            l.setStepNo(i + 1);
            l.setRole(hasFinal && i == ids.size() - 1 ? "결재" : "검토");
            l.setApproverId(u.getLoginId());
            l.setApproverName(u.getUserName());
            l.setApproverPosition(u.getPositionName());
            l.setApproverDept(u.getDeptName());
            l.setStatus(ApprovalLineVO.WAIT);
            mapper.insertLine(l);
        }
    }

    private void saveFiles(long docId, List<MultipartFile> files, LocalDateTime now) throws IOException {
        if (files == null) {
            return;
        }
        for (MultipartFile mf : files) {
            if (mf == null || mf.isEmpty()) {
                continue;
            }
            String stored = fileStorage.save(mf);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        fileStorage.deleteQuietly(stored);
                    }
                }
            });
            ApprovalFileVO f = new ApprovalFileVO();
            f.setFileId(mapper.nextFileId());
            f.setDocId(docId);
            f.setOriginalName(FileStorage.cleanName(mf.getOriginalFilename()));
            f.setStoredName(stored);
            f.setFileSize(mf.getSize());
            f.setCreatedAt(now);
            mapper.insertFile(f);
        }
    }

    private void notifyTurn(ApprovalDocVO d, ApprovalLineVO line) {
        notificationService.notify(line.getApproverId(),
                d.getDrafterName() + "님이 올린 '" + d.getTitle() + "' 문서를 " + line.getRole() + "할 차례입니다.",
                link(d.getDocId()));
    }

    private static String link(long docId) {
        return "/approval/" + docId;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static int len(String s) {
        return s == null ? 0 : s.length();
    }

    private static String trim(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
