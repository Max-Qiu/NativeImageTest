[CmdletBinding()]
param([string]$BaseUrl = 'http://localhost:8080')

$ErrorActionPreference = 'Stop'
$checks = [System.Collections.Generic.List[string]]::new()
$session = $null

function Check([string]$Name, [bool]$Passed) {
    if (-not $Passed) { throw "Assertion failed: $Name" }
    $checks.Add($Name)
    Write-Host "PASS: $Name"
}

function Request([string]$Path, [string]$Method = 'Get', $Body = $null) {
    $options = @{ Uri = "$BaseUrl$Path"; Method = $Method; TimeoutSec = 20; UseBasicParsing = $true }
    if ($null -ne $session) { $options.WebSession = $session }
    if ($null -ne $Body) {
        $options.Body = ConvertTo-Json -InputObject $Body -Depth 6 -Compress
        $options.ContentType = 'application/json'
    }
    try {
        $response = Invoke-WebRequest @options
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Body = ($response.Content | ConvertFrom-Json) }
    } catch {
        if ($null -eq $_.Exception.Response) { throw }
        $status = [int]$_.Exception.Response.StatusCode
        $json = $_.ErrorDetails.Message
        if ([string]::IsNullOrEmpty($json)) {
            # Windows PowerShell 5.1 does not always populate ErrorDetails.
            $reader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
            try { $json = $reader.ReadToEnd() } finally { $reader.Dispose() }
        }
        $bodyValue = if ([string]::IsNullOrWhiteSpace($json)) { $null } else { $json | ConvertFrom-Json }
        return [pscustomobject]@{ Status = $status; Body = $bodyValue }
    }
}

function ValidBody {
    return @{
        name = 'Native Demo'; email = 'demo@example.com'; age = 28
        address = @{ city = 'Shanghai'; zipCode = '200000' }
        tags = @('native'); code = 'DEMO-ABC'
    }
}

function ExpectInvalid([string]$Name, [string]$Path, [string]$Method, $Body, [string]$Field) {
    $response = Request $Path $Method $Body
    $violations = @($response.Body.violations)
    $matching = @($violations | Where-Object { $_.field -eq $Field -and -not [string]::IsNullOrWhiteSpace($_.message) })
    Check $Name ($response.Status -eq 400 -and $response.Body.error -eq 'VALIDATION_FAILED' -and $matching.Count -gt 0)
}

try {
    $unauthenticated = Request '/validation/body' 'Post' (ValidBody)
    Check 'Validation endpoints require authentication' ($unauthenticated.Status -eq 401)
    $registration = Request '/auth/register' 'Post' @{ username = 'test-admin'; password = 'admin123'; role = 'ADMIN' }
    if ($registration.Status -ne 200 -and $registration.Status -ne 409) { throw "Admin setup failed: HTTP $($registration.Status)" }
    $null = Invoke-WebRequest -Uri "$BaseUrl/auth/login" -Method Post -UseBasicParsing `
        -Body @{ username = 'test-admin'; password = 'admin123' } `
        -ContentType 'application/x-www-form-urlencoded' -SessionVariable validationSession -TimeoutSec 20
    $session = $validationSession

    $valid = Request '/validation/body' 'Post' (ValidBody)
    Check 'Valid body round trip includes nested object and collection' ($valid.Status -eq 200 -and $valid.Body.name -eq 'Native Demo' -and $valid.Body.address.zipCode -eq '200000' -and $valid.Body.tags[0] -eq 'native' -and $valid.Body.code -eq 'DEMO-ABC')

    $body = ValidBody; $body.name = ' '
    ExpectInvalid 'NotBlank rejects blank name' '/validation/body' 'Post' $body 'name'
    $body = ValidBody; $body.name = 'x' * 41
    ExpectInvalid 'Size rejects overly long name' '/validation/body' 'Post' $body 'name'
    $body = ValidBody; $body.email = 'invalid'
    ExpectInvalid 'Email rejects malformed address' '/validation/body' 'Post' $body 'email'
    $body = ValidBody; $body.age = $null
    ExpectInvalid 'NotNull rejects null age' '/validation/body' 'Post' $body 'age'
    $body = ValidBody; $body.age = 17
    ExpectInvalid 'Min rejects age below minimum' '/validation/body' 'Post' $body 'age'
    $body = ValidBody; $body.age = 121
    ExpectInvalid 'Max rejects age above maximum' '/validation/body' 'Post' $body 'age'
    $body = ValidBody; $body.address = $null
    ExpectInvalid 'NotNull rejects missing nested object' '/validation/body' 'Post' $body 'address'
    $body = ValidBody; $body.address.city = ''
    ExpectInvalid 'Valid cascades to nested city' '/validation/body' 'Post' $body 'address.city'
    $body = ValidBody; $body.address.zipCode = 'invalid'
    ExpectInvalid 'Pattern checks nested zip code' '/validation/body' 'Post' $body 'address.zipCode'
    $body = ValidBody; $body.tags = @()
    ExpectInvalid 'NotEmpty rejects empty collection' '/validation/body' 'Post' $body 'tags'
    $body = ValidBody; $body.tags = @('')
    ExpectInvalid 'Container element constraint rejects blank tag' '/validation/body' 'Post' $body 'tags[0]'
    $body = ValidBody; $body.code = 'INVALID'
    ExpectInvalid 'Custom ConstraintValidator rejects invalid demo code' '/validation/body' 'Post' $body 'code'

    ExpectInvalid 'Update group requires ID' '/validation/body' 'Put' (ValidBody) 'id'
    $body = ValidBody; $body.id = 1
    $updated = Request '/validation/body' 'Put' $body
    Check 'Update group accepts valid ID and fields' ($updated.Status -eq 200 -and $updated.Body.id -eq 1)
    $body.name = ''
    ExpectInvalid 'Update group inherits Default field constraints' '/validation/body' 'Put' $body 'name'

    foreach ($count in @(1, 10)) {
        $response = Request "/validation/parameters?count=$count"
        Check "MVC method validation accepts boundary $count" ($response.Status -eq 200 -and $response.Body.count -eq $count)
    }
    foreach ($count in @(0, 11)) {
        ExpectInvalid "MVC method validation rejects count $count" "/validation/parameters?count=$count" 'Get' $null 'count'
    }
    $response = Request '/validation/service?value=native'
    Check 'Validated service accepts valid argument' ($response.Status -eq 200 -and $response.Body.value -eq 'native')
    foreach ($value in @('', 'x', '   ', ('x' * 21))) {
        $encoded = [Uri]::EscapeDataString($value)
        ExpectInvalid "Service proxy rejects invalid argument length $($value.Length)" "/validation/service?value=$encoded" 'Get' $null 'echo.value'
    }

    $reportDirectory = Join-Path $PSScriptRoot 'target'
    $null = New-Item -ItemType Directory -Path $reportDirectory -Force
    [pscustomobject]@{ passed = $checks.Count; checks = $checks; timestamp = (Get-Date).ToString('o') } |
        ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $reportDirectory 'validation-assertions.json')
    Write-Host "All $($checks.Count) validation assertions passed."
} finally {
    if ($null -ne $session) {
        try { $null = Request '/auth/logout' 'Post' }
        catch { Write-Warning "Could not log out validation session: $_" }
    }
}
