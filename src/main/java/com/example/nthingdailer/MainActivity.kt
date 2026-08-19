package com.example.nthingdailer

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nthingdailer.model.DialerViewModel
import com.example.nthingdailer.ui.screens.FrontDialerScreen
import com.example.nthingdailer.ui.screens.PermissionRationaleScreen
import com.example.nthingdailer.ui.screens.RearGlyphScreen
import com.example.nthingdailer.ui.theme.*
import android.content.IntentFilter
import android.telephony.TelephonyManager
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val callStateReceiver = CallStateReceiver()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions updated, UI will recompose via check
    }

    private val requestRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // Role updated, UI will recompose via check
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val filter = IntentFilter().apply {
            addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            addAction(Intent.ACTION_NEW_OUTGOING_CALL)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(callStateReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(callStateReceiver, filter)
        }

        setContent {
            NthingDailerTheme {
                val context = LocalContext.current
                val prefs = remember { context.getSharedPreferences("nthing_prefs", MODE_PRIVATE) }
                
                val hasContactPermission = remember {
                    mutableStateOf(checkPermission(Manifest.permission.READ_CONTACTS))
                }
                val hasOverlayPermission = remember {
                    mutableStateOf(android.provider.Settings.canDrawOverlays(context))
                }
                val isDefaultDialer = remember {
                    mutableStateOf(isRoleHeld())
                }
                val userProceededWithoutRole = remember { 
                    mutableStateOf(prefs.getBoolean("skipped_role", false)) 
                }

                // Periodic check for permissions/role
                LaunchedEffect(Unit) {
                    while(true) {
                        hasContactPermission.value = checkPermission(Manifest.permission.READ_CONTACTS)
                        hasOverlayPermission.value = android.provider.Settings.canDrawOverlays(context)
                        isDefaultDialer.value = isRoleHeld()
                        delay(1000)
                    }
                }

                if (!hasContactPermission.value || !hasOverlayPermission.value || (!isDefaultDialer.value && !userProceededWithoutRole.value)) {
                    PermissionRationaleScreen(
                        isDefaultDialer = isDefaultDialer.value,
                        hasContactPermission = hasContactPermission.value,
                        hasOverlayPermission = hasOverlayPermission.value,
                        onRequestRole = { requestDefaultDialerRole() },
                        onRequestPermissions = { requestPermissions() },
                        onRequestOverlay = { 
                            val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                            startActivity(intent)
                        },
                        onContinue = { 
                            prefs.edit { putBoolean("skipped_role", true) }
                            userProceededWithoutRole.value = true 
                        }
                    )
                } else {
                    // Show emergency warning if overlay is somehow lost for active users
                    if (!hasOverlayPermission.value) {
                        AlertDialog(
                            onDismissRequest = { },
                            title = { Text("PERMISSION REQUIRED", style = NothingDotTextStyle) },
                            text = { Text("The 'Display over other apps' permission is required for the call popup feature. Please enable it in settings.", style = NothingMonoTextStyle) },
                            confirmButton = {
                                Button(onClick = {
                                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                                    startActivity(intent)
                                }) {
                                    Text("OPEN SETTINGS")
                                }
                            },
                            containerColor = NothingSurface,
                            titleContentColor = NothingRed,
                            textContentColor = Color.White
                        )
                    }

                    MainDialerApp(
                        onStartRealCall = { number ->
                            makeRealPhoneCall(number)
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(callStateReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }

    private fun checkPermission(permission: String): Boolean {
        return try {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    private fun isRoleHeld(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = getSystemService(RoleManager::class.java)
                roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) ?: false
            } catch (e: Exception) {
                false
            }
        } else {
            true
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.PROCESS_OUTGOING_CALLS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun requestDefaultDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = getSystemService(RoleManager::class.java)
                val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                if (intent != null) {
                    requestRoleLauncher.launch(intent)
                }
            } catch (e: Exception) {
                // Ignore or log
            }
        }
    }

    private fun makeRealPhoneCall(number: String) {
        if (checkPermission(Manifest.permission.CALL_PHONE)) {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$number")
            }
            startActivity(intent)
        } else {
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
                        text = "NThing DIALER",
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
