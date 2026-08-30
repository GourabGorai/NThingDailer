package com.example.nthingdailer.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nthingdailer.R
import com.example.nthingdailer.ui.theme.*

@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NothingBlack)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NothingSurface)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NothingRed)
                        )
                        Text(
                            text = "ABOUT NTHING",
                            style = NothingDotTextStyle,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                    }
                    Text(
                        text = "CONCEPT v3.0 • FEATURES & PROFILE",
                        style = NothingMonoTextStyle,
                        color = NothingLightGray,
                        fontSize = 9.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(NothingRed.copy(alpha = 0.15f))
                    .border(1.dp, NothingRed.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "ACTIVE",
                    style = NothingMonoTextStyle,
                    color = NothingRed,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Concept Header Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(NothingCardBg)
                .border(1.dp, NothingBorderGlass, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(NothingRed)
                    )
                    Text(
                        text = "NTHING DIALER",
                        style = NothingDotTextStyle,
                        color = Color.White,
                        fontSize = 20.sp
                    )
                    Text(
                        text = "v3.0",
                        style = NothingMonoTextStyle,
                        color = NothingLightGray,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "A concept dialer crafted specifically for the Nothing OS ecosystem, integrating Nothing's distinct retro-futuristic dot-matrix visual identity, universal floating call overlays, smart T9 search, and granular call controls.",
                    style = NothingMonoTextStyle,
                    color = NothingOffWhite.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section Title: Features
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(NothingRed)
            )
            Text(
                text = "APPLICATION FEATURES",
                style = NothingDotTextStyle,
                color = Color.White,
                fontSize = 14.sp
            )
        }

        // Features List
        FeatureItem(
            icon = Icons.Default.Layers,
            title = "UNIVERSAL CALL OVERLAY",
            description = "Movable, draggable floating call card that appears system-wide over any app. Answer, reject, toggle mute/speaker, or check caller ID without app switching."
        )

        Spacer(modifier = Modifier.height(10.dp))

        FeatureItem(
            icon = Icons.Default.Dialpad,
            title = "SMART T9 & AUDIO SYNTH",
            description = "Real-time predictive contact search matching names while entering phone numbers, accompanied by synthesized DTMF audio feedback."
        )

        Spacer(modifier = Modifier.height(10.dp))

        FeatureItem(
            icon = Icons.Default.History,
            title = "RECENTS & CALL LOGS",
            description = "Comprehensive call history filtering (All vs. Missed), granular call duration stats, quick one-tap callback, and contact blocking actions."
        )

        Spacer(modifier = Modifier.height(10.dp))

        FeatureItem(
            icon = Icons.Default.Contacts,
            title = "CONTACTS & SPEED DIAL",
            description = "Full phonebook synchronization with starred favorites, quick contact management, detailed logs, and direct communication shortcuts."
        )

        Spacer(modifier = Modifier.height(10.dp))

        FeatureItem(
            icon = Icons.Default.Mic,
            title = "CALL RECORDING PLAYER",
            description = "Integrated audio player with interactive scrubber, timestamp tracking, and seamless export to the Android system share sheet."
        )

        Spacer(modifier = Modifier.height(10.dp))

        FeatureItem(
            icon = Icons.Default.Smartphone,
            title = "NOTHING OS DESIGN LANGUAGE",
            description = "Authentic NDot matrix typography, monospace data fields, translucent glassmorphism surfaces, and iconic Nothing Red accents."
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Section Title: Developer Details
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(NothingRed)
            )
            Text(
                text = "DEVELOPER PROFILE",
                style = NothingDotTextStyle,
                color = Color.White,
                fontSize = 14.sp
            )
        }

        // Developer Profile Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(NothingCardBg)
                .border(1.dp, NothingBorderGlass, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column {
                // Developer Photo with Nothing Frame
                DeveloperPhotoDisplay()

                Spacer(modifier = Modifier.height(16.dp))

                // Developer Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "GOURAB GORAI",
                            style = NothingDotTextStyle,
                            color = Color.White,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Software Engineer & ML Developer",
                            style = NothingMonoTextStyle,
                            color = NothingRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "INDIA",
                            style = NothingMonoTextStyle,
                            color = NothingLightGray,
                            fontSize = 9.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bio
                Text(
                    text = "Driven computer science professional with expertise in Android, Python, and Machine Learning. Passionate about retro-minimalist design and building intelligent, human-centric software experiences.",
                    style = NothingMonoTextStyle,
                    color = NothingOffWhite.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Scholarship Distinction Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NothingButtonGlass)
                        .border(1.dp, NothingRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(NothingRed.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = NothingRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "RELIANCE FOUNDATION SCHOLAR",
                                style = NothingDotTextStyle,
                                color = Color.White,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Selected Scholar • Regional Meet 2026 'Into the AI Verse'",
                                style = NothingMonoTextStyle,
                                color = NothingLightGray,
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Education Section
                Text(
                    text = "ACADEMIC BACKGROUND",
                    style = NothingDotTextStyle,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                InfoRow(
                    label = "MCA (2026 - Present)",
                    value = "Maulana Abul Kalam Azad University of Technology (MAKAUT)"
                )
                Spacer(modifier = Modifier.height(6.dp))
                InfoRow(
                    label = "BCA (2022 - 2025)",
                    value = "Dr. B.C. Roy Academy of Professional Courses"
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Internships Section
                Text(
                    text = "EXPERIENCE & INTERNSHIPS",
                    style = NothingDotTextStyle,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                InfoRow(
                    label = "TechSaksham (Microsoft & SAP)",
                    value = "AI & Machine Learning Internship (Image Classification)"
                )
                Spacer(modifier = Modifier.height(6.dp))
                InfoRow(
                    label = "Infosys SpringBoard",
                    value = "Python Full-Stack Virtual Internship"
                )
                Spacer(modifier = Modifier.height(6.dp))
                InfoRow(
                    label = "IBM SkillsBuild & Edunet",
                    value = "Advanced Machine Learning & AI Internship"
                )
                Spacer(modifier = Modifier.height(6.dp))
                InfoRow(
                    label = "Deloitte Australia & Forage",
                    value = "Technology Job Simulation"
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Connect / Links Buttons
                Text(
                    text = "CONNECT & PORTFOLIO",
                    style = NothingDotTextStyle,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PortfolioLinkButton(
                        text = "PORTFOLIO",
                        modifier = Modifier.weight(1f),
                        onClick = { openUrl("https://top-noreen-1nothing1-342d4c0b.koyeb.app/") }
                    )
                    PortfolioLinkButton(
                        text = "LINKEDIN",
                        modifier = Modifier.weight(1f),
                        onClick = { openUrl("https://www.linkedin.com/in/gourab-gorai-4a51541ba") }
                    )
                    PortfolioLinkButton(
                        text = "GITHUB",
                        modifier = Modifier.weight(1f),
                        onClick = { openUrl("https://github.com/GourabGorai") }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // System Specs / App Details
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NothingButtonGlass)
                .border(1.dp, NothingBorderGlass, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "SYSTEM SPECIFICATIONS",
                    style = NothingDotTextStyle,
                    color = NothingLightGray,
                    fontSize = 11.sp
                )
                Text(
                    text = "ARCHITECTURE: Jetpack Compose • Kotlin Coroutines • Telecom API",
                    style = NothingMonoTextStyle,
                    color = Color.Gray,
                    fontSize = 9.sp
                )
                Text(
                    text = "TARGET: Android 15+ (API 36) • Minimum: Android 7.0 (API 24)",
                    style = NothingMonoTextStyle,
                    color = Color.Gray,
                    fontSize = 9.sp
                )
                Text(
                    text = "DESIGN SYSTEM: Nothing OS Retro-Futuristic Dot-Matrix v3.0",
                    style = NothingMonoTextStyle,
                    color = Color.Gray,
                    fontSize = 9.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun DeveloperPhotoDisplay() {
    val context = LocalContext.current
    val imageBitmap = rememberDeveloperBitmap(context)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Gourab Gorai - Developer",
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                contentScale = ContentScale.FillWidth
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.developer_photo),
                contentDescription = "Gourab Gorai - Developer",
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                contentScale = ContentScale.FillWidth
            )
        }
    }
}

@Composable
fun rememberDeveloperBitmap(context: Context): ImageBitmap? {
    return remember {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeResource(context.resources, R.drawable.developer_photo, options)

            var sampleSize = 1
            while (options.outWidth / (sampleSize * 2) >= 600 && options.outHeight / (sampleSize * 2) >= 600) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.developer_photo, decodeOptions)
            bitmap?.asImageBitmap()
        } catch (e: Throwable) {
            null
        }
    }
}

@Composable
fun FeatureItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NothingButtonGlass)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(NothingSurface)
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NothingRed,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = NothingDotTextStyle,
                color = Color.White,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                style = NothingMonoTextStyle,
                color = NothingLightGray,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NothingButtonGlass)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = NothingMonoTextStyle,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = NothingMonoTextStyle,
            color = NothingLightGray,
            fontSize = 9.sp
        )
    }
}

@Composable
fun PortfolioLinkButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(NothingSurface)
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = text,
                style = NothingMonoTextStyle,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = NothingRed,
                modifier = Modifier.size(11.dp)
            )
        }
    }
}
