# 행정누리(공무원용)와 행정누리 전자민원(국민용)을 내 PC에서 함께 켭니다.
#
# 쓰는 법 (PowerShell 에서 행정누리 폴더로 가서):
#   powershell -ExecutionPolicy Bypass -File .\scripts\start-all.ps1
#   (또는 탐색기에서 scripts\start-all.bat 를 더블클릭)
#
# - 행정누리는 이 PC에서만 (http://localhost:8080)
# - 전자민원은 같은 공유기에 붙은 휴대폰·노트북에서도 (http://이PC주소:3000)
# - 두 프로그램이 함께 쓰는 비밀값은 data\epetition-secret.txt 에 처음 한 번 만들어 둡니다 (저장소에 올라가지 않음)
# - 창이 두 개 새로 뜹니다. 끄려면 각 창에서 Ctrl + C 를 누르거나 창을 닫습니다.

param(
    # 전자민원 폴더 (기본: 행정누리 폴더 옆의 haengjeongnuli-e-Petition)
    [string]$EPetitionDir = '',
    [int]$NuriPort = 8080,
    [int]$EPetitionPort = 3000,
    [string]$OrgName = '시험시'
)

$ErrorActionPreference = 'Stop'

# 예상하지 못한 오류가 나도 창이 바로 닫히지 않게 빨간 글로 보여 준다
trap {
    Write-Host ''
    Write-Host ('오류: ' + $_) -ForegroundColor Red
    Write-Host ('위치: ' + $_.InvocationInfo.PositionMessage) -ForegroundColor Red
    Write-Host ''
    Write-Host '위 빨간 글을 그대로 알려 주시면 고쳐 드립니다.'
    exit 1
}

Write-Host '행정누리 + 전자민원 켜기를 시작합니다.'

function Fail([string]$message) {
    Write-Host ''
    Write-Host $message -ForegroundColor Red
    Write-Host ''
    Write-Host '위 빨간 글을 그대로 알려 주시면 고쳐 드립니다.'
    exit 1
}

$nuriDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ($EPetitionDir -eq '') {
    # 행정누리 폴더 옆, 행정누리 폴더 안, 내려받기(Downloads) 폴더에서 찾는다.
    # zip 으로 받으면 폴더 이름 뒤에 -main, -claude-... 가 붙고, 압축을 풀면 한 단계 더 들어가 있기도 해서 둘 다 본다.
    $places = @((Split-Path $nuriDir -Parent), $nuriDir, (Join-Path $env:USERPROFILE 'Downloads'), (Join-Path $env:USERPROFILE 'Desktop'))
    $found = @()
    foreach ($place in $places) {
        if (-not (Test-Path $place)) { continue }
        foreach ($d in @(Get-ChildItem -Path $place -Directory -Filter 'haengjeongnuli-e-Petition*' -ErrorAction SilentlyContinue)) {
            if (Test-Path (Join-Path $d.FullName 'package.json')) { $found += $d.FullName }
            foreach ($inner in @(Get-ChildItem -Path $d.FullName -Directory -Filter 'haengjeongnuli-e-Petition*' -ErrorAction SilentlyContinue)) {
                if (Test-Path (Join-Path $inner.FullName 'package.json')) { $found += $inner.FullName }
            }
        }
    }
    if ($found.Count -gt 0) { $EPetitionDir = $found[0] }
    else { $EPetitionDir = Join-Path (Split-Path $nuriDir -Parent) 'haengjeongnuli-e-Petition' }
}
if (-not (Test-Path (Join-Path $EPetitionDir 'package.json'))) {
    Fail "전자민원 폴더를 찾을 수 없습니다: $EPetitionDir`n전자민원을 받은 폴더(package.json 이 들어 있는 폴더)를 직접 알려 주세요. 예:`n  .\scripts\start-all.bat -EPetitionDir C:\haengjeongnuli-e-Petition"
}
Write-Host "행정누리 폴더 : $nuriDir"
Write-Host "전자민원 폴더 : $EPetitionDir"
$EPetitionDir = (Resolve-Path $EPetitionDir).Path

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Fail 'Java(JDK 21)가 없습니다. 설치한 뒤 PowerShell 을 새로 열어 다시 실행하세요.'
}
if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    Fail 'Node.js 가 없습니다. https://nodejs.org 에서 설치한 뒤 PowerShell 을 새로 열어 다시 실행하세요.'
}

# 두 프로그램이 같이 쓰는 서명 비밀값 (영문·숫자 64자, 처음 한 번 만듦)
$dataDir = Join-Path $nuriDir 'data'
New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
$secretFile = Join-Path $dataDir 'epetition-secret.txt'
if (-not (Test-Path $secretFile)) {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    $hex = -join ($bytes | ForEach-Object { $_.ToString('x2') })
    [System.IO.File]::WriteAllText($secretFile, $hex)
    Write-Host "비밀값을 새로 만들었습니다: $secretFile"
}
$secret = ([System.IO.File]::ReadAllText($secretFile)).Trim()
if ($secret -notmatch '^[0-9a-f]{64}$') {
    Fail "비밀값 파일이 잘못되었습니다. $secretFile 을 지우고 다시 실행하세요."
}

# 다른 기기에서 열 주소 (공유기가 준 이 PC 주소)
$addrs = @()
try {
    $addrs = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop |
        Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } |
        Select-Object -ExpandProperty IPAddress
} catch { }


# ---------------------------------------------------------------- HTTPS 인증서
# 휴대폰·노트북과 와이파이로 주고받는 비밀번호·개인정보를 가로채지 못하게 HTTPS 로만 연다.
# 이 PC 전용 인증서(자체 서명)를 data\tls 에 처음 한 번 만든다. PC 주소가 바뀌면 다시 만든다.
$tlsDir = Join-Path $dataDir 'tls'
New-Item -ItemType Directory -Force -Path $tlsDir | Out-Null
$p12 = Join-Path $tlsDir 'haengjeongnuli.p12'
$cer = Join-Path $tlsDir 'haengjeongnuli.cer'
$passFile = Join-Path $tlsDir 'password.txt'
$ipsFile = Join-Path $tlsDir 'ips.txt'

$keytool = Join-Path (Split-Path (Get-Command java).Source -Parent) 'keytool.exe'
if (-not (Test-Path $keytool) -and $env:JAVA_HOME) { $keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe' }
if (-not (Test-Path $keytool)) {
    $kt = Get-Command keytool -ErrorAction SilentlyContinue
    if ($kt) { $keytool = $kt.Source } else { Fail 'keytool(JDK 에 들어 있음)을 찾을 수 없습니다. JDK 21 을 설치하고 JAVA_HOME 을 정한 뒤 다시 실행하세요.' }
}

$san = 'dns:localhost,ip:127.0.0.1'
foreach ($a in $addrs) { $san += ",ip:$a" }
$oldSan = ''
if (Test-Path $ipsFile) { $oldSan = ([System.IO.File]::ReadAllText($ipsFile)).Trim() }
if (-not (Test-Path $p12) -or -not (Test-Path $passFile) -or $oldSan -ne $san) {
    Write-Host 'HTTPS 인증서를 만듭니다...'
    $pb = New-Object byte[] 24
    $rng2 = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng2.GetBytes($pb)
    $rng2.Dispose()
    $newPass = -join ($pb | ForEach-Object { $_.ToString('x2') })
    if (Test-Path $p12) { Remove-Item $p12 -Force }
    if (Test-Path $cer) { Remove-Item $cer -Force }
    # keytool 은 진행 안내를 오류 출력으로 내보내므로, 그 동안은 오류로 멈추지 않게 한다
    $ErrorActionPreference = 'Continue'
    & $keytool -genkeypair -alias haengjeongnuli -keyalg EC -groupname secp256r1 -sigalg SHA256withECDSA -validity 397 `
        -storetype PKCS12 -keystore $p12 -storepass $newPass -keypass $newPass `
        -dname 'CN=Haengjeongnuli Test, O=Haengjeongnuli' -ext "SAN=$san" -ext 'BC:c=ca:true' -ext 'KU:c=digitalSignature,keyCertSign' -ext 'EKU=serverAuth' 2>&1 | Out-Null
    if (-not (Test-Path $p12)) { Fail 'HTTPS 인증서를 만들지 못했습니다 (keytool 오류).' }
    & $keytool -exportcert -rfc -alias haengjeongnuli -keystore $p12 -storetype PKCS12 -storepass $newPass -file $cer 2>&1 | Out-Null
    $ErrorActionPreference = 'Stop'
    if (-not (Test-Path $cer)) { Fail 'HTTPS 인증서를 내보내지 못했습니다 (keytool 오류).' }
    [System.IO.File]::WriteAllText($passFile, $newPass)
    [System.IO.File]::WriteAllText($ipsFile, $san)
    Write-Host "인증서를 만들었습니다: $p12"
}
$tlsPass = ([System.IO.File]::ReadAllText($passFile)).Trim()

# 새 창은 이 창의 환경변수를 물려받는다. 그래서 값을 여기서 정해 두고,
# 새 창에는 짧은 명령만 넘긴다 (따옴표·한글이 섞인 긴 명령은 Windows 에서 깨지기 쉬움).
# 행정누리: 이 PC에서만 열리게 (APP_ALLOWED_NETWORKS 를 주지 않으면 기본이 이 PC만), HTTPS 로
$env:SERVER_PORT = "$NuriPort"
$env:APP_SSL_ENABLED = 'true'
$env:APP_SSL_KEYSTORE = 'file:' + ($p12 -replace '\\', '/')
$env:APP_SSL_KEYSTORE_PASSWORD = $tlsPass
$env:APP_EPETITION_ENABLED = 'true'
$env:APP_EPETITION_SECRET = $secret
$env:APP_EPETITION_NETWORKS = '127.0.0.1/32,::1/128'
# 전자민원: 같은 PC 안의 행정누리를 HTTPS 로 부르고(위 인증서를 믿음), 휴대폰에도 HTTPS 로만 연다
$env:NURI_BASE_URL = "https://127.0.0.1:$NuriPort"
$env:NURI_CA_FILE = $cer
Remove-Item Env:EP_ALLOW_HTTP -ErrorAction SilentlyContinue
$env:EP_SECRET = $secret
$env:PORT = "$EPetitionPort"
$env:TLS_PFX_FILE = $p12
$env:TLS_PFX_PASSPHRASE = $tlsPass
$env:COOKIE_SECURE = 'true'
$env:ORG_NAME = $OrgName

# cmd 창으로 띄운다. chcp 65001 은 서버가 내보내는 UTF-8 한글이 깨지지 않게 한다.
Write-Host '행정누리 창을 엽니다...'
Start-Process -FilePath 'cmd.exe' -WorkingDirectory $nuriDir -ArgumentList '/k chcp 65001 >nul & title 행정누리 - 끄려면 Ctrl+C & gradlew.bat bootRun'

if (-not (Test-Path (Join-Path $EPetitionDir 'node_modules')) -and (Test-Path (Join-Path $EPetitionDir 'package-lock.json'))) {
    $install = 'npm install & '
} else {
    $install = ''
}
Write-Host '전자민원 창을 엽니다...'
Start-Process -FilePath 'cmd.exe' -WorkingDirectory $EPetitionDir -ArgumentList ('/k chcp 65001 >nul & title 행정누리 전자민원 - 끄려면 Ctrl+C & ' + $install + 'npm start')

Write-Host ''
Write-Host '두 창이 떴습니다. 행정누리 창에 글자가 올라가다 멈추면 (1분쯤) 다 켜진 것입니다.' -ForegroundColor Green
Write-Host ''
Write-Host "  행정누리 (이 PC에서만)     : https://localhost:$NuriPort"
Write-Host "  전자민원 (이 PC)           : https://localhost:$EPetitionPort"
foreach ($a in $addrs) {
    Write-Host "  전자민원 (휴대폰·노트북)   : https://${a}:$EPetitionPort"
}
Write-Host ''
Write-Host '  주소는 꼭 https:// 로 넣으세요. 오가는 내용이 암호화되어 같은 와이파이의 다른 사람이 가로채도 읽을 수 없습니다.' -ForegroundColor Yellow
Write-Host '  처음 열면 "안전하지 않음/연결이 비공개로 설정되어 있지 않습니다" 경고가 뜹니다 (이 PC가 직접 만든 시험용 인증서라서).'
Write-Host '  경고를 없애려면 이 파일을 휴대폰에 옮겨 인증서로 설치하세요 (안드로이드: 설정 > 보안 > 인증서 설치 > CA 인증서):'
Write-Host "    $cer"
Write-Host '  국민에게 실제로 열 때는 기관 정식 인증서(GPKI·상용 SSL)를 쓰세요.'
Write-Host ''
Write-Host '  휴대폰·노트북은 이 PC와 같은 공유기(와이파이)에 연결되어 있어야 합니다.'
Write-Host "  다른 기기에서 열리지 않으면 관리자 PowerShell 에서 방화벽을 한 번 열어 주세요:"
Write-Host "  New-NetFirewallRule -DisplayName '행정누리 전자민원 $EPetitionPort' -Direction Inbound -Protocol TCP -LocalPort $EPetitionPort -Action Allow -Profile Any -RemoteAddress LocalSubnet"
Write-Host ''
