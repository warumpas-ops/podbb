Add-Type -AssemblyName System.Drawing

$size = 512
$bmp = New-Object System.Drawing.Bitmap($size, $size)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

$g.Clear([System.Drawing.Color]::Transparent)

# Outer squircle path (NoteVault style: #141920 background, #25303D border)
$radius = 110.0
$w = [float]$size
$h = [float]$size
$path = New-Object System.Drawing.Drawing2D.GraphicsPath
$path.AddArc([float]0, [float]0, [float]($radius*2), [float]($radius*2), [float]180, [float]90)
$path.AddArc([float]($w - $radius*2), [float]0, [float]($radius*2), [float]($radius*2), [float]270, [float]90)
$path.AddArc([float]($w - $radius*2), [float]($h - $radius*2), [float]($radius*2), [float]($radius*2), [float]0, [float]90)
$path.AddArc([float]0, [float]($h - $radius*2), [float]($radius*2), [float]($radius*2), [float]90, [float]90)
$path.CloseFigure()

$bgBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 20, 25, 32))
$g.FillPath($bgBrush, $path)

$borderPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 37, 48, 61), 5.0)
$g.DrawPath($borderPen, $path)

# Draw NoteVault / Orbit style podcast emblem
# Radio broadcast waves + sound bars in #00A8DF (BlackBerry blue) & #F5F5F5 (white)
$cx = 256.0
$cy = 240.0

# Broadcast arcs
$wavePen1 = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 0, 168, 223), 10.0)
$wavePen1.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$wavePen1.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$g.DrawArc($wavePen1, [float]($cx - 150), [float]($cy - 120), [float]300, [float]240, [float]200, [float]140)

$wavePen2 = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 245, 245, 245), 9.0)
$wavePen2.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$wavePen2.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$g.DrawArc($wavePen2, [float]($cx - 100), [float]($cy - 80), [float]200, [float]160, [float]205, [float]130)

# Center microphone / transmitter capsule
$micPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 0, 168, 223), 8.0)
$micBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 245, 245, 245))

# Center head
$g.FillEllipse($micBrush, [float]($cx - 36), [float]($cy - 45), [float]72, [float]72)

$centerDot = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 0, 168, 223))
$g.FillEllipse($centerDot, [float]($cx - 18), [float]($cy - 27), [float]36, [float]36)

# Stand stem
$stemPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 245, 245, 245), 8.0)
$stemPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$stemPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$g.DrawLine($stemPen, [float]$cx, [float]($cy + 35), [float]$cx, [float]($cy + 130))

# Stand base
$basePen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 0, 168, 223), 10.0)
$basePen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$basePen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$g.DrawLine($basePen, [float]($cx - 60), [float]($cy + 130), [float]($cx + 60), [float]($cy + 130))

$g.Dispose()

# Save master
$master = "C:\Users\Growth\.gemini\antigravity\scratch\podbb\icon_master.png"
$bmp.Save($master, [System.Drawing.Imaging.ImageFormat]::Png)

$artIcon = "C:\Users\Growth\.gemini\antigravity\brain\5921e6f2-9781-4865-8ce5-7ca3ea177b9b\podbb_icon_notevault.png"
$bmp.Save($artIcon, [System.Drawing.Imaging.ImageFormat]::Png)

# Rescale to mipmaps and drawable
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

    $outPath = "C:\Users\Growth\.gemini\antigravity\scratch\podbb\app\src\main\res\mipmap-$($density.Key)\ic_launcher.png"
    $resized.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    if ($s -eq 144) {
        $resized.Save("C:\Users\Growth\.gemini\antigravity\scratch\podbb\app\src\main\res\drawable\ic_launcher.png", [System.Drawing.Imaging.ImageFormat]::Png)
    }
    $resized.Dispose()
    Write-Host "Saved $outPath ($s x $s)"
}

$bmp.Dispose()
Write-Host "NoteVault style icon generated!"
