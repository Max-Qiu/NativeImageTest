[CmdletBinding()]
param([string]$BaseUrl = 'http://localhost:8080')

$ErrorActionPreference = 'Stop'
$checks = [System.Collections.Generic.List[string]]::new()
$session = $null
$createdId = $null
$suffix = [Guid]::NewGuid().ToString('N')
$cacheKey = "native-verify-$suffix"
$redisKey = "native-demo:verify:$suffix"

function Check([string]$Name, [bool]$Passed) {
    if (-not $Passed) { throw "Assertion failed: $Name" }
    $checks.Add($Name)
    Write-Output "PASS: $Name"
}

function Request([string]$Path, [string]$Method = 'Get', $Body = $null) {
    $options = @{ Uri = "$baseUrl$Path"; Method = $Method; TimeoutSec = 20 }
    if ($null -ne $session) { $options.WebSession = $session }
    if ($null -ne $Body) {
        $options.Body = ConvertTo-Json -InputObject $Body -Compress
        $options.ContentType = 'application/json'
    }
    Write-Output -InputObject (Invoke-RestMethod @options)
}

function ExpectStatus([string]$Path, [string]$Method, [int]$Status, $Body = $null) {
    try { $null = Request $Path $Method $Body } catch {
        if ($null -ne $_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq $Status) { return }
        throw
    }
    throw "Expected HTTP $Status for $Method $Path"
}

try {
    ExpectStatus '/users' 'Get' 401
    Check 'Unauthenticated business request returns 401' $true
    ExpectStatus '/auth/register' 'Post' 409 @{ username = 'test-admin'; password = 'admin123'; role = 'ADMIN' }
    Check 'Duplicate registration returns 409' $true

    $null = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body @{ username = 'test-admin'; password = 'admin123' } -ContentType 'application/x-www-form-urlencoded' -SessionVariable nativeSession -TimeoutSec 20
    $session = $nativeSession
    Check 'Login sets session cookie' ($session.Cookies.GetCookies([Uri]$baseUrl).Count -gt 0)
    $me = Request '/auth/me'
    Check 'Redis session restores authenticated admin' ($me.authenticated -and $me.username -eq 'test-admin' -and $me.authorities -contains 'ROLE_ADMIN')
    $roles = Request '/auth/roles'
    Check 'Role endpoint returns ADMIN' ($roles -contains 'ROLE_ADMIN')

    $initialUsers = @(Request '/users')
    $initialStats = @(Request '/users/stats/age')
    $initialAgeCount = ($initialStats | Where-Object { $_.age -eq 93 } | Measure-Object -Property count -Sum).Sum
    $created = Request '/users' 'Post' @{ name = "native-verify-$suffix"; age = 93 }
    $createdId = $created.id
    Check 'MyBatis-Plus insert generates ID and binds fields' ($null -ne $createdId -and $created.name -eq "native-verify-$suffix" -and $created.age -eq 93)
    $loaded = Request "/users/$createdId"
    Check 'MyBatis-Plus select restores inserted record' ($loaded.id -eq $createdId -and $loaded.name -eq $created.name -and $loaded.age -eq 93)
    $stats = @(Request '/users/stats/age')
    $actualAgeCount = ($stats | Where-Object { $_.age -eq 93 } | Measure-Object -Property count -Sum).Sum
    Check 'XML mapper binds AgeCount DTO and returns correct aggregate' ($actualAgeCount -eq ($initialAgeCount + 1))
    $null = Request "/users/$createdId" 'Put' @{ name = "native-updated-$suffix"; age = 94 }
    $updated = Request "/users/$createdId"
    Check 'MyBatis-Plus update persists changed fields' ($updated.name -eq "native-updated-$suffix" -and $updated.age -eq 94)
    $deleted = Request "/users/$createdId" 'Delete'
    $remaining = @(Request '/users')
    Check 'MyBatis-Plus delete removes only the test record' ($deleted -eq $true -and @($remaining | Where-Object { $_.id -eq $createdId }).Count -eq 0 -and $remaining.Count -eq $initialUsers.Count)
    $createdId = $null

    $first = Request "/cache/anno/load?key=$cacheKey"
    $second = Request "/cache/anno/load?key=$cacheKey"
    Check 'Cacheable hit preserves value and method invocation counter' ($first.value -eq $second.value -and $first.hits -eq $second.hits)
    $put = Request "/cache/anno/put?key=$cacheKey&value=verified" 'Post'
    $afterPut = Request "/cache/anno/load?key=$cacheKey"
    Check 'CachePut updates Redis value without executing load again' ($afterPut.value -eq $put.value -and $afterPut.value.StartsWith('verified@') -and $afterPut.hits -eq $first.hits)
    $null = Request "/cache/anno/evict?key=$cacheKey" 'Delete'
    $afterEvict = Request "/cache/anno/load?key=$cacheKey"
    Check 'CacheEvict forces load method to execute again' ($afterEvict.hits -eq ($first.hits + 1) -and $afterEvict.value.StartsWith("value-of-$cacheKey@"))
    $null = Request "/cache/anno/evict?key=$cacheKey" 'Delete'

    $null = Request "/cache/redis/set?key=$redisKey&value=verified" 'Post'
    $redisValue = Request "/cache/redis/get?key=$redisKey"
    Check 'StringRedisTemplate round trip returns original value' ($redisValue -eq 'verified')
    $keys = Request '/cache/redis/keys'
    Check 'Redis key listing includes test key' ($keys -contains $redisKey)
    $removed = Request "/cache/redis/delete?key=$redisKey" 'Delete'
    $afterDelete = Request "/cache/redis/get?key=$redisKey"
    Check 'Redis delete removes the key' ($removed.deleted -and [string]::IsNullOrEmpty($afterDelete))
    $null = Request "/cache/redis/set?key=$redisKey&value=expires&ttlSeconds=1" 'Post'
    Start-Sleep -Seconds 2
    $afterExpiry = Request "/cache/redis/get?key=$redisKey"
    Check 'Redis TTL expires the key' ([string]::IsNullOrEmpty($afterExpiry))

    $null = Request '/auth/logout' 'Post'
    ExpectStatus '/auth/me' 'Get' 401
    Check 'Logout invalidates Redis session and returns 401 on reuse' $true
    $reportDirectory = Join-Path $PSScriptRoot 'target'
    $null = New-Item -ItemType Directory -Path $reportDirectory -Force
    [pscustomobject]@{ passed = $checks.Count; checks = $checks; timestamp = (Get-Date).ToString('o') } | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $reportDirectory 'native-assertions.json')
    Write-Output "All $($checks.Count) native behavior assertions passed."
} finally {
    if ($null -ne $createdId) {
        try { $null = Request "/users/$createdId" 'Delete' }
        catch { Write-Warning "Could not clean up test user ${createdId}: $_" }
    }
}
