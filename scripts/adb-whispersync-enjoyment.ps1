param(
    [string] $DeviceSerial,
    [string] $EnvFile = "C:\Users\darka\Documents\Projects\Android\Navic\bindery-debug.env",
    [string] $Package = "darkaxt.navic.readerdev",
    [string] $ExpectedVersionName = "v1.0.11-theta17",
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
        [string] $ProbeResultsPath
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
    "whispersync-page-scoped-control",
    "whispersync-audio-follow",
    "whispersync-char-offset-overlay",
    "whispersync-companion-progress"
)

$probeResults = foreach ($probe in $probes) {
    Invoke-WhispersyncProbe -ProbeName $probe -RunRoot $runRoot -ProbeResultsPath $probeResultsPath
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
