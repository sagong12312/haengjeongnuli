package egovframework.haengjeongnuli.cmm.hwp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import egovframework.haengjeongnuli.civil.service.CivilComplaintVO;
import egovframework.haengjeongnuli.civil.service.CivilProcessService;
import egovframework.haengjeongnuli.civil.service.ResultType;
import egovframework.haengjeongnuli.building.service.BuildingCaseVO;
import egovframework.haengjeongnuli.building.service.BuildingService;
import egovframework.haengjeongnuli.cmm.LoginUser;
import egovframework.haengjeongnuli.cmm.file.FileStorage;
import egovframework.haengjeongnuli.cmm.org.OrgService;
import egovframework.haengjeongnuli.hygiene.service.FoodBusinessVO;
import egovframework.haengjeongnuli.hygiene.service.HygieneCheckVO;
import egovframework.haengjeongnuli.hygiene.service.HygieneService;

/**
 * 한글 서식함: 관리자가 HWP 서식을 올리면 누름틀 이름을 읽어 두고, 직원은 내려받거나
 * 민원·위생·건축 자료로 누름틀을 채운 HWP를 만든다.
 * 가족관계·기본증명서·주민등록 등·초본처럼 법정 증명서 서식은 이 시스템에서 채워 만들 수 없다(내려받기·보기만):
 * 그런 증명서는 해당 전산 시스템에서만 발급해야 위조 문서가 되지 않는다.
 */
@Service
public class HwpFormService {

    /** 채워 만들기를 막는 법정 증명서 서식 이름 */
    static final Pattern CERTIFICATE = Pattern.compile("가족관계|기본증명|기본등록표|등본|초본|인감|혼인관계|입양관계|친양자|제적");
    private static final DateTimeFormatter KDATE = DateTimeFormatter.ofPattern("yyyy. M. d.");

    private final HwpFormMapper mapper;
    private final FileStorage storage;
    private final OrgService org;
    private final CivilProcessService civil;
    private final HygieneService hygiene;
    private final BuildingService building;
    private final egovframework.haengjeongnuli.user.service.UserService users;

    public HwpFormService(HwpFormMapper mapper, FileStorage storage, OrgService org, CivilProcessService civil,
                          HygieneService hygiene, BuildingService building, egovframework.haengjeongnuli.user.service.UserService users) {
        this.users = users;
        this.mapper = mapper;
        this.storage = storage;
        this.org = org;
        this.civil = civil;
        this.hygiene = hygiene;
        this.building = building;
    }

    public record Result(boolean ok, String message, Long id) {}

    public List<HwpFormVO> list(boolean onlyInUse) { return mapper.selectAll(onlyInUse); }
    public HwpFormVO get(long id) { return mapper.selectById(id); }

    public static boolean isCertificate(String name) {
        return name != null && CERTIFICATE.matcher(name).find();
    }

    @Transactional
    public Result upload(String name, String category, MultipartFile file, LoginUser by) throws IOException {
        if (file == null || file.isEmpty()) {
            return new Result(false, "서식 파일을 고르세요.", null);
        }
        String original = FileStorage.cleanName(file.getOriginalFilename());
        String lower = original.toLowerCase(java.util.Locale.ROOT);
        if (!lower.endsWith(".hwp") && !lower.endsWith(".hwpx")) {
            return new Result(false, "한글 파일(.hwp, .hwpx)만 올릴 수 있습니다.", null);
        }
        String problem = storage.check(file);
        if (problem != null) {
            return new Result(false, problem, null);
        }
        String n = name == null || name.isBlank() ? original.replaceAll("\\.(?i)hwpx?$", "").replace('_', ' ').trim() : name.trim();
        if (n.isEmpty() || n.length() > 100) {
            return new Result(false, "서식 이름을 100자 이내로 입력하세요.", null);
        }
        String cat = category != null && HwpFormVO.CATEGORIES.contains(category) ? category : (isCertificate(n) ? "증명" : "일반");
        List<String> fields = List.of();
        if (lower.endsWith(".hwp")) {
            try (InputStream in = file.getInputStream()) {
                fields = HwpFormFiller.fieldNames(in);
            } catch (Exception e) {
                return new Result(false, "한글 파일로 읽을 수 없습니다. 암호가 걸렸거나 손상된 파일인지 확인하세요.", null);
            }
        }
        String stored = storage.save(file);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        storage.deleteQuietly(stored);
                    }
                }
            });
        }
        HwpFormVO f = new HwpFormVO();
        f.setFormId(mapper.nextId());
        f.setName(n);
        f.setCategory(cat);
        f.setOriginalName(original);
        f.setStoredName(stored);
        f.setFileSize(file.getSize());
        String joined = String.join("\n", fields);
        f.setFields(joined.length() > 1300 ? joined.substring(0, joined.lastIndexOf('\n', 1300)) : joined);
        boolean cert = isCertificate(n) || isCertificate(original);
        f.setFillableYn(cert ? "N" : "Y");
        f.setUploadedBy(by.getLoginId());
        f.setUploadedAt(LocalDateTime.now());
        mapper.insert(f);
        String msg = "서식을 올렸습니다: " + n + ". ";
        if (cert) {
            msg += "법정 증명서 서식이라 내려받기·보기만 됩니다(채워 만들기 안 됨).";
        } else if (fields.isEmpty()) {
            msg += lower.endsWith(".hwpx") ? "HWPX는 내려받기·보기만 됩니다. 채워 만들려면 HWP로 저장해 올리세요."
                    : "누름틀이 없어 내려받기만 됩니다. 한글에서 누름틀을 넣고 이름을 정하면 채워 만들 수 있습니다.";
        } else {
            msg += "누름틀 " + fields.size() + "개: " + String.join(", ", fields);
        }
        return new Result(true, msg, f.getFormId());
    }

    @Transactional
    public boolean setUse(long id, boolean use) {
        return mapper.setUse(id, use ? "Y" : "N") == 1;
    }

    /** 누름틀에 넣을 수 있는 이름과 설명 (관리자가 서식의 누름틀 이름을 이것으로 정한다) */
    public static final Map<String, String> KNOWN = new LinkedHashMap<>();
    static {
        KNOWN.put("기관명", "기관 정보의 행정기관명");
        KNOWN.put("발신명의", "기관 정보의 발신명의");
        KNOWN.put("작성일", "오늘 (2026. 11. 16.)");
        KNOWN.put("담당자", "서식을 만드는 직원 이름");
        KNOWN.put("담당부서", "서식을 만드는 직원 부서");
        KNOWN.put("담당자직급", "서식을 만드는 직원 직급");
        KNOWN.put("담당자전화", "서식을 만드는 직원 내선번호");
        KNOWN.put("직원성명", "고른 직원(본인, 관리자는 아무 직원) 이름");
        KNOWN.put("직원부서", "고른 직원 부서");
        KNOWN.put("직원직급", "고른 직원 직급");
        KNOWN.put("직렬", "고른 직원 직렬");
        KNOWN.put("직원번호", "고른 직원 직원번호");
        KNOWN.put("임용일", "고른 직원 임용일");
        KNOWN.put("근속기간", "임용일부터 오늘까지 (예: 3년 2개월)");
        KNOWN.put("내선번호", "고른 직원 내선번호");
        KNOWN.put("민원번호", "민원 접수번호");
        KNOWN.put("접수일", "민원 접수일");
        KNOWN.put("민원종류", "질의·건의·법정민원 등");
        KNOWN.put("성명", "민원인·대표자·건축주");
        KNOWN.put("주소", "민원인 주소·소재지·대지위치");
        KNOWN.put("연락처", "민원인 휴대전화(없으면 집전화)");
        KNOWN.put("팩스", "민원인 팩스");
        KNOWN.put("제목", "민원 제목");
        KNOWN.put("민원내용", "민원 내용");
        KNOWN.put("처리결과", "민원 처리 결과");
        KNOWN.put("처리구분", "수용·불수용 등");
        KNOWN.put("처리일", "민원 처리 완료일");
        KNOWN.put("처리기한", "민원 처리기한");
        KNOWN.put("업소명", "식품 영업소 이름");
        KNOWN.put("대표자", "영업소 대표자");
        KNOWN.put("소재지", "영업소 주소");
        KNOWN.put("영업종류", "일반음식점 등");
        KNOWN.put("신고번호", "영업 신고번호");
        KNOWN.put("신고일", "영업 신고일");
        KNOWN.put("점검일", "최근 위생 점검일");
        KNOWN.put("점검결과", "최근 점검 결과");
        KNOWN.put("위반내용", "최근 점검의 위반 내용");
        KNOWN.put("조치내용", "최근 점검의 조치 내용");
        KNOWN.put("접수번호", "건축 접수번호");
        KNOWN.put("건축주", "건축주");
        KNOWN.put("대지위치", "건축 대지위치");
        KNOWN.put("용도", "건축물 용도");
        KNOWN.put("연면적", "연면적(㎡)");
        KNOWN.put("층수", "층수");
        KNOWN.put("허가일", "허가(수리)일");
        KNOWN.put("착수기한", "공사 착수기한");
    }

    /** 채울 값: 공통 값 + 고른 자료(민원·영업소·건축)의 값. 볼 수 없는 자료면 null */
    public Map<String, String> values(String source, Long sourceId, LoginUser me) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("기관명", nz(org.get("orgName")));
        v.put("발신명의", nz(org.get("senderTitle")));
        v.put("작성일", LocalDate.now().format(KDATE));
        v.put("담당자", me.getUserName());
        v.put("담당부서", me.dept());
        v.put("담당자직급", nz(me.getPositionName()));
        v.put("담당자전화", nz(me.getExtension()));
        if ("staff".equals(source)) {
            return staffValues(v, sourceId, me);
        }
        if (source == null || sourceId == null) {
            return v;
        }
        switch (source) {
            case "civil" -> {
                CivilComplaintVO c = civil.get(sourceId);
                if (c == null || !civil.canView(c, me)) {
                    return null;
                }
                v.put("민원번호", c.getReceiptNo());
                v.put("접수일", c.getReceivedAt() == null ? "" : c.getReceivedAt().toLocalDate().format(KDATE));
                v.put("민원종류", c.getKind() == null ? "" : c.getKind().getLabel());
                v.put("성명", c.getApplicantName());
                v.put("주소", nz(c.getAddress()));
                v.put("연락처", nz(c.getMobile() != null ? c.getMobile() : c.getHomePhone()));
                v.put("팩스", nz(c.getFaxNo()));
                v.put("제목", c.getTitle());
                v.put("민원내용", nz(c.getContent()));
                v.put("처리결과", nz(c.getResultText()));
                v.put("처리구분", resultLabel(c.getResultType()));
                v.put("처리일", c.getCompletedAt() == null ? "" : c.getCompletedAt().toLocalDate().format(KDATE));
                v.put("처리기한", c.getDeadlineAt() == null ? "" : c.getDeadlineAt().toLocalDate().format(KDATE));
                if (c.getDept() != null) {
                    v.put("담당부서", c.getDept());
                }
            }
            case "hygiene" -> {
                FoodBusinessVO b = hygiene.get(sourceId);
                if (b == null) {
                    return null;
                }
                v.put("업소명", b.getBizName());
                v.put("대표자", b.getOwnerName());
                v.put("성명", b.getOwnerName());
                v.put("소재지", b.getAddress());
                v.put("주소", b.getAddress());
                v.put("영업종류", b.getBizType());
                v.put("신고번호", nz(b.getReceiptNo()));
                v.put("신고일", b.getReportedOn() == null ? "" : b.getReportedOn().format(KDATE));
                List<HygieneCheckVO> checks = hygiene.checks(sourceId);
                HygieneCheckVO last = checks.stream().max(java.util.Comparator.comparing(HygieneCheckVO::getCheckedOn)).orElse(null);
                if (last != null) {
                    v.put("점검일", last.getCheckedOn().format(KDATE));
                    v.put("점검결과", nz(last.getResult()));
                    v.put("위반내용", nz(last.getViolation()));
                    v.put("조치내용", nz(last.getAction()));
                }
            }
            case "building" -> {
                BuildingCaseVO b = building.get(sourceId);
                if (b == null) {
                    return null;
                }
                v.put("접수번호", nz(b.getReceiptNo()));
                v.put("건축주", b.getOwnerName());
                v.put("성명", b.getOwnerName());
                v.put("대지위치", b.getSite());
                v.put("주소", b.getSite());
                v.put("용도", nz(b.getBuildingUse()));
                v.put("연면적", b.getFloorArea() == null ? "" : b.getFloorArea() + "㎡");
                v.put("층수", b.getFloors() == null ? "" : b.getFloors() + "층");
                v.put("허가일", b.getDecidedOn() == null ? "" : b.getDecidedOn().format(KDATE));
                v.put("착수기한", b.getStartDeadline() == null ? "" : b.getStartDeadline().format(KDATE));
            }
            default -> {
                return null;
            }
        }
        return v;
    }

    /**
     * 직원 정보: 본인 것은 누구나, 다른 직원 것은 관리자만 (개인정보이므로 채운 기록을 남긴다).
     * staffId 가 없으면 본인
     */
    private Map<String, String> staffValues(Map<String, String> v, Long staffUserId, LoginUser me) {
        LoginUser s = staffUserId == null || staffUserId.equals(me.getUserId()) ? me
                : users.list().stream().filter(u -> staffUserId.equals(u.getUserId())).findFirst().orElse(null);
        if (s == null || (s != me && !me.isAdmin())) {
            return null;
        }
        v.put("직원성명", s.getUserName());
        v.put("성명", s.getUserName());
        v.put("직원부서", nz(s.getDeptName()));
        v.put("직원직급", nz(s.getPositionName()));
        v.put("직렬", nz(s.getJobSeries()));
        v.put("직원번호", nz(s.getEmpNo()));
        v.put("임용일", s.getHiredOn() == null ? "" : s.getHiredOn().format(KDATE));
        v.put("근속기간", s.getHiredOn() == null ? "" : period(s.getHiredOn(), LocalDate.now()));
        v.put("내선번호", nz(s.getExtension()));
        return v;
    }

    static String period(LocalDate from, LocalDate to) {
        java.time.Period p = java.time.Period.between(from, to);
        if (p.isNegative()) {
            return "";
        }
        return (p.getYears() > 0 ? p.getYears() + "년 " : "") + p.getMonths() + "개월";
    }

    /** 누름틀을 채운 HWP */
    public byte[] fill(HwpFormVO f, Map<String, String> values) throws Exception {
        if (!f.isFillable() || !f.isInUse()) {
            throw new IllegalArgumentException("채워 만들 수 없는 서식입니다.");
        }
        try (InputStream in = Files.newInputStream(storage.path(f.getStoredName()))) {
            return HwpFormFiller.fill(in, values);
        }
    }

    private static String resultLabel(String type) {
        try {
            return type == null ? "" : ResultType.valueOf(type).getLabel();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
