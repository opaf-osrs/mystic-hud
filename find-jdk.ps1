# Finds a Java 11 or newer that is already installed but not on PATH and has no
# JAVA_HOME pointing at it, which is the normal state after someone installs Java
# by hand. Prints the folder to stdout and exits 0, or prints nothing and exits 1.
#
# By default it insists on bin\javac.exe: gradle has to compile, and a bare JRE
# cannot. Pass -RuntimeOnly to accept bin\java.exe as well, which is all that is
# needed to start the packaged jar.

param([switch]$RuntimeOnly)

$ErrorActionPreference = 'SilentlyContinue'
$needle = if ($RuntimeOnly) { 'bin\java.exe' } else { 'bin\javac.exe' }

$roots = @(
	$env:ProgramFiles,
	${env:ProgramFiles(x86)},
	"$env:LOCALAPPDATA\Programs",
	"$env:ProgramData",
	'C:\Java',
	'C:\tools'
) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -Unique

# vendors nest one level deep (Microsoft\jdk-11, Eclipse Adoptium\jdk-11, Java\jdk-17)
# and some install straight into the root, so check both. depth stops there: a full
# disk walk on someone else's machine is not worth the minutes.
$dirs = foreach ($r in $roots) {
	$lvl1 = Get-ChildItem -LiteralPath $r -Directory
	$lvl1
	foreach ($d in $lvl1) { Get-ChildItem -LiteralPath $d.FullName -Directory }
}

# anything the registry knows about, which catches unusual install locations
$regHomes = foreach ($key in @(
	'HKLM:\SOFTWARE\JavaSoft\JDK\*',
	'HKLM:\SOFTWARE\JavaSoft\Java Development Kit\*',
	'HKLM:\SOFTWARE\WOW6432Node\JavaSoft\JDK\*'
)) {
	Get-ItemProperty -Path $key | ForEach-Object { $_.JavaHome }
}

$found = @()
foreach ($path in @($dirs.FullName) + @($regHomes)) {
	if (-not $path) { continue }
	if (-not (Test-Path (Join-Path $path $needle))) { continue }

	# the release file carries the version, which beats launching javac for every hit
	$ver = $null
	$rel = Join-Path $path 'release'
	if (Test-Path $rel) {
		$line = Select-String -LiteralPath $rel -Pattern '^JAVA_VERSION="?([^"]+)"?' | Select-Object -First 1
		if ($line) { $ver = $line.Matches[0].Groups[1].Value }
	}
	if (-not $ver) {
		# no release file, fall back to the folder name (jdk-17.0.9, jdk1.8.0_402)
		if ($path -match 'jdk-?(\d+(\.\d+)*)') { $ver = $Matches[1] }
	}
	if (-not $ver) { continue }

	$major = ($ver -split '[._]')[0]
	if ($major -eq '1') { $major = ($ver -split '[._]')[1] }   # 1.8.0_402 means 8
	$m = 0
	[int]::TryParse($major, [ref]$m) | Out-Null
	if ($m -ge 11) {
		$found += [pscustomobject]@{ Path = $path; Major = $m; Version = $ver }
	}
}

if (-not $found) { exit 1 }

# the same JDK turns up more than once: registry and folder scan both find it, and
# the registry spells it with a trailing slash
$found = $found |
	Group-Object { $_.Path.TrimEnd('\').ToLowerInvariant() } |
	ForEach-Object { $_.Group[0] }

# NOT newest-wins. gradle 8.10 refuses to run on anything past Java 22, and the
# plugin only needs 11, so take the oldest usable one: it is the best tested and
# cannot be too new. only reach outside that window if there is nothing in it.
$usable = $found | Where-Object { $_.Major -le 22 }
if (-not $usable) { $usable = $found }
$best = $usable | Sort-Object -Property Major | Select-Object -First 1

Write-Output $best.Path.TrimEnd('\')
exit 0
