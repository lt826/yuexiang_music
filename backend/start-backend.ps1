# ============================================================
# 悦享音乐后端一键启动脚本（PowerShell）
# 用法：右键"使用 PowerShell 运行"，或在终端执行  .\start-backend.ps1
# 说明：
#   1. 自动检测每个服务是否已在线（端口监听），在线的跳过，不重复启动
#   2. 日志写到各服务目录 run.log / run-err.log
#   3. 依赖：MySQL/Redis/RabbitMQ/Nacos 需先启动
# ============================================================

$ErrorActionPreference = 'Continue'
$base = $PSScriptRoot

# 服务启动顺序：基础服务在前（被别人 Feign 依赖），网关最后
$services = @(
    @{ name = 'user';      port = 8081 },
    @{ name = 'song';      port = 8082 },
    @{ name = 'singer';    port = 8083 },
    @{ name = 'playlist';  port = 8084 },
    @{ name = 'favorite';  port = 8085 },
    @{ name = 'message';   port = 8086 },
    @{ name = 'file';      port = 8087 },
    @{ name = 'admin';     port = 8088 },
    @{ name = 'ai';        port = 8089 },
    @{ name = 'circle';    port = 8090 },
    @{ name = 'gateway';   port = 8080 }
)

Write-Host "==== 悦享音乐后端启动 ====" -ForegroundColor Cyan

# 0. 基础设施检查
foreach ($dep in @(@{n='MySQL'; p=3306}, @{n='Nacos'; p=8848}, @{n='Redis'; p=6379}, @{n='RabbitMQ'; p=5672})) {
    $ok = Test-NetConnection -ComputerName 127.0.0.1 -Port $dep.p -InformationLevel Quiet -WarningAction SilentlyContinue
    if ($ok) { Write-Host "[OK] $($dep.n) ($($dep.p))" -ForegroundColor Green }
    else { Write-Host "[!!] $($dep.n) ($($dep.p)) 未启动——相关服务可能起不来" -ForegroundColor Yellow }
}

# 1. 编译检查：target 里没有 jar 的先打包（只打缺的）
foreach ($svc in $services) {
    $jar = Join-Path $base "yuexiang-$($svc.name)\target\yuexiang-$($svc.name)-0.0.1-SNAPSHOT.jar"
    if (-not (Test-Path $jar)) {
        Write-Host "[BUILD] yuexiang-$($svc.name) 没有可运行 jar，先打包..." -ForegroundColor Yellow
        & (Join-Path $base "mvnw.cmd") -q -pl "yuexiang-$($svc.name)" -am package -DskipTests
        if ($LASTEXITCODE -ne 0) { Write-Host "[BUILD FAILED] yuexiang-$($svc.name)" -ForegroundColor Red; exit 1 }
    }
}

# 2. 启动未在线的服务
$started = 0
foreach ($svc in $services) {
    $listening = Get-NetTCPConnection -LocalPort $svc.port -State Listen -ErrorAction SilentlyContinue
    if ($listening) {
        Write-Host "[SKIP] $($svc.name) 已在线 (端口 $($svc.port))" -ForegroundColor DarkGray
        continue
    }
    $jar = Join-Path $base "yuexiang-$($svc.name)\target\yuexiang-$($svc.name)-0.0.1-SNAPSHOT.jar"
    $dir = Join-Path $base "yuexiang-$($svc.name)"
    Start-Process -FilePath "java" `
        -ArgumentList "-jar", "`"$jar`"" `
        -WorkingDirectory $dir `
        -RedirectStandardOutput (Join-Path $dir "run.log") `
        -RedirectStandardError (Join-Path $dir "run-err.log") `
        -WindowStyle Hidden
    Write-Host "[START] $($svc.name) -> 端口 $($svc.port)" -ForegroundColor Green
    $started++
    Start-Sleep -Seconds 2
}

if ($started -eq 0) {
    Write-Host "全部服务已在线，无需启动。" -ForegroundColor Cyan
} else {
    Write-Host "已发起 $started 个服务启动，等待就绪（最多 120s）..." -ForegroundColor Cyan
    $deadline = (Get-Date).AddSeconds(120)
    while ((Get-Date) -lt $deadline) {
        $down = @()
        foreach ($svc in $services) {
            if (-not (Get-NetTCPConnection -LocalPort $svc.port -State Listen -ErrorAction SilentlyContinue)) {
                $down += $svc.name
            }
        }
        if ($down.Count -eq 0) { break }
        Start-Sleep -Seconds 5
    }
    $down = @()
    foreach ($svc in $services) {
        if (-not (Get-NetTCPConnection -LocalPort $svc.port -State Listen -ErrorAction SilentlyContinue)) {
            $down += $svc.name
        }
    }
    if ($down.Count -eq 0) {
        Write-Host "==== 全部 11 个服务已就绪（网关 http://localhost:8080） ====" -ForegroundColor Green
    } else {
        Write-Host "仍未就绪: $($down -join ', ') —— 查看对应目录 run-err.log" -ForegroundColor Red
    }
}
