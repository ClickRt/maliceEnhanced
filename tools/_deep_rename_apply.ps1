$ErrorActionPreference = 'Stop'
Set-Location 'F:\admx\newFile\Extend_enchantment'

$OLD = 'extended_enchantments'
$NEW = 'malice_enhanced'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Replace-InFile($path) {
    $text = [System.IO.File]::ReadAllText($path)
    if ($text -notmatch [regex]::Escape($OLD)) { return $false }
    $new = $text.Replace($OLD, $NEW)
    [System.IO.File]::WriteAllText($path, $new, $utf8NoBom)
    return $true
}

# ---- 1) Backup (only if none present) ----
if (-not (Get-ChildItem -Directory -Filter '_pre_deep_rename_backup_*')) {
    $ts = Get-Date -Format 'yyyyMMdd_HHmmss'
    $bk = "_pre_deep_rename_backup_$ts"
    New-Item -ItemType Directory -Path $bk | Out-Null
    Copy-Item -Recurse -Path 'src\main' -Destination (Join-Path $bk 'src_main')
    Copy-Item -Path 'build.gradle' -Destination (Join-Path $bk 'build.gradle')
    Copy-Item -Path 'gradle.properties' -Destination (Join-Path $bk 'gradle.properties')
    Write-Host "BACKUP created -> $bk"
} else {
    Write-Host 'Backup already present, skipping.'
}

# ---- 2) Global text replace across src/main text files ----
$exts = @('*.java','*.json','*.txt','*.py','*.mcmeta','*.toml','*.cfg','*.gradle','*.properties')
$targets = Get-ChildItem -Recurse -File -Path 'src\main' -Include $exts
$changed = 0
foreach ($f in $targets) {
    if (Replace-InFile $f.FullName) { $changed++; Write-Host ("CHANGED {0}" -f $f.FullName) }
}
# root build files
foreach ($rf in @('build.gradle','gradle.properties')) {
    if (Replace-InFile (Join-Path (Get-Location) $rf)) { $changed++; Write-Host ("CHANGED {0}" -f $rf) }
}
Write-Host "total files text-changed: $changed"

# ---- 3) gradle.properties mod_id: shatter -> malice_enhanced ----
$gp = 'gradle.properties'
$g = [System.IO.File]::ReadAllText($gp)
$g2 = $g.Replace('mod_id=shatter', "mod_id=$NEW")
[System.IO.File]::WriteAllText($gp, $g2, $utf8NoBom)
Write-Host "gradle.properties mod_id updated"

# ---- 4) Rename mixin config file ----
$mixOld = 'src\main\resources\extended_enchantments.mixins.json'
$mixNew = "src\main\resources\$NEW.mixins.json"
if (Test-Path $mixOld) { Move-Item -Path $mixOld -Destination $mixNew; Write-Host "renamed mixins config -> $mixNew" }

# ---- 5) Rename resource directories ----
$dirs = @(
    @{ old='src\main\resources\assets\extended_enchantments'; new='src\main\resources\assets\malice_enhanced' },
    @{ old='src\main\resources\data\extended_enchantments';  new='src\main\resources\data\malice_enhanced' }
)
foreach ($d in $dirs) {
    if (Test-Path $d.old) { Rename-Item -Path $d.old -NewName $NEW; Write-Host ("renamed dir {0} -> {1}" -f $d.old, $d.new) }
}

# ---- 6) Report any remaining literal occurrences ----
Write-Host ''
Write-Host '=== remaining extended_enchantments occurrences (should be none) ==='
$remain = Get-ChildItem -Recurse -File -Path 'src\main','build.gradle','gradle.properties' -Include $exts |
    Select-String -Pattern $OLD
if ($remain) { $remain | ForEach-Object { Write-Host ($_.Path + ':' + $_.LineNumber) } } else { Write-Host 'NONE' }

Write-Host ''
Write-Host '=== remaining mod_id=shatter ==='
Select-String -Path 'gradle.properties' -Pattern 'mod_id=shatter' | ForEach-Object { Write-Host $_.Line }
