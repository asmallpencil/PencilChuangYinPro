#Requires -Version 5.1
<#
.SYNOPSIS
    真机核验脚本：把 debug APK 装到已连接的 Android 设备，并采集液态玻璃相关的现场证据。

.DESCRIPTION
    只依赖 adb，不调用 Gradle。执行流程：
      1) 自动探测 adb 与设备（支持 USB 与无线 adb）；
      2) 安装 app-debug.apk（-r -t -d，保留应用数据）；
      3) 清空 logcat 后启动 com.pencil.chuangyin.pro/.MainActivity；
      4) 用 screencap + pull 截图（不用 `adb exec-out > file`，
         因为 PowerShell 的二进制重定向会把 PNG 写坏）；
      5) 导出 logcat（全量尾部 + 液态玻璃/崩溃过滤两份）；
      6) 生成 report.txt：设备信息、APK 的 SHA256、ABI 兼容性检查、截图清单、人工核验清单。

    注意：液态玻璃覆层由独立 GL 层合成，**截屏（screencap）能看到**，
    但 uiautomator / View 层级里看不到，所以核验一律以截屏为准。

.PARAMETER Serial
    指定设备序列号（`adb devices` 第一列）。有多台设备时必填。

.PARAMETER Connect
    无线调试地址，例如 192.168.1.20:5555；会先执行 adb connect。

.PARAMETER Apk
    APK 路径，默认 app/build/outputs/apk/debug/app-debug.apk。

.PARAMETER OutDir
    证据输出根目录，默认 <仓库>/build-verify。

.PARAMETER Burst
    启动后连续截图张数，默认 1。

.PARAMETER Interval
    连续截图间隔秒数，默认 3。

.PARAMETER NoInstall
    跳过安装，仅截图 / 抓日志（用于已装好的设备）。

.EXAMPLE
    .\verify-device.ps1

.EXAMPLE
    .\verify-device.ps1 -Connect 192.168.1.20:5555 -Burst 3

.EXAMPLE
    .\verify-device.ps1 -Serial ABC123 -NoInstall -Burst 2
#>
[CmdletBinding()]
param(
    [string]$Serial,
    [string]$Connect,
    [string]$Apk,
    [string]$OutDir,
    [int]$Burst = 1,
    [int]$Interval = 3,
    [switch]$NoInstall
)

$ErrorActionPreference = 'Stop'
$pkg = 'com.pencil.chuangyin.pro'
$activity = "$pkg/.MainActivity"

# ---------- 定位 adb ----------
function Resolve-Adb {
    param([string]$Explicit)
    $candidates = @()
    if ($Explicit) { $candidates += $Explicit }

    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { $candidates += $onPath.Source }

    $candidates += @(
        'D:\android-sdk\platform-tools\adb.exe',
        'D:\tools\sdk-cmdtools-x\platform-tools\adb.exe',
        'D:\platform-tools\adb.exe',
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        'C:\platform-tools\adb.exe'
    )
    foreach ($p in $candidates) {
        if ($p -and (Test-Path -LiteralPath $p)) { return (Resolve-Path -LiteralPath $p).Path }
    }
    return $null
}

$adbPath = Resolve-Adb -Explicit $env:ADB
if (-not $adbPath) {
    Write-Host '[FAIL] 未找到 adb。' -ForegroundColor Red
    Write-Host '       请把 platform-tools 加入 PATH，或用 set ADB=<adb.exe 全路径> 指定。'
    exit 2
}

$repoRoot = if ($PSScriptRoot) { $PSScriptRoot } else { (Get-Location).Path }
if (-not $Apk) { $Apk = Join-Path $repoRoot 'app\build\outputs\apk\debug\app-debug.apk' }
if (-not $OutDir) { $OutDir = Join-Path $repoRoot 'build-verify' }

# ---------- 设备解析后统一走这个入口 ----------
$script:AdbArgs = @()
$script:AdbPath = $adbPath
function Adb { & $script:AdbPath @script:AdbArgs @args }

Write-Host "adb      : $adbPath"
Write-Host "APK      : $Apk"
Write-Host "输出目录 : $OutDir"

if ($Connect) {
    Write-Host "无线连接 : $Connect"
    Adb connect $Connect | ForEach-Object { Write-Host "  $_" }
    Start-Sleep -Seconds 1
}

# ---------- 枚举设备 ----------
function Get-DeviceRows {
    $out = Adb devices | Select-Object -Skip 1
    foreach ($line in $out) {
        $t = $line.Trim()
        if (-not $t) { continue }
        $parts = $t -split '\s+'
        if ($parts.Count -ge 2) { [pscustomobject]@{ Serial = $parts[0]; State = $parts[1] } }
    }
}

$rows = @(Get-DeviceRows)
$ready = @($rows | Where-Object { $_.State -eq 'device' })
$blocked = @($rows | Where-Object { $_.State -ne 'device' -and $_.State -ne '' })

if ($ready.Count -eq 0) {
    Write-Host ''
    Write-Host '[BLOCKED] 没有处于 device 状态的设备。' -ForegroundColor Yellow
    if ($blocked.Count -gt 0) {
        Write-Host '  检测到以下未就绪条目：' -ForegroundColor Yellow
        foreach ($r in $blocked) { Write-Host ("    {0}  ->  {1}" -f $r.Serial, $r.State) -ForegroundColor Yellow }
        Write-Host '  unauthorized：请在手机屏幕上点「允许 USB 调试」后重跑本脚本。'
        Write-Host '  offline     ：执行 adb kill-server 后重新插拔数据线。'
    }
    else {
        Write-Host '  未发现任何设备。请：'
        Write-Host '    1) 用数据线连接手机，开启「开发者选项 -> USB 调试」，并在弹窗中授权本机；'
        Write-Host "    2) 或改用无线调试：手机与电脑同一局域网，先配对再 adb connect <ip:port>，然后本脚本 -Connect <ip:port>。"
    }
    Write-Host ''
    Write-Host ('当前 `adb devices` 原始输出：')
    Adb devices
    exit 1
}

if ($Serial) {
    if (-not ($ready | Where-Object { $_.Serial -eq $Serial })) {
        Write-Host "[FAIL] 指定的 -Serial '$Serial' 不在已就绪设备中。" -ForegroundColor Red
        exit 1
    }
    $target = $Serial
}
elseif ($ready.Count -eq 1) {
    $target = $ready[0].Serial
}
else {
    Write-Host '[FAIL] 检测到多台设备，请用 -Serial 指定其一：' -ForegroundColor Red
    $ready | ForEach-Object { Write-Host ('   ' + $_.Serial) }
    exit 1
}

$script:AdbArgs = @('-s', $target)
Write-Host "设备     : $target"
Write-Host ''

# ---------- 准备输出目录 ----------
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$session = Join-Path $OutDir $stamp
New-Item -ItemType Directory -Force -Path $session | Out-Null
Write-Host "本次证据 : $session"

$report = New-Object System.Collections.Generic.List[string]
function Note([string]$line) { $report.Add($line); Write-Host $line }

# ---------- 设备信息与 ABI 兼容性 ----------
$model  = (Adb shell getprop ro.product.model 2>$null | Out-String).Trim()
$rel    = (Adb shell getprop ro.build.version.release 2>$null | Out-String).Trim()
$sdk    = (Adb shell getprop ro.build.version.sdk 2>$null | Out-String).Trim()
$abi    = (Adb shell getprop ro.product.cpu.abi 2>$null | Out-String).Trim()
$abi2   = (Adb shell getprop ro.product.cpu.abi2 2>$null | Out-String).Trim()
$sizeOut= (Adb shell wm size 2>$null | Out-String).Trim()

Note "===== LiquidGlass 真机核验 ====="
Note "时间        : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Note "序列号      : $target"
Note "型号        : $model"
Note "Android     : $rel (API $sdk)"
Note "屏幕        : $sizeOut"
Note "CPU abi     : $abi / $abi2"

# 原生库只打包 arm64-v8a 与 armeabi-v7a（见 app/build.gradle.kts 的 abiFilters）。
if ($abi -match 'x86|arm64-v8a|armeabi') {
    if ($abi -notmatch 'arm') {
        Note "[WARN] 设备 ABI 为 $abi，而本应用仅打包 arm64-v8a / armeabi-v7a，" 
        Note "       液态玻璃原生层无法加载，核验结果不具参考性。请改用 ARM 真机。"
    }
}

# ---------- 安装 ----------
if (-not $NoInstall) {
    if (-not (Test-Path -LiteralPath $Apk)) {
        Note "[FAIL] 找不到 APK：$Apk"
        Note "       请先构建：gradle -p `"$repoRoot`" :app:assembleDebug"
        $report | Set-Content -Encoding UTF8 (Join-Path $session 'report.txt')
        exit 2
    }
    $sha = (Get-FileHash -LiteralPath $Apk -Algorithm SHA256).Hash
    $apkLen = (Get-Item -LiteralPath $Apk).Length
    Note "APK         : $Apk"
    Note "APK 字节数  : $apkLen"
    Note "APK SHA256  : $sha"

    Write-Host ''
    Write-Host '--- 安装中 ---'
    $install = Adb install -r -t -d "$Apk" 2>&1
    $install | ForEach-Object { Write-Host "  $_" }
    Note ("安装结果    : " + (($install | Out-String).Trim()))
    if (($install | Out-String) -notmatch 'Success') {
        Note '[FAIL] 安装未返回 Success，后续截图可能仍是旧版本，已中止。'
        $report | Set-Content -Encoding UTF8 (Join-Path $session 'report.txt')
        exit 3
    }
}
else {
    Note '安装        : 已跳过（-NoInstall）'
}

# ---------- 启动并截图 ----------
Write-Host ''
Write-Host '--- 清空日志并启动 ---'
Adb logcat -c | Out-Null
Adb shell am force-stop $pkg | Out-Null
Start-Sleep -Milliseconds 400
$startOut = Adb shell am start -n $activity 2>&1
$startOut | ForEach-Object { Write-Host "  $_" }
Note ("启动        : " + (($startOut | Out-String).Trim()))

function Save-Shot([string]$name) {
    $remote = '/sdcard/lg_verify.png'
    Adb shell screencap -p $remote | Out-Null
    $local = Join-Path $session $name
    Adb pull $remote $local 2>&1 | Out-Null
    Adb shell rm -f $remote | Out-Null
    if (Test-Path -LiteralPath $local) {
        $len = (Get-Item -LiteralPath $local).Length
        Write-Host ("  截图 {0}  ({1} 字节)" -f $name, $len)
        Note ("截图        : {0}  ({1} 字节)" -f $name, $len)
    }
    else {
        Write-Host ("  [WARN] 截图 {0} 未生成" -f $name) -ForegroundColor Yellow
        Note ("[WARN] 截图 {0} 未生成" -f $name)
    }
}

Start-Sleep -Seconds 5
for ($i = 1; $i -le [Math]::Max(1, $Burst); $i++) {
    $name = if ($Burst -le 1) { '01-launch.png' } else { ('{0:d2}-burst.png' -f $i) }
    Save-Shot $name
    if ($i -lt $Burst) { Start-Sleep -Seconds ([Math]::Max(1, $Interval)) }
}

# ---------- 前台窗口与崩溃确认 ----------
$focus = (Adb shell dumpsys window 2>$null | Select-String -Pattern 'mCurrentFocus|mFocusedApp' | Out-String).Trim()
Note "前台窗口    : $focus"

# ---------- 日志 ----------
$logFull = Join-Path $session 'logcat-full.txt'
$logGlass = Join-Path $session 'logcat-glass.txt'
Adb logcat -d -t 4000 -v threadtime 2>&1 | Set-Content -Encoding UTF8 $logFull
Adb logcat -d -t 4000 -v threadtime -s 'LiquidGlass:V' 'AndroidRuntime:E' 'DEBUG:V' 'libc:V' 2>&1 |
    Set-Content -Encoding UTF8 $logGlass

function Count-Hits([string]$path, [string]$pattern) {
    if (-not (Test-Path -LiteralPath $path)) { return 0 }
    return @(Select-String -LiteralPath $path -Pattern $pattern -AllMatches -ErrorAction SilentlyContinue).Count
}
$crashHits = Count-Hits $logFull 'FATAL EXCEPTION|beginning of crash'
$nativeHits = Count-Hits $logFull 'liblgm|LiquidGlass'
Note "日志        : logcat-full.txt / logcat-glass.txt"
Note "崩溃标记    : $crashHits 处 (FATAL EXCEPTION / beginning of crash)"
Note "原生层日志  : $nativeHits 行命中 (liblgm / LiquidGlass)"
if ($crashHits -gt 0) {
    Note '[WARN] 检出崩溃标记，请优先看 logcat-full.txt 中 AndroidRuntime 段。'
}

# ---------- 人工核验清单 ----------
Note ''
Note '===== 人工核验清单（对照截图逐项确认） ====='
Note '[需求1] 图片/文字预览区、输入框：滑动时应为静止的伪玻璃底，无抖动。'
Note '[需求2] 弹窗：长正文（图片打印失败提示 errText + \n\n + detailText）应完整显示，不压住按钮行。'
Note '[需求3] 弹窗弹出/关闭动画应明显快于旧版。'
Note '[需求4] 弹窗卡片材质 与 子背景/侧边栏/弹窗按钮 的玻璃观感应一致。'
Note '[需求5] 各子页面背景、侧边栏应为同一套弹窗玻璃材质。'
Note '[需求6] 图片/文字打印页底部功能栏：已连接打印机后，「保存 / 打印 / 单页」按钮必须可见可点，不得隐形。'
Note '[需求7] 批量打印开关与拖拉条、液态玻璃预览区开关：圆头（knob）必须出现，不得「无头」。'
Note ''
Note "证据目录    : $session"

$reportPath = Join-Path $session 'report.txt'
$report | Set-Content -Encoding UTF8 $reportPath

Write-Host ''
Write-Host '完成。报告：' -NoNewline
Write-Host $reportPath -ForegroundColor Green
