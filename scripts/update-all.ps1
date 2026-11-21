# 행정누리와 전자민원을 한 번에 최신으로 바꾸고 다시 켭니다.
#
# 쓰는 법: 탐색기에서 scripts\update-all.bat 를 더블클릭
#   또는 PowerShell 에서:  C:\haengjeongnuli\scripts\update-all.bat
#
# - 켜져 있는 행정누리·전자민원을 먼저 끄고 (포트 8443·8080·3000 을 쓰는 java·node 만)
# - 두 폴더를 최신으로 바꾼 뒤 (git 으로 받은 폴더는 git, 압축 파일로 받은 폴더는 새 압축 파일)
# - start-all 로 다시 켭니다. 켜지 않으려면: update-all.bat -NoStart
# - data 폴더(계정·DB·비밀값·인증서)는 건드리지 않습니다.
# - start-all 의 옵션도 그대로 넘길 수 있습니다. 예: update-all.bat -ThisPcOnly

param(
    [string]$EPetitionDir = '',
    [string]$Branch = 'claude/gifted-mendel-jpqcab',
    [switch]$NoStart,
    # start-all 로 그대로 넘기는 옵션
    [switch]$ThisPcOnly,
    [switch]$Internet,
    [string]$PublicHost = ''
)

$ErrorActionPreference = 'Stop'
trap {
    Write-Host ''
    Write-Host ('오류: ' + $_) -ForegroundColor Red
    Write-Host ''
    Write-Host '위 빨간 글을 그대로 알려 주시면 고쳐 드립니다.'
    exit 1
}

$nuriDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ($EPetitionDir -eq '') { $EPetitionDir = Join-Path (Split-Path $nuriDir -Parent) 'haengjeongnuli-e-Petition' }
$repos = @(
    @{ name = '행정누리'; path = $nuriDir;      url = 'https://github.com/sagong12312/haengjeongnuli' },
    @{ name = '전자민원'; path = $EPetitionDir; url = 'https://github.com/sagong12312/haengjeongnuli-e-Petition' }
)
$hasGit = [bool](Get-Command git -ErrorAction SilentlyContinue)

Write-Host '행정누리 + 전자민원 업데이트를 시작합니다.' -ForegroundColor Cyan
Write-Host "행정누리 폴더 : $nuriDir"
Write-Host "전자민원 폴더 : $EPetitionDir"

# 1) 켜져 있으면 끈다 (파일이 쓰이는 중이면 바꿀 수 없음)
$stopped = 0
foreach ($port in 8443, 8080, 3000) {
    Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | ForEach-Object {
        $p = Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue
        if ($p -and ($p.ProcessName -eq 'java' -or $p.ProcessName -eq 'node')) {
            Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
            $stopped++
        }
    }
}
if ($stopped -gt 0) { Write-Host "켜져 있던 프로그램 $stopped 개를 껐습니다."; Start-Sleep -Seconds 3 }

# 2) 두 폴더를 최신으로
foreach ($r in $repos) {
    Write-Host ''
    Write-Host ("=== " + $r.name + " ===") -ForegroundColor Cyan
    $path = $r.path
    if ($hasGit -and (Test-Path (Join-Path $path '.git'))) {
        $before = (git -C $path rev-parse --short HEAD 2>$null)
        $ErrorActionPreference = 'Continue'
        git -C $path fetch origin $Branch 2>&1 | Out-Null
        $dirty = git -C $path status --porcelain --untracked-files=no
        if ($dirty) {
            Write-Host '직접 고친 파일이 있어 덮어쓰지 않았습니다. 아래 파일을 확인해 주세요:' -ForegroundColor Yellow
            $dirty | ForEach-Object { Write-Host "  $_" }
            $ErrorActionPreference = 'Stop'
            continue
        }
        git -C $path checkout $Branch 2>&1 | Out-Null
        git -C $path merge --ff-only FETCH_HEAD 2>&1 | Out-Null
        $ErrorActionPreference = 'Stop'
        $after = (git -C $path rev-parse --short HEAD 2>$null)
        if ($before -eq $after) { Write-Host "이미 최신입니다 ($after)." } else { Write-Host "업데이트했습니다: $before → $after" -ForegroundColor Green }
    } else {
        $zip = Join-Path $env:TEMP ("hjn-" + (Split-Path $path -Leaf) + '.zip')
        $tmp = Join-Path $env:TEMP ("hjn-" + (Split-Path $path -Leaf) + '-new')
        Write-Host '최신 압축 파일을 받는 중...'
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest ($r.url + '/archive/refs/heads/' + $Branch + '.zip') -OutFile $zip -UseBasicParsing
        Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
        Expand-Archive $zip $tmp -Force
        $src = (Get-ChildItem $tmp -Directory | Select-Object -First 1).FullName
        if (-not (Test-Path $path)) { New-Item -ItemType Directory -Path $path | Out-Null }
        robocopy $src $path /E /XD data node_modules .git build .gradle /NFL /NDL /NJH /NJS | Out-Null
        if ($LASTEXITCODE -ge 8) { throw "파일을 복사하지 못했습니다 (robocopy $LASTEXITCODE)" }
        Remove-Item $zip, $tmp -Recurse -Force -ErrorAction SilentlyContinue
        Write-Host '새 파일로 덮어썼습니다 (data 폴더는 그대로 둠).' -ForegroundColor Green
    }
}

# 3) 다시 켜기
Write-Host ''
if ($NoStart) {
    Write-Host '업데이트를 마쳤습니다. 켜려면 scripts\start-all.bat 를 실행하세요.' -ForegroundColor Green
    exit 0
}
Write-Host '업데이트를 마쳤습니다. 이어서 켭니다...' -ForegroundColor Green
Write-Host ''
$startArgs = @{ EPetitionDir = $EPetitionDir }
if ($ThisPcOnly) { $startArgs.ThisPcOnly = $true }
if ($Internet) { $startArgs.Internet = $true }
if ($PublicHost -ne '') { $startArgs.PublicHost = $PublicHost }
& (Join-Path $PSScriptRoot 'start-all.ps1') @startArgs
