$BaseUrl = "http://localhost:8080"

function Invoke-Test {
    param([string]$Title, [scriptblock]$Action)
    Write-Host "`n========== $Title ==========" -ForegroundColor Cyan
    try {
        $result = & $Action
        if ($null -ne $result) {
            $result | ConvertTo-Json -Depth 5 -Compress | Write-Host -ForegroundColor Green
        }
    } catch {
        Write-Host "FAILED: $($_.Exception.Message)" -ForegroundColor Red
    }
}

Invoke-Test "1. GET /users (list)" {
    Invoke-RestMethod -Uri "$BaseUrl/users" -Method Get
}

Invoke-Test "2. POST /users (create)" {
    $body = @{ name = "Henry"; age = 33 } | ConvertTo-Json
    Invoke-RestMethod -Uri "$BaseUrl/users" -Method Post -Body $body -ContentType "application/json"
}

Invoke-Test "3. GET /users/2 (single)" {
    Invoke-RestMethod -Uri "$BaseUrl/users/2" -Method Get
}

Invoke-Test "4. PUT /users/2 (update)" {
    $body = @{ name = "BobUpdated"; age = 26 } | ConvertTo-Json
    Invoke-RestMethod -Uri "$BaseUrl/users/2" -Method Put -Body $body -ContentType "application/json"
}

Invoke-Test "5. DELETE /users/999 (non-existent)" {
    Invoke-RestMethod -Uri "$BaseUrl/users/999" -Method Delete
}

Invoke-Test "6. GET /users/stats/age (XML mapper group-by)" {
    Invoke-RestMethod -Uri "$BaseUrl/users/stats/age" -Method Get
}

Invoke-Test "7. GET /users (final list)" {
    Invoke-RestMethod -Uri "$BaseUrl/users" -Method Get
}

Write-Host "`n========== Done ==========" -ForegroundColor Cyan
