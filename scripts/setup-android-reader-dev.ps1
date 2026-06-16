param(
    [string] $AvdName = "NavicReaderLab",
    [string] $SystemImage = "system-images;android-35;google_apis;x86_64",
    [string] $DeviceProfile = "pixel_7",
    [switch] $NoStart
)

$ErrorActionPreference = "Stop"

function Get-AndroidSdkRoot {
    if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) {
        return $env:ANDROID_HOME
    }
    if ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) {
        return $env:ANDROID_SDK_ROOT
    }
    $default = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path $default) {
        return $default
    }
    throw "Android SDK root not found. Set ANDROID_HOME or ANDROID_SDK_ROOT."
}

function Get-SdkTool {
    param(
        [Parameter(Mandatory = $true)]
        [string] $SdkRoot,
        [Parameter(Mandatory = $true)]
        [string] $ToolName
    )

    $direct = Join-Path $SdkRoot $ToolName
    if (Test-Path $direct) {
        return $direct
    }
    $candidate = Get-ChildItem -Path $SdkRoot -Recurse -Filter (Split-Path $ToolName -Leaf) -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -like "*cmdline-tools*" -or $_.FullName -like "*emulator*" -or $_.FullName -like "*platform-tools*" } |
        Select-Object -First 1
    if ($candidate) {
        return $candidate.FullName
    }
    throw "Android SDK tool '$ToolName' not found under $SdkRoot."
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)]
        [string] $FilePath,
        [Parameter(Mandatory = $true)]
        [string[]] $Arguments
    )

    Write-Host "> $FilePath $($Arguments -join ' ')"
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FilePath $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Test-SdkPackageInstalled {
    param(
        [Parameter(Mandatory = $true)]
        [string] $SdkManager,
        [Parameter(Mandatory = $true)]
        [string] $Package
    )

    $installed = & $SdkManager "--list_installed"
    if ($LASTEXITCODE -ne 0) {
        throw "sdkmanager --list_installed failed with exit code $LASTEXITCODE"
    }
    return ($installed -join "`n").Contains($Package)
}

function Ensure-SdkPackage {
    param(
        [Parameter(Mandatory = $true)]
        [string] $SdkManager,
        [Parameter(Mandatory = $true)]
        [string] $Package
    )

    if (Test-SdkPackageInstalled -SdkManager $SdkManager -Package $Package) {
        Write-Host "SDK package already installed: $Package"
        return
    }
    Invoke-Checked -FilePath $SdkManager -Arguments @($Package)
}

function Ensure-Avd {
    param(
        [Parameter(Mandatory = $true)]
        [string] $AvdManager,
        [Parameter(Mandatory = $true)]
        [string] $Name,
        [Parameter(Mandatory = $true)]
        [string] $Image,
        [Parameter(Mandatory = $true)]
        [string] $Profile
    )

    $avds = & $AvdManager "list" "avd"
    if ($LASTEXITCODE -ne 0) {
        throw "avdmanager list avd failed with exit code $LASTEXITCODE"
    }
    if (($avds -join "`n") -match "Name:\s*$([regex]::Escape($Name))\b") {
        Write-Host "AVD already exists: $Name"
        return
    }
    $command = "echo no | `"$AvdManager`" create avd -n `"$Name`" -k `"$Image`" -d `"$Profile`""
    Write-Host "> $command"
    cmd /c $command
    if ($LASTEXITCODE -ne 0) {
        throw "avdmanager create avd failed with exit code $LASTEXITCODE"
    }
}

function Start-ReaderAvd {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Emulator,
        [Parameter(Mandatory = $true)]
        [string] $Name
    )

    $devices = (& adb devices) -join "`n"
    if ($devices -match "emulator-\d+\s+device") {
        Write-Host "An emulator is already connected."
        return
    }
    Write-Host "Starting AVD $Name..."
    Start-Process -FilePath $Emulator -ArgumentList @("-avd", $Name, "-no-boot-anim") -WindowStyle Hidden
    & adb wait-for-device
    if ($LASTEXITCODE -ne 0) {
        throw "adb wait-for-device failed with exit code $LASTEXITCODE"
    }
    do {
        Start-Sleep -Seconds 2
        $bootCompleted = (& adb shell getprop sys.boot_completed 2>$null) -join ""
        Write-Host "Waiting for emulator boot..."
    } until ($bootCompleted.Trim() -eq "1")
    & adb shell settings put global stay_on_while_plugged_in 3 | Out-Null
    & adb shell settings put global window_animation_scale 0 | Out-Null
    & adb shell settings put global transition_animation_scale 0 | Out-Null
    & adb shell settings put global animator_duration_scale 0 | Out-Null
    & adb shell settings put secure immersive_mode_confirmations confirmed | Out-Null
    & adb shell input keyevent 82 | Out-Null
    Write-Host "Reader emulator is ready."
}

$sdkRoot = Get-AndroidSdkRoot
$sdkManager = Get-SdkTool -SdkRoot $sdkRoot -ToolName "cmdline-tools\latest\bin\sdkmanager.bat"
$avdManager = Get-SdkTool -SdkRoot $sdkRoot -ToolName "cmdline-tools\latest\bin\avdmanager.bat"

Ensure-SdkPackage -SdkManager $sdkManager -Package "platform-tools"
Ensure-SdkPackage -SdkManager $sdkManager -Package "emulator"
Ensure-SdkPackage -SdkManager $sdkManager -Package "platforms;android-37"
Ensure-SdkPackage -SdkManager $sdkManager -Package $SystemImage

$emulator = Get-SdkTool -SdkRoot $sdkRoot -ToolName "emulator\emulator.exe"
Ensure-Avd -AvdManager $avdManager -Name $AvdName -Image $SystemImage -Profile $DeviceProfile

if (!$NoStart) {
    Start-ReaderAvd -Emulator $emulator -Name $AvdName
}
