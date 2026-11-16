package egovframework.haengjeongnuli.cmm.audit;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.civildefense.CdMapper;
import egovframework.haengjeongnuli.foreign.ForeignMapper;

/**
 * 보유기간이 지난 개인정보 파기 (개인정보 보호법 제21조: 불필요하게 되면 지체 없이 파기).
 * 민방위 해제 대원, 지원이 끝난 외국인주민. 매일 새벽 실행, 파기 건수는 접속기록에 남긴다.
 */
@Service
public class PrivacyPurgeService {

    private static final Logger log = LoggerFactory.getLogger(PrivacyPurgeService.class);

    private final CdMapper cd;
    private final ForeignMapper foreign;
    private final AuditService audit;

    public PrivacyPurgeService(CdMapper cd, ForeignMapper foreign, AuditService audit) {
        this.cd = cd;
        this.foreign = foreign;
        this.audit = audit;
    }

    public record Purged(int civilDefense, int foreignResidents) {}

    @Scheduled(cron = "${app.privacy.purge-cron:0 40 3 * * *}")
    public void scheduled() {
        Purged p = purge("system");
        if (p.civilDefense() + p.foreignResidents() > 0) {
            log.info("보유기간이 지난 개인정보를 파기했습니다: 민방위 {}명, 외국인주민 {}명", p.civilDefense(), p.foreignResidents());
        }
    }

    @Transactional
    public Purged purge(String actorId) {
        LocalDate today = LocalDate.now();
        cd.purgeAttends(today);
        int a = cd.purgeMembers(today);
        foreign.purgeSupports(today);
        int b = foreign.purgeResidents(today);
        if (a + b > 0) {
            audit.recordAs(actorId, AuditAction.PRIVACY_PURGE, "민방위 대원 " + a + "명, 외국인주민 " + b + "명", "보유기간 경과");
        }
        return new Purged(a, b);
    }
}
