[CmdletBinding()]
param([string]$BaseUrl = 'http://localhost:8080')

$ErrorActionPreference = 'Stop'
$checks = [System.Collections.Generic.List[string]]::new()
$session = $null
$createdId = $null
$suffix = [Guid]::NewGuid().ToString('N')
$cacheKey = "native-verify-$suffix"
$redisKey = "native-demo:verify:$suffix"
$probeDatasetCreated = $false
$probeArtifactCreated = $false

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

function Ordinals($Rows) { return (@($Rows | ForEach-Object ordinalIndex) -join ',') }

function XmlText([string]$Path) {
    $response = Invoke-WebRequest -Uri "$BaseUrl$Path" -WebSession $session -UseBasicParsing -TimeoutSec 20
    if ($response.Headers['Content-Type'] -notlike 'application/xml*') { throw 'Expected XML response' }
    return ([xml]$response.Content).probe.text
}

function ReadPackagedEntry($Zip, [string]$Name) {
    $entry = $Zip.GetEntry($Name)
    if ($null -eq $entry) { throw "Missing packaged metadata: $Name" }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

try {
    ExpectStatus '/users' 'Get' 401
    Check 'Unauthenticated business request returns 401' $true
    ExpectStatus '/probes/environment' 'Get' 401
    Check 'Compatibility examples require authentication' $true
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

    $environment = Request '/probes/environment'
    Check 'Compatibility examples execute in the main Native application' ($environment.nativeRuntime -eq $true -and $environment.application -eq 'com.maxqiu.test.NativeImageTestApplication')
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $jar = Join-Path $PSScriptRoot 'target/NativeImageTest-0.0.1-SNAPSHOT.jar'
    $zip = [IO.Compression.ZipFile]::OpenRead($jar)
    try {
        $generatedOptions = ReadPackagedEntry $zip 'META-INF/native-image/com.maxqiu/NativeImageTest/native-image.properties'
        $customOptions = ReadPackagedEntry $zip 'META-INF/native-image/com.maxqiu/native-image-test/native-image.properties'
        Check 'AOT options and custom Lambda feature survive in separate JAR entries' ($generatedOptions -match 'com.maxqiu.test.NativeImageTestApplication' -and $customOptions -match '--features=com.maxqiu.test.config.NativeLambdaFeature')
    } finally { $zip.Dispose() }

    $collections = Request '/probes/collections'
    Check 'DefaultObjectFactory constructs ArrayList results' ($collections.list -eq 'java.util.ArrayList')
    Check 'DefaultObjectFactory constructs HashMap results' ($collections.map -eq 'java.util.HashMap')
    Check 'DefaultObjectFactory constructs HashSet results' ($collections.set -eq 'java.util.HashSet')
    Check 'DefaultObjectFactory constructs TreeSet results' ($collections.sortedSet -eq 'java.util.TreeSet')

    $probeDatasetCreated = $true
    $rows = @(Request "/probes/datasets/$suffix" 'Post')
    Check 'Numbered fixture inserts six rows' ($rows.Count -eq 6 -and (Ordinals $rows) -eq '1,2,3,4,5,6')
    Check 'Fixture rows receive distinct generated IDs' (@($rows | Select-Object -ExpandProperty id -Unique).Count -eq 6 -and $rows[0].id -gt 0)
    $codes = @(Request "/probes/datasets/$suffix/codes")
    Check 'Enum values persist as explicit codes' (($codes -join ',') -eq '10,20,30,10,20,30')
    $firstPage = Request "/probes/datasets/$suffix/page?page=1&size=2"
    Check 'Pagination counts records and total pages' ($firstPage.total -eq 6 -and $firstPage.pages -eq 3 -and $firstPage.size -eq 2)
    Check 'First page returns only rows one and two' ((Ordinals $firstPage.records) -eq '1,2')
    $secondPage = Request "/probes/datasets/$suffix/page?page=2&size=2"
    Check 'Second page applies SQL offset and limit' ($secondPage.current -eq 2 -and (Ordinals $secondPage.records) -eq '3,4')
    $lastPage = Request "/probes/datasets/$suffix/page?page=3&size=2"
    Check 'Last page returns the remaining rows' ((Ordinals $lastPage.records) -eq '5,6')
    $overflowPage = Request "/probes/datasets/$suffix/page?page=4&size=2"
    Check 'Out of range page retains total and returns no rows' ($overflowPage.total -eq 6 -and @($overflowPage.records).Count -eq 0)
    $filteredPage = Request "/probes/datasets/$suffix/page?phase=BETA&page=1&size=2"
    Check 'Lambda enum filter binds codes and restores enum values' ($filteredPage.total -eq 2 -and (Ordinals $filteredPage.records) -eq '2,5' -and @($filteredPage.records | Where-Object phase -ne 'BETA').Count -eq 0)

    $snapshot = Request "/probes/datasets/$suffix/cache"
    Check 'Enum list loads from database on first cache request' ($snapshot.loads -eq 1 -and $snapshot.rows.Count -eq 6 -and $snapshot.rows[0].phase -eq 'ALPHA')
    $null = Request "/probes/datasets/$suffix/first-phase?phase=GAMMA" 'Post'
    $changed = Request "/probes/datasets/$suffix/page?page=1&size=1"
    Check 'Database change is visible outside cached snapshot' ($changed.records[0].phase -eq 'GAMMA')
    $cacheHit = Request "/probes/datasets/$suffix/cache"
    Check 'Redis hit retains old enum without another database load' ($cacheHit.loads -eq 1 -and $cacheHit.rows[0].phase -eq 'ALPHA')
    Check 'Redis restores concrete list and enum types' ($cacheHit.listType -eq 'java.util.ArrayList' -and $cacheHit.phaseType -eq 'com.maxqiu.test.compatibility.ProbeRow$Phase')
    $null = Request "/probes/datasets/$suffix/cache" 'Delete'
    $freshSnapshot = Request "/probes/datasets/$suffix/cache"
    Check 'Eviction reloads updated enum value' ($freshSnapshot.loads -eq 2 -and $freshSnapshot.rows[0].phase -eq 'GAMMA')

    $resource = Request '/probes/resources'
    Check 'Native classpath resource is readable as a stream' ($resource.protocol -eq 'resource' -and $resource.content -eq 'native-regression-static-v1')
    Check 'Embedded resource cannot be resolved as a filesystem File' ($resource.fileResolved -eq $false)
    $asset = Invoke-WebRequest -Uri "$BaseUrl/native-probes/sample.txt" -WebSession $session -UseBasicParsing -TimeoutSec 20
    Check 'Static resource is served from the Native image' ($asset.Content.Trim() -eq 'native-regression-static-v1')
    ExpectStatus "/probes/artifacts/$suffix" 'Get' 404
    Check 'Generated file is absent before creation' $true
    $probeArtifactCreated = $true
    $generated = Request "/probes/artifacts/$suffix" 'Post' @{text='first <probe> & value'}
    Check 'Generated file uses an external writable location' ($generated.location.StartsWith('/tmp/') -and $generated.location.EndsWith("$suffix.xml"))
    Check 'External XML generation preserves escaped text' ((XmlText "/probes/artifacts/$suffix") -eq 'first <probe> & value')
    $null = Request "/probes/artifacts/$suffix" 'Post' @{text='second version'}
    Check 'Generated XML is replaceable without rebuilding' ((XmlText "/probes/artifacts/$suffix") -eq 'second version')
    $unchangedResource = Request '/probes/resources'
    Check 'Generated files do not alter packaged static resources' ($unchangedResource.content -eq $resource.content)
    $null = Request "/probes/artifacts/$suffix" 'Delete'
    $probeArtifactCreated = $false
    ExpectStatus "/probes/artifacts/$suffix" 'Get' 404
    Check 'Generated XML can be deleted independently' $true
    $null = Request "/probes/datasets/$suffix" 'Delete'
    $probeDatasetCreated = $false
    $emptyPage = Request "/probes/datasets/$suffix/page?page=1&size=2"
    Check 'Fixture cleanup leaves an empty dataset' ($emptyPage.total -eq 0 -and @($emptyPage.records).Count -eq 0)

    $null = Request '/auth/logout' 'Post'
    ExpectStatus '/auth/me' 'Get' 401
    Check 'Logout invalidates Redis session and returns 401 on reuse' $true
    $reportDirectory = Join-Path $PSScriptRoot 'target'
    $null = New-Item -ItemType Directory -Path $reportDirectory -Force
    [pscustomobject]@{ passed = $checks.Count; checks = $checks; timestamp = (Get-Date).ToString('o') } | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $reportDirectory 'native-assertions.json')
    Write-Output "All $($checks.Count) native behavior assertions passed."
} finally {
    if ($probeArtifactCreated) {
        try { $null = Request "/probes/artifacts/$suffix" 'Delete' }
        catch { Write-Warning "Could not clean up generated XML: $_" }
    }
    if ($probeDatasetCreated) {
        try { $null = Request "/probes/datasets/$suffix" 'Delete' }
        catch { Write-Warning "Could not clean up numbered fixture: $_" }
    }
    if ($null -ne $createdId) {
        try { $null = Request "/users/$createdId" 'Delete' }
        catch { Write-Warning "Could not clean up test user ${createdId}: $_" }
    }
}
