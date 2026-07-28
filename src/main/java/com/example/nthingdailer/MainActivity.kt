package com.example.nthingdailer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nthingdailer.audio.AudioSynthHelper
import com.example.nthingdailer.model.DialerViewModel
import com.example.nthingdailer.ui.screens.FrontDialerScreen
import com.example.nthingdailer.ui.screens.RearGlyphScreen
import com.example.nthingdailer.ui.theme.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permission result handled gracefully
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request telephony permissions on launch
        val permissionsToRequest = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        }

        setContent {
            NthingDailerTheme {
                MainDialerApp(
                    onStartRealCall = { number ->
                        makeRealPhoneCall(number)
                    }
                )
            }
        }
    }

    private fun makeRealPhoneCall(number: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$number")
            }
            startActivity(intent)
        } else {
            // Fallback to dialer if CALL_PHONE permission is not granted
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$number")
            }
            startActivity(intent)
        }
    }
}

@Composable
fun MainDialerApp(onStartRealCall: (String) -> Unit) {
    val viewModel: DialerViewModel = viewModel()
    var isShowingRearGlyph by remember { mutableStateOf(false) }
    var isSoundEnabled by remember { mutableStateOf(AudioSynthHelper.isSoundEnabled) }
    var isGlyphPulsing by remember { mutableStateOf(false) }

    LaunchedEffect(isGlyphPulsing) {
        if (isGlyphPulsing) {
            delay(400)
            isGlyphPulsing = false
        }
    }

    fun triggerGlyphPulse() {
        isGlyphPulsing = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NothingBlack)
            .statusBarsPadding()
    ) {
        // Concept Branding Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                        text = "NOTHING (R) DIALER",
                        style = NothingDotTextStyle,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "v3.0 CONCEPT",
                            style = NothingMonoTextStyle,
                            color = NothingLightGray,
                            fontSize = 8.sp
                        )
                    }
                }
                Text(
                    text = "Sleek Glyph-Integrated Dialer for Nothing OS Ecosystem",
                    style = NothingMonoTextStyle,
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Flip Button
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        .clickable { isShowingRearGlyph = !isShowingRearGlyph }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.RotateRight,
                        contentDescription = "Flip",
                        tint = NothingRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isShowingRearGlyph) "FRONT DIALER" else "REAR GLYPH",
                        style = NothingMonoTextStyle,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Sound Toggle Button
                IconButton(
                    onClick = {
                        isSoundEnabled = !isSoundEnabled
                        AudioSynthHelper.isSoundEnabled = isSoundEnabled
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isSoundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = "Sound",
                        tint = if (isSoundEnabled) NothingRed else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

        // Main Screen Frame with Crossfade Flip
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(36.dp))
                .border(2.dp, Color(0xFF222226), RoundedCornerShape(36.dp))
                .background(Color.Black)
        ) {
            AnimatedContent(
                targetState = isShowingRearGlyph,
                transitionSpec = {
                    fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                },
                label = "phoneFlip"
            ) { showingRear ->
                if (showingRear) {
                    RearGlyphScreen(
                        isGlyphActive = isGlyphPulsing,
                        onFlipToFront = { isShowingRearGlyph = false }
                    )
                } else {
                    FrontDialerScreen(
                        viewModel = viewModel,
                        onFlipToRear = { isShowingRearGlyph = true },
                        onTriggerGlyphPulse = { triggerGlyphPulse() },
                        onStartRealCall = onStartRealCall
                    )
                }
            }
        }
    }
}