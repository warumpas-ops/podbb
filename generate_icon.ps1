Add-Type -AssemblyName System.Drawing

$iconSize = 512

# Pixel art pattern for headphones (32x32 grid mapped to 512x512)
# '.' = background #0D0F11, 'G' = accent green #A8D8A8, 'W' = white #E0E2E6, 'D' = dark gray #1A1C20
$pattern = @(
    "................................",
    "................................",
    "................................",
    "........GGGGGGGGGGGGGGGG........",
    "......GG................GG......",
    ".....G....................G.....",
    ".....G....................G.....",
    "....G......................G....",
    "....G......................G....",
    "....G......................G....",
    "....G......................G....",
    "....G......................G....",
    "....G......................G....",
    "....G......................G....",
    "....G......................G....",
    "....G......................G....",
    "..DDGG.....................GGDD.",
    "..DWWD.....................DWWD.",
    "..DWWD.....................DWWD.",
    "..DWWD.....................DWWD.",
    "..DWWD.....................DWWD.",
    "..DWWD.....................DWWD.",
    "..DDGG.....................GGDD.",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................",
    "................................"
)

$bmp = New-Object System.Drawing.Bitmap($iconSize, $iconSize)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::FromArgb(255, 13, 15, 17))

# Draw rounded rect (squircle)
$radius = 100.0
$w = [float]$iconSize
$h = [float]$iconSize
$path = New-Object System.Drawing.Drawing2D.GraphicsPath
$path.AddArc([float]0, [float]0, [float]($radius*2), [float]($radius*2), [float]180, [float]90)
$path.AddArc([float]($w - $radius*2), [float]0, [float]($radius*2), [float]($radius*2), [float]270, [float]90)
$path.AddArc([float]($w - $radius*2), [float]($h - $radius*2), [float]($radius*2), [float]($radius*2), [float]0, [float]90)
$path.AddArc([float]0, [float]($h - $radius*2), [float]($radius*2), [float]($radius*2), [float]90, [float]90)
$path.CloseFigure()
$bgBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 13, 15, 17))
$g.FillPath($bgBrush, $path)

# Draw pixel art
$cellSize = $iconSize / $pattern[0].Length
for ($row = 0; $row -lt $pattern.Count; $row++) {
    for ($col = 0; $col -lt $pattern[$row].Length; $col++) {
        $ch = $pattern[$row][$col]
        $color = $null
        switch ($ch) {
            'G' { $color = [System.Drawing.Color]::FromArgb(255, 168, 216, 168) }
            'W' { $color = [System.Drawing.Color]::FromArgb(255, 224, 226, 230) }
            'D' { $color = [System.Drawing.Color]::FromArgb(255, 26, 28, 32) }
            default { $color = $null }
        }
        if ($color -ne $null) {
            $brush = New-Object System.Drawing.SolidBrush($color)
            $x = [int]($col * $cellSize)
            $y = [int]($row * $cellSize)
            $size = [int]$cellSize + 1
            $g.FillRectangle($brush, $x, $y, $size, $size)
            $brush.Dispose()
        }
    }
}

$g.Dispose()

# Save master
$master = "C:\Users\Growth\.gemini\antigravity\scratch\podbb\icon_master.png"
$bmp.Save($master, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host "Master icon saved: $master"

# Apply to all mipmap densities
$densities = @{
    "mdpi" = 48
    "hdpi" = 72
    "xhdpi" = 96
    "xxhdpi" = 144
    "xxxhdpi" = 192
}

foreach ($density in $densities.GetEnumerator()) {
    $size = $density.Value
    $resized = New-Object System.Drawing.Bitmap($bmp, [System.Drawing.Size]::new($size, $size))
    $outPath = "C:\Users\Growth\.gemini\antigravity\scratch\podbb\app\src\main\res\mipmap-$($density.Key)\ic_launcher.png"
    $resized.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $resized.Dispose()
    Write-Host "Saved $outPath ($size x $size)"
}

$bmp.Dispose()
Write-Host "All icons generated"
