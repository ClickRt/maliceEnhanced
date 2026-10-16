Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead('F:\Minecraft\.minecraft\versions\1.20.1-Forge_47.4.23SSS\mods\withering-heights-alpha-1.jar')
$classes = @($zip.Entries | Where-Object { $_.FullName -match '\.class$' })
Write-Output ("CLASS_COUNT=" + $classes.Count)
$tops = @{}
foreach ($e in $zip.Entries) {
  $p = ($e.FullName -split '/')[0..1] -join '/'
  $tops[$p] = 1
}
$tops.Keys | Sort-Object
Write-Output '---KEY FILES---'
$zip.Entries | Where-Object { $_.FullName -match 'pack\.mcmeta|mods\.toml|\.lang$|\.json$' -and $_.FullName -match 'lang|mcmeta|mods' } | Select-Object -ExpandProperty FullName | Select-Object -First 20
$zip.Dispose()
