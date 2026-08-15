param(
    [Parameter(Mandatory = $false)]
    [string]$BaseUrl = 'http://127.0.0.1:8080',

    [Parameter(Mandatory = $false)]
    [string]$Username = 'young'
)

$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
$uri = [Uri]$BaseUrl
if ($uri.Scheme -ne 'https' -and -not @('127.0.0.1', 'localhost').Contains($uri.Host)) {
    throw 'Remote smoke tests require HTTPS.'
}

$securePassword = Read-Host 'Administrator password (hidden)' -AsSecureString
$passwordPointer = [IntPtr]::Zero
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
$client = $null
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    Add-Type -AssemblyName System.Net.Http
    $handler = New-Object System.Net.Http.HttpClientHandler
    $handler.CookieContainer = New-Object System.Net.CookieContainer
    $client = New-Object System.Net.Http.HttpClient($handler)

    foreach ($path in @(
        '/api/v1/system/status',
        '/actuator/health/liveness',
        '/actuator/health/readiness'
    )) {
        $response = $client.GetAsync("$BaseUrl$path").Result
        $response.EnsureSuccessStatusCode() | Out-Null
    }

    $csrfResponse = $client.GetAsync("$BaseUrl/api/v1/csrf").Result
    $csrfResponse.EnsureSuccessStatusCode() | Out-Null
    $csrf = ($csrfResponse.Content.ReadAsStringAsync().Result | ConvertFrom-Json).token

    $loginRequest = New-Object System.Net.Http.HttpRequestMessage(
        [System.Net.Http.HttpMethod]::Post,
        "$BaseUrl/api/v1/sessions")
    $loginRequest.Headers.Add('X-CSRF-TOKEN', $csrf)
    $loginJson = @{ username = $Username; password = $password } | ConvertTo-Json -Compress
    $loginRequest.Content = New-Object System.Net.Http.StringContent(
        $loginJson,
        [System.Text.Encoding]::UTF8,
        'application/json')
    $loginResponse = $client.SendAsync($loginRequest).Result
    $loginResponse.EnsureSuccessStatusCode() | Out-Null

    $sessionResponse = $client.GetAsync("$BaseUrl/api/v1/session").Result
    $sessionResponse.EnsureSuccessStatusCode() | Out-Null
    $session = $sessionResponse.Content.ReadAsStringAsync().Result | ConvertFrom-Json
    if ($session.username -ne $Username -or $session.roles -notcontains 'ADMINISTRATOR') {
        throw 'The authenticated session does not contain the expected administrator identity.'
    }

    $learnersResponse = $client.GetAsync("$BaseUrl/api/v1/admin/learners?limit=1").Result
    $learnersResponse.EnsureSuccessStatusCode() | Out-Null

    $logoutCsrfResponse = $client.GetAsync("$BaseUrl/api/v1/csrf").Result
    $logoutCsrfResponse.EnsureSuccessStatusCode() | Out-Null
    $logoutCsrf = ($logoutCsrfResponse.Content.ReadAsStringAsync().Result | ConvertFrom-Json).token
    $logoutRequest = New-Object System.Net.Http.HttpRequestMessage(
        [System.Net.Http.HttpMethod]::Delete,
        "$BaseUrl/api/v1/session")
    $logoutRequest.Headers.Add('X-CSRF-TOKEN', $logoutCsrf)
    $logoutResponse = $client.SendAsync($logoutRequest).Result
    if ([int]$logoutResponse.StatusCode -ne 204) {
        throw "Expected logout status 204, received $([int]$logoutResponse.StatusCode)."
    }

    $expiredSession = $client.GetAsync("$BaseUrl/api/v1/session").Result
    if ([int]$expiredSession.StatusCode -ne 401) {
        throw "Expected session status 401 after logout, received $([int]$expiredSession.StatusCode)."
    }

    Write-Output 'smoke-test=passed'
    Write-Output "username=$Username"
    Write-Output 'role=ADMINISTRATOR'
    Write-Output 'logout=204'
    Write-Output 'session-after-logout=401'
} finally {
    $password = $null
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
    if ($client) {
        $client.Dispose()
    }
}
