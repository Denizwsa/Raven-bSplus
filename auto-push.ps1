[CmdletBinding()]
param(
    [string]$Path = (Get-Location).Path,
    [string]$Remote = "origin",
    [string]$Branch = "main",
    [int]$DebounceSeconds = 5,
    [switch]$Once
)

$ErrorActionPreference = "Continue"
$Path = (Resolve-Path $Path).Path

Write-Host "Auto-push watcher"
Write-Host "  Path  : $Path"
Write-Host "  Remote: $Remote  Branch: $Branch"
Write-Host "  Debounce: ${DebounceSeconds}s"
Write-Host ""

if ($Once) {
    & {
        git -C $Path add -A 2>&1 | Out-Null
        $status = git -C $Path status --porcelain 2>&1
        if ([string]::IsNullOrWhiteSpace($status)) {
            Write-Host "Nothing to commit."
            return
        }
        $count = ($status -split "`r?`n" | Where-Object { $_ }).Count
        $msg = "auto: $count file(s) at $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
        git -C $Path commit -m $msg 2>&1 | Out-Null
        git -C $Path push $Remote $Branch 2>&1 | Out-Null
        Write-Host "[$(Get-Date -Format 'HH:mm:ss')] pushed: $msg"
    }
    return
}

$global:pending = $false
$global:lastChange = Get-Date

$excluded = @('\.git\', '\\node_modules\\', '\\build\\', '\\bin\\', '\\\.gradle\\', '\\out\\')

function Test-Excluded {
    param([string]$fullPath)
    if ([string]::IsNullOrEmpty($fullPath)) { return $true }
    $norm = $fullPath -replace '/', '\'
    foreach ($ex in $excluded) {
        if ($norm -match $ex) { return $true }
    }
    return $false
}

function Invoke-Push {
    if (-not $global:pending) { return }
    $global:pending = $false
    try {
        git -C $Path add -A 2>&1 | Out-Null
        $status = git -C $Path status --porcelain 2>&1
        if ([string]::IsNullOrWhiteSpace($status)) { return }
        $count = ($status -split "`r?`n" | Where-Object { $_ }).Count
        $msg = "auto: $count file(s) at $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
        $commitOut = git -C $Path commit -m $msg 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "commit failed: $commitOut"
            return
        }
        $pushOut = git -C $Path push $Remote $Branch 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "push failed: $pushOut"
            return
        }
        Write-Host "[$(Get-Date -Format 'HH:mm:ss')] pushed: $msg"
    } catch {
        Write-Warning "push error: $_"
    }
}

$watcher = New-Object System.IO.FileSystemWatcher
$watcher.Path = $Path
$watcher.IncludeSubdirectories = $true
$watcher.EnableRaisingEvents = $true
$watcher.InternalBufferSize = 65536
$watcher.NotifyFilter = [System.IO.NotifyFilters]::FileName -bor `
                        [System.IO.NotifyFilters]::LastWrite -bor `
                        [System.IO.NotifyFilters]::DirectoryName -bor `
                        [System.IO.NotifyFilters]::Size

$handler = {
    try {
        $fullPath = $Event.SourceEventArgs.FullPath
        if (Test-Excluded $fullPath) { return }
    } catch { }
    $global:pending = $true
    $global:lastChange = Get-Date
}

Register-ObjectEvent -InputObject $watcher -EventName Changed  -Action $handler | Out-Null
Register-ObjectEvent -InputObject $watcher -EventName Created  -Action $handler | Out-Null
Register-ObjectEvent -InputObject $watcher -EventName Deleted  -Action $handler | Out-Null
Register-ObjectEvent -InputObject $watcher -EventName Renamed  -Action $handler | Out-Null

$timer = New-Object System.Timers.Timer
$timer.Interval = 1000
$timer.AutoReset = $true

Register-ObjectEvent -InputObject $timer -EventName Elapsed -Action {
    if (-not $global:pending) { return }
    $elapsed = (Get-Date) - $global:lastChange
    if ($elapsed.TotalSeconds -ge $DebounceSeconds) {
        Invoke-Push
    }
} | Out-Null

$timer.Start()

Write-Host "Watching for changes. Press Ctrl+C to stop."
while ($true) { Start-Sleep -Seconds 60 }
