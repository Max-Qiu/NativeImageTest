
# ============================================================
#  test-api.ps1 -- NativeImageTest API Test Suite
#  Requires: service running at http://localhost:8080
#            DB initialized with db/init-schema.sql
# ============================================================

$BaseUrl   = 'http://localhost:8080'
$AdminUser = 'test-admin'
$AdminPass = 'admin123'

$script:session   = $null
$script:newUserId = $null   # ID of user created in PART 1

# -------------------------------------------------------
# Helpers
# -------------------------------------------------------
function Invoke-Test {
    param([string]$Title, [scriptblock]$Action)
    Write-Host "`n========== $Title ==========" -ForegroundColor Cyan
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $result = & $Action
        $sw.Stop()
        Write-Host "elapsed: $($sw.ElapsedMilliseconds)ms" -ForegroundColor Yellow
        if ($null -ne $result) {
            $result | ConvertTo-Json -Depth 5 -Compress | Write-Host -ForegroundColor Green
        }
        return $result
    } catch {
        $sw.Stop()
        Write-Host "FAILED: $($_.Exception.Message)" -ForegroundColor Red
        return $null
    }
}

function Req {
    param(
        [string]$Path,
        [string]$Method = 'Get',
        $Body = $null
    )
    $params = @{
        Uri        = "$BaseUrl$Path"
        Method     = $Method
        WebSession = $script:session
    }
    if ($null -ne $Body) {
        $params.Body        = ($Body | ConvertTo-Json)
        $params.ContentType = 'application/json'
    }
    Invoke-RestMethod @params
}


# ============================================================
Write-Host "`n###############  PART 0 : Setup -- register admin + login  ###############" -ForegroundColor Magenta
# ============================================================

Invoke-Test '0a. POST /auth/register -- register admin (skip if exists)' {
    try {
        Invoke-RestMethod -Uri "$BaseUrl/auth/register" -Method Post `
            -Body (@{ username = $AdminUser; password = $AdminPass; role = 'ADMIN' } | ConvertTo-Json) `
            -ContentType 'application/json'
    } catch {
        Write-Host "(already exists, skipped: $($_.Exception.Message))" -ForegroundColor DarkGray
        $null
    }
}

Invoke-Test '0b. POST /auth/login -- form login (SESSION stored in Redis)' {
    Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post `
        -Body @{ username = $AdminUser; password = $AdminPass } `
        -ContentType 'application/x-www-form-urlencoded' `
        -SessionVariable sv -UseBasicParsing | Out-Null
    $script:session = $sv
    "login OK; cookies=$($sv.Cookies.Count)"
}

Invoke-Test '0c. GET /auth/me -- verify session' {
    Req '/auth/me'
}

Invoke-Test '0d. GET /auth/roles -- list current roles' {
    Req '/auth/roles'
}


# ============================================================
Write-Host "`n###############  PART 1 : MySQL CRUD  (t_user table)  ###############" -ForegroundColor Magenta
# ============================================================

Invoke-Test '1a. GET /users -- list all' {
    Req '/users'
}

$created = Invoke-Test '1b. POST /users -- create user' {
    Req '/users' 'Post' @{ name = 'Henry'; age = 28 }
}
if ($null -ne $created) { $script:newUserId = $created.id }
Write-Host "  -> created user ID: $($script:newUserId)" -ForegroundColor DarkYellow

Invoke-Test '1c. GET /users/{id} -- get by id' {
    Req "/users/$($script:newUserId)"
}

Invoke-Test '1d. PUT /users/{id} -- update name and age' {
    Req "/users/$($script:newUserId)" 'Put' @{ name = 'Henry-Updated'; age = 29 }
}

Invoke-Test '1e. GET /users -- list all (verify update)' {
    Req '/users'
}

Invoke-Test '1f. DELETE /users/{id} -- delete user' {
    Req "/users/$($script:newUserId)" 'Delete'
}

Invoke-Test '1g. GET /users -- list all (verify delete)' {
    Req '/users'
}

Invoke-Test '1h. GET /users/stats/age -- XML SQL: group by age with COUNT' {
    Req '/users/stats/age'
}


# ============================================================
Write-Host "`n###############  PART 2 : Spring Cache  (@Cacheable / @CachePut / @CacheEvict)  ###############" -ForegroundColor Magenta
# ============================================================

Invoke-Test '2a. DELETE /cache/anno/evictAll -- clear all cache entries' {
    Req '/cache/anno/evictAll' 'Delete'
}

Invoke-Test '2b. POST /cache/anno/resetHits -- reset hit counter' {
    Req '/cache/anno/resetHits' 'Post'
}

Invoke-Test '2c. GET /cache/anno/load?key=foo -- first load, @Cacheable miss, hits=1' {
    Req '/cache/anno/load?key=foo'
}

Invoke-Test '2d. GET /cache/anno/load?key=foo -- second load, @Cacheable hit, hits unchanged' {
    Req '/cache/anno/load?key=foo'
}

Invoke-Test '2e. GET /cache/anno/load?key=bar -- different key, cache miss, hits=2' {
    Req '/cache/anno/load?key=bar'
}

Invoke-Test '2f. POST /cache/anno/put -- @CachePut overwrite key=foo' {
    Req '/cache/anno/put?key=foo&value=manual' 'Post'
}

Invoke-Test '2g. GET /cache/anno/load?key=foo -- @Cacheable hits @CachePut value, hits unchanged' {
    Req '/cache/anno/load?key=foo'
}

Invoke-Test '2h. DELETE /cache/anno/evict?key=foo -- @CacheEvict single key' {
    Req '/cache/anno/evict?key=foo' 'Delete'
}

Invoke-Test '2i. GET /cache/anno/load?key=foo -- cache cleared, method runs again, hits+1' {
    Req '/cache/anno/load?key=foo'
}

Invoke-Test '2j. DELETE /cache/anno/evictAll -- @CacheEvict allEntries=true' {
    Req '/cache/anno/evictAll' 'Delete'
}

Invoke-Test '2k. GET /cache/anno/load?key=bar -- cache cleared, method runs again, hits+1' {
    Req '/cache/anno/load?key=bar'
}


# ============================================================
Write-Host "`n###############  PART 3 : RedisTemplate  (StringRedisTemplate direct ops)  ###############" -ForegroundColor Magenta
# ============================================================

Invoke-Test '3a. POST /cache/redis/set -- write key with no TTL' {
    Req '/cache/redis/set?key=native-demo:plain:hello&value=world' 'Post'
}

Invoke-Test '3b. POST /cache/redis/set -- write key with TTL=10s' {
    Req '/cache/redis/set?key=native-demo:plain:ttl&value=expires-soon&ttlSeconds=10' 'Post'
}

Invoke-Test '3c. GET /cache/redis/get -- read key value' {
    Req '/cache/redis/get?key=native-demo:plain:hello'
}

Invoke-Test '3d. GET /cache/redis/keys -- list all native-demo:* keys' {
    Req '/cache/redis/keys'
}

Invoke-Test '3e. DELETE /cache/redis/delete -- delete key' {
    Req '/cache/redis/delete?key=native-demo:plain:hello' 'Delete'
}

Invoke-Test '3f. GET /cache/redis/get -- after delete (should return null)' {
    Req '/cache/redis/get?key=native-demo:plain:hello'
}

Invoke-Test '3g. GET /cache/redis/keys -- final key list' {
    Req '/cache/redis/keys'
}


# ============================================================
Write-Host "`n###############  PART 4 : Logout  ###############" -ForegroundColor Magenta
# ============================================================

Invoke-Test '4a. POST /auth/logout -- destroy Redis session' {
    Req '/auth/logout' 'Post'
}

Invoke-Test '4b. GET /auth/me -- after logout (expect 401)' {
    try {
        Req '/auth/me'
    } catch {
        "401 as expected: $($_.Exception.Message)"
    }
}

Write-Host "`n========== All tests done ==========" -ForegroundColor Cyan
