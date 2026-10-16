Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead('F:\Minecraft\.minecraft\versions\1.20.1-Forge_47.4.10\mods\dog-1.2.0.jar')
$zip.Entries | ForEach-Object { $_.FullName } | Where-Object { $_ -notmatch '\.class$' } | Sort-Object
$zip.Dispose()
