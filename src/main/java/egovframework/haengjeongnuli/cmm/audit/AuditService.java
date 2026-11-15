package egovframework.haengjeongnuli.cmm.audit;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import egovframework.haengjeongnuli.cmm.LoginUser;

/**
 * 누가·언제·어디서·무엇을 했는지 남긴다.
 * 기록에 실패해도 업무 처리는 막지 않는다 (서버 로그에는 남김).
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditMapper auditMapper;
    private final org.springframework.transaction.support.TransactionTemplate chainTx;

    public AuditService(AuditMapper auditMapper, org.springframework.transaction.PlatformTransactionManager txManager) {
        this.auditMapper = auditMapper;
        // 업무 처리와 따로, 짧은 트랜잭션으로 마지막 해시를 잠그고 이어 붙인다
        this.chainTx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        this.chainTx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 기록 한 줄의 해시: 앞 기록의 해시와 이 기록의 내용(번호·시각·행위자·작업·대상·내용·주소)을 이어 SHA-256.
     * 중간 기록을 고치거나 지우면 그 뒤 기록의 해시가 맞지 않게 된다.
     */
    public static String hash(String prevHash, AuditLogVO v) {
        String sep = "\u001F";
        String text = String.join(sep, nz(prevHash), String.valueOf(v.getLogId()),
                v.getOccurredAt() == null ? "" : v.getOccurredAt().toString(), nz(v.getActorId()),
                v.getAction() == null ? "" : v.getAction().name(), nz(v.getTarget()), nz(v.getDetail()), nz(v.getClientIp()));
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(d);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** 지금 로그인한 직원이 한 일로 기록 */
    public void record(AuditAction action, String target, String detail) {
        recordAs(currentLoginId(), action, target, detail);
    }

    /** 행위자를 직접 지정해 기록 (로그인 실패 등) */
    public void recordAs(String actorId, AuditAction action, String target, String detail) {
        AuditLogVO v = new AuditLogVO();
        // DB 에 저장되는 정밀도(밀리초)로 맞춰야 다시 읽어 해시를 계산할 때 같은 값이 된다
        v.setOccurredAt(LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        v.setActorId(cut(actorId, 50));
        v.setAction(action);
        v.setTarget(cut(target, 200));
        v.setDetail(cut(detail, 1000));
        v.setClientIp(currentIp());
        try {
            chainTx.executeWithoutResult(status -> {
                java.util.Map<String, Object> chain = auditMapper.lockChain();
                String prev = chain == null ? null : (String) chain.get("lastHash");
                v.setLogId(auditMapper.nextLogId());
                v.setPrevHash(prev);
                v.setRowHash(hash(prev, v));
                auditMapper.insertLog(v);
                auditMapper.updateChain(v.getLogId(), v.getRowHash());
            });
        } catch (RuntimeException e) {
            log.error("작업 기록 저장 실패: {} {} {}", action, actorId, target, e);
        }
    }

    public List<AuditLogVO> search(AuditSearchVO search) {
        return auditMapper.selectLogs(search);
    }

    public int count(AuditSearchVO search) {
        return auditMapper.selectLogCount(search);
    }

    private static String currentLoginId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof LoginUser u) {
            return u.getLoginId();
        }
        return a == null ? null : a.getName();
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
            return sra.getRequest().getRemoteAddr();
        }
        return null;
    }

    private static String cut(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }
}
