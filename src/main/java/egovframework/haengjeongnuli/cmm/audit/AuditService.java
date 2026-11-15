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

    public AuditService(AuditMapper auditMapper) {
        this.auditMapper = auditMapper;
    }

    /** 지금 로그인한 직원이 한 일로 기록 */
    public void record(AuditAction action, String target, String detail) {
        recordAs(currentLoginId(), action, target, detail);
    }

    /** 행위자를 직접 지정해 기록 (로그인 실패 등) */
    public void recordAs(String actorId, AuditAction action, String target, String detail) {
        AuditLogVO v = new AuditLogVO();
        v.setOccurredAt(LocalDateTime.now());
        v.setActorId(cut(actorId, 50));
        v.setAction(action);
        v.setTarget(cut(target, 200));
        v.setDetail(cut(detail, 1000));
        v.setClientIp(currentIp());
        try {
            auditMapper.insertLog(v);
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
