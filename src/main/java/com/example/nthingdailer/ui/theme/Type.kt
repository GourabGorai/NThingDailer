package com.example.nthingdailer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.example.nthingdailer.R

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val DotGothic16 = FontFamily(
    Font(googleFont = GoogleFont("DotGothic16"), fontProvider = provider)
)

val Inter = FontFamily(
    Font(googleFont = GoogleFont("Inter"), fontProvider = provider)
)

val SpaceMono = FontFamily(
    Font(googleFont = GoogleFont("Space Mono"), fontProvider = provider)
)

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = DotGothic16,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = 0.05.sp
    ),
    titleLarge = TextStyle(
        fontFamily = DotGothic16,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        letterSpacing = 0.05.sp
    ),
    labelSmall = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
