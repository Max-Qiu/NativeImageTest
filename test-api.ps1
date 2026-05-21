$BaseUrl = "http://localhost:8080"

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
    } catch {
        $sw.Stop()
        Write-Host "FAILED: $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host "`n############### PART 1 : USER CRUD (MySQL) ###############" -ForegroundColor Magenta

Invoke-Test "1. GET /users (list)" {
    Invoke-RestMethod -Uri "$BaseUrl/users" -Method Get
}

Invoke-Test "2. POST /users (create)" {
    $body = @{ name = "Henry"; age = 33 } | ConvertTo-Json
    Invoke-RestMethod -Uri "$BaseUrl/users" -Method Post -Body $body -ContentType "application/json"
}

Invoke-Test "3. GET /users/1 (get by id)" {
    Invoke-RestMethod -Uri "$BaseUrl/users/1" -Method Get
}

Invoke-Test "4. PUT /users/2 (update)" {
    $body = @{ name = "BobUpdated"; age = 26 } | ConvertTo-Json
    Invoke-RestMethod -Uri "$BaseUrl/users/2" -Method Put -Body $body -ContentType "application/json"
}

Invoke-Test "5. GET /users/stats/age (group by age)" {
    Invoke-RestMethod -Uri "$BaseUrl/users/stats/age" -Method Get
}

Invoke-Test "6. DELETE /users/999 (non-existent)" {
    Invoke-RestMethod -Uri "$BaseUrl/users/999" -Method Delete
}


Write-Host "`n############### PART 2 : CACHE ANNOTATIONS (@Cacheable / @CachePut / @CacheEvict) ###############" -ForegroundColor Magenta

Invoke-Test "7. DELETE /cache/anno/evictAll (reset cache)" {
    Invoke-RestMethod -Uri "$BaseUrl/cache/anno/evictAll" -Method Delete
}

Invoke-Test "8. POST /cache/anno/resetHits (reset hit counter)" {
    Invoke-RestMethod -Uri "$BaseUrl/cache/anno/resetHits" -Method Post
}

Invoke-Test "9a. GET /cache/anno/load?key=foo (FIRST call: method runs, hits=1)" {
    Invoke-RestMethod -Uri "$BaseUrl/cache/anno/load?key=foo" -Method Get
}

Invoke-Test "9b. GET /cache/anno/load?key=foo (SECOND call: cache hit, hits stays 1)" {
    Invoke-RestMethod -Uri "$BaseUrl/cache/anno/load?key=foo" -Method Get
}

Invoke-Test "9c. GET /cache/anno/load?key=bar (different key: method runs, hits=2)" {
    Invoke-RestMethod -Uri "$BaseUrl/cache/anno/load?key=bar" -Method Get
}

Invoke-Test "10. POST /cache/anno/put?key=foo&value=manual (@CachePut overwrites cache)" {
    Invoke-RestMethod -Uri "$BaseUrl/cache/anno/put?key=foo&value=manual" -Method Post
}

Invoke-Test "11. GET /cache/anno/load?key=foo (returns @CachePut value, hits stays 2)" {
    Invoke-RestMethod -Uri "$BaseUrl/cache/anno/load?key=foo" -Method Get
}

Invoke-Test "12. DELETE /cache/anno/evict?key=foo (@CacheEvict one key)" {
    Invoke-RestMethod -Uri "$BaseUrl/cache/anno/evict?key=foo" -Method Delete
}

Invoke-Test "13. GET /cache/anno/load?key=foo (after evict: method re-runs, hits=3)" {
    Invoke-RestMethod -Uri "$BaseUrl/cache/anno/load?key=foo" -Method Get
}

Invoke-Test "14. DELETE /cache/anno/evictAll (@CacheEvict allEntries)" {
    Invoke-RestMethod -Uri "$BaseUrl/cache/anno/evictAll" -Method Delete
}

Invoke-Test "15. GET /cache/anno/load?key=bar (after evictAll: method re-runs, hits=4)" {
    Invoke-RestMethod -Uri "$BaseUrl/cache/anno/load?key=bar" -Method Get
}


Write-Host "`n############### PART 3 : StringRedisTemplate ###############" -ForegroundColor Magenta

Invoke-Test "16. POST /cache/redis/set?key=native-demo:plain:hello&value=world (no TTL)" {
    Invoke-RestMethod -Uri "$BaseUrl/cache/redis/set?key=native-demo:plain:hello&value=world" -Method Post
}

Invoke-Test "17. POST /cache/redis/set?key=native-demo:plain:ttl&value=10s&ttlSeconds=10" {
    Invoke-RestMethod -Uri "$BaseUrl/cache/redis/set?key=native-demo:plain:ttl&value=10s&ttlSeconds=10" -Method Post
}

Invoke-Test "18. GET /cache/redis/get?key=native-demo:plain:hello" {
    Invoke-RestMethod -Uri "$BaseUrl/cache/redis/get?key=native-demo:plain:hello" -Method Get
}

Invoke-Test "19. GET /cache/redis/keys (list all native-demo:* keys)" {
    Invoke-RestMethod -Uri "$BaseUrl/cache/redis/keys" -Method Get
}

Invoke-Test "20. DELETE /cache/redis/delete?key=native-demo:plain:hello" {
    Invoke-RestMethod -Uri "$BaseUrl/cache/redis/delete?key=native-demo:plain:hello" -Method Delete
}

Invoke-Test "21. GET /cache/redis/get?key=native-demo:plain:hello (should be null/empty after delete)" {
    Invoke-RestMethod -Uri "$BaseUrl/cache/redis/get?key=native-demo:plain:hello" -Method Get
}

Invoke-Test "22. GET /cache/redis/keys (final view)" {
    Invoke-RestMethod -Uri "$BaseUrl/cache/redis/keys" -Method Get
}

Write-Host "`n========== Done ==========" -ForegroundColor Cyan
