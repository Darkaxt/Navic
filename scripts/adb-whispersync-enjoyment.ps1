param(
    [string] $DeviceSerial,
    [string] $EnvFile = "C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env",
    [string] $Package = "darkaxt.navic.readerdev",
    [string] $ExpectedVersionName = "",
    [string] $ArtifactRoot = "captures\reader-whispersync-enjoyment",
    [switch] $NoBuild,
    [switch] $NoInstall,
    [switch] $SkipLaunch,
    [string] $ReaderPublicationUrl = "https://bindery.remaxku.eu/book/3809",
    [string] $ReaderResourceHref = "https://bindery.remaxku.eu/api/v1/book/3809/file?bookFileId=426",
    [string] $ReaderBookId = "3809",
    [string] $ReaderTitle = "Bastille vs. the Evil Librarians",
    [string] $ReaderKind = "Ebook",
    [string] $ReaderFormat = "epub",
    [string] $ReaderWhispersyncSidecarUrl = "/opds/books/3809/sync/8",
    [string] $ReaderWhispersyncArtifactId = "8",
    [string] $ReaderWhispersyncAudiobookId = "34",
    [string] $ReaderWhispersyncAudiobookBookFileId = "633",
    [string] $ReaderWhispersyncAudiobookTitle = "Bastille vs. the Evil Librarians"
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptRoot "..")).Path
$installReaderDevScript = Join-Path $scriptRoot "install-reader-dev.ps1"
$smokeScript = Join-Path $scriptRoot "adb-reader-smoke.ps1"

if (-not (Test-Path -LiteralPath $installReaderDevScript -PathType Leaf)) {
    throw "install-reader-dev.ps1 was not found: $installReaderDevScript"
}
if (-not (Test-Path -LiteralPath $smokeScript -PathType Leaf)) {
    throw "adb-reader-smoke.ps1 was not found: $smokeScript"
}

if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
    $env:ANDROID_SERIAL = $DeviceSerial
}

function Get-AndroidReleaseVersionName {
    $buildFile = Join-Path $repoRoot "androidApp\build.gradle.kts"
    if (-not (Test-Path -LiteralPath $buildFile -PathType Leaf)) {
        throw "Android build file not found: $buildFile"
    }

    $content = Get-Content -LiteralPath $buildFile -Raw
    $versionNameMatch = [regex]::Match($content, 'versionName\s*=\s*"([^"]+)"')
    if (-not $versionNameMatch.Success) {
        throw "Could not find androidApp versionName in $buildFile"
    }
    return $versionNameMatch.Groups[1].Value
}

if ([string]::IsNullOrWhiteSpace($ExpectedVersionName)) {
    $ExpectedVersionName = Get-AndroidReleaseVersionName
}

function Resolve-StagePath {
    param([Parameter(Mandatory = $true)][string] $Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }
    return Join-Path $repoRoot $Path
}

function Invoke-StageCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string] $ScriptPath,
        [Parameter(Mandatory = $true)]
        [hashtable] $Arguments,
        [Parameter(Mandatory = $true)]
        [string] $LogPath
    )

    try {
        & $ScriptPath @Arguments *> $LogPath
        $exitCode = if ($null -ne $LASTEXITCODE) { [int] $LASTEXITCODE } else { 0 }
    } catch {
        $_ | Out-File -Encoding utf8 -Append $LogPath
        throw "Stage command failed: $ScriptPath. See $LogPath"
    }
    if ($exitCode -ne 0) {
        throw "Stage command failed with exit code $exitCode`: $ScriptPath. See $LogPath"
    }
}

function Add-OptionalArgument {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable] $Target,
        [Parameter(Mandatory = $true)]
        [string] $Name,
        [string] $Value
    )

    if (-not [string]::IsNullOrWhiteSpace($Value)) {
        $Target[$Name] = $Value
    }
}

function Invoke-WhispersyncProbe {
    param(
        [Parameter(Mandatory = $true)]
        [string] $ProbeName,
        [Parameter(Mandatory = $true)]
        [string] $RunRoot,
        [Parameter(Mandatory = $true)]
        [string] $ProbeResultsPath,
        [string[]] $PostProbeAction = @(),
        [string[]] $RequireReaderLog = @()
    )

    $probeDir = Join-Path $RunRoot $ProbeName
    New-Item -ItemType Directory -Force -Path $probeDir | Out-Null
    $probeLog = Join-Path $RunRoot "$ProbeName.log"

    $smokeArgs = @{
        Package = $Package
        NoLaunch = $true
        CaptureReaderDiagnostics = $true
        ReaderDevtoolsProbe = $ProbeName
        ArtifactDir = $probeDir
    }
    if ($PostProbeAction.Count -gt 0) {
        $smokeArgs["PostProbeAction"] = $PostProbeAction
    }
    if ($RequireReaderLog.Count -gt 0) {
        $smokeArgs["RequireReaderLog"] = $RequireReaderLog
    }
    Add-OptionalArgument -Target $smokeArgs -Name "DeviceSerial" -Value $DeviceSerial
    Add-OptionalArgument -Target $smokeArgs -Name "ExpectedVersionName" -Value $ExpectedVersionName

    Invoke-StageCommand -ScriptPath $smokeScript -Arguments $smokeArgs -LogPath $probeLog

    $entry = [pscustomobject]@{
        stage = "5C.3"
        probe = $ProbeName
        Result = "PASS"
        artifactDir = $probeDir
        log = $probeLog
    }
    $entry | ConvertTo-Json -Compress | Out-File -Encoding utf8 -Append $ProbeResultsPath
    return $entry
}

$resolvedArtifactRoot = Resolve-StagePath -Path $ArtifactRoot
$runName = "stage5c3-whispersync-enjoyment-{0:yyyyMMdd-HHmmss}" -f (Get-Date)
$runRoot = Join-Path $resolvedArtifactRoot $runName
New-Item -ItemType Directory -Force -Path $runRoot | Out-Null

$probeResultsPath = Join-Path $runRoot "probe-results.jsonl"
$summaryPath = Join-Path $runRoot "stage5c3-whispersync-enjoyment-summary.txt"
$launchLog = Join-Path $runRoot "launch-readerdev.log"

"Secrets are passed through the existing readerdev launcher and are not printed here." |
    Out-File -Encoding utf8 $summaryPath

if (-not $SkipLaunch) {
    $launchArgs = @{
        EnvFile = $EnvFile
        Package = $Package
        RequireReaderLaunch = $true
        Capture = $true
        ReaderPublicationUrl = $ReaderPublicationUrl
        ReaderResourceHref = $ReaderResourceHref
        ReaderBookId = $ReaderBookId
        ReaderTitle = $ReaderTitle
        ReaderKind = $ReaderKind
        ReaderFormat = $ReaderFormat
        ReaderWhispersyncSidecarUrl = $ReaderWhispersyncSidecarUrl
        ReaderWhispersyncArtifactId = $ReaderWhispersyncArtifactId
        ReaderWhispersyncAudiobookId = $ReaderWhispersyncAudiobookId
        ReaderWhispersyncAudiobookBookFileId = $ReaderWhispersyncAudiobookBookFileId
        ReaderWhispersyncAudiobookTitle = $ReaderWhispersyncAudiobookTitle
    }
    Add-OptionalArgument -Target $launchArgs -Name "DeviceSerial" -Value $DeviceSerial
    if ($NoBuild) {
        $launchArgs["NoBuild"] = $true
    }
    if ($NoInstall) {
        $launchArgs["NoInstall"] = $true
    }

    Invoke-StageCommand -ScriptPath $installReaderDevScript -Arguments $launchArgs -LogPath $launchLog
}

$probes = @(
    @{
        Name = "whispersync-page-scoped-control"
        PostProbeAction = @()
        RequireReaderLog = @()
    },
    @{
        Name = "whispersync-audio-follow"
        PostProbeAction = @()
        RequireReaderLog = @()
    },
    @{
        Name = "whispersync-char-offset-overlay"
        PostProbeAction = @()
        RequireReaderLog = @()
    },
    @{
        Name = "whispersync-companion-progress"
        PostProbeAction = @(
            "tapDescWhenPresent:Play Whispersync audiobook,20,500",
            "waitDesc:Pause Whispersync audiobook,20,500",
            "tapDescIfPresent:Close history controls,500",
            "keyevent:4,1000"
        )
        RequireReaderLog = @(
            "Whispersync play preseek",
            "Whispersync activeSegment",
            "ApplyMediaOverlay",
            "overlayFragmentActive",
            "Pausing Whispersync audiobook on reader exit"
        )
    }
)

$probeResults = foreach ($probe in $probes) {
    Invoke-WhispersyncProbe `
        -ProbeName $probe.Name `
        -RunRoot $runRoot `
        -ProbeResultsPath $probeResultsPath `
        -PostProbeAction @($probe.PostProbeAction) `
        -RequireReaderLog @($probe.RequireReaderLog)
}

@(
    "Stage=5C.3 Whispersync Enjoyment Gate Orchestrator",
    "Result=PASS",
    "Package=$Package",
    "DeviceSerial=$DeviceSerial",
    "BookId=$ReaderBookId",
    "EbookFile=426",
    "Sidecar=$ReaderWhispersyncSidecarUrl",
    "AudiobookId=$ReaderWhispersyncAudiobookId",
    "AudiobookBookFileId=$ReaderWhispersyncAudiobookBookFileId",
    "LaunchLog=$launchLog",
    "ProbeResults=$probeResultsPath"
) + ($probeResults | ForEach-Object {
    "Probe=$($_.probe) Result=PASS ArtifactDir=$($_.artifactDir)"
}) | Out-File -Encoding utf8 -Append $summaryPath

Write-Host "Whispersync enjoyment gate passed."
Write-Host "Summary: $summaryPath"
Write-Host "Probe results: $probeResultsPath"
