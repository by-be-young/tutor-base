param(
    [int]$DatabasePort = 55433
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$containerName = 'tutor-base-e2e-postgres'
$databasePassword = 'e2e-database-password'
$backendProcess = $null
$backendLog = Join-Path $repository 'backend-e2e.log'
$backendErrorLog = Join-Path $repository 'backend-e2e-error.log'

if ((docker ps -a --filter "name=^/$containerName$" --format '{{.Names}}') -eq $containerName) {
    throw "已存在名为 $containerName 的容器；请先确认它是否可以删除。"
}

try {
    docker run --name $containerName `
        -e POSTGRES_DB=tutor_base_e2e `
        -e POSTGRES_USER=tutor_base_e2e `
        -e POSTGRES_PASSWORD=$databasePassword `
        -p "127.0.0.1:${DatabasePort}:5432" `
        --health-cmd='pg_isready -U tutor_base_e2e -d tutor_base_e2e' `
        --health-interval=2s --health-timeout=2s --health-retries=30 `
        -d postgres:17-alpine | Out-Null

    for ($attempt = 1; $attempt -le 30; $attempt++) {
        if ((docker inspect --format '{{.State.Health.Status}}' $containerName) -eq 'healthy') { break }
        if ($attempt -eq 30) { throw 'E2E PostgreSQL 未进入 healthy 状态。' }
        Start-Sleep -Seconds 2
    }

    $env:DATABASE_URL = "jdbc:postgresql://127.0.0.1:${DatabasePort}/tutor_base_e2e"
    $env:DATABASE_USERNAME = 'tutor_base_e2e'
    $env:DATABASE_PASSWORD = $databasePassword
    $env:DATABASE_MIGRATIONS_ENABLED = 'true'
    $env:TUTOR_CSRF_SECRET = 'e2e-only-csrf-secret-with-more-than-thirty-two-bytes'
    $env:TUTOR_SESSION_COOKIE_NAME = 'TUTOR_SESSION'
    $env:TUTOR_SESSION_COOKIE_SECURE = 'false'
    $env:TUTOR_WEB_ALLOWED_ORIGINS = 'http://127.0.0.1:3000'
    $env:TUTOR_BOOTSTRAP_ENABLED = 'true'
    $env:TUTOR_BOOTSTRAP_USERNAME = 'e2e-admin'
    $env:TUTOR_BOOTSTRAP_PASSWORD = 'E2e-admin-password-2026'
    $env:E2E_ADMIN_USERNAME = 'e2e-admin'
    $env:E2E_ADMIN_PASSWORD = 'E2e-admin-password-2026'
    $env:VITE_API_BASE_URL = 'http://127.0.0.1:8080/api/v1'

    Push-Location (Join-Path $repository 'backend')
    try {
        .\mvnw.cmd -B -ntp package '-DskipTests'
        if ($LASTEXITCODE -ne 0) { throw '后端打包失败。' }
        $applicationJar = Get-ChildItem 'target\tutor-base-backend-*.jar' |
            Where-Object { $_.Name -notlike '*.original' } |
            Select-Object -First 1 -ExpandProperty FullName
        if (-not $applicationJar) { throw '没有找到后端可执行 JAR。' }

        $env:SPRING_PROFILES_ACTIVE = 'bootstrap-admin'
        & java -jar $applicationJar
        if ($LASTEXITCODE -ne 0) { throw 'E2E 管理员自举失败。' }
        Remove-Item Env:SPRING_PROFILES_ACTIVE
        $env:TUTOR_BOOTSTRAP_ENABLED = 'false'

        $backendProcess = Start-Process -FilePath 'java' `
            -ArgumentList '-jar', $applicationJar `
            -WorkingDirectory (Join-Path $repository 'backend') `
            -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput $backendLog -RedirectStandardError $backendErrorLog
    } finally {
        Pop-Location
    }

    for ($attempt = 1; $attempt -le 60; $attempt++) {
        try {
            Invoke-RestMethod 'http://127.0.0.1:8080/actuator/health/readiness' | Out-Null
            break
        } catch {
            if ($attempt -eq 60) { throw "后端未就绪，请检查 $backendLog 和 $backendErrorLog。" }
            Start-Sleep -Seconds 2
        }
    }

    Push-Location $repository
    try {
        npm run test:e2e
        if ($LASTEXITCODE -ne 0) { throw 'Playwright 端到端测试失败。' }
    } finally {
        Pop-Location
    }
} finally {
    if ($backendProcess -and -not $backendProcess.HasExited) {
        Stop-Process -Id $backendProcess.Id -Force -ErrorAction SilentlyContinue
    }
    docker rm --force $containerName 2>$null | Out-Null
    Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
}
