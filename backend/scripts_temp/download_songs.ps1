# Download CC-licensed vocal songs from ccmixter into local song storage
# All are real artists' full songs, mp3 format, UUID renamed to match file service convention

$ErrorActionPreference = 'Continue'
# relative path avoids .ps1 non-BOM UTF8/GBK mojibake on CN Windows
$destDir = Join-Path (Get-Location) "yuexiang_backed\data\file\song"
Write-Output "Dest: $destDir"
if (-not (Test-Path $destDir)) { throw "dest dir not found: $destDir" }

$downloads = @()
$downloads += @{ id = 66442; file = 'a1f3c2d84e9b47c6b8d2051e3f7a9042.mp3' }   # Inner Light - Wiseman
$downloads += @{ id = 66421; file = 'b2e5d9f16a3c48e79f04c5d2b6a81f33.mp3' }   # Reves et Perspectives - Bocrew
$downloads += @{ id = 66339; file = 'c3d7a1e25b8f49c68e15d3a7c9b02e44.mp3' }   # Pastore Amore - Duo Arrogante
$downloads += @{ id = 66248; file = 'd4a8b2f36c9d40d78f26e4b8d0c13a55.mp3' }   # Grow - Poor Legacy
$downloads += @{ id = 66151; file = 'e5b9c3a47d0e41e69a37f5c9e1d24b66.mp3' }   # Goodbar Good night - Thespinwires
$downloads += @{ id = 66097; file = 'f6c0d4b58e1f42970a48c6d0f2e35c77.mp3' }   # The Ladder - 7OOP3D
$downloads += @{ id = 65803; file = '17d1e5c69f2a43c08b59d7e1c3f46d88.mp3' }   # As the Solstice Draws Near - Kara Square
$downloads += @{ id = 65790; file = '28e2f6d70c3b54d19c60e8f2d4a57e99.mp3' }   # Save The World With The Bicycle - Zep Hurme
$downloads += @{ id = 65705; file = '39f3c7e81d4c65e20d71f9c3e5b68fa0.mp3' }   # Trancemutate - Rataxes
$downloads += @{ id = 65589; file = '40c4d8f92e5d76f31e82c0d4f6c79eb11.mp3' }  # Into the Night - Thespinwires
$downloads += @{ id = 65452; file = '51d5e9c03f6e87a42f93d1e5c7d80af22.mp3' }  # Wake Up - Dominique Cypres
$downloads += @{ id = 65425; file = '62e6f0d14c7f98b53c04e2f6d8e91bc33.mp3' }  # Waiting for You - Rewob
$downloads += @{ id = 65230; file = '73f7c1e25d8c09a64d15f3c7e9f02bd44.mp3' }  # Something Magical Is Happening - septahelix
$downloads += @{ id = 65229; file = '84c8d2f36e9d10b75e26c4d8f0c13ce55.mp3' }  # Dream Of A Healed World - septahelix

$ok = 0; $fail = @()
$headers = @{
    'User-Agent' = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36'
    'Referer'    = 'https://ccmixter.org/'
}
foreach ($d in $downloads) {
    $dest = Join-Path $destDir $d.file
    if ((Test-Path $dest) -and ((Get-Item $dest).Length -gt 100000)) {
        Write-Output "SKIP (exists): $($d.file)"
        $ok++
        continue
    }
    try {
        $meta = Invoke-RestMethod -Uri "https://ccmixter.org/api/query?f=json&ids=$($d.id)" -TimeoutSec 30 -Headers $headers
        $mp3file = $meta.files | Where-Object { $_.file_name -match '\.mp3$' } | Select-Object -First 1
        if (-not $mp3file) {
            Write-Output "FAIL no-mp3: $($d.id)"
            $fail += $d.id
            continue
        }
        $url = $mp3file.download_url
        Invoke-WebRequest -Uri $url -OutFile $dest -Headers $headers -TimeoutSec 180 -UseBasicParsing
        $size = (Get-Item $dest).Length
        if ($size -gt 100000) {
            Write-Output "OK: $($d.file) ($size bytes)"
            $ok++
        } else {
            Write-Output "FAIL small: $($d.id) ($size bytes)"
            $fail += $d.id
            Remove-Item $dest -Force
        }
    } catch {
        Write-Output "FAIL: $($d.id) - $($_.Exception.Message)"
        $fail += $d.id
        if (Test-Path $dest) { Remove-Item $dest -Force }
    }
}
Write-Output "=== Downloaded OK: $ok / $($downloads.Count) ==="
if ($fail.Count -gt 0) { Write-Output ("Failed ids: " + ($fail -join ', ')) }
