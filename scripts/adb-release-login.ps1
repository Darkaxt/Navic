param(
    [string] $Package = "darkaxt.navic",
    [string] $DeviceSerial,
    [string] $EnvFile = "navic-release-login.env",
    [string] $ArtifactDir,
    [switch] $NoLaunch,
    [switch] $RequireLoginScreen,
    [switch] $DetectOnly,
    [switch] $ValidateEnvOnly
)

$ErrorActionPreference = "Stop"

if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
    $env:ANDROID_SERIAL = $DeviceSerial
}

if ([string]::IsNullOrWhiteSpace($ArtifactDir)) {
    $ArtifactDir = Join-Path (Get-Location) ("captures\release-login\{0:yyyyMMdd-HHmmss}" -f (Get-Date))
}
New-Item -ItemType Directory -Force -Path $ArtifactDir | Out-Null

$summaryPath = Join-Path $ArtifactDir "release-login-summary.txt"
$windowXmlPath = Join-Path $ArtifactDir "navic-release-login-window.xml"
$remoteWindowXmlPath = "/sdcard/navic-release-login-window.xml"

function Add-SummaryLine {
    param([Parameter(Mandatory = $true)][string] $Line)

    Add-Content -LiteralPath $summaryPath -Value $Line
}

function Read-EnvFile {
    param([Parameter(Mandatory = $true)][string] $Path)

    $values = @{}
    if (!(Test-Path -LiteralPath $Path)) {
        return $values
    }

    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#")) {
            continue
        }
        $separator = $trimmed.IndexOf("=")
        if ($separator -le 0) {
            continue
        }
        $name = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        if (
            ($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))
        ) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        if (![string]::IsNullOrWhiteSpace($name)) {
            $values[$name] = $value
        }
    }
    return $values
}

function Get-EnvValue {
    param(
        [Parameter(Mandatory = $true)][hashtable] $Values,
        [Parameter(Mandatory = $true)][string[]] $Keys
    )

    foreach ($key in $Keys) {
        if ($Values.ContainsKey($key) -and ![string]::IsNullOrWhiteSpace($Values[$key])) {
            return [string] $Values[$key]
        }
    }
    return $null
}

function Write-RedactedCredentialSummary {
    param(
        [Parameter(Mandatory = $true)][string] $Name,
        [Parameter(Mandatory = $true)][string[]] $Keys,
        [string] $Value
    )

    $state = if ([string]::IsNullOrWhiteSpace($Value)) { "missing" } else { "present" }
    Add-SummaryLine ("credentialGroup={0} keys={1} state={2}" -f $Name, ($Keys -join "|"), $state)
    if (![string]::IsNullOrWhiteSpace($Value)) {
        Write-Host ("{0}: value: <redacted>" -f $Name)
    } else {
        Write-Host ("{0}: missing keys {1}" -f $Name, ($Keys -join " or "))
    }
}

function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Arguments,
        [switch] $PassThru
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & adb @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0) {
        throw "adb $($Arguments -join ' ') failed with exit code $exitCode`n$($output -join "`n")"
    }
    if ($PassThru) {
        return @($output)
    }
}

function Get-AdbDeviceSerials {
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & adb devices 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0) {
        throw "adb devices failed with exit code $exitCode`n$($output -join "`n")"
    }
    return @(
        $output |
            Where-Object { $_ -match '^([^\s]+)\s+device$' } |
            ForEach-Object { $Matches[1] }
    )
}

function Assert-SingleAdbDeviceOrSelectedSerial {
    if (![string]::IsNullOrWhiteSpace($DeviceSerial)) {
        return
    }

    $serials = @(Get-AdbDeviceSerials)
    if ($serials.Count -eq 0) {
        throw "No adb device is connected. Start an emulator or connect a device before release login validation."
    }
    if ($serials.Count -gt 1) {
        throw "Multiple adb devices are connected: $($serials -join ', '). Pass -DeviceSerial to select the release validation target."
    }
}

function ConvertTo-XPathLiteral {
    param([Parameter(Mandatory = $true)][string] $Value)

    if (!$Value.Contains("'")) {
        return "'$Value'"
    }
    if (!$Value.Contains('"')) {
        return '"' + $Value + '"'
    }
    $parts = $Value.Split("'") | ForEach-Object { "'$_'" }
    return "concat(" + ($parts -join ', "\"''\"", ') + ")"
}

function Get-UiXml {
    Invoke-Adb -Arguments @("shell", "uiautomator", "dump", $remoteWindowXmlPath) | Out-Null
    Invoke-Adb -Arguments @("pull", $remoteWindowXmlPath, $windowXmlPath) | Out-Null
    [xml] (Get-Content -LiteralPath $windowXmlPath -Raw)
}

function Select-UiNodeByText {
    param(
        [Parameter(Mandatory = $true)][xml] $Document,
        [Parameter(Mandatory = $true)][string] $Text
    )

    $literal = ConvertTo-XPathLiteral -Value $Text
    $Document.SelectSingleNode("//*[@text=$literal or @content-desc=$literal]")
}

function Test-LoginScreen {
    param([Parameter(Mandatory = $true)][xml] $Document)

    $requiredLabels = @("Log in", "Instance URL", "Username", "Password")
    foreach ($label in $requiredLabels) {
        if ($null -eq (Select-UiNodeByText -Document $Document -Text $label)) {
            return $false
        }
    }
    return $true
}

function Get-UiNodeCenter {
    param([Parameter(Mandatory = $true)] $Node)

    $bounds = [string] $Node.bounds
    if ($bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') {
        throw "Could not parse UI node bounds: $bounds"
    }
    return [pscustomobject]@{
        X = [math]::Round(([int] $Matches[1] + [int] $Matches[3]) / 2)
        Y = [math]::Round(([int] $Matches[2] + [int] $Matches[4]) / 2)
    }
}

function Invoke-TapNode {
    param([Parameter(Mandatory = $true)] $Node)

    $center = Get-UiNodeCenter -Node $Node
    Invoke-Adb -Arguments @("shell", "input", "tap", "$($center.X)", "$($center.Y)")
}

function ConvertTo-AdbInputText {
    param([Parameter(Mandatory = $true)][string] $Value)

    $escaped = $Value
    $escaped = $escaped.Replace("\", "\\")
    foreach ($character in @("&", "|", ";", "<", ">", "(", ")", "`"", "'", "`$", "*", "?", "[", "]", "{", "}", "!")) {
        $escaped = $escaped.Replace($character, "\$character")
    }
    $escaped = $escaped.Replace(" ", "%s")
    return $escaped
}

function Clear-FocusedTextField {
    Invoke-Adb -Arguments @("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    for ($index = 0; $index -lt 96; $index++) {
        Invoke-Adb -Arguments @("shell", "input", "keyevent", "KEYCODE_DEL")
    }
}

function Set-LoginField {
    param(
        [Parameter(Mandatory = $true)][string] $Label,
        [Parameter(Mandatory = $true)][string] $Value
    )

    $document = Get-UiXml
    $node = Select-UiNodeByText -Document $document -Text $Label
    if ($null -eq $node) {
        throw "Could not find login field label '$Label'."
    }
    Invoke-TapNode -Node $node
    Start-Sleep -Milliseconds 250
    Clear-FocusedTextField
    Invoke-Adb -Arguments @("shell", "input", "text", (ConvertTo-AdbInputText -Value $Value))
}

function Invoke-ReleaseLoginDetection {
    if (!$NoLaunch) {
        Invoke-Adb -Arguments @("shell", "monkey", "-p", $Package, "1")
        Start-Sleep -Seconds 1
    }

    $document = Get-UiXml
    $isLoginScreen = Test-LoginScreen -Document $document
    Add-SummaryLine "package=$Package"
    Add-SummaryLine "loginScreen=$isLoginScreen"
    Add-SummaryLine "windowXml=$windowXmlPath"
    if ($DetectOnly) {
        Add-SummaryLine "detectOnly=true"
    }

    if (!$isLoginScreen) {
        if ($RequireLoginScreen) {
            throw "Release login screen was not detected for $Package. Artifact: $windowXmlPath"
        }
        Write-Host "Release login screen not detected; no credentials were entered. Artifact: $windowXmlPath"
        return $false
    }
    Write-Host "Release login screen detected for $Package. Artifact: $windowXmlPath"
    return $true
}

function Invoke-ReleaseLogin {
    param(
        [Parameter(Mandatory = $true)][string] $InstanceUrl,
        [Parameter(Mandatory = $true)][string] $Username,
        [Parameter(Mandatory = $true)][string] $Password
    )

    $isLoginScreen = Invoke-ReleaseLoginDetection
    if (!$isLoginScreen) {
        return
    }

    Set-LoginField -Label "Instance URL" -Value $InstanceUrl
    Set-LoginField -Label "Username" -Value $Username
    Set-LoginField -Label "Password" -Value $Password

    $document = Get-UiXml
    $loginButton = Select-UiNodeByText -Document $document -Text "Log in"
    if ($null -eq $loginButton) {
        throw "Could not find the Log in button after filling credentials."
    }
    Invoke-TapNode -Node $loginButton
    Add-SummaryLine "submitted=true"
    Write-Host "Release login submitted for $Package. Credential values were not printed."
}

if ($DetectOnly) {
    Assert-SingleAdbDeviceOrSelectedSerial
    Invoke-ReleaseLoginDetection | Out-Null
    return
}

$envValues = Read-EnvFile -Path $EnvFile
$instanceUrlKeys = @("NAVIC_INSTANCE_URL", "NAVIDROME_BASE_URL", "NAVIDROME_URL")
$usernameKeys = @("NAVIC_USERNAME", "NAVIDROME_USERNAME")
$passwordKeys = @("NAVIC_PASSWORD", "NAVIDROME_PASSWORD")

$instanceUrl = Get-EnvValue -Values $envValues -Keys $instanceUrlKeys
$username = Get-EnvValue -Values $envValues -Keys $usernameKeys
$password = Get-EnvValue -Values $envValues -Keys $passwordKeys

Add-SummaryLine "envFile=$EnvFile"
Write-RedactedCredentialSummary -Name "instanceUrl" -Keys $instanceUrlKeys -Value $instanceUrl
Write-RedactedCredentialSummary -Name "username" -Keys $usernameKeys -Value $username
Write-RedactedCredentialSummary -Name "password" -Keys $passwordKeys -Value $password

$missingGroups = @()
if ([string]::IsNullOrWhiteSpace($instanceUrl)) {
    $missingGroups += ($instanceUrlKeys -join "/")
}
if ([string]::IsNullOrWhiteSpace($username)) {
    $missingGroups += ($usernameKeys -join "/")
}
if ([string]::IsNullOrWhiteSpace($password)) {
    $missingGroups += ($passwordKeys -join "/")
}

if ($missingGroups.Count -gt 0) {
    Add-SummaryLine ("missing={0}" -f ($missingGroups -join ","))
    throw "Missing release login env keys: $($missingGroups -join ', '). Copy navic-release-login.env.example to navic-release-login.env or pass -EnvFile."
}

if ($ValidateEnvOnly) {
    Add-SummaryLine "validateEnvOnly=true"
    Write-Host "Release login env is complete. Credential values were not printed."
    return
}

Assert-SingleAdbDeviceOrSelectedSerial
Invoke-ReleaseLogin -InstanceUrl $instanceUrl -Username $username -Password $password
