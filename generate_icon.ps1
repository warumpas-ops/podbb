Add-Type -AssemblyName System.Drawing

$size = 512
$bmp = New-Object System.Drawing.Bitmap($size, $size)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

# Clear with transparent
$g.Clear([System.Drawing.Color]::Transparent)

# Outer squircle path
$radius = 110.0
$w = [float]$size
$h = [float]$size
$path = New-Object System.Drawing.Drawing2D.GraphicsPath
$path.AddArc([float]0, [float]0, [float]($radius*2), [float]($radius*2), [float]180, [float]90)
$path.AddArc([float]($w - $radius*2), [float]0, [float]($radius*2), [float]($radius*2), [float]270, [float]90)
$path.AddArc([float]($w - $radius*2), [float]($h - $radius*2), [float]($radius*2), [float]($radius*2), [float]0, [float]90)
$path.AddArc([float]0, [float]($h - $radius*2), [float]($radius*2), [float]($radius*2), [float]90, [float]90)
$path.CloseFigure()

# Background gradient: Cosmos/Orbit style deep dark #0F0F12 to #16161C
$bgBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.PointF(0, 0)),
    (New-Object System.Drawing.PointF(0, $size)),
    [System.Drawing.Color]::FromArgb(255, 15, 15, 18),
    [System.Drawing.Color]::FromArgb(255, 22, 22, 28)
)
$g.FillPath($bgBrush, $path)

# Subtle outer border
$borderPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 45, 45, 55), 3.0)
$g.DrawPath($borderPen, $path)

# Concentric orbit / radio rings (Orbit & Cosmos style)
$cx = 256.0
$cy = 240.0

# Outer glowing ring 1 (Cyan #00A8DF / #007EA7)
$ringPen1 = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(70, 0, 168, 223), 4.0)
$g.DrawEllipse($ringPen1, [float]($cx - 150), [float]($cy - 150), [float]300, [float]300)

# Ring 2 (Active orbital wave arc)
$ringPen2 = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(180, 0, 168, 223), 6.0)
$ringPen2.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$ringPen2.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$g.DrawArc($ringPen2, [float]($cx - 110), [float]($cy - 110), [float]220, [float]220, [float]135, [float]270)

# Ring 3 (Inner orbit)
$ringPen3 = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(230, 240, 240, 245), 5.0)
$ringPen3.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$ringPen3.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$g.DrawArc($ringPen3, [float]($cx - 70), [float]($cy - 70), [float]140, [float]140, [float]160, [float]220)

# Center celestial podcast transmitter node (filled circle with cyan halo)
$haloBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(100, 0, 168, 223))
$g.FillEllipse($haloBrush, [float]($cx - 42), [float]($cy - 42), [float]84, [float]84)

$coreBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 245, 245, 250))
$g.FillEllipse($coreBrush, [float]($cx - 28), [float]($cy - 28), [float]56, [float]56)

$innerNodeBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 0, 168, 223))
$g.FillEllipse($innerNodeBrush, [float]($cx - 14), [float]($cy - 14), [float]28, [float]28)

# Vertical signal pulse / antenna stem downwards
$stemPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 0, 168, 223), 6.0)
$stemPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$stemPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$g.DrawLine($stemPen, [float]$cx, [float]($cy + 30), [float]$cx, [float]($cy + 130))

# Horizontal base platform for the antenna
$basePen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(200, 240, 240, 245), 5.0)
$basePen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$basePen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$g.DrawLine($basePen, [float]($cx - 45), [float]($cy + 130), [float]($cx + 45), [float]($cy + 130))

$g.Dispose()

# Save master
$master = "C:\Users\Growth\.gemini\antigravity\scratch\podbb\icon_master.png"
$bmp.Save($master, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host "New master logo saved to $master"

# Copy to brain artifact
$artIcon = "C:\Users\Growth\.gemini\antigravity\brain\5921e6f2-9781-4865-8ce5-7ca3ea177b9b\podbb_icon_cosmos.png"
$bmp.Save($artIcon, [System.Drawing.Imaging.ImageFormat]::Png)

# Rescale and save into mipmap and drawable folders
$densities = @{
    "mdpi" = 48
    "hdpi" = 72
    "xhdpi" = 96
    "xxhdpi" = 144
    "xxxhdpi" = 192
}

foreach ($density in $densities.GetEnumerator()) {
    $s = $density.Value
    $resized = New-Object System.Drawing.Bitmap($s, $s)
    $rg = [System.Drawing.Graphics]::FromImage($resized)
    $rg.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $rg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $rg.DrawImage($bmp, 0, 0, $s, $s)
    $rg.Dispose()

    # Mipmap
    $outPath = "C:\Users\Growth\.gemini\antigravity\scratch\podbb\app\src\main\res\mipmap-$($density.Key)\ic_launcher.png"
    $resized.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    
    # Also save to drawable
    $dPath = "C:\Users\Growth\.gemini\antigravity\scratch\podbb\app\src\main\res\drawable\ic_launcher.png"
    if ($s -eq 144) {
        $resized.Save($dPath, [System.Drawing.Imaging.ImageFormat]::Png)
    }
    
    $resized.Dispose()
    Write-Host "Saved $outPath ($s x $s)"
}

$bmp.Dispose()
Write-Host "All Cosmos/Orbit style icons successfully generated!"
