param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $Tag,

    [ValidateNotNullOrEmpty()]
    [string] $Repo = "Darkaxt/Navic",

    [ValidateNotNullOrEmpty()]
    [string] $Remote = "fork",

    [string] $Branch = "",

    [ValidateNotNullOrEmpty()]
    [string] $Workflow = "Build Navic",

    [long] $RunId = 0,

    [ValidateRange(10, 300)]
    [int] $PollSeconds = 30,

    [ValidateRange(5, 240)]
    [int] $TimeoutMinutes = 90,

    [switch] $SkipPush,

    [switch] $Background,

    [string] $LogFile = ""
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$logDir = Join-Path $repoRoot "release\logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

if ([string]::IsNullOrWhiteSpace($LogFile)) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $LogFile = Join-Path $logDir "$Tag-$stamp.log"
}

function Write-ReleaseLog {
    param([Parameter(Mandatory = $true)][string] $Message)

    $line = "[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ssK"), $Message
    Add-Content -LiteralPath $LogFile -Value $line
    Write-Host $line
}

function Invoke-LoggedCommand {
    param(
        [Parameter(Mandatory = $true)][string] $Command,
        [Parameter(Mandatory = $true)][string[]] $Arguments
    )

    Write-ReleaseLog ("> {0} {1}" -f $Command, ($Arguments -join " "))
    $output = & $Command @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    foreach ($line in $output) {
        Write-ReleaseLog ([string] $line)
    }
    if ($exitCode -ne 0) {
        throw "$Command exited with code $exitCode"
    }
    return ($output -join [Environment]::NewLine)
}

function Invoke-GhJson {
    param([Parameter(Mandatory = $true)][string[]] $Arguments)

    $output = & gh @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        foreach ($line in $output) {
            Write-ReleaseLog ([string] $line)
        }
        throw "gh exited with code $exitCode"
    }
    return ($output -join [Environment]::NewLine)
}

function Get-WorkflowRunForTag {
    $json = Invoke-GhJson @(
        "run", "list",
        "--repo", $Repo,
        "--workflow", $Workflow,
        "--limit", "30",
        "--json", "databaseId,headBranch,event,status,conclusion,url,createdAt"
    )
    $runs = @($json | ConvertFrom-Json)
    $matchingRuns = $runs |
        Where-Object { $_.headBranch -eq $Tag -and $_.event -eq "push" } |
        Sort-Object createdAt -Descending |
        Select-Object -First 1
    return $matchingRuns
}

function Get-WorkflowRun {
    param([Parameter(Mandatory = $true)][long] $Id)

    $json = Invoke-GhJson @(
        "run", "view", "$Id",
        "--repo", $Repo,
        "--json", "status,conclusion,url,jobs"
    )
    return $json | ConvertFrom-Json
}

function Get-ReleaseForTag {
    $json = & gh release view $Tag --repo $Repo --json tagName,url,isLatest,publishedAt,assets 2>&1
    if ($LASTEXITCODE -ne 0) {
        return $null
    }
    return ($json -join [Environment]::NewLine) | ConvertFrom-Json
}

if ($Background) {
    $scriptPath = $PSCommandPath
    $argsList = @(
        "-NoProfile",
        "-NonInteractive",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        "`"$scriptPath`"",
        "-Tag",
        "`"$Tag`"",
        "-Repo",
        "`"$Repo`"",
        "-Remote",
        "`"$Remote`"",
        "-Workflow",
        "`"$Workflow`"",
        "-PollSeconds",
        "$PollSeconds",
        "-TimeoutMinutes",
        "$TimeoutMinutes",
        "-LogFile",
        "`"$LogFile`""
    )
    if (-not [string]::IsNullOrWhiteSpace($Branch)) {
        $argsList += @("-Branch", "`"$Branch`"")
    }
    if ($RunId -gt 0) {
        $argsList += @("-RunId", "$RunId")
    }
    if ($SkipPush) {
        $argsList += "-SkipPush"
    }

    $process = Start-Process `
        -FilePath "powershell.exe" `
        -ArgumentList $argsList `
        -WorkingDirectory $repoRoot `
        -WindowStyle Hidden `
        -PassThru

    Write-Host "Started background release deploy/watch for $Tag."
    Write-Host "PID: $($process.Id)"
    Write-Host "Log: $LogFile"
    exit 0
}

Set-Location -LiteralPath $repoRoot
Write-ReleaseLog "Release deploy/watch started for $Tag in $Repo."
Write-ReleaseLog "Log file: $LogFile"

Invoke-LoggedCommand "gh" @("--version") | Out-Null
Invoke-LoggedCommand "gh" @("auth", "status") | Out-Null

if (-not $SkipPush) {
    if ([string]::IsNullOrWhiteSpace($Branch)) {
        $Branch = Invoke-LoggedCommand "git" @("branch", "--show-current")
        $Branch = $Branch.Trim()
    }
    if ([string]::IsNullOrWhiteSpace($Branch)) {
        throw "Could not resolve the current git branch."
    }

    Invoke-LoggedCommand "git" @("rev-parse", "$Tag^{commit}") | Out-Null
    Invoke-LoggedCommand "git" @("push", $Remote, $Branch) | Out-Null
    Invoke-LoggedCommand "git" @("push", $Remote, $Tag) | Out-Null
} else {
    Write-ReleaseLog "Skipping git push because -SkipPush was supplied."
}

if ($RunId -le 0) {
    $lookupDeadline = (Get-Date).AddMinutes(10)
    do {
        $run = Get-WorkflowRunForTag
        if ($null -ne $run) {
            $RunId = [long] $run.databaseId
            Write-ReleaseLog "Found workflow run $RunId for ${Tag}: $($run.url)"
            break
        }
        Write-ReleaseLog "No workflow run found for $Tag yet; polling again in $PollSeconds seconds."
        Start-Sleep -Seconds $PollSeconds
    } while ((Get-Date) -lt $lookupDeadline)
}

if ($RunId -le 0) {
    throw "Could not find a GitHub Actions run for tag $Tag."
}

$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
do {
    $run = Get-WorkflowRun -Id $RunId
    $jobSummary = (@($run.jobs) |
        ForEach-Object { "{0}:{1}/{2}" -f $_.name, $_.status, $_.conclusion }) -join "; "
    Write-ReleaseLog "Run $RunId status=$($run.status) conclusion=$($run.conclusion) jobs=[$jobSummary]"

    if ($run.status -eq "completed") {
        if ($run.conclusion -ne "success") {
            Write-ReleaseLog "Workflow failed; fetching failed-job logs."
            $failedLogs = & gh run view $RunId --repo $Repo --log-failed 2>&1
            foreach ($line in $failedLogs) {
                Write-ReleaseLog ([string] $line)
            }
            exit 1
        }
        break
    }

    Start-Sleep -Seconds $PollSeconds
} while ((Get-Date) -lt $deadline)

if ((Get-Date) -ge $deadline) {
    throw "Timed out waiting for workflow run $RunId after $TimeoutMinutes minutes."
}

$releaseDeadline = (Get-Date).AddMinutes(10)
do {
    $release = Get-ReleaseForTag
    if ($null -ne $release) {
        $assets = (@($release.assets) | ForEach-Object { $_.name }) -join ", "
        Write-ReleaseLog "Release published: $($release.url)"
        Write-ReleaseLog "Assets: $assets"
        exit 0
    }
    Write-ReleaseLog "Workflow succeeded, but release $Tag is not visible yet; polling again."
    Start-Sleep -Seconds $PollSeconds
} while ((Get-Date) -lt $releaseDeadline)

throw "Workflow succeeded, but GitHub release $Tag was not visible after 10 minutes."
