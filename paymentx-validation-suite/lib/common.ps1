# ==============================================================================
# PaymentX Validation Suite - shared helper library
# Dot-source this from any script in ../scripts: . "$PSScriptRoot\..\lib\common.ps1"
# ==============================================================================

$Script:Results = New-Object System.Collections.Generic.List[PSObject]
$Script:StartTime = Get-Date

# Single source of truth for every script that starts/stops/health-checks the
# platform (run-e2e.ps1, start-all.ps1, stop-all.ps1, health-check.ps1) -
# defined once here so port/jar/module names can never drift between them.
$Script:Root = 'C:\PaymentX'
$Script:SuiteRoot = "$Script:Root\paymentx-validation-suite"
$Script:OutDir = "$Script:Root\paymentx-validation-output"
$Script:GatewayUrl = 'http://localhost:8080'

$Script:Services = [ordered]@{
    'api-gateway'             = @{ Port = 8080; Module = 'paymentx-api-gateway';             Jar = 'paymentx-api-gateway-0.1.0-SNAPSHOT.jar' }
    'auth-service'            = @{ Port = 8081; Module = 'paymentx-auth-service';            Jar = 'paymentx-auth-service-0.1.0-SNAPSHOT.jar' }
    'validation-service'      = @{ Port = 8082; Module = 'paymentx-validation-service';      Jar = 'paymentx-validation-service-0.1.0-SNAPSHOT.jar' }
    'payment-service'         = @{ Port = 8083; Module = 'paymentx-payment-service';         Jar = 'paymentx-payment-service-0.1.0-SNAPSHOT.jar' }
    'routing-service'         = @{ Port = 8084; Module = 'paymentx-routing-service';         Jar = 'paymentx-routing-service-0.1.0-SNAPSHOT.jar' }
    'audit-service'           = @{ Port = 8085; Module = 'paymentx-audit-service';           Jar = 'paymentx-audit-service-0.1.0-SNAPSHOT.jar' }
    'notification-service'    = @{ Port = 8086; Module = 'paymentx-notification-service';    Jar = 'paymentx-notification-service-0.1.0-SNAPSHOT.jar' }
    'reconciliation-service'  = @{ Port = 8087; Module = 'paymentx-reconciliation-service';  Jar = 'paymentx-reconciliation-service-0.1.0-SNAPSHOT.jar' }
    'reporting-service'       = @{ Port = 8088; Module = 'paymentx-reporting-service';       Jar = 'paymentx-reporting-service-0.1.0-SNAPSHOT.jar' }
}

$Script:InfraChecks = [ordered]@{
    'PostgreSQL' = @{ Container='paymentx-postgres'; Check={ (docker exec paymentx-postgres pg_isready -U postgres 2>&1) -match 'accepting connections' } }
    'Redis'      = @{ Container='paymentx-redis';    Check={ (docker exec paymentx-redis redis-cli ping 2>&1) -match 'PONG' } }
    'Kafka'      = @{ Container='paymentx-kafka';    Check={ (docker exec paymentx-kafka kafka-broker-api-versions --bootstrap-server localhost:9092 2>&1) -notmatch 'refused|timed out|Error' } }
    'RabbitMQ'   = @{ Container='paymentx-rabbitmq'; Check={ (docker exec paymentx-rabbitmq rabbitmq-diagnostics ping 2>&1) -match 'Ping succeeded' } }
    'Zipkin'     = @{ Container='paymentx-zipkin';   Check={ (Invoke-HealthCheck 'http://localhost:9411/health').Ok } }
    'Prometheus' = @{ Container='paymentx-prometheus'; Check={ (Invoke-HealthCheck 'http://localhost:9090/-/healthy').Ok } }
    'Grafana'    = @{ Container='paymentx-grafana';  Check={ (Invoke-HealthCheck 'http://localhost:3000/api/health').Ok } }
    'MailHog'    = @{ Container='paymentx-mailhog';  Check={ (Invoke-HealthCheck 'http://localhost:8025/api/v2/messages').Ok } }
    'pgAdmin'    = @{ Container='paymentx-pgadmin';  Check={ (Invoke-HealthCheck 'http://localhost:5050/misc/ping').Ok } }
    'Kafka UI'   = @{ Container='paymentx-kafka-ui'; Check={ (Invoke-HealthCheck 'http://localhost:8090/actuator/health').Ok } }
}

function ConvertTo-SafeString {
    # Defensive stringification: never let a weird value (unexpected API
    # response shape, $null property chain, an array, a nested object)
    # from a live external call crash a long-running validation run. This
    # is exactly the kind of thing that happened when Prometheus/Grafana/
    # Zipkin returned a shape one of the checks didn't expect.
    param([Parameter(ValueFromPipeline)] $Value)
    if ($null -eq $Value) { return '' }
    if ($Value -is [string]) { return $Value }
    if ($Value -is [System.Array]) { return ($Value -join ', ') }
    try { return [string]$Value } catch { return ($Value | Out-String).Trim() }
}

function Add-Result {
    param(
        [Parameter(Mandatory)] [string]$Category,
        [Parameter(Mandatory)] [string]$Name,
        [Parameter(Mandatory)] [ValidateSet('PASS','FAIL','SKIP','N/A')] [string]$Status,
        $Detail = '',
        $Fix = ''
    )
    $Detail = ConvertTo-SafeString $Detail
    $Fix = ConvertTo-SafeString $Fix
    $entry = [PSCustomObject]@{
        Category = $Category
        Name     = $Name
        Status   = $Status
        Detail   = $Detail
        Fix      = $Fix
        At       = Get-Date -Format 'HH:mm:ss'
    }
    $Script:Results.Add($entry) | Out-Null
    $color = switch ($Status) { 'PASS' {'Green'} 'FAIL' {'Red'} 'SKIP' {'Yellow'} default {'DarkGray'} }
    Write-Host ("  [{0,-4}] {1,-28} {2}" -f $Status, $Name, $Detail) -ForegroundColor $color
    # Deliberately no return value: PowerShell auto-emits any non-void
    # expression to the success stream, which - when the whole script is
    # captured with `*> logfile` - dumps a full default-formatted property
    # table after every single call site that doesn't explicitly discard
    # it. Every call site in this suite ignores the return value anyway.
}

function Write-Section {
    param([string]$Title)
    Write-Host ""
    Write-Host ("=" * 78) -ForegroundColor Cyan
    Write-Host $Title -ForegroundColor Cyan
    Write-Host ("=" * 78) -ForegroundColor Cyan
}

function Invoke-HealthCheck {
    param([string]$Url, [int]$TimeoutSec = 3)
    try {
        $resp = Invoke-WebRequest -Uri $Url -TimeoutSec $TimeoutSec -UseBasicParsing -ErrorAction Stop
        return @{ Ok = ($resp.StatusCode -eq 200); Code = $resp.StatusCode; Body = $resp.Content }
    } catch {
        $code = $null
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        return @{ Ok = $false; Code = $code; Body = $_.Exception.Message }
    }
}

function Wait-ForHealthy {
    param([string]$Url, [int]$TimeoutSec = 60, [int]$PollSec = 2)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $r = Invoke-HealthCheck -Url $Url -TimeoutSec 3
        if ($r.Ok) { return $true }
        Start-Sleep -Seconds $PollSec
    }
    return $false
}

# Runs SQL text against the real postgres container (not a mock connection).
# Splits on \c the same way psql itself does, since we pipe the whole script
# to one psql invocation.
function Invoke-Psql {
    param(
        [Parameter(Mandatory)] [string]$SqlFile,
        [string]$Database = 'postgres'
    )
    $content = Get-Content -Raw -Path $SqlFile
    $output = $content | docker exec -i paymentx-postgres psql -U postgres -d $Database -v ON_ERROR_STOP=1 2>&1
    return @{ ExitCode = $LASTEXITCODE; Output = ($output -join "`n") }
}

function Invoke-PsqlQuery {
    param(
        [Parameter(Mandatory)] [string]$Database,
        [Parameter(Mandatory)] [string]$Query
    )
    $output = docker exec paymentx-postgres psql -U postgres -d $Database -t -A -c $Query 2>&1
    return @{ ExitCode = $LASTEXITCODE; Output = ($output -join "`n").Trim() }
}

function Invoke-KafkaTopics {
    param([Parameter(Mandatory)] [string[]]$Args)
    $output = docker exec paymentx-kafka kafka-topics --bootstrap-server localhost:9092 @Args 2>&1
    return @{ ExitCode = $LASTEXITCODE; Output = ($output -join "`n") }
}

function Invoke-Redis {
    param([Parameter(Mandatory)] [string[]]$Args)
    $output = docker exec paymentx-redis redis-cli @Args 2>&1
    return @{ ExitCode = $LASTEXITCODE; Output = ($output -join "`n").Trim() }
}

function Invoke-RabbitApi {
    param(
        [Parameter(Mandatory)] [string]$Path,
        [string]$Method = 'GET',
        [object]$Body = $null
    )
    $cred = [System.Convert]::ToBase64String([System.Text.Encoding]::ASCII.GetBytes('guest:guest'))
    $headers = @{ Authorization = "Basic $cred" }
    $uri = "http://localhost:15672/api/$Path"
    try {
        if ($Body -ne $null) {
            $json = $Body | ConvertTo-Json -Depth 6
            $resp = Invoke-RestMethod -Uri $uri -Method $Method -Headers $headers -Body $json -ContentType 'application/json' -ErrorAction Stop
        } else {
            $resp = Invoke-RestMethod -Uri $uri -Method $Method -Headers $headers -ErrorAction Stop
        }
        return @{ Ok = $true; Data = $resp }
    } catch {
        return @{ Ok = $false; Error = $_.Exception.Message }
    }
}

# Mints a REAL, validly-signed HS256 JWT using the gateway's own configured
# dev secret (gateway.security.jwt-secret in application.yml - a checked-in
# local-dev-only value, not a production secret). auth-service has no
# implementation yet (bare @SpringBootApplication skeleton, no controllers),
# so there is no real IdP to obtain a token from in this environment; this
# mints exactly the token a client holding that shared dev secret could
# legitimately present, and the gateway validates it with the real
# NimbusReactiveJwtDecoder - not a mocked/bypassed check.
function New-TestJwt {
    param(
        [string]$Secret = 'local-dev-only-secret-change-me-32chars',
        [string]$ParticipantId = 'BANK001',
        [string[]]$Roles = @('ROUTING_ADMIN','AUDIT_WRITER'),
        [int]$ExpirySeconds = 3600
    )
    function ConvertTo-Base64Url([byte[]]$bytes) {
        return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+','-').Replace('/','_')
    }
    $header = @{ alg = 'HS256'; typ = 'JWT' } | ConvertTo-Json -Compress
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $payload = @{
        sub           = $ParticipantId
        participantId = $ParticipantId
        roles         = $Roles
        iat           = $now
        nbf           = $now
        exp           = $now + $ExpirySeconds
        iss           = 'paymentx-validation-suite'
    } | ConvertTo-Json -Compress
    $headerB64  = ConvertTo-Base64Url ([System.Text.Encoding]::UTF8.GetBytes($header))
    $payloadB64 = ConvertTo-Base64Url ([System.Text.Encoding]::UTF8.GetBytes($payload))
    $signingInput = "$headerB64.$payloadB64"
    $hmac = New-Object System.Security.Cryptography.HMACSHA256
    $hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($Secret)
    $sig = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($signingInput))
    $sigB64 = ConvertTo-Base64Url $sig
    return "$signingInput.$sigB64"
}

function Get-JsonSafe {
    param([string]$Text)
    try { return $Text | ConvertFrom-Json -ErrorAction Stop } catch { return $null }
}

# WHY this exists instead of `Invoke-RestMethod -Form @{...}`: -Form was
# added in PowerShell 6.1 (pwsh, cross-platform). This suite runs under
# Windows PowerShell 5.1 (powershell.exe) - the platform default on a plain
# Windows box - where -Form is not a recognized parameter at all and fails
# every call site that uses it. Building the multipart/form-data body by
# hand is the PS 5.1-compatible way to do a real file upload.
function Invoke-MultipartFileUpload {
    param(
        [Parameter(Mandatory)] [string]$Uri,
        [Parameter(Mandatory)] [string]$FilePath,
        [string]$FieldName = 'file',
        [hashtable]$Headers = @{},
        [int]$TimeoutSec = 15
    )
    $boundary = [System.Guid]::NewGuid().ToString()
    $fileName = [System.IO.Path]::GetFileName($FilePath)
    $fileText = [System.IO.File]::ReadAllText($FilePath)
    $LF = "`r`n"
    $bodyText = (
        "--$boundary",
        "Content-Disposition: form-data; name=`"$FieldName`"; filename=`"$fileName`"",
        "Content-Type: text/csv",
        "",
        $fileText,
        "--$boundary--",
        ""
    ) -join $LF
    $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($bodyText)
    return Invoke-RestMethod -Uri $Uri -Method POST -Headers $Headers `
        -ContentType "multipart/form-data; boundary=$boundary" -Body $bodyBytes -TimeoutSec $TimeoutSec
}
