package dev.andikune.iottools.ui.theme

import androidx.compose.ui.graphics.Color

// Modern Studio Slate Palette - Nyaman di mata & Elegan
val SlateCanvas = Color(0xFF090D16)      // Canvas utama gelap mewah
val SlateElevated = Color(0xFF101726)    // Card background
val SlateSurface = Color(0xFF172033)     // Inner surface / Input field
val SlateSurfaceHover = Color(0xFF1E2B45)
val SlateBorder = Color(0xFF263550)      // Border halus profesional
val SlateBorderSubtle = Color(0xFF1C273C)

// Modern Accent Colors (Indigo, Emerald, Cyan, Amber, Rose)
val PrimaryIndigo = Color(0xFF6366F1)       // Indigo 500 (Vibrant, modern, clean)
val PrimaryIndigoLight = Color(0xFF818CF8)  // Indigo 400
val PrimaryIndigoSoft = Color(0x266366F1)   // Soft indigo pill background

val AccentEmerald = Color(0xFF10B981)       // Emerald 500 (Status aktif, sukses)
val AccentEmeraldSoft = Color(0x2610B981)

val AccentCyan = Color(0xFF38BDF8)          // Sky 400 (Informasi, link, chip)
val AccentCyanSoft = Color(0x2638BDF8)

val AccentRose = Color(0xFFF43F5E)          // Rose 500 (Bukan merah neon norak, tapi soft rose modern)
val AccentRoseSoft = Color(0x26F43F5E)

val AccentAmber = Color(0xFFF59E0B)         // Amber 500
val AccentAmberSoft = Color(0x26F59E0B)

val AccentPurple = Color(0xFFA855F7)        // Purple 500

// Modern Text Hierarchy
val TextPrimary = Color(0xFFF8FAFC)         // Slate 50 (Putih bersih lembut)
val TextSecondary = Color(0xFF94A3B8)       // Slate 400 (Abu-abu terbaca jelas)
val TextMuted = Color(0xFF64748B)           // Slate 500 (Subtle)
val TextDark = Color(0xFF090D16)

// Theme compatibility aliases mapping to modern comfortable palette
val CyberBlack = SlateCanvas
val CyberDark = SlateSurface
val CyberCard = SlateElevated
val CyberCardBorder = SlateBorder
val NeonRed = PrimaryIndigo
val NeonRedGlow = PrimaryIndigoLight
val NeonCyan = AccentCyan
val MatrixGreen = AccentEmerald
val NeonYellow = AccentAmber
val NeonPurple = AccentPurple
val TerminalText = TextPrimary
val TerminalDim = TextSecondary
