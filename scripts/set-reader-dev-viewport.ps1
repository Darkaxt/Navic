param(
    [ValidateSet(
        "zfold7-inner",
        "zfold7-inner-landscape",
        "zfold7-cover",
        "tab-s9-ultra-landscape",
        "tab-s9-ultra-portrait",
        "reset"
    )]
    [string] $Profile = "zfold7-inner",
    [string] $DeviceSerial
)

$ErrorActionPreference = "Stop"

function Invoke-Adb {
    param([Parameter(Mandatory = $true)][string[]] $Arguments)

    $adbArgs = @()
    if ($DeviceSerial) {
        $adbArgs += @("-s", $DeviceSerial)
    }
    $adbArgs += $Arguments
    & adb @adbArgs
    if ($LASTEXITCODE -ne 0) {
        throw "adb $($adbArgs -join ' ') failed with exit code $LASTEXITCODE"
    }
}

$profiles = @{
    "zfold7-inner" = @{
        Label = "Galaxy Z Fold7 inner display portrait"
        Size = "1968x2184"
        Density = "368"
        LockRotation = $false
    }
    "zfold7-inner-landscape" = @{
        Label = "Galaxy Z Fold7 inner display landscape"
        Size = "2184x1968"
        Density = "368"
        LockRotation = $false
    }
    "zfold7-cover" = @{
        Label = "Galaxy Z Fold7 cover display portrait"
        Size = "1080x2520"
        Density = "422"
        LockRotation = $false
    }
    "tab-s9-ultra-landscape" = @{
        Label = "Galaxy Tab S9 Ultra landscape"
        Size = "2960x1848"
        Density = "240"
        LockRotation = $false
    }
    "tab-s9-ultra-portrait" = @{
        Label = "Galaxy Tab S9 Ultra portrait"
        Size = "1848x2960"
        Density = "240"
        LockRotation = $false
    }
}

if ($Profile -eq "reset") {
    Invoke-Adb -Arguments @("shell", "wm", "size", "reset")
    Invoke-Adb -Arguments @("shell", "wm", "density", "reset")
    Invoke-Adb -Arguments @("shell", "settings", "put", "system", "accelerometer_rotation", "1")
    Write-Host "Reader dev viewport reset to emulator defaults."
    exit 0
}

$profileConfig = $profiles[$Profile]
Invoke-Adb -Arguments @("shell", "wm", "size", $profileConfig.Size)
Invoke-Adb -Arguments @("shell", "wm", "density", $profileConfig.Density)
if ($profileConfig.LockRotation) {
    Invoke-Adb -Arguments @("shell", "settings", "put", "system", "accelerometer_rotation", "0")
    Invoke-Adb -Arguments @("shell", "settings", "put", "system", "user_rotation", "0")
} else {
    Invoke-Adb -Arguments @("shell", "settings", "put", "system", "accelerometer_rotation", "1")
    Invoke-Adb -Arguments @("shell", "settings", "put", "system", "user_rotation", "0")
}

Write-Host ("Reader dev viewport set: {0} ({1}, {2} dpi)" -f $profileConfig.Label, $profileConfig.Size, $profileConfig.Density)
