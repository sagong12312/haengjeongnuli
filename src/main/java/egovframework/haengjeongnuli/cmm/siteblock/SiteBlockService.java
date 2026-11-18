package egovframework.haengjeongnuli.cmm.siteblock;

import java.net.IDN;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import egovframework.haengjeongnuli.cmm.secapp.SecurityAppService;
import egovframework.haengjeongnuli.cmm.secapp.SecurityAppVO;

/**
 * 유해사이트 차단 (직원 업무용 PC).
 * 웹 화면은 브라우저가 다른 사이트에 가는 것을 막을 수 없으므로, 관리자가 정한 목록으로 PC 에 까는 차단 스크립트를 만든다.
 *  1) 거르는 DNS: 악성코드(·성인) 사이트 주소를 알려 주지 않는 DNS 로 바꾼다 (기본: 1.1.1.1 for Families 1.1.1.3)
 *  2) Chrome·Edge 정책: 관리자가 적은 도메인(도박 등)과 그 하위 주소를 막고, DNS-over-HTTPS 를 꺼서 1)을 우회하지 못하게
 *  3) hosts 파일: 다른 브라우저·프로그램에서도 그 도메인을 이 PC(127.0.0.1)로 돌리고, PC 의 작은 안내 프로그램이 http 접속을 행정누리 차단 안내 화면으로 넘긴다
 * 스크립트는 '유해사이트 차단' 강제 보안 프로그램으로 등록해, 설치하지 않은 직원은 행정누리를 쓰지 못하게 한다.
 */
@Service
public class SiteBlockService {

    public static final String MODE_OFF = "OFF";
    public static final String MODE_MALWARE = "MALWARE";
    public static final String MODE_ADULT = "ADULT";
    public static final String MODE_CUSTOM = "CUSTOM";

    public static final Map<String, String> CATEGORIES = Map.of("GAMBLING", "도박", "ADULT", "성인·음란", "MALWARE", "악성·피싱",
            "NK", "북한·이적 표현물", "PIRACY", "불법 복제·위조", "DRUG", "불법 식품·의약품", "ETC", "기타");
    static final int MAX_RULES = 3000;

    private final SiteBlockMapper mapper;
    private final SecurityAppService apps;
    private final egovframework.haengjeongnuli.cmm.notification.NotificationService notifications;
    private final egovframework.haengjeongnuli.user.service.UserService users;

    public SiteBlockService(SiteBlockMapper mapper, SecurityAppService apps,
                            egovframework.haengjeongnuli.cmm.notification.NotificationService notifications,
                            egovframework.haengjeongnuli.user.service.UserService users) {
        this.mapper = mapper;
        this.apps = apps;
        this.notifications = notifications;
        this.users = users;
    }

    /* ================================================================ 차단 안내 · 항의(이의) 신청 */

    /** 안내 화면에 보여 줄 차단 이유: 목록의 도메인(또는 상위 도메인)이면 그 분류 */
    public String reasonFor(String site) {
        String d = normalize(site);
        if (d == null) {
            return null;
        }
        for (String x = d; x.contains("."); x = x.substring(x.indexOf('.') + 1)) {
            String c = mapper.category(x);
            if (c != null) {
                return CATEGORIES.getOrDefault(c, "기타");
            }
        }
        return null;
    }

    /** 목록에 있는 도메인(하위 주소면 목록의 상위 도메인)을 돌려준다 */
    String listed(String site) {
        String d = normalize(site);
        if (d == null) {
            return null;
        }
        for (String x = d; x.contains("."); x = x.substring(x.indexOf('.') + 1)) {
            if (mapper.category(x) != null) {
                return x;
            }
        }
        return null;
    }

    @Transactional
    public Result appeal(String site, String reason, egovframework.haengjeongnuli.cmm.LoginUser me) {
        String d = normalize(site);
        if (d == null) {
            return new Result(false, "사이트 주소를 알아볼 수 없습니다.");
        }
        String r = reason == null ? "" : reason.replace("\r\n", "\n").trim();
        if (r.length() < 5 || r.length() > 1000) {
            return new Result(false, "업무상 필요한 이유를 5~1,000자로 적어 주세요.");
        }
        if (mapper.waiting(d, me.getLoginId()) > 0) {
            return new Result(false, "이 사이트로 낸 신청을 아직 검토하고 있습니다. 결과는 알림으로 알려 드립니다.");
        }
        long id = mapper.nextAppealId();
        mapper.insertAppeal(id, d, me.getLoginId(), me.getUserName(), me.dept().isEmpty() ? null : me.dept(), r, LocalDateTime.now());
        for (egovframework.haengjeongnuli.cmm.LoginUser a : users.list()) {
            if (a.isEnabled() && a.isAdmin()) {
                notifications.notify(a.getLoginId(), me.getUserName() + "님이 사이트 차단 해제를 요청했습니다: " + d, "/admin/site-block#appeals");
            }
        }
        return new Result(true, "차단 해제 신청을 받았습니다. 관리자가 검토한 뒤 결과를 알림으로 알려 드립니다.");
    }

    public List<Map<String, Object>> appeals() {
        return times(mapper.appeals());
    }

    public List<Map<String, Object>> myAppeals(String loginId) {
        return times(mapper.myAppeals(loginId));
    }

    private static List<Map<String, Object>> times(List<Map<String, Object>> list) {
        for (Map<String, Object> m : list) {
            m.replaceAll((k, v) -> v instanceof java.sql.Timestamp t ? t.toLocalDateTime() : v);
        }
        return list;
    }

    /** 받아들이면 목록에서 빼고(직원 PC 는 스크립트를 다시 받아야 풀림), 거절하면 이유를 알린다 */
    @Transactional
    public Result decide(long id, boolean accept, String answer, String by) {
        Map<String, Object> a = mapper.appeal(id);
        if (a == null || !"WAIT".equals(a.get("status"))) {
            return new Result(false, "검토할 신청이 아닙니다.");
        }
        String ans = answer == null || answer.isBlank() ? null : answer.trim();
        if (ans != null && ans.length() > 500) {
            return new Result(false, "답변은 500자 이내로 적으세요.");
        }
        if (!accept && ans == null) {
            return new Result(false, "거절하는 이유를 적으세요.");
        }
        mapper.decide(id, accept ? "OK" : "NO", ans, by, LocalDateTime.now());
        String d = (String) a.get("domain");
        String removed = null;
        if (accept) {
            removed = listed(d);
            if (removed != null) {
                mapper.deleteRule(removed);
                conf();
                mapper.bump(by, LocalDateTime.now());
            }
        }
        notifications.notify((String) a.get("loginId"), accept
                ? "사이트 차단 해제 신청이 받아들여졌습니다: " + d + ". 보안 프로그램 화면에서 '유해사이트 차단'을 다시 내려받아 실행하면 풀립니다."
                : "사이트 차단 해제 신청이 거절되었습니다: " + d + " — " + ans, "/site-blocked?site=" + d);
        return new Result(true, accept ? d + " 해제를 받아들였습니다." + (removed != null ? " 목록에서 " + removed + "을(를) 뺐습니다. '다시 설치 요청'을 누르면 모든 PC 에 반영됩니다." : "")
                : "거절하고 신청한 직원에게 알렸습니다.");
    }

    public record Result(boolean ok, String message) {}

    @Transactional
    public Map<String, Object> conf() {
        Map<String, Object> c = mapper.conf();
        if (c == null) {
            mapper.insertConf(LocalDateTime.now());
            c = mapper.conf();
        }
        return c;
    }

    public List<Map<String, Object>> rules() {
        return mapper.rules();
    }

    /** 이 모드에서 PC 에 넣을 DNS 주소 (OFF 면 빈 목록: 바꾸지 않음) */
    public List<String> dnsServers(Map<String, Object> c) {
        String mode = String.valueOf(c.get("dnsMode"));
        return switch (mode) {
            case MODE_MALWARE -> List.of("1.1.1.2", "1.0.0.2");
            case MODE_ADULT -> List.of("1.1.1.3", "1.0.0.3");
            case MODE_CUSTOM -> {
                List<String> out = new ArrayList<>();
                for (Object o : new Object[] {c.get("dns1"), c.get("dns2")}) {
                    if (o != null && ipv4(o.toString())) {
                        out.add(o.toString());
                    }
                }
                yield out;
            }
            default -> List.of();
        };
    }

    static boolean ipv4(String s) {
        if (s == null || !s.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return false;
        }
        for (String p : s.split("\\.")) {
            if (Integer.parseInt(p) > 255) {
                return false;
            }
        }
        return true;
    }

    /**
     * 적은 것을 도메인으로 바꾼다. 주소(https://…/경로)를 붙여 넣어도 도메인만 남긴다. 한글 도메인은 퓨니코드로.
     * 맞지 않으면 null
     */
    public static String normalizeSite(String raw) {
        return normalize(raw);
    }

    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        s = s.replaceFirst("^[a-z][a-z0-9+.-]*://", "");
        int cut = s.length();
        for (char ch : new char[] {'/', '?', '#', ':'}) {
            int i = s.indexOf(ch);
            if (i >= 0 && i < cut) {
                cut = i;
            }
        }
        s = s.substring(0, cut);
        if (s.startsWith("*.")) {
            s = s.substring(2);
        }
        if (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.isEmpty()) {
            return null;
        }
        try {
            s = IDN.toASCII(s, IDN.USE_STD3_ASCII_RULES);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (s.length() > 253 || !s.contains(".") || ipv4(s)
                || !s.matches("([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z][a-z0-9-]{0,61}[a-z0-9]")) {
            return null;
        }
        return s;
    }

    /** 여러 줄을 한꺼번에 더한다 (한 줄에 하나). 몇 개 더했는지와 받지 않은 줄을 알려 준다 */
    @Transactional
    public Result add(String lines, String category, String memo, String by) {
        String cat = category != null && CATEGORIES.containsKey(category) ? category : null;
        if (cat == null) {
            return new Result(false, "분류를 고르세요.");
        }
        String m = memo == null || memo.isBlank() ? null : memo.trim();
        if (m != null && m.length() > 200) {
            return new Result(false, "메모는 200자 이내로 적으세요.");
        }
        Set<String> wanted = new LinkedHashSet<>();
        List<String> bad = new ArrayList<>();
        for (String line : (lines == null ? "" : lines).split("[\\r\\n,]+")) {
            if (line.isBlank()) {
                continue;
            }
            String d = normalize(line);
            if (d == null) {
                bad.add(line.trim().length() > 40 ? line.trim().substring(0, 40) + "…" : line.trim());
            } else {
                wanted.add(d);
            }
        }
        if (wanted.isEmpty()) {
            return new Result(false, bad.isEmpty() ? "막을 도메인을 한 줄에 하나씩 적으세요." : "도메인으로 알아볼 수 없습니다: " + String.join(", ", bad));
        }
        int have = mapper.countRules();
        int added = 0;
        LocalDateTime now = LocalDateTime.now();
        for (String d : wanted) {
            if (mapper.exists(d) > 0) {
                continue;
            }
            if (have + added >= MAX_RULES) {
                return new Result(added > 0, MAX_RULES + "개까지만 둘 수 있습니다. " + added + "개를 더했습니다.");
            }
            mapper.insertRule(d, cat, m, by, now);
            added++;
        }
        if (added > 0) {
            conf();
            mapper.bump(by, now);
        }
        return new Result(added > 0, added + "개를 더했습니다." + (wanted.size() > added ? " (이미 있는 " + (wanted.size() - added) + "개는 건너뜀)" : "")
                + (bad.isEmpty() ? "" : " 알아볼 수 없는 줄: " + String.join(", ", bad)));
    }

    /** 기본 차단 목록(resources/siteblock/preset-kr.txt)을 한꺼번에 넣는다. 이미 있는 것은 건너뛴다 */
    @Transactional
    public Result loadPreset(String by) {
        Map<String, StringBuilder> byCat = new java.util.LinkedHashMap<>();
        try (var in = getClass().getResourceAsStream("/siteblock/preset-kr.txt")) {
            if (in == null) {
                return new Result(false, "기본 목록 파일을 찾을 수 없습니다.");
            }
            for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
                String t = line.contains("#") ? line.substring(0, line.indexOf('#')).trim() : line.trim();
                String[] parts = t.split("\\s+");
                if (parts.length == 2 && CATEGORIES.containsKey(parts[0])) {
                    byCat.computeIfAbsent(parts[0], k -> new StringBuilder()).append(parts[1]).append('\n');
                }
            }
        } catch (java.io.IOException e) {
            return new Result(false, "기본 목록 파일을 읽지 못했습니다.");
        }
        int before = mapper.countRules();
        for (var e : byCat.entrySet()) {
            add(e.getValue().toString(), e.getKey(), "기본 목록", by);
        }
        int added = mapper.countRules() - before;
        return new Result(true, "기본 차단 목록에서 " + added + "개를 더했습니다 (이미 있던 것은 건너뜀). '다시 설치 요청'을 누르면 모든 PC 에 반영됩니다.");
    }

    @Transactional
    public Result remove(String domain, String by) {
        String d = normalize(domain);
        if (d == null || mapper.deleteRule(d) == 0) {
            return new Result(false, "목록에 없는 도메인입니다.");
        }
        conf();
        mapper.bump(by, LocalDateTime.now());
        return new Result(true, d + "을(를) 목록에서 뺐습니다.");
    }

    @Transactional
    public Result setDns(String mode, String dns1, String dns2, String by) {
        String m = mode == null ? "" : mode;
        if (!List.of(MODE_OFF, MODE_MALWARE, MODE_ADULT, MODE_CUSTOM).contains(m)) {
            return new Result(false, "DNS 방식을 고르세요.");
        }
        String a = dns1 == null || dns1.isBlank() ? null : dns1.trim();
        String b = dns2 == null || dns2.isBlank() ? null : dns2.trim();
        if (MODE_CUSTOM.equals(m) && (a == null || !ipv4(a) || (b != null && !ipv4(b)))) {
            return new Result(false, "기관 DNS 주소를 IPv4 형식(예: 10.0.0.53)으로 적으세요.");
        }
        conf();
        mapper.updateConf(m, MODE_CUSTOM.equals(m) ? a : null, MODE_CUSTOM.equals(m) ? b : null, by, LocalDateTime.now());
        return new Result(true, "DNS 설정을 저장했습니다.");
    }

    /** 차단 스크립트를 '유해사이트 차단' 강제 보안 프로그램으로 등록한다 (이미 있으면 그것을 쓴다) */
    @Transactional
    public Result registerApp(String by) {
        Map<String, Object> c = conf();
        Object id = c.get("appId");
        if (id != null && apps.get(((Number) id).longValue()) != null) {
            SecurityAppVO a = apps.get(((Number) id).longValue());
            if (!a.isInUse()) {
                apps.setUse(a.getAppId(), true, by);
            }
            return new Result(true, "이미 강제 보안 프로그램으로 등록되어 있습니다.");
        }
        String problem = apps.save(null, "유해사이트 차단", "악성코드·도박·성인 사이트에 접속하지 못하게 이 PC 의 DNS 와 브라우저 정책을 바꿉니다.",
                "1. '내려받기'로 site-block.ps1 을 받습니다.\n2. 받은 파일을 마우스 오른쪽 단추로 눌러 'PowerShell에서 실행'을 고르고, 관리자 권한을 묻으면 '예'를 누릅니다.\n"
                        + "3. '차단을 적용했습니다'가 나오면 브라우저를 모두 닫았다가 다시 엽니다.\n4. 이 화면에서 '설치했습니다'를 누릅니다.",
                "/security-apps/site-block.ps1", true, 1, "STAFF", null, by);
        if (problem != null) {
            return new Result(false, problem);
        }
        SecurityAppVO made = apps.all().stream().filter(a -> "유해사이트 차단".equals(a.getName()))
                .max((x, y) -> Long.compare(x.getAppId(), y.getAppId())).orElseThrow();
        mapper.setApp(made.getAppId());
        return new Result(true, "'유해사이트 차단'을 강제 보안 프로그램으로 등록했습니다. 설치하지 않은 직원은 행정누리를 쓰지 못합니다.");
    }

    /** 목록이 바뀌었으니 모든 직원에게 차단 스크립트를 다시 설치하게 한다 */
    @Transactional
    public Result requestReinstall(String by) {
        Object id = conf().get("appId");
        if (id == null || apps.get(((Number) id).longValue()) == null) {
            return new Result(false, "먼저 강제 보안 프로그램으로 등록하세요.");
        }
        apps.bump(((Number) id).longValue(), by);
        return new Result(true, "모든 직원에게 차단 스크립트를 다시 설치하도록 요청했습니다.");
    }

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * PC 에 까는 PowerShell 스크립트. 도메인·DNS 는 위에서 형식을 확인한 값만 들어간다.
     * 관리자 권한이 없으면 스스로 관리자 권한으로 다시 실행한다.
     */
    public String script() {
        return script("https://localhost:8443");
    }

    /** 안내 프로그램이 넘길 행정누리 주소로 쓸 수 있는가 (스크립트에 그대로 들어가므로 글자를 좁게) */
    static boolean safeBase(String base) {
        return base != null && base.length() <= 200 && base.matches("https?://[A-Za-z0-9.\\-]+(:\\d{1,5})?(/[A-Za-z0-9._\\-/]*)?");
    }

    /** base: 직원이 스크립트를 받은 행정누리 주소 (차단 안내 화면으로 넘길 곳) */
    public String script(String base) {
        String noticeBase = safeBase(base) ? base.replaceAll("/+$", "") : "https://localhost:8443";
        Map<String, Object> c = conf();
        List<String> domains = rules().stream().map(r -> (String) r.get("domain")).filter(d -> normalize(d) != null).toList();
        List<String> dns = dnsServers(c);
        StringBuilder sb = new StringBuilder();
        sb.append("# 행정누리 유해사이트 차단 (판 ").append(c.get("version")).append(", ").append(LocalDateTime.now().format(TS)).append(" 만듦)\r\n");
        sb.append("# 하는 일: 1) 거르는 DNS 로 바꾸기  2) Chrome·Edge·Whale·Firefox 에서 목록의 사이트 막기, DNS-over-HTTPS 끄기  3) hosts 파일  4) 차단 안내 프로그램\r\n");
        sb.append("# 되돌리기: 이 파일을 -Remove 를 붙여 실행  (powershell -ExecutionPolicy Bypass -File site-block.ps1 -Remove)\r\n");
        sb.append("param([switch]$Remove)\r\n");
        sb.append("$ErrorActionPreference = 'Stop'\r\n");
        sb.append("$me = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()\r\n");
        sb.append("if (-not $me.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {\r\n");
        sb.append("    $args2 = '-NoProfile -ExecutionPolicy Bypass -File \"' + $PSCommandPath + '\"'\r\n");
        sb.append("    if ($Remove) { $args2 += ' -Remove' }\r\n");
        sb.append("    Start-Process powershell.exe -Verb RunAs -ArgumentList $args2\r\n");
        sb.append("    exit\r\n}\r\n");
        sb.append("$domains = @(").append(String.join(", ", domains.stream().map(d -> "'" + d + "'").toList())).append(")\r\n");
        sb.append("$dns = @(").append(String.join(", ", dns.stream().map(d -> "'" + d + "'").toList())).append(")\r\n");
        sb.append("$noticeBase = '").append(noticeBase).append("'\r\n");
        sb.append("""
                $begin = '# >>> haengjeongnuli site-block'
                $end = '# <<< haengjeongnuli site-block'
                $hosts = Join-Path $env:SystemRoot 'System32\\drivers\\etc\\hosts'
                $policyKeys = @('HKLM:\\SOFTWARE\\Policies\\Google\\Chrome', 'HKLM:\\SOFTWARE\\Policies\\Microsoft\\Edge', 'HKLM:\\SOFTWARE\\Policies\\Naver\\Naver Whale')
                $ffBlock = 'HKLM:\\SOFTWARE\\Policies\\Mozilla\\Firefox\\WebsiteFilter\\Block'
                $taskName = 'HaengjeongnuliBlockNotice'
                $noticeDir = Join-Path $env:ProgramData 'Haengjeongnuli'
                $noticeFile = Join-Path $noticeDir 'block-notice.ps1'
                # 이 PC 의 80 번(http)으로 온 접속을 행정누리 차단 안내 화면으로 넘기는 작은 프로그램 (127.0.0.1 에서만 받음)
                $notice = @'
                $base = '__BASE__'
                $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 80)
                try { $listener.Start() } catch { exit 1 }
                while ($true) {
                    $client = $listener.AcceptTcpClient()
                    try {
                        $client.ReceiveTimeout = 3000
                        $stream = $client.GetStream()
                        $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::ASCII)
                        $site = ''
                        $line = $reader.ReadLine()
                        while ($line) {
                            $line = $reader.ReadLine()
                            if ($line -match '^[Hh]ost:\\s*([A-Za-z0-9.\\-]+)') { $site = $Matches[1] }
                        }
                        $loc = $base + '/site-blocked?site=' + [Uri]::EscapeDataString($site)
                        $resp = "HTTP/1.1 302 Found`r`nLocation: $loc`r`nCache-Control: no-store`r`nContent-Length: 0`r`nConnection: close`r`n`r`n"
                        $bytes = [System.Text.Encoding]::ASCII.GetBytes($resp)
                        $stream.Write($bytes, 0, $bytes.Length)
                    } catch { } finally { $client.Close() }
                }
                '@

                function Strip-Hosts {
                    $lines = @()
                    if (Test-Path $hosts) { $lines = @(Get-Content -Path $hosts) }
                    $out = New-Object System.Collections.Generic.List[string]
                    $skip = $false
                    foreach ($l in $lines) {
                        if ($l -eq $begin) { $skip = $true; continue }
                        if ($l -eq $end) { $skip = $false; continue }
                        if (-not $skip) { $out.Add($l) }
                    }
                    return ,$out
                }

                $adapters = @(Get-NetAdapter | Where-Object { $_.Status -eq 'Up' })

                if ($Remove) {
                    foreach ($a in $adapters) { Set-DnsClientServerAddress -InterfaceIndex $a.ifIndex -ResetServerAddresses }
                    foreach ($k in $policyKeys) {
                        if (Test-Path "$k\\URLBlocklist") { Remove-Item "$k\\URLBlocklist" -Recurse -Force }
                        if (Test-Path $k) { Remove-ItemProperty -Path $k -Name DnsOverHttpsMode -ErrorAction SilentlyContinue }
                    }
                    if (Test-Path $ffBlock) { Remove-Item $ffBlock -Recurse -Force }
                    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
                    Remove-Item $noticeDir -Recurse -Force -ErrorAction SilentlyContinue
                    $kept = Strip-Hosts
                    Set-Content -Path $hosts -Value $kept -Encoding ASCII
                    Clear-DnsClientCache
                    Write-Host '유해사이트 차단을 풀었습니다. 브라우저를 다시 여세요.' -ForegroundColor Yellow
                    Read-Host '끝내려면 Enter'
                    exit
                }

                # 1) 거르는 DNS (켜진 네트워크 장치 모두)
                if ($dns.Count -gt 0) {
                    foreach ($a in $adapters) { Set-DnsClientServerAddress -InterfaceIndex $a.ifIndex -ServerAddresses $dns }
                }

                # 2) Chrome·Edge: DNS-over-HTTPS 를 꺼서 거르는 DNS 를 우회하지 못하게, 목록의 사이트(하위 주소 포함) 막기
                foreach ($k in $policyKeys) {
                    New-Item -Path $k -Force | Out-Null
                    Set-ItemProperty -Path $k -Name DnsOverHttpsMode -Value 'off' -Type String
                    if (Test-Path "$k\\URLBlocklist") { Remove-Item "$k\\URLBlocklist" -Recurse -Force }
                    New-Item -Path "$k\\URLBlocklist" -Force | Out-Null
                    $i = 1
                    foreach ($d in $domains) { New-ItemProperty -Path "$k\\URLBlocklist" -Name "$i" -Value $d -PropertyType String -Force | Out-Null; $i++ }
                }
                # Firefox: 목록의 사이트 막기 (하위 주소 포함)
                if (Test-Path $ffBlock) { Remove-Item $ffBlock -Recurse -Force }
                New-Item -Path $ffBlock -Force | Out-Null
                $i = 1
                foreach ($d in $domains) {
                    New-ItemProperty -Path $ffBlock -Name "$i" -Value "*://$d/*" -PropertyType String -Force | Out-Null; $i++
                    New-ItemProperty -Path $ffBlock -Name "$i" -Value "*://*.$d/*" -PropertyType String -Force | Out-Null; $i++
                }
                # Firefox 도 DNS-over-HTTPS 끄기
                $ff = 'HKLM:\\SOFTWARE\\Policies\\Mozilla\\Firefox\\DNSOverHTTPS'
                New-Item -Path $ff -Force | Out-Null
                Set-ItemProperty -Path $ff -Name Enabled -Value 0 -Type DWord
                Set-ItemProperty -Path $ff -Name Locked -Value 1 -Type DWord

                # 3) hosts 파일 (다른 브라우저·프로그램용). 전에 넣은 묶음은 지우고 새로 넣는다
                Copy-Item $hosts "$hosts.haengjeongnuli.bak" -Force -ErrorAction SilentlyContinue
                $kept = Strip-Hosts
                $kept.Add($begin)
                foreach ($d in $domains) { $kept.Add("127.0.0.1 $d"); $kept.Add("127.0.0.1 www.$d") }
                $kept.Add($end)
                Set-Content -Path $hosts -Value $kept -Encoding ASCII
                Clear-DnsClientCache

                # 4) 차단 안내 프로그램: 컴퓨터를 켤 때마다 SYSTEM 으로 실행
                New-Item -ItemType Directory -Force -Path $noticeDir | Out-Null
                Set-Content -Path $noticeFile -Value $notice.Replace('__BASE__', $noticeBase) -Encoding UTF8
                $action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument ('-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "' + $noticeFile + '"')
                $trigger = New-ScheduledTaskTrigger -AtStartup
                $principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
                $settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1)
                Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Force | Out-Null
                Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
                Start-ScheduledTask -TaskName $taskName
                Start-Sleep -Seconds 2
                $noticeOk = $false
                try { $c = New-Object System.Net.Sockets.TcpClient; $c.Connect('127.0.0.1', 80); $noticeOk = $true; $c.Close() } catch { }

                Write-Host ''
                Write-Host ('차단을 적용했습니다. 막은 도메인 ' + $domains.Count + '개' + $(if ($dns.Count -gt 0) { ', 거르는 DNS ' + ($dns -join ', ') } else { '' })) -ForegroundColor Green
                if ($noticeOk) { Write-Host '막힌 사이트에 들어가면 차단 안내 화면이 뜹니다 (크롬·엣지·웨일·Firefox 는 브라우저의 차단 화면).' }
                else { Write-Host '이 PC 의 80 번 포트를 다른 프로그램이 쓰고 있어 차단 안내 프로그램을 켜지 못했습니다. 차단은 그대로 됩니다.' -ForegroundColor Yellow }
                Write-Host '브라우저를 모두 닫았다가 다시 연 뒤, 행정누리 보안 프로그램 화면에서 "설치했습니다"를 누르세요.'
                Read-Host '끝내려면 Enter'
                """.replace("\n", "\r\n"));
        return sb.toString();
    }
}
