package egovframework.haengjeongnuli.approval.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.approval.service.impl.ApprovalTemplateMapper;
import egovframework.haengjeongnuli.cmm.LoginUser;

/** 기안 서식 */
@Service
public class ApprovalTemplateService {

    private final ApprovalTemplateMapper mapper;

    public ApprovalTemplateService(ApprovalTemplateMapper mapper) {
        this.mapper = mapper;
    }

    public List<ApprovalTemplateVO> visible(LoginUser me) {
        return mapper.selectVisible(me.getLoginId(), me.getDeptName());
    }

    /**
     * 서식 찾기: 이름·제목·본문·문서 종류에서 찾는다. 띄어쓰기와 대소문자는 가리지 않고 (USB허브 = usb 허브),
     * 여러 낱말을 넣으면 모두 들어 있는 서식만. 이름에 들어 있으면 앞에 보여 준다.
     */
    public List<ApprovalTemplateVO> search(LoginUser me, String q) {
        List<ApprovalTemplateVO> all = visible(me);
        if (q == null || q.isBlank()) {
            return all;
        }
        String[] words = q.strip().toLowerCase(java.util.Locale.ROOT).split("\\s+");
        List<ApprovalTemplateVO> inName = new java.util.ArrayList<>();
        List<ApprovalTemplateVO> inBody = new java.util.ArrayList<>();
        for (ApprovalTemplateVO t : all) {
            String name = norm(t.getName());
            String text = name + norm(t.getTitle()) + norm(t.getKind()) + norm(t.getBody());
            boolean all1 = true;
            boolean nameHit = true;
            for (String w : words) {
                String n = norm(w);
                if (n.isEmpty()) {
                    continue;
                }
                if (!text.contains(n)) {
                    all1 = false;
                    break;
                }
                if (!name.contains(n)) {
                    nameHit = false;
                }
            }
            if (all1) {
                (nameHit ? inName : inBody).add(t);
            }
        }
        inName.addAll(inBody);
        return inName;
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", "");
    }

    public boolean canUse(ApprovalTemplateVO t, LoginUser me) {
        return t != null && (me.getLoginId().equals(t.getOwnerId()) || "ALL".equals(t.getScope())
                || ("DEPT".equals(t.getScope()) && t.getDeptName() != null && t.getDeptName().equals(me.getDeptName())));
    }

    public boolean canDelete(ApprovalTemplateVO t, LoginUser me) {
        return t != null && (me.getLoginId().equals(t.getOwnerId()) || me.isAdmin());
    }

    public ApprovalTemplateVO get(long id, LoginUser me) {
        ApprovalTemplateVO t = mapper.selectById(id);
        return canUse(t, me) ? t : null;
    }

    /** 서식을 저장한다. 문제가 있으면 안내 문구 */
    @Transactional
    public String save(String name, String scope, String kind, String retention, String title, String body, LoginUser me) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 100) {
            return "서식 이름을 100자 이내로 입력하세요.";
        }
        if (scope == null || !ApprovalTemplateVO.SCOPES.containsKey(scope)) {
            return "누가 쓸 서식인지 고르세요.";
        }
        if ("ALL".equals(scope) && !me.isAdmin()) {
            return "기관 전체 서식은 시스템 관리자만 만들 수 있습니다.";
        }
        if ("DEPT".equals(scope) && (me.getDeptName() == null || me.getDeptName().isBlank())) {
            return "부서가 정해진 직원만 부서 서식을 만들 수 있습니다.";
        }
        if (!ApprovalForm.isValidKind(kind)) {
            return "문서 종류를 고르세요.";
        }
        if (title != null && title.trim().length() > 300) {
            return "제목은 300자 이내로 입력하세요.";
        }
        if (body != null && body.length() > 100_000) {
            return "본문이 너무 깁니다.";
        }
        if ((title == null || title.isBlank()) && (body == null || body.isBlank())) {
            return "제목이나 본문을 입력한 뒤 서식으로 저장하세요.";
        }
        ApprovalTemplateVO t = new ApprovalTemplateVO();
        t.setTemplateId(mapper.nextId());
        t.setName(n);
        t.setScope(scope);
        t.setOwnerId(me.getLoginId());
        t.setOwnerName(me.getUserName());
        t.setDeptName(me.getDeptName());
        t.setKind(kind);
        t.setRetention(retention != null && ApprovalForm.RETENTIONS.contains(retention) ? retention : null);
        t.setTitle(title == null || title.isBlank() ? null : title.trim());
        t.setBody(body);
        t.setCreatedAt(LocalDateTime.now());
        mapper.insert(t);
        return null;
    }

    @Transactional
    public boolean delete(long id, LoginUser me) {
        ApprovalTemplateVO t = mapper.selectById(id);
        return canDelete(t, me) && mapper.markDeleted(id) == 1;
    }
}
