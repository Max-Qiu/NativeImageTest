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

function Request([string]$Path, [string]$Method = 'Get') {
    $options = @{ Uri = "$BaseUrl$Path"; Method = $Method; TimeoutSec = 20; UseBasicParsing = $true }
    if ($null -ne $session) { $options.WebSession = $session }
    return Invoke-WebRequest @options
}

function Html([string]$Path) {
    $response = Request $Path
    if ($response.StatusCode -ne 200 -or $response.Headers['Content-Type'] -notlike 'text/html*') {
        throw "Expected HTML response for $Path"
    }
    $content = $response.Content
    if ($content -match '<[^>]*\sth:[a-z-]+=' -or $content.Contains('<th:block') -or $content.Contains('[[${') -or $content.Contains('Prototype')) {
        throw "Unprocessed template syntax in $Path"
    }
    return $content
}

function Tag([string]$Content, [string]$Id) {
    $match = [regex]::Match($Content, '<[^>]+\bid="' + [regex]::Escape($Id) + '"[^>]*>')
    if (-not $match.Success) { throw "Missing rendered element #$Id" }
    return $match.Value
}

try {
    $unauthorized = $false
    try { $null = Request '/thymeleaf/text' } catch {
        if ($null -eq $_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 401) { throw }
        $unauthorized = $true
    }
    Check 'Syntax test pages require authentication' $unauthorized
    try {
        $null = Invoke-RestMethod -Uri "$BaseUrl/auth/register" -Method Post -ContentType 'application/json' `
            -Body '{"username":"test-admin","password":"admin123","role":"ADMIN"}' -TimeoutSec 20
    } catch {
        if ($null -eq $_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 409) { throw }
    }
    $null = Invoke-WebRequest -Uri "$BaseUrl/auth/login" -Method Post -UseBasicParsing `
        -Body @{ username = 'test-admin'; password = 'admin123' } -ContentType 'application/x-www-form-urlencoded' `
        -SessionVariable thymeleafSession -TimeoutSec 20
    $session = $thymeleafSession

    $textResponse = Request '/thymeleaf/text'
    $textHtml = $textResponse.Content
    Check 'Text template returns HTML' ($textResponse.StatusCode -eq 200 -and $textResponse.Headers['Content-Type'] -like 'text/html*' -and $textHtml.Contains('<!DOCTYPE html>'))
    Check 'Th text escapes special characters' ($textHtml.Contains('id="escaped">Native &lt;Syntax&gt; &amp; Thymeleaf</p>'))
    Check 'Th utext outputs markup' ($textHtml.Contains('id="unescaped"><em>Unescaped markup</em></div>'))

    $conditionHtml = Html '/thymeleaf/conditions'
    Check 'Th if renders true branch' ($conditionHtml.Contains('id="enabled"') -and -not $conditionHtml.Contains('id="disabled"'))
    Check 'Ternary expressions set text and appended class' ($conditionHtml.Contains('>enabled</p>') -and (Tag $conditionHtml 'choice').Contains('active'))
    Check 'Th each iterates JavaBean values' ($conditionHtml.Contains('id="item-alpha"') -and $conditionHtml.Contains('id="item-beta"') -and [regex]::Matches($conditionHtml, 'class="item-code"').Count -eq 2)
    Check 'Th if evaluates each item boolean property' ([regex]::Matches($conditionHtml, 'class="item-enabled"').Count -eq 1)
    $disabledHtml = Html '/thymeleaf/conditions?enabled=false'
    Check 'Negation renders false branch' ($disabledHtml.Contains('id="disabled"') -and -not $disabledHtml.Contains('id="enabled"') -and $disabledHtml.Contains('>disabled</p>') -and -not (Tag $disabledHtml 'choice').Contains('active'))
    $emptyHtml = Html '/thymeleaf/conditions?empty=true'
    Check 'Th each handles an empty collection' ($emptyHtml.Contains('id="empty"') -and -not $emptyHtml.Contains('id="item-alpha"') -and -not $emptyHtml.Contains('id="item-beta"'))

    $attributeHtml = Html '/thymeleaf/attributes'
    Check 'Th href evaluates concatenation and URL expression' ((Tag $attributeHtml 'concatenated-link').Contains('href="/thymeleaf/text?sample=7"') -and (Tag $attributeHtml 'url-expression').Contains('href="/thymeleaf/text"'))
    Check 'Value title and content attributes read escaped model property' ((Tag $attributeHtml 'value').Contains('value="Native &lt;Syntax&gt; &amp; Thymeleaf"') -and (Tag $attributeHtml 'value').Contains('title="Native &lt;Syntax&gt; &amp; Thymeleaf"') -and (Tag $attributeHtml 'metadata').Contains('content="Native &lt;Syntax&gt; &amp; Thymeleaf"'))
    Check 'Contains controls checked attribute in iteration' ((Tag $attributeHtml 'checkbox-alpha').Contains('checked="checked"') -and -not (Tag $attributeHtml 'checkbox-beta').Contains('checked=') -and (Tag $attributeHtml 'checkbox-beta').Contains('value="beta"') -and (Tag $attributeHtml 'checkbox-beta').Contains('title="beta"'))
    Check 'Default attribute processor sets custom data attribute' ((Tag $attributeHtml 'custom-attribute').Contains('data-syntax="attributes"'))
    Check 'Style and onclick evaluate numeric expressions' ((Tag $attributeHtml 'event').Contains('style="margin-left:14px"') -and (Tag $attributeHtml 'event').Contains('onclick="select(7)"'))
    $nullAttributeHtml = Html '/thymeleaf/attributes?populated=false'
    Check 'Alternate model type resolves null property and clears attributes' ((Tag $nullAttributeHtml 'value').Contains('value=""') -and -not (Tag $nullAttributeHtml 'value').Contains('title=') -and -not (Tag $nullAttributeHtml 'metadata').Contains('content=') -and -not $nullAttributeHtml.Contains('checked='))

    $expressionHtml = Html '/thymeleaf/expressions'
    Check 'Property expression traverses nested JavaBean getters' ($expressionHtml.Contains('id="nested-property">nested value</p>'))
    Check 'String split result supports iteration' ($expressionHtml.Contains('class="split-value">alpha</span>') -and $expressionHtml.Contains('class="split-value">beta</span>') -and [regex]::Matches($expressionHtml, 'class="split-value"').Count -eq 2)
    Check 'Collection method expression returns boolean' ($expressionHtml.Contains('id="contains">true</p>'))
    Check 'Temporals formats date and date time' ($expressionHtml.Contains('id="date">2026/11/14</p>') -and $expressionHtml.Contains('id="date-time">2026/11/14 10:20:30</p>'))
    Check 'Arithmetic comparison and concatenation evaluate correctly' ($expressionHtml.Contains('id="arithmetic">42</p>') -and $expressionHtml.Contains('id="comparison">less</p>') -and $expressionHtml.Contains('id="concatenation">value-7</p>'))

    $fragmentHtml = Html '/thymeleaf/fragments'
    Check 'Replace and insert compose layout and head fragment' ($fragmentHtml.Contains('<title>Fragment syntax</title>') -and $fragmentHtml.Contains('id="layout-marker"') -and $fragmentHtml.Contains('id="fragment-slot"'))
    Check 'Fragment parameter supplies text value' ($fragmentHtml.Contains('id="fragment-message">Fragment parameter value</p>'))
    Check 'Remove tag omits wrappers and nested script tags' (-not $fragmentHtml.Contains('<section') -and [regex]::Matches($fragmentHtml, '<script\b').Count -eq 1 -and $fragmentHtml.Contains('id="fragment-script"') -and $fragmentHtml.Contains("window.fragment = 'ready';"))
    Check 'Recursive fragment traverses nodes and passes depth' ((Tag $fragmentHtml 'node-root').Contains('data-depth="0"') -and (Tag $fragmentHtml 'node-branch').Contains('data-depth="1"') -and (Tag $fragmentHtml 'node-leaf').Contains('data-depth="2"') -and [regex]::Matches($fragmentHtml, 'class="node-name"').Count -eq 3)
    $flatFragmentHtml = Html '/thymeleaf/fragments?recursive=false'
    Check 'Conditional fragment branch skips recursion' (-not $flatFragmentHtml.Contains('id="tree"') -and -not $flatFragmentHtml.Contains('id="node-root"') -and $flatFragmentHtml.Contains('Fragment parameter value'))

    $inlineHtml = Html '/thymeleaf/inlining'
    Check 'Text inlining evaluates element text' ($inlineHtml.Contains('id="inline-text">inline value</p>'))
    Check 'Text inlining evaluates numbers and strings in plain script' ($inlineHtml.Contains('window.syntax = {count: 3, position: 7};') -and $inlineHtml.Contains("window.literal = 'inline value';"))

    $contextHtml = Html '/thymeleaf/context'
    $parameterHtml = Html '/thymeleaf/context?flag'
    Check 'Param expression distinguishes absent and present query parameter' (-not $contextHtml.Contains('id="parameter-present"') -and $parameterHtml.Contains('id="parameter-present"'))
    Check 'Null session values omit conditional elements' (-not $contextHtml.Contains('id="session-string"') -and -not $contextHtml.Contains('id="session-property"'))
    $seeded = Request '/thymeleaf/context' 'Post'
    $storedHtml = Html '/thymeleaf/context'
    Check 'Session expressions read values and object property after Redis round trip' ($seeded.StatusCode -eq 204 -and $storedHtml.Contains('id="session-string">stored value</p>') -and $storedHtml.Contains('id="session-property">Session property value</p>'))
    $cleared = Request '/thymeleaf/context' 'Delete'
    $clearedHtml = Html '/thymeleaf/context'
    Check 'Removing session values restores null branches' ($cleared.StatusCode -eq 204 -and -not $clearedHtml.Contains('id="session-string"') -and -not $clearedHtml.Contains('id="session-property"'))

    $reportDirectory = Join-Path $PSScriptRoot 'target'
    $null = New-Item -ItemType Directory -Path $reportDirectory -Force
    [pscustomobject]@{ passed = $checks.Count; checks = $checks; timestamp = (Get-Date).ToString('o') } |
        ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $reportDirectory 'thymeleaf-assertions.json')
    Write-Host "All $($checks.Count) Thymeleaf assertions passed."
} finally {
    if ($null -ne $session) {
        try { $null = Request '/thymeleaf/context' 'Delete'; $null = Request '/auth/logout' 'Post' }
        catch { Write-Warning "Could not clean up Thymeleaf session: $_" }
    }
}
