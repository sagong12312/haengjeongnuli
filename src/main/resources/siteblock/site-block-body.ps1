# ---- 여기부터 본문 (행정누리가 위에 $domains, $dns, $noticeBase 를 넣어 내려준다) ----
$begin = '# >>> haengjeongnuli site-block'
$end = '# <<< haengjeongnuli site-block'
$hosts = Join-Path $env:SystemRoot 'System32\drivers\etc\hosts'
$policyKeys = @('HKLM:\SOFTWARE\Policies\Google\Chrome', 'HKLM:\SOFTWARE\Policies\Microsoft\Edge', 'HKLM:\SOFTWARE\Policies\Naver\Naver Whale')
$ffRoot = 'HKLM:\SOFTWARE\Policies\Mozilla\Firefox'
$ffBlock = "$ffRoot\WebsiteFilter\Block"
$taskName = 'HaengjeongnuliBlockNotice'
$workDir = Join-Path $env:ProgramData 'Haengjeongnuli'
$noticeFile = Join-Path $workDir 'block-notice.ps1'
$log = Join-Path $workDir 'site-block.log'
New-Item -ItemType Directory -Force -Path $workDir | Out-Null
$script:fails = New-Object System.Collections.Generic.List[string]

function Log([string]$text) {
    try { Add-Content -Path $log -Value ((Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + ' ' + $text) -Encoding UTF8 } catch { }
}

# 단계마다 따로 한다: 하나가 실패해도 나머지는 계속하고, 무엇이 실패했는지 보여 준다
function Step([string]$name, [scriptblock]$work) {
    try {
        $ErrorActionPreference = 'Stop'
        & $work
        Write-Host ('  [완료] ' + $name) -ForegroundColor Green
        Log ('완료 ' + $name)
    } catch {
        $msg = $_.Exception.Message
        $script:fails.Add($name + ': ' + $msg)
        Write-Host ('  [실패] ' + $name + ' - ' + $msg) -ForegroundColor Red
        Log ('실패 ' + $name + ' - ' + $msg)
    }
}

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

function Finish([string]$okText) {
    Write-Host ''
    if ($script:fails.Count -eq 0) {
        Write-Host $okText -ForegroundColor Green
    } else {
        Write-Host ('실패한 단계가 ' + $script:fails.Count + '개 있습니다. 아래 내용을 행정누리 관리자에게 알려 주세요.') -ForegroundColor Red
        foreach ($f in $script:fails) { Write-Host ('  - ' + $f) -ForegroundColor Red }
    }
    Write-Host ('기록: ' + $log)
    Read-Host '끝내려면 Enter'
}

Log ('시작 ' + $(if ($Remove) { '되돌리기' } else { '적용' }) + ', 도메인 ' + $domains.Count + '개')
Write-Host ''

if ($Remove) {
    Write-Host '유해사이트 차단을 풉니다...'
    Step '브라우저 차단 정책 지우기' {
        foreach ($k in $policyKeys) {
            if (Test-Path "$k\URLBlocklist") { Remove-Item "$k\URLBlocklist" -Recurse -Force }
            if (Test-Path $k) { Remove-ItemProperty -Path $k -Name DnsOverHttpsMode -ErrorAction SilentlyContinue }
        }
        if (Test-Path "$ffRoot\WebsiteFilter") { Remove-Item "$ffRoot\WebsiteFilter" -Recurse -Force }
        if (Test-Path "$ffRoot\DNSOverHTTPS") { Remove-Item "$ffRoot\DNSOverHTTPS" -Recurse -Force }
    }
    Step 'hosts 파일 되돌리기' {
        $kept = Strip-Hosts
        Set-Content -Path $hosts -Value $kept -Encoding ASCII
    }
    Step '차단 안내 프로그램 지우기' {
        Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
        Remove-Item $noticeFile -Force -ErrorAction SilentlyContinue
    }
    Step 'DNS 를 원래대로 (자동)' {
        foreach ($a in @(Get-NetAdapter | Where-Object { $_.Status -eq 'Up' })) {
            try { Set-DnsClientServerAddress -InterfaceIndex $a.ifIndex -ResetServerAddresses -ErrorAction Stop } catch { Log ('DNS 되돌리기 건너뜀 ' + $a.Name) }
        }
        Clear-DnsClientCache
    }
    Finish '유해사이트 차단을 풀었습니다. 브라우저를 모두 닫았다가 다시 여세요.'
    exit
}

Write-Host '유해사이트 차단을 적용합니다...'

# 1) 가장 중요한 것부터: 크롬·엣지·웨일에서 목록의 사이트(하위 주소 포함) 막기, DNS-over-HTTPS 끄기
Step '크롬·엣지·웨일 차단 정책' {
    foreach ($k in $policyKeys) {
        New-Item -Path $k -Force | Out-Null
        Set-ItemProperty -Path $k -Name DnsOverHttpsMode -Value 'off' -Type String
        if (Test-Path "$k\URLBlocklist") { Remove-Item "$k\URLBlocklist" -Recurse -Force }
        New-Item -Path "$k\URLBlocklist" -Force | Out-Null
        $i = 1
        foreach ($d in $domains) { New-ItemProperty -Path "$k\URLBlocklist" -Name "$i" -Value $d -PropertyType String -Force | Out-Null; $i++ }
    }
}

# 2) Firefox
Step 'Firefox 차단 정책' {
    if (Test-Path "$ffRoot\WebsiteFilter") { Remove-Item "$ffRoot\WebsiteFilter" -Recurse -Force }
    New-Item -Path $ffBlock -Force | Out-Null
    $i = 1
    foreach ($d in $domains) {
        New-ItemProperty -Path $ffBlock -Name "$i" -Value "*://$d/*" -PropertyType String -Force | Out-Null; $i++
        New-ItemProperty -Path $ffBlock -Name "$i" -Value "*://*.$d/*" -PropertyType String -Force | Out-Null; $i++
    }
    New-Item -Path "$ffRoot\DNSOverHTTPS" -Force | Out-Null
    Set-ItemProperty -Path "$ffRoot\DNSOverHTTPS" -Name Enabled -Value 0 -Type DWord
    Set-ItemProperty -Path "$ffRoot\DNSOverHTTPS" -Name Locked -Value 1 -Type DWord
}

# 3) hosts 파일: 다른 브라우저·프로그램도 목록의 사이트를 이 PC(127.0.0.1)로
Step 'hosts 파일' {
    Copy-Item $hosts "$hosts.haengjeongnuli.bak" -Force -ErrorAction SilentlyContinue
    $kept = Strip-Hosts
    $kept.Add($begin)
    foreach ($d in $domains) { $kept.Add("127.0.0.1 $d"); $kept.Add("127.0.0.1 www.$d") }
    $kept.Add($end)
    Set-Content -Path $hosts -Value $kept -Encoding ASCII
}

# 4) 차단 안내 프로그램: 이 PC 의 80 번(http)으로 온 접속을 행정누리 차단 안내 화면으로 (127.0.0.1 에서만 받음)
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
            if ($line -match '^[Hh]ost:\s*([A-Za-z0-9.\-]+)') { $site = $Matches[1] }
        }
        $loc = $base + '/site-blocked?site=' + [Uri]::EscapeDataString($site)
        $resp = "HTTP/1.1 302 Found`r`nLocation: $loc`r`nCache-Control: no-store`r`nContent-Length: 0`r`nConnection: close`r`n`r`n"
        $bytes = [System.Text.Encoding]::ASCII.GetBytes($resp)
        $stream.Write($bytes, 0, $bytes.Length)
    } catch { } finally { $client.Close() }
}
'@
$script:noticeOk = $false
Step '차단 안내 프로그램' {
    Set-Content -Path $noticeFile -Value $notice.Replace('__BASE__', $noticeBase) -Encoding UTF8
    $action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument ('-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "' + $noticeFile + '"')
    $trigger = New-ScheduledTaskTrigger -AtStartup
    $principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
    $settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1)
    Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Force | Out-Null
    Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    Start-ScheduledTask -TaskName $taskName
    Start-Sleep -Seconds 2
    try { $c = New-Object System.Net.Sockets.TcpClient; $c.Connect('127.0.0.1', 80); $script:noticeOk = $true; $c.Close() } catch { }
    if (-not $script:noticeOk) { Write-Host '    (80 번 포트를 다른 프로그램이 쓰고 있으면 안내 화면은 뜨지 않지만 차단은 됩니다)' -ForegroundColor Yellow }
}

# 5) 거르는 DNS: 네트워크 장치마다 따로 (VPN·가상 장치에서 실패해도 나머지는 바꾼다)
if ($dns.Count -gt 0) {
    Step ('거르는 DNS (' + ($dns -join ', ') + ')') {
        $changed = 0
        foreach ($a in @(Get-NetAdapter | Where-Object { $_.Status -eq 'Up' })) {
            try { Set-DnsClientServerAddress -InterfaceIndex $a.ifIndex -ServerAddresses $dns -ErrorAction Stop; $changed++ }
            catch { Log ('DNS 건너뜀 ' + $a.Name + ' - ' + $_.Exception.Message) }
        }
        if ($changed -eq 0) { throw '바꿀 수 있는 네트워크 장치가 없습니다.' }
    }
}

Step 'DNS 기록 비우기' { Clear-DnsClientCache }

Finish ('차단을 적용했습니다. 막은 도메인 ' + $domains.Count + '개. 브라우저 창을 모두 닫았다가 다시 열고, 주소창에 chrome://policy 를 넣어 URLBlocklist 가 보이는지 확인하세요. 그다음 행정누리 보안 프로그램 화면에서 "설치했습니다"를 누르세요.')
