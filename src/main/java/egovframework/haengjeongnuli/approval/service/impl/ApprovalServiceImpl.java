package egovframework.haengjeongnuli.approval.service.impl;

import java.io.IOException;
import java.time.LocalDate;
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
import egovframework.haengjeongnuli.approval.service.ApprovalProxyVO;
import egovframework.haengjeongnuli.approval.service.ApprovalSearchVO;
import egovframework.haengjeongnuli.approval.service.ApprovalService;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.file.FileStorage;
import egovframework.haengjeongnuli.cmm.notification.NotificationService;
import egovframework.haengjeongnuli.finance.service.FinanceService;
import egovframework.haengjeongnuli.user.service.UserService;

@Service("approvalService")
public class ApprovalServiceImpl implements ApprovalService {

    static final List<String> BOX_IDS = List.of("wait", "prog", "done", "back", "recv", "temp");

    private final ApprovalMapper mapper;
    private final UserService userService;
    private final FileStorage fileStorage;
    private final NotificationService notificationService;
    private final FinanceService finance;
    private final egovframework.haengjeongnuli.records.service.RecordService records;

    public ApprovalServiceImpl(ApprovalMapper mapper, UserService userService, FileStorage fileStorage,
                               NotificationService notificationService, FinanceService finance,
                               egovframework.haengjeongnuli.records.service.RecordService records) {
        this.records = records;
        this.mapper = mapper;
        this.userService = userService;
        this.fileStorage = fileStorage;
        this.notificationService = notificationService;
        this.finance = finance;
    }

    @Override
    public Map<String, String> validate(ApprovalForm f, boolean submit, LoginUser me, Long docId) {
        Map<String, String> err = new LinkedHashMap<>();
        if (blank(f.getTitle())) {
            err.put("title", "제목을 입력하세요.");
        } else if (f.getTitle().trim().length() > 300) {
            err.put("title", "제목은 300자 이내로 입력하세요.");
        }
        if (!ApprovalForm.isValidKind(f.getKind())) {
            err.put("kind", "문서 종류를 고르세요.");
        }
        if (f.getRetention() == null || !ApprovalForm.RETENTIONS.contains(f.getRetention())) {
            err.put("retention", "보존기간을 고르세요.");
        }
        if (f.getDisclosure() == null || !ApprovalForm.DISCLOSURES.contains(f.getDisclosure())) {
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
        List<String> cooperators = f.realCooperators();
        if (cooperators.size() > ApprovalForm.MAX_COOPERATORS) {
            err.put("reviewers", "협조자는 " + ApprovalForm.MAX_COOPERATORS + "명까지 지정할 수 있습니다.");
        }
        List<String> all = new ArrayList<>(reviewers);
        all.addAll(cooperators);
        if (!blank(f.getFinalApprover())) {
            all.add(f.getFinalApprover().trim());
        }
        Set<String> candidates = approverCandidates(me).stream().map(LoginUser::getLoginId).collect(Collectors.toSet());
        if (all.stream().anyMatch(id -> !candidates.contains(id))) {
            err.put("reviewers", "결재선에는 사용 중인 다른 직원만 넣을 수 있습니다.");
        } else if (new HashSet<>(all).size() != all.size()) {
            err.put("reviewers", "같은 직원을 결재선에 두 번 넣을 수 없습니다.");
        }

        if (f.isSpending()) {
            if (f.getPayMethod() == null || !ApprovalForm.PAY_METHODS.contains(f.getPayMethod())) {
                err.put("payMethod", "지급 방법을 고르세요.");
            }
            if (!blank(f.getPayAmount()) && f.payAmountValue() == null) {
                err.put("payAmount", "지출 금액은 숫자로 입력하세요.");
            }
            if (len(f.getPayee()) > 200) {
                err.put("payee", "채권자(지급받는 곳)는 200자 이내로 입력하세요.");
            }
            if (submit) {
                if (blank(f.getPayee())) {
                    err.put("payee", "채권자(지급받는 곳)를 입력하세요.");
                }
                String problem = finance.checkSpend(f.getBudgetId(), f.payAmountValue(), me.getDeptName(), docId);
                if (problem != null) {
                    err.putIfAbsent(f.getBudgetId() == null ? "budgetId" : "payAmount", problem);
                }
            }
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
            ApprovalLineVO p = d.getPendingLine();
            if (ApprovalDocVO.PROG.equals(d.getStatus()) && p != null && p.isFinalRole()) {
                d.setPendingProxyIds(mapper.activeProxyIds(p.getApproverId(), LocalDate.now()));
            }
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
        if (!d.canAct(me)) {
            return Result.NOT_YOUR_TURN;
        }
        ApprovalLineVO line = d.getPendingLine();
        boolean last = d.getLines().stream().noneMatch(l -> l.getStepNo() == line.getStepNo() + 1);
        if (last && d.isSpending() && spendingProblem(d) != null) {
            return Result.BUDGET_SHORT;
        }
        LocalDateTime now = LocalDateTime.now();
        if (act(d, line, me, ApprovalLineVO.APPROVED, trim(opinion, 1000), now) == 0) {
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
        if (d.isSpending()) {
            // 지출결의는 결재가 끝나면 집행으로 기록한다 (잔액이 모자라면 예외로 결재까지 되돌린다)
            finance.executeFromDoc(docId, d.getTitle(), d.getBudgetId(), d.getPayAmount(), d.getPayee(),
                    d.getDrafterId(), d.getDrafterName(), d.getDrafterDept());
        }
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
     * 결재선 한 칸을 처리한다. 결재권자가 부재 중이면 지정된 직무대리자가 대결하고(규정 제10조),
     * 누가 대결했는지 남긴 뒤 결재권자에게 알린다(사후 보고).
     */
    private int act(ApprovalDocVO d, ApprovalLineVO line, LoginUser me, String status, String opinion, LocalDateTime now) {
        if (d.isMyTurn(me)) {
            return mapper.actLine(line.getLineId(), me.getLoginId(), status, opinion, now);
        }
        // 대결자 지정이 지금도 유효한지 다시 확인한다
        if (mapper.countActiveProxy(line.getApproverId(), me.getLoginId(), now.toLocalDate()) == 0) {
            return 0;
        }
        int n = mapper.actLineAsProxy(line.getLineId(), line.getApproverId(), status, opinion, now,
                me.getLoginId(), me.getUserName(), me.getPositionName());
        if (n == 1) {
            notificationService.notify(line.getApproverId(), me.getUserName() + "님이 '" + d.getTitle() + "' 문서를 "
                    + (ApprovalLineVO.APPROVED.equals(status) ? "대결" : "대결로 반려") + "했습니다. 내용을 확인해 주세요.",
                    link(d.getDocId()));
        }
        return n;
    }

    /**
     * 생산등록번호: 처리과명-연도별 등록일련번호.
     * 결재가 끝난 뒤 부여하고(공공기록물법 시행규칙 제4조 제1호), 문서에는 처리과 기관코드 대신
     * 처리과명을 표기한다(같은 규칙 제5조 제3항).
     */
    private String assignNumber(ApprovalDocVO d, int year) {
        String dept = deptKey(d.getDrafterDept());
        int seq = records.nextSeq(dept, year);
        String no = dept + "-" + seq;
        mapper.updateDocNumber(d.getDocId(), year, seq, no);
        // 기록물 등록대장에도 생산 등록으로 남긴다
        records.fromApproval(egovframework.haengjeongnuli.records.service.RecordVO.PRODUCE, no, year, dept, d.getTitle(),
                LocalDateTime.now(), d.getReceiver(), null, d.getRetention(), d.getDisclosure(), d.getDocId(),
                d.getDrafterId(), d.getDrafterName());
        return no;
    }

    private static String deptKey(String dept) {
        return egovframework.haengjeongnuli.records.service.RecordService.deptKey(dept);
    }

    @Override
    public String spendingProblem(long docId) {
        ApprovalDocVO d = get(docId);
        return d == null ? null : spendingProblem(d);
    }

    private String spendingProblem(ApprovalDocVO d) {
        if (!d.isSpending()) {
            return null;
        }
        return finance.checkSpend(d.getBudgetId(), d.getPayAmount(), d.getDrafterDept(), d.getDocId());
    }

    @Override
    @Transactional
    public Result reject(long docId, String opinion, LoginUser me) {
        ApprovalDocVO d = get(docId);
        if (d == null) {
            return Result.NOT_FOUND;
        }
        if (!d.canAct(me)) {
            return Result.NOT_YOUR_TURN;
        }
        LocalDateTime now = LocalDateTime.now();
        if (act(d, d.getPendingLine(), me, ApprovalLineVO.REJECTED, trim(opinion, 1000), now) == 0) {
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
        String receiptNo = dept + "-" + records.nextSeq(dept, now.getYear());
        if (mapper.markReceived(docId, me.getLoginId(), now, receiptNo) != 1) {
            throw new IllegalStateException("이미 접수된 문서입니다");
        }
        // 기록물 등록대장에 접수 등록으로 남긴다 (발신: 기안 부서, 상대 문서번호: 생산등록번호)
        records.fromApproval(egovframework.haengjeongnuli.records.service.RecordVO.RECEIVE, receiptNo, now.getYear(), dept,
                d.getTitle(), now, deptKey(d.getDrafterDept()), d.getDocNo(), d.getRetention(), d.getDisclosure(), docId,
                me.getLoginId(), me.getUserName());
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
        d.setDelegatedYn(f.isDelegated() && !blank(f.getFinalApprover()) ? "Y" : "N");
        boolean spending = f.isSpending();
        d.setBudgetId(spending ? f.getBudgetId() : null);
        d.setPayAmount(spending ? f.payAmountValue() : null);
        d.setPayee(spending ? trim(f.getPayee(), 200) : null);
        d.setPayMethod(spending ? f.getPayMethod() : null);
    }

    /** 검토자들, 협조자들, 결재권자 차례. 결재권자가 비어 있으면(임시저장) 검토·협조만 둔다. */
    private void saveLines(long docId, ApprovalForm f, LoginUser me) {
        Map<String, LoginUser> users = approverCandidates(me).stream()
                .collect(Collectors.toMap(LoginUser::getLoginId, Function.identity()));
        List<String[]> steps = new ArrayList<>();
        f.realReviewers().forEach(id -> steps.add(new String[] {id, ApprovalLineVO.ROLE_REVIEW}));
        f.realCooperators().forEach(id -> steps.add(new String[] {id, ApprovalLineVO.ROLE_COOP}));
        if (!blank(f.getFinalApprover())) {
            steps.add(new String[] {f.getFinalApprover().trim(), ApprovalLineVO.ROLE_FINAL});
        }
        for (int i = 0; i < steps.size(); i++) {
            LoginUser u = users.get(steps.get(i)[0]);
            ApprovalLineVO l = new ApprovalLineVO();
            l.setLineId(mapper.nextLineId());
            l.setDocId(docId);
            l.setStepNo(i + 1);
            l.setRole(steps.get(i)[1]);
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
        if (line.isFinalRole()) {
            for (String proxy : mapper.activeProxyIds(line.getApproverId(), LocalDate.now())) {
                notificationService.notify(proxy, line.getApproverName() + "님 부재 중 대결할 문서가 왔습니다: '"
                        + d.getTitle() + "'", link(d.getDocId()));
            }
        }
    }

    @Override
    public List<ApprovalProxyVO> proxies(LoginUser me) {
        return mapper.selectProxies(me.getLoginId(), LocalDate.now().minusDays(30));
    }

    @Override
    @Transactional
    public String addProxy(LoginUser me, String proxyLoginId, LocalDate start, LocalDate end, String reason) {
        LocalDate today = LocalDate.now();
        if (blank(proxyLoginId) || start == null || end == null) {
            return "대결자와 기간을 모두 입력하세요.";
        }
        LoginUser p = approverCandidates(me).stream().filter(u -> u.getLoginId().equals(proxyLoginId.trim()))
                .findFirst().orElse(null);
        if (p == null) {
            return "대결자는 사용 중인 다른 직원에서 고르세요.";
        }
        if (end.isBefore(start) || end.isBefore(today)) {
            return "기간을 확인하세요 (끝나는 날은 시작일과 오늘 이후).";
        }
        if (start.isAfter(today.plusYears(1)) || java.time.temporal.ChronoUnit.DAYS.between(start, end) > 365) {
            return "대결자는 1년 안의 기간으로만 지정할 수 있습니다.";
        }
        if (reason == null || !ApprovalProxyVO.REASONS.contains(reason)) {
            return "부재 사유를 고르세요.";
        }
        if (mapper.countOverlapProxy(me.getLoginId(), start, end) > 0) {
            return "이 기간과 겹치는 대결자 지정이 있습니다. 먼저 취소하세요.";
        }
        ApprovalProxyVO v = new ApprovalProxyVO();
        v.setProxyId(mapper.nextProxyId());
        v.setOwnerId(me.getLoginId());
        v.setOwnerName(me.getUserName());
        v.setProxyLoginId(p.getLoginId());
        v.setProxyName(p.getUserName());
        v.setStartDate(start);
        v.setEndDate(end);
        v.setReason(reason);
        v.setCreatedAt(LocalDateTime.now());
        mapper.insertProxy(v);
        notificationService.notify(p.getLoginId(), me.getUserName() + "님이 " + start + "~" + end + " 동안 대결자로 지정했습니다 ("
                + reason + ").", "/approval/proxy");
        return null;
    }

    @Override
    @Transactional
    public boolean cancelProxy(LoginUser me, long proxyId) {
        return mapper.cancelProxy(proxyId, me.getLoginId()) == 1;
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
