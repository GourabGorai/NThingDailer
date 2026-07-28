package com.example.nthingdailer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.example.nthingdailer.R

val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val DotGothicFont = GoogleFont("DotGothic16")
val SpaceMonoFont = GoogleFont("Space Mono")

val DotGothicFontFamily = FontFamily(
    Font(googleFont = DotGothicFont, fontProvider = fontProvider)
)

val SpaceMonoFontFamily = FontFamily(
    Font(googleFont = SpaceMonoFont, fontProvider = fontProvider)
)

val NothingDotTextStyle = TextStyle(
    fontFamily = DotGothicFontFamily,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.2.sp
)

val NothingMonoTextStyle = TextStyle(
    fontFamily = SpaceMonoFontFamily,
    fontWeight = FontWeight.Normal
)

private val DarkColorScheme = darkColorScheme(
    primary = NothingRed,
    secondary = NothingLightGray,
    background = NothingBlack,
    surface = NothingSurface,
    onPrimary = Color.White,
    onBackground = NothingOffWhite,
    onSurface = NothingOffWhite
)

@Composable
fun NthingDailerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}