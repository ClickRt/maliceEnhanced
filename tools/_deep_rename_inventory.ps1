$ErrorActionPreference = 'Stop'
Set-Location 'F:\admx\newFile\Extend_enchantment'

Write-Host '=== backups present ==='
Get-ChildItem -Directory -Filter '_pre_deep_rename_backup_*' | ForEach-Object { Write-Host $_.Name }

Write-Host ''
Write-Host '=== extended_enchantments per-file counts (src/main text files) ==='
$files = Get-ChildItem -Recurse -File -Path 'src\main' -Include *.java,*.json,*.txt,*.py,*.mcmeta
foreach ($f in $files) {
    $m = (Select-String -Path $f.FullName -Pattern 'extended_enchantments' -AllMatches)
    $cnt = 0
    foreach ($line in $m) { $cnt += $line.Matches.Count }
    if ($cnt -gt 0) { Write-Host ("{0,3}  {1}" -f $cnt, $f.FullName) }
}

Write-Host ''
Write-Host '=== build.gradle / gradle.properties literals ==='
Select-String -Path 'build.gradle','gradle.properties' -Pattern 'extended_enchantments|mod_id=' | ForEach-Object {
    Write-Host ("{0}:{1}: {2}" -f $_.Filename, $_.LineNumber, $_.Line.Trim())
}

Write-Host ''
Write-Host '=== resource dirs named extended_enchantments ==='
Get-ChildItem -Recurse -Directory -Path 'src\main\resources' -Filter 'extended_enchantments' | ForEach-Object { Write-Host $_.FullName }
Get-ChildItem -Recurse -File -Path 'src\main\resources' -Filter 'extended_enchantments.mixins.json' | ForEach-Object { Write-Host $_.FullName }
