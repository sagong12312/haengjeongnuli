# 행정누리(공무원용)와 행정누리 전자민원(국민용)을 내 PC에서 함께 켭니다.
#
# 쓰는 법 (PowerShell 에서 행정누리 폴더로 가서):
#   powershell -ExecutionPolicy Bypass -File .\scripts\start-all.ps1
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

function Fail([string]$message) {
    Write-Host ''
    Write-Host $message -ForegroundColor Red
    Write-Host ''
    exit 1
}

$nuriDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ($EPetitionDir -eq '') {
    $EPetitionDir = Join-Path (Split-Path $nuriDir -Parent) 'haengjeongnuli-e-Petition'
}
if (-not (Test-Path (Join-Path $EPetitionDir 'package.json'))) {
    Fail "전자민원 폴더를 찾을 수 없습니다: $EPetitionDir`n다른 곳에 받았다면 -EPetitionDir 'C:\경로\haengjeongnuli-e-Petition' 을 붙여 실행하세요."
}
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

# 행정누리: 이 PC에서만 열리게 (APP_ALLOWED_NETWORKS 를 주지 않으면 기본이 이 PC만)
# 서버 글자는 UTF-8 로 나오므로 창도 UTF-8 로 맞춘다 (안 하면 한글이 깨져 보임)
$utf8 = "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; "
$nuriCmd = $utf8 + "`$host.UI.RawUI.WindowTitle = '행정누리 (끄려면 Ctrl+C)'; " +
    "`$env:SERVER_PORT = '$NuriPort'; " +
    "`$env:APP_EPETITION_ENABLED = 'true'; " +
    "`$env:APP_EPETITION_SECRET = '$secret'; " +
    "`$env:APP_EPETITION_NETWORKS = '127.0.0.1/32,::1/128'; " +
    ".\gradlew.bat bootRun"
Start-Process powershell -WorkingDirectory $nuriDir -ArgumentList @('-NoExit', '-ExecutionPolicy', 'Bypass', '-Command', $nuriCmd)

# 전자민원: 같은 PC 안의 행정누리를 부른다 (PC 밖으로 나가지 않으므로 http 허용)
$safeOrg = $OrgName.Replace("'", "''")
if (-not (Test-Path (Join-Path $EPetitionDir 'node_modules')) -and (Test-Path (Join-Path $EPetitionDir 'package-lock.json'))) {
    $install = 'npm install; '
} else {
    $install = ''
}
$epCmd = $utf8 + "`$host.UI.RawUI.WindowTitle = '행정누리 전자민원 (끄려면 Ctrl+C)'; " +
    "`$env:NURI_BASE_URL = 'http://127.0.0.1:$NuriPort'; " +
    "`$env:EP_ALLOW_HTTP = 'true'; " +
    "`$env:EP_SECRET = '$secret'; " +
    "`$env:PORT = '$EPetitionPort'; " +
    "`$env:COOKIE_SECURE = 'false'; " +
    "`$env:ORG_NAME = '$safeOrg'; " +
    $install + "npm start"
Start-Process powershell -WorkingDirectory $EPetitionDir -ArgumentList @('-NoExit', '-ExecutionPolicy', 'Bypass', '-Command', $epCmd)

# 다른 기기에서 열 주소 (공유기가 준 이 PC 주소)
$addrs = @()
try {
    $addrs = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop |
        Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } |
        Select-Object -ExpandProperty IPAddress
} catch { }

Write-Host ''
Write-Host '두 창이 떴습니다. 행정누리 창에 글자가 올라가다 멈추면 (1분쯤) 다 켜진 것입니다.' -ForegroundColor Green
Write-Host ''
Write-Host "  행정누리 (이 PC에서만)     : http://localhost:$NuriPort"
Write-Host "  전자민원 (이 PC)           : http://localhost:$EPetitionPort"
foreach ($a in $addrs) {
    Write-Host "  전자민원 (휴대폰·노트북)   : http://${a}:$EPetitionPort"
}
Write-Host ''
Write-Host '  휴대폰·노트북은 이 PC와 같은 공유기(와이파이)에 연결되어 있어야 합니다.'
Write-Host "  다른 기기에서 열리지 않으면 관리자 PowerShell 에서 방화벽을 한 번 열어 주세요:"
Write-Host "  New-NetFirewallRule -DisplayName '행정누리 전자민원 $EPetitionPort' -Direction Inbound -Protocol TCP -LocalPort $EPetitionPort -Action Allow -Profile Any -RemoteAddress LocalSubnet"
Write-Host ''
