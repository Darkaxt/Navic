param(
    [string] $EnvFile = "C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env",
    [string] $Package = "darkaxt.navic.readerdev",
    [string] $Activity = "paige.navic.androidApp.MainActivity",
    [string] $DeviceSerial,
    [switch] $NoBuild,
    [switch] $NoInstall,
    [switch] $NoLaunch,
    [switch] $Capture,
    [int] $ReaderAssetServerPort = 0,
    [switch] $NoDiscoverPublication,
    [switch] $RequireReaderLaunch,
    [int] $MaxDiscoveryBooks = 150,
    [string] $StartProgress,
    [Alias("PublicationUrl")]
    [string] $ReaderPublicationUrl,
    [Alias("ResourceHref")]
    [string] $ReaderResourceHref,
    [Alias("BookId")]
    [string] $ReaderBookId,
    [Alias("Title")]
    [string] $ReaderTitle,
    [Alias("Kind")]
    [string] $ReaderKind,
    [Alias("Format")]
    [string] $ReaderFormat,
    [Alias("StartHref")]
    [string] $ReaderStartHref,
    [Alias("StartCfi")]
    [string] $ReaderStartCfi,
    [Alias("WhispersyncSidecarUrl")]
    [string] $ReaderWhispersyncSidecarUrl,
    [Alias("WhispersyncArtifactId")]
    [string] $ReaderWhispersyncArtifactId,
    [Alias("WhispersyncAudiobookId")]
    [string] $ReaderWhispersyncAudiobookId,
    [Alias("WhispersyncAudiobookBookFileId")]
    [string] $ReaderWhispersyncAudiobookBookFileId,
    [Alias("WhispersyncAudiobookTitle")]
    [string] $ReaderWhispersyncAudiobookTitle
)

$ErrorActionPreference = "Stop"

function Read-EnvFile {
    param([Parameter(Mandatory = $true)][string] $Path)

    $values = @{}
    if (!(Test-Path $Path)) {
        throw "Env file not found: $Path"
    }
    foreach ($line in Get-Content $Path) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#") -or !$trimmed.Contains("=")) {
            continue
        }
        $key, $value = $trimmed.Split("=", 2)
        $values[$key.Trim()] = $value.Trim().Trim('"').Trim("'")
    }
    return $values
}

function Invoke-Adb {
    param([Parameter(Mandatory = $true)][string[]] $Arguments)

    $adbArgs = @()
    if ($DeviceSerial) {
        $adbArgs += @("-s", $DeviceSerial)
    }
    $adbArgs += $Arguments

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & adb @adbArgs 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    $output = @($output | ForEach-Object { "$_" })

    if ($exitCode -ne 0) {
        throw "adb $($adbArgs -join ' ') failed with exit code $exitCode`n$($output -join "`n")"
    }

    return $output
}

function ConvertTo-ProcessArgument {
    param([AllowEmptyString()][string] $Value)

    if ($null -eq $Value -or $Value.Length -eq 0) {
        return '""'
    }
    if ($Value -notmatch '[\s"]') {
        return $Value
    }

    $builder = [System.Text.StringBuilder]::new()
    [void] $builder.Append('"')
    $backslashCount = 0
    foreach ($character in $Value.ToCharArray()) {
        if ($character -eq '\') {
            $backslashCount += 1
        } elseif ($character -eq '"') {
            [void] $builder.Append(('\' * (($backslashCount * 2) + 1)))
            [void] $builder.Append('"')
            $backslashCount = 0
        } else {
            if ($backslashCount -gt 0) {
                [void] $builder.Append(('\' * $backslashCount))
                $backslashCount = 0
            }
            [void] $builder.Append($character)
        }
    }
    if ($backslashCount -gt 0) {
        [void] $builder.Append(('\' * ($backslashCount * 2)))
    }
    [void] $builder.Append('"')
    return $builder.ToString()
}

function Invoke-GradleWrapper {
    param([Parameter(Mandatory = $true)][string[]] $Arguments)

    $wrapperJar = Join-Path (Get-Location) "gradle\wrapper\gradle-wrapper.jar"
    if (!(Test-Path -LiteralPath $wrapperJar -PathType Leaf)) {
        throw "Gradle wrapper jar not found: $wrapperJar"
    }

    $javaCommand = Get-Command java -ErrorAction Stop
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $javaCommand.Source
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.Arguments = (@("-jar", $wrapperJar) + $Arguments | ForEach-Object {
        ConvertTo-ProcessArgument -Value $_
    }) -join " "

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (!$process.Start()) {
        throw "Failed to start Gradle wrapper jar."
    }

    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $process.WaitForExit()
    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()

    if (![string]::IsNullOrWhiteSpace($stdout)) {
        Write-Output $stdout.TrimEnd()
    }
    if (![string]::IsNullOrWhiteSpace($stderr)) {
        Write-Error -Message $stderr.TrimEnd() -ErrorAction Continue
    }
    if ($process.ExitCode -ne 0) {
        throw "Gradle $($Arguments -join ' ') failed with exit code $($process.ExitCode)"
    }
}

function Wait-ReaderDevForeground {
    param([Parameter(Mandatory = $true)][string] $Package)

    $escapedPackage = [regex]::Escape($Package)
    Write-Output "Waiting for $Package to become the focused Android window before capture..."
    while ($true) {
        $windowDump = Invoke-Adb -Arguments @("shell", "dumpsys", "activity", "activities")
        $focusLine = @(
            $windowDump |
                Where-Object { $_ -match "mCurrentFocus=.*$escapedPackage" -or $_ -match "mFocusedApp=.*$escapedPackage" } |
                Select-Object -First 1
        )
        if ($focusLine.Count -gt 0) {
            Write-Output ("Foreground confirmed: {0}" -f $focusLine[0].Trim())
            return
        }

        Start-Sleep -Seconds 1
    }
}

function Wait-ReaderDevPublicationReady {
    param([Parameter(Mandatory = $true)][string] $Package)

    Write-Output "Waiting for reader publicationReady bridge event before capture..."
    while ($true) {
        $logLines = Invoke-Adb -Arguments @("logcat", "-d", "-v", "brief", "-t", "1000")
        $readyLine = @(
            $logLines |
                Where-Object { $_ -match "Reader bridge event: publicationReady" -or $_ -match '"type":"publicationReady"' } |
                Select-Object -First 1
        )
        if ($readyLine.Count -gt 0) {
            Write-Output ("Reader publication ready: {0}" -f $readyLine[0].Trim())
            return
        }

        Start-Sleep -Seconds 1
    }
}

function Get-EnvValue {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable] $Values,
        [Parameter(Mandatory = $true)]
        [string[]] $Keys
    )

    foreach ($key in $Keys) {
        if ($Values.ContainsKey($key) -and ![string]::IsNullOrWhiteSpace($Values[$key])) {
            return $Values[$key].Trim()
        }
    }
    return $null
}

function Join-BinderyEndpoint {
    param(
        [Parameter(Mandatory = $true)]
        [string] $BaseUrl,
        [Parameter(Mandatory = $true)]
        [string] $Path
    )

    $trimmedPath = $Path.Trim()
    if ($trimmedPath -match '^https?://') {
        return $trimmedPath
    }
    $normalizedBase = $BaseUrl.Trim().TrimEnd("/")
    $uri = [Uri] $normalizedBase
    $origin = "{0}://{1}" -f $uri.Scheme, $uri.Authority
    $apiRoot = if ($normalizedBase.EndsWith("/opds", [System.StringComparison]::OrdinalIgnoreCase)) {
        $normalizedBase.Substring(0, $normalizedBase.Length - 5)
    } else {
        $origin
    }
    $relative = $trimmedPath.TrimStart("/")
    if ($relative.StartsWith("opds/", [System.StringComparison]::OrdinalIgnoreCase)) {
        return "$origin/$relative"
    }
    if ($relative.StartsWith("api/v1/", [System.StringComparison]::OrdinalIgnoreCase) -or $relative -eq "api/v1") {
        return "$apiRoot/$relative"
    }
    return "$normalizedBase/$relative"
}

function Get-ObjectValue {
    param(
        [object] $InputObject,
        [Parameter(Mandatory = $true)]
        [string[]] $Names
    )

    if ($null -eq $InputObject) {
        return $null
    }
    foreach ($name in $Names) {
        if ($InputObject -is [hashtable] -and $InputObject.ContainsKey($name)) {
            return $InputObject[$name]
        }
        $property = $InputObject.PSObject.Properties[$name]
        if ($property -and $null -ne $property.Value) {
            return $property.Value
        }
    }
    return $null
}

function Get-ObjectString {
    param(
        [object] $InputObject,
        [Parameter(Mandatory = $true)]
        [string[]] $Names
    )

    $value = Get-ObjectValue -InputObject $InputObject -Names $Names
    if ($null -eq $value) {
        return $null
    }
    if ($value -is [array]) {
        $value = $value | Select-Object -First 1
    }
    return "$value".Trim()
}

function Test-LinkRel {
    param(
        [object] $Link,
        [Parameter(Mandatory = $true)]
        [string] $Rel
    )

    $relValue = Get-ObjectValue -InputObject $Link -Names @("rel")
    if ($null -eq $relValue) {
        return $false
    }
    foreach ($item in @($relValue)) {
        if ("$item".Trim().Equals($Rel, [System.StringComparison]::OrdinalIgnoreCase)) {
            return $true
        }
    }
    return $false
}

function Get-LinkByRel {
    param(
        [object[]] $Links,
        [Parameter(Mandatory = $true)]
        [string] $Rel
    )

    foreach ($link in @($Links)) {
        if (Test-LinkRel -Link $link -Rel $Rel) {
            return $link
        }
    }
    return $null
}

function Test-ReaderDevAcquisitionRel {
    param([object] $Link)

    $relValue = Get-ObjectValue -InputObject $Link -Names @("rel")
    foreach ($item in @($relValue)) {
        $normalized = "$item".Trim().ToLowerInvariant()
        if ($normalized -eq "http://opds-spec.org/acquisition" -or
            $normalized.EndsWith("/acquisition") -or
            $normalized -eq "acquisition") {
            return $true
        }
    }
    return $false
}

function Get-BinderyBookRouteId {
    param([object] $Publication)

    $metadata = Get-ObjectValue -InputObject $Publication -Names @("metadata")
    $selfLink = Get-LinkByRel -Links @(Get-ObjectValue -InputObject $Publication -Names @("links")) -Rel "self"
    $candidates = @(
        (Get-ObjectString -InputObject $Publication -Names @("id")),
        (Get-ObjectString -InputObject $metadata -Names @("identifier")),
        (Get-ObjectString -InputObject $selfLink -Names @("href"))
    ) | Where-Object { ![string]::IsNullOrWhiteSpace($_) }

    foreach ($candidate in $candidates) {
        $trimmed = $candidate.Trim()
        $withoutUrn = $trimmed -replace '^urn:bindery:book:', ''
        if ($withoutUrn -match '/opds/books/([^/?#]+)') {
            return $matches[1]
        }
        $withoutQuery = $withoutUrn.Split("?", 2)[0].Trim("/")
        if ($withoutQuery -match '^[0-9]+$') {
            return $withoutQuery
        }
    }
    return $null
}

function Invoke-BinderyJson {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Url,
        [Parameter(Mandatory = $true)]
        [hashtable] $Headers
    )

    try {
        return Invoke-RestMethod -Uri $Url -Headers $Headers -Method Get -ErrorAction Stop
    } catch {
        return $null
    }
}

function Get-ReaderDevResourceFields {
    param([object] $Resource)

    $properties = Get-ObjectValue -InputObject $Resource -Names @("properties")
    $metadata = Get-ObjectValue -InputObject $Resource -Names @("metadata")
    $sourceRelease = Get-ObjectValue -InputObject $metadata -Names @("sourceRelease")
    return @(
        (Get-ObjectString -InputObject $Resource -Names @("type", "kind")),
        (Get-ObjectString -InputObject $properties -Names @("kind", "mediaType", "format")),
        (Get-ObjectString -InputObject $sourceRelease -Names @("format"))
    ) | Where-Object { ![string]::IsNullOrWhiteSpace($_) }
}

function Test-ReaderDevEbookResource {
    param([object] $Resource)

    foreach ($field in Get-ReaderDevResourceFields -Resource $Resource) {
        $normalized = $field.Trim().ToLowerInvariant()
        if ($normalized -eq "ebook" -or
            $normalized -eq "book" -or
            $normalized -eq "epub" -or
            $normalized -eq "pdf" -or
            $normalized.Contains("epub") -or
            $normalized.Contains("pdf")) {
            return $true
        }
    }
    return $false
}

function Get-ReaderDevResourceFormat {
    param([object] $Resource)

    foreach ($field in Get-ReaderDevResourceFields -Resource $Resource) {
        if ($field.Trim().ToLowerInvariant().Contains("pdf")) {
            return "pdf"
        }
    }
    return "epub"
}

function Get-ReaderDevPublicationEbookLink {
    param(
        [object] $Publication,
        [string] $LanguageFilter
    )

    foreach ($link in @(Get-ObjectValue -InputObject $Publication -Names @("links"))) {
        if (!(Test-ReaderDevAcquisitionRel -Link $link)) {
            continue
        }
        if (!(Test-ReaderDevEbookResource -Resource $link)) {
            continue
        }
        if (!(Test-ReaderDevResourceLanguage -Resource $link -LanguageFilter $LanguageFilter)) {
            continue
        }
        $href = Get-ObjectString -InputObject $link -Names @("href")
        if (![string]::IsNullOrWhiteSpace($href)) {
            return $link
        }
    }
    return $null
}

function Test-ReaderDevResourceLanguage {
    param(
        [object] $Resource,
        [string] $LanguageFilter
    )

    if ([string]::IsNullOrWhiteSpace($LanguageFilter)) {
        return $true
    }
    $properties = Get-ObjectValue -InputObject $Resource -Names @("properties")
    $metadata = Get-ObjectValue -InputObject $Resource -Names @("metadata")
    $language = @(
        (Get-ObjectString -InputObject $Resource -Names @("language")),
        (Get-ObjectString -InputObject $properties -Names @("language")),
        (Get-ObjectString -InputObject $metadata -Names @("language"))
    ) | Where-Object { ![string]::IsNullOrWhiteSpace($_) } | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($language)) {
        return $true
    }
    return $language.Trim().ToLowerInvariant().StartsWith($LanguageFilter.Trim().ToLowerInvariant())
}

function Get-ReaderDevBookFileIdFromUrl {
    param([string] $Url)

    if ([string]::IsNullOrWhiteSpace($Url)) {
        return $null
    }

    $candidate = $Url.Trim()
    $query = $null
    try {
        $uri = [Uri] $candidate
        $query = $uri.Query
    } catch {
        $query = $null
    }

    if ([string]::IsNullOrWhiteSpace($query) -and $candidate.Contains("?")) {
        $query = $candidate.Substring($candidate.IndexOf("?"))
    }
    if ([string]::IsNullOrWhiteSpace($query)) {
        return $null
    }

    foreach ($pair in $query.TrimStart("?").Split("&")) {
        if ([string]::IsNullOrWhiteSpace($pair)) {
            continue
        }
        $name, $value = $pair.Split("=", 2)
        if ($name.Trim().Equals("bookFileId", [System.StringComparison]::OrdinalIgnoreCase) -and
            ![string]::IsNullOrWhiteSpace($value)) {
            return [Uri]::UnescapeDataString($value.Trim())
        }
    }
    return $null
}

function Get-ReaderDevBookIdFromDirectFileUrl {
    param([string] $Url)

    if ([string]::IsNullOrWhiteSpace($Url)) {
        return $null
    }
    if ($Url.Trim() -match '/api/v1/book/([^/?#]+)/file(?:[?#].*)?$') {
        return [Uri]::UnescapeDataString($matches[1])
    }
    return $null
}

function Get-ReaderDevResourceBookFileId {
    param([object] $Resource)

    # Match direct API bookFileId against properties.bookFileId from Bindery OPDS.
    $properties = Get-ObjectValue -InputObject $Resource -Names @("properties")
    $metadata = Get-ObjectValue -InputObject $Resource -Names @("metadata")
    $sourceRelease = Get-ObjectValue -InputObject $metadata -Names @("sourceRelease")
    return @(
        (Get-ObjectString -InputObject $Resource -Names @("bookFileId")),
        (Get-ObjectString -InputObject $properties -Names @("bookFileId")),
        (Get-ObjectString -InputObject $metadata -Names @("bookFileId")),
        (Get-ObjectString -InputObject $sourceRelease -Names @("bookFileId"))
    ) | Where-Object { ![string]::IsNullOrWhiteSpace($_) } | Select-Object -First 1
}

function Get-ReaderDevResourceByBookFileId {
    param(
        [object[]] $Resources,
        [Parameter(Mandatory = $true)]
        [string] $BookFileId,
        [string] $LanguageFilter
    )

    foreach ($resource in @($Resources)) {
        $resourceBookFileId = Get-ReaderDevResourceBookFileId -Resource $resource
        if ([string]::IsNullOrWhiteSpace($resourceBookFileId)) {
            continue
        }
        if (!$resourceBookFileId.Trim().Equals($BookFileId.Trim(), [System.StringComparison]::OrdinalIgnoreCase)) {
            continue
        }
        if (!(Test-ReaderDevEbookResource -Resource $resource)) {
            continue
        }
        if (!(Test-ReaderDevResourceLanguage -Resource $resource -LanguageFilter $LanguageFilter)) {
            continue
        }
        $href = Get-ObjectString -InputObject $resource -Names @("href")
        if (![string]::IsNullOrWhiteSpace($href)) {
            return $resource
        }
    }
    return $null
}

function Resolve-ReaderDevExplicitBinderyResource {
    param(
        [string] $BaseUrl,
        [string] $ApiKey,
        [string] $ApiKeyHeader,
        [string] $BookId,
        [string] $PublicationUrl,
        [string] $ResourceHref,
        [string] $LanguageFilter
    )

    if ([string]::IsNullOrWhiteSpace($BaseUrl) -or [string]::IsNullOrWhiteSpace($ApiKey)) {
        return $null
    }

    $bookFileId = @(
        (Get-ReaderDevBookFileIdFromUrl -Url $ResourceHref),
        (Get-ReaderDevBookFileIdFromUrl -Url $PublicationUrl)
    ) | Where-Object { ![string]::IsNullOrWhiteSpace($_) } | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($bookFileId)) {
        return $null
    }

    $resolvedBookId = @(
        $BookId,
        (Get-ReaderDevBookIdFromDirectFileUrl -Url $ResourceHref),
        (Get-ReaderDevBookIdFromDirectFileUrl -Url $PublicationUrl)
    ) | Where-Object { ![string]::IsNullOrWhiteSpace($_) } | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($resolvedBookId)) {
        return $null
    }

    $headerName = if ([string]::IsNullOrWhiteSpace($ApiKeyHeader)) { "X-Api-Key" } else { $ApiKeyHeader.Trim() }
    $headers = @{ $headerName = $ApiKey }
    $bookUrl = Join-BinderyEndpoint -BaseUrl $BaseUrl -Path "/opds/books/$resolvedBookId"
    $publication = Invoke-BinderyJson -Url $bookUrl -Headers $headers
    if ($null -eq $publication) {
        return $null
    }

    $resource = Get-ReaderDevResourceByBookFileId `
        -Resources @(Get-ObjectValue -InputObject $publication -Names @("links")) `
        -BookFileId $bookFileId `
        -LanguageFilter $LanguageFilter

    if ($null -eq $resource) {
        $resourceCatalogUrl = Join-BinderyEndpoint -BaseUrl $BaseUrl -Path "/opds/books/$resolvedBookId/resources"
        $resourceCatalog = Invoke-BinderyJson -Url $resourceCatalogUrl -Headers $headers
        if ($null -eq $resourceCatalog) {
            return $null
        }
        $resource = Get-ReaderDevResourceByBookFileId `
            -Resources @(Get-ObjectValue -InputObject $resourceCatalog -Names @("resources")) `
            -BookFileId $bookFileId `
            -LanguageFilter $LanguageFilter
    }

    if ($null -eq $resource) {
        return $null
    }

    $resourceHref = Get-ObjectString -InputObject $resource -Names @("href")
    if ([string]::IsNullOrWhiteSpace($resourceHref)) {
        return $null
    }
    $metadata = Get-ObjectValue -InputObject $publication -Names @("metadata")
    return @{
        BookId = $resolvedBookId
        Title = Get-ObjectString -InputObject $metadata -Names @("title")
        ResourceHref = $resourceHref
        PublicationUrl = Join-BinderyEndpoint -BaseUrl $BaseUrl -Path $resourceHref
        Format = Get-ReaderDevResourceFormat -Resource $resource
    }
}

function Resolve-ReaderDevPublicationFromBindery {
    param(
        [string] $BaseUrl,
        [string] $ApiKey,
        [string] $ApiKeyHeader,
        [string] $LanguageFilter,
        [string] $PreferredBookId,
        [int] $MaxBooks
    )

    if ([string]::IsNullOrWhiteSpace($BaseUrl) -or [string]::IsNullOrWhiteSpace($ApiKey)) {
        return $null
    }

    $headerName = if ([string]::IsNullOrWhiteSpace($ApiKeyHeader)) { "X-Api-Key" } else { $ApiKeyHeader.Trim() }
    $headers = @{ $headerName = $ApiKey }
    $catalogUrl = Join-BinderyEndpoint -BaseUrl $BaseUrl -Path "/opds/books"
    $visitedCatalogs = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    $visitedBooks = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    $checkedBooks = 0

    while (![string]::IsNullOrWhiteSpace($catalogUrl) -and $checkedBooks -lt $MaxBooks) {
        if (!$visitedCatalogs.Add($catalogUrl)) {
            break
        }
        $catalog = Invoke-BinderyJson -Url $catalogUrl -Headers $headers
        if ($null -eq $catalog) {
            break
        }
        $publications = @(Get-ObjectValue -InputObject $catalog -Names @("publications"))
        if (![string]::IsNullOrWhiteSpace($PreferredBookId)) {
            $publications = $publications | Sort-Object {
                $routeId = Get-BinderyBookRouteId -Publication $_
                if ($routeId -and $routeId.Equals($PreferredBookId.Trim(), [System.StringComparison]::OrdinalIgnoreCase)) { 0 } else { 1 }
            }
        }
        foreach ($publication in $publications) {
            if ($checkedBooks -ge $MaxBooks) {
                break
            }
            $bookId = Get-BinderyBookRouteId -Publication $publication
            if ([string]::IsNullOrWhiteSpace($bookId) -or !$visitedBooks.Add($bookId)) {
                continue
            }
            $checkedBooks += 1
            # Use catalog acquisition links before probing /resources; the OPDS book list
            # already carries EPUB/PDF links and this keeps the dirty reader loop fast.
            $publicationResource = Get-ReaderDevPublicationEbookLink -Publication $publication -LanguageFilter $LanguageFilter
            if ($null -ne $publicationResource) {
                $metadata = Get-ObjectValue -InputObject $publication -Names @("metadata")
                $resourceHref = Get-ObjectString -InputObject $publicationResource -Names @("href")
                return @{
                    BookId = $bookId
                    Title = Get-ObjectString -InputObject $metadata -Names @("title")
                    ResourceHref = $resourceHref
                    PublicationUrl = Join-BinderyEndpoint -BaseUrl $BaseUrl -Path $resourceHref
                    Format = Get-ReaderDevResourceFormat -Resource $publicationResource
                }
            }
            $resourceCatalogUrl = Join-BinderyEndpoint -BaseUrl $BaseUrl -Path "/opds/books/$bookId/resources"
            $resourceCatalog = Invoke-BinderyJson -Url $resourceCatalogUrl -Headers $headers
            if ($null -eq $resourceCatalog) {
                continue
            }
            $resource = @(Get-ObjectValue -InputObject $resourceCatalog -Names @("resources")) |
                Where-Object { Test-ReaderDevEbookResource -Resource $_ } |
                Where-Object { Test-ReaderDevResourceLanguage -Resource $_ -LanguageFilter $LanguageFilter } |
                Select-Object -First 1
            if ($null -eq $resource) {
                continue
            }
            $metadata = Get-ObjectValue -InputObject $publication -Names @("metadata")
            $resourceHref = Get-ObjectString -InputObject $resource -Names @("href")
            if ([string]::IsNullOrWhiteSpace($resourceHref)) {
                continue
            }
            return @{
                BookId = $bookId
                Title = Get-ObjectString -InputObject $metadata -Names @("title")
                ResourceHref = $resourceHref
                PublicationUrl = Join-BinderyEndpoint -BaseUrl $BaseUrl -Path $resourceHref
                Format = Get-ReaderDevResourceFormat -Resource $resource
            }
        }
        $nextLink = Get-LinkByRel -Links @(Get-ObjectValue -InputObject $catalog -Names @("links")) -Rel "next"
        $nextHref = Get-ObjectString -InputObject $nextLink -Names @("href")
        $catalogUrl = if ([string]::IsNullOrWhiteSpace($nextHref)) {
            $null
        } else {
            Join-BinderyEndpoint -BaseUrl $BaseUrl -Path $nextHref
        }
    }
    return $null
}

function Add-StringExtra {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.Generic.List[string]] $Arguments,
        [Parameter(Mandatory = $true)]
        [string] $Name,
        [string] $Value
    )

    if (![string]::IsNullOrWhiteSpace($Value)) {
        $Arguments.Add("--es")
        $Arguments.Add($Name)
        $Arguments.Add($Value)
    }
}

function ConvertTo-AdbShellQuotedValue {
    param([string] $Value)

    if ($null -eq $Value) {
        return $null
    }
    $escaped = $Value.Replace("'", "'\''")
    return "'$escaped'"
}

function Add-ShellStringExtra {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.Generic.List[string]] $Arguments,
        [Parameter(Mandatory = $true)]
        [string] $Name,
        [string] $Value
    )

    if (![string]::IsNullOrWhiteSpace($Value)) {
        $Arguments.Add("--es")
        $Arguments.Add($Name)
        $Arguments.Add((ConvertTo-AdbShellQuotedValue -Value $Value))
    }
}

function Grant-ReaderDevNotificationPermission {
    param([Parameter(Mandatory = $true)][string] $Package)

    try {
        Invoke-Adb -Arguments @("shell", "pm", "grant", $Package, "android.permission.POST_NOTIFICATIONS")
        Write-Host "Granted readerDev notification permission for $Package."
    } catch {
        Write-Host "Could not grant readerDev notification permission for ${Package}: $($_.Exception.Message)"
    }
}

$envValues = Read-EnvFile -Path $EnvFile
$opdsBaseUrl = Get-EnvValue -Values $envValues -Keys @("BINDERY_OPDS_BASE_URL", "BINDERY_OPDS_URL")
$apiKey = Get-EnvValue -Values $envValues -Keys @("BINDERY_API_KEY")
$apiKeyHeader = Get-EnvValue -Values $envValues -Keys @("BINDERY_API_KEY_HEADER")
$languageFilter = Get-EnvValue -Values $envValues -Keys @("BINDERY_LANGUAGE_FILTER")
$resourceHref = if (![string]::IsNullOrWhiteSpace($ReaderResourceHref)) {
    $ReaderResourceHref.Trim()
} else {
    Get-EnvValue -Values $envValues -Keys @("NAVIC_READER_DEV_RESOURCE_HREF", "BINDERY_TEST_RESOURCE_ID")
}
$publicationUrl = if (![string]::IsNullOrWhiteSpace($ReaderPublicationUrl)) {
    $ReaderPublicationUrl.Trim()
} else {
    Get-EnvValue -Values $envValues -Keys @("NAVIC_READER_DEV_PUBLICATION_URL")
}
if (!$publicationUrl -and $opdsBaseUrl -and $resourceHref) {
    $publicationUrl = Join-BinderyEndpoint -BaseUrl $opdsBaseUrl -Path $resourceHref
}
$bookId = if (![string]::IsNullOrWhiteSpace($ReaderBookId)) {
    $ReaderBookId.Trim()
} else {
    Get-EnvValue -Values $envValues -Keys @("NAVIC_READER_DEV_BOOK_ID", "BINDERY_TEST_PUBLICATION_ID")
}
$title = if (![string]::IsNullOrWhiteSpace($ReaderTitle)) {
    $ReaderTitle.Trim()
} else {
    Get-EnvValue -Values $envValues -Keys @("NAVIC_READER_DEV_TITLE")
}
$kind = if (![string]::IsNullOrWhiteSpace($ReaderKind)) {
    $ReaderKind.Trim()
} else {
    Get-EnvValue -Values $envValues -Keys @("NAVIC_READER_DEV_KIND")
}
$format = if (![string]::IsNullOrWhiteSpace($ReaderFormat)) {
    $ReaderFormat.Trim()
} else {
    Get-EnvValue -Values $envValues -Keys @("NAVIC_READER_DEV_FORMAT")
}
$startHref = if (![string]::IsNullOrWhiteSpace($ReaderStartHref)) {
    $ReaderStartHref.Trim()
} else {
    Get-EnvValue -Values $envValues -Keys @("NAVIC_READER_DEV_START_HREF")
}
$startCfi = if (![string]::IsNullOrWhiteSpace($ReaderStartCfi)) {
    $ReaderStartCfi.Trim()
} else {
    Get-EnvValue -Values $envValues -Keys @("NAVIC_READER_DEV_START_CFI")
}
$startProgress = if (![string]::IsNullOrWhiteSpace($StartProgress)) {
    $StartProgress
} else {
    Get-EnvValue -Values $envValues -Keys @("NAVIC_READER_DEV_START_PROGRESS")
}
$whispersyncSidecarUrl = if (![string]::IsNullOrWhiteSpace($ReaderWhispersyncSidecarUrl)) {
    $ReaderWhispersyncSidecarUrl.Trim()
} else {
    Get-EnvValue -Values $envValues -Keys @("NAVIC_READER_DEV_WHISPERSYNC_SIDECAR_URL")
}
$whispersyncArtifactId = if (![string]::IsNullOrWhiteSpace($ReaderWhispersyncArtifactId)) {
    $ReaderWhispersyncArtifactId.Trim()
} else {
    Get-EnvValue -Values $envValues -Keys @("NAVIC_READER_DEV_WHISPERSYNC_ARTIFACT_ID")
}
$whispersyncAudiobookId = if (![string]::IsNullOrWhiteSpace($ReaderWhispersyncAudiobookId)) {
    $ReaderWhispersyncAudiobookId.Trim()
} else {
    Get-EnvValue -Values $envValues -Keys @("NAVIC_READER_DEV_WHISPERSYNC_AUDIOBOOK_ID")
}
$whispersyncAudiobookBookFileId = if (![string]::IsNullOrWhiteSpace($ReaderWhispersyncAudiobookBookFileId)) {
    $ReaderWhispersyncAudiobookBookFileId.Trim()
} else {
    Get-EnvValue -Values $envValues -Keys @("NAVIC_READER_DEV_WHISPERSYNC_AUDIOBOOK_BOOK_FILE_ID")
}
$whispersyncAudiobookTitle = if (![string]::IsNullOrWhiteSpace($ReaderWhispersyncAudiobookTitle)) {
    $ReaderWhispersyncAudiobookTitle.Trim()
} else {
    Get-EnvValue -Values $envValues -Keys @("NAVIC_READER_DEV_WHISPERSYNC_AUDIOBOOK_TITLE")
}

$explicitBinderyResource = Resolve-ReaderDevExplicitBinderyResource `
    -BaseUrl $opdsBaseUrl `
    -ApiKey $apiKey `
    -ApiKeyHeader $apiKeyHeader `
    -BookId $bookId `
    -PublicationUrl $publicationUrl `
    -ResourceHref $resourceHref `
    -LanguageFilter $languageFilter
if ($explicitBinderyResource) {
    $bookId = if ($bookId) { $bookId } else { $explicitBinderyResource.BookId }
    $title = if ($title) { $title } else { $explicitBinderyResource.Title }
    $resourceHref = $explicitBinderyResource.ResourceHref
    $publicationUrl = $explicitBinderyResource.PublicationUrl
    $format = if ($format) { $format } else { $explicitBinderyResource.Format }
    Write-Host ("Resolved explicit reader target to Bindery OPDS resource: {0}" -f $resourceHref)
}

if (!$publicationUrl -and !$resourceHref -and !$NoDiscoverPublication) {
    $discoveredPublication = Resolve-ReaderDevPublicationFromBindery `
        -BaseUrl $opdsBaseUrl `
        -ApiKey $apiKey `
        -ApiKeyHeader $apiKeyHeader `
        -LanguageFilter $languageFilter `
        -PreferredBookId $bookId `
        -MaxBooks $MaxDiscoveryBooks
    if ($discoveredPublication) {
        $bookId = if ($bookId) { $bookId } else { $discoveredPublication.BookId }
        $title = if ($title) { $title } else { $discoveredPublication.Title }
        $resourceHref = $discoveredPublication.ResourceHref
        $publicationUrl = $discoveredPublication.PublicationUrl
        $format = if ($format) { $format } else { $discoveredPublication.Format }
        Write-Host ("Discovered Bindery reader target: {0} ({1})" -f $title, $format)
    } else {
        Write-Host "No Bindery reader resource discovered; launching Bindery Books catalog."
    }
}

$readerLaunchHasPublication = ![string]::IsNullOrWhiteSpace($publicationUrl) -or ![string]::IsNullOrWhiteSpace($resourceHref)
if ($RequireReaderLaunch -and !$NoLaunch -and !$readerLaunchHasPublication) {
    throw "Reader launch target required, but no publication URL or resource href was resolved. Pass -PublicationUrl/-ResourceHref, set NAVIC_READER_DEV_PUBLICATION_URL, NAVIC_READER_DEV_RESOURCE_HREF, BINDERY_TEST_RESOURCE_ID in $EnvFile, or allow Bindery discovery to find an EPUB/PDF resource."
}

if (!$NoBuild) {
    Write-Host "Building androidApp:assembleReaderDev..."
    Invoke-GradleWrapper -Arguments @("--no-daemon", ":androidApp:assembleReaderDev")
}

$apkPath = Join-Path (Get-Location) "androidApp\build\outputs\apk\readerDev\Navic.apk"
if (!(Test-Path $apkPath)) {
    throw "readerDev APK not found: $apkPath"
}

if (!$NoInstall) {
    Invoke-Adb -Arguments @("install", "-r", $apkPath)
    Grant-ReaderDevNotificationPermission -Package $Package
}

if ($ReaderAssetServerPort -gt 0) {
    Invoke-Adb -Arguments @("reverse", "tcp:$ReaderAssetServerPort", "tcp:$ReaderAssetServerPort")
}

if (!$NoLaunch) {
    Invoke-Adb -Arguments @("shell", "settings", "put", "secure", "immersive_mode_confirmations", "confirmed")
    if ($readerLaunchHasPublication) {
        Invoke-Adb -Arguments @("logcat", "-c")
    }
    $launchArgs = [System.Collections.Generic.List[string]]::new()
    $launchArgs.AddRange([string[]] @("shell", "am", "start", "-S", "-n", "$Package/$Activity"))
    Add-ShellStringExtra -Arguments $launchArgs -Name "navic.dev.bindery.opds_url" -Value $opdsBaseUrl
    Add-ShellStringExtra -Arguments $launchArgs -Name "navic.dev.bindery.api_key" -Value $apiKey
    Add-ShellStringExtra -Arguments $launchArgs -Name "navic.dev.bindery.language_filter" -Value $languageFilter
    $launchArgs.Add("--ez")
    $launchArgs.Add("navic.dev.reader.web_debugging")
    $launchArgs.Add("true")
    Add-ShellStringExtra -Arguments $launchArgs -Name "navic.dev.reader.publication_url" -Value $publicationUrl
    Add-ShellStringExtra -Arguments $launchArgs -Name "navic.dev.reader.book_id" -Value $bookId
    Add-ShellStringExtra -Arguments $launchArgs -Name "navic.dev.reader.resource_href" -Value $resourceHref
    Add-ShellStringExtra -Arguments $launchArgs -Name "navic.dev.reader.title" -Value $title
    Add-ShellStringExtra -Arguments $launchArgs -Name "navic.dev.reader.kind" -Value $kind
    Add-ShellStringExtra -Arguments $launchArgs -Name "navic.dev.reader.format" -Value $format
    Add-ShellStringExtra -Arguments $launchArgs -Name "navic.dev.reader.start_href" -Value $startHref
    Add-ShellStringExtra -Arguments $launchArgs -Name "navic.dev.reader.start_cfi" -Value $startCfi
    Add-ShellStringExtra -Arguments $launchArgs -Name "navic.dev.reader.start_progress" -Value $startProgress
    Add-ShellStringExtra -Arguments $launchArgs -Name "navic.dev.reader.whispersync_sidecar_url" -Value $whispersyncSidecarUrl
    Add-ShellStringExtra -Arguments $launchArgs -Name "navic.dev.reader.whispersync_artifact_id" -Value $whispersyncArtifactId
    Add-ShellStringExtra -Arguments $launchArgs -Name "navic.dev.reader.whispersync_audiobook_id" -Value $whispersyncAudiobookId
    Add-ShellStringExtra -Arguments $launchArgs -Name "navic.dev.reader.whispersync_audiobook_book_file_id" -Value $whispersyncAudiobookBookFileId
    Add-ShellStringExtra -Arguments $launchArgs -Name "navic.dev.reader.whispersync_audiobook_title" -Value $whispersyncAudiobookTitle

    Write-Host "Launching $Package. Secrets are passed through adb extras and not printed."
    Invoke-Adb -Arguments $launchArgs.ToArray()
    if ($readerLaunchHasPublication) {
        Wait-ReaderDevForeground -Package $Package
        Wait-ReaderDevPublicationReady -Package $Package
    }
}

if ($Capture) {
    if ($NoLaunch) {
        Wait-ReaderDevForeground -Package $Package
    }
    $captureDir = Join-Path (Get-Location) "captures\reader-dev"
    New-Item -ItemType Directory -Force -Path $captureDir | Out-Null
    $remote = "/sdcard/navic-reader-dev.png"
    $local = Join-Path $captureDir ("reader-dev-{0:yyyyMMdd-HHmmss}.png" -f (Get-Date))
    Invoke-Adb -Arguments @("shell", "screencap", "-p", $remote)
    Invoke-Adb -Arguments @("pull", $remote, $local)
    Write-Output "Pulled screenshot: $local"
}
