package com.example.nthingdailer.ui.screens

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.TelecomManager
import android.telecom.Call
import android.telecom.VideoProfile
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nthingdailer.CallStateManager
import com.example.nthingdailer.NothingInCallService
import com.example.nthingdailer.audio.AudioFileGenerator
import com.example.nthingdailer.audio.AudioSynthHelper
import com.example.nthingdailer.audio.RecordingPlayer
import com.example.nthingdailer.model.ContactItem
import com.example.nthingdailer.model.DialerViewModel
import com.example.nthingdailer.model.RecentItem
import com.example.nthingdailer.model.RecordingItem
import com.example.nthingdailer.model.SampleData
import com.example.nthingdailer.ui.components.DotMatrixBackground
import com.example.nthingdailer.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class DialerTab {
    RECENTS, KEYPAD, CONTACTS, RECORDINGS, SETTINGS
}

@Composable
fun FrontDialerScreen(
    viewModel: DialerViewModel = viewModel(),
    initialTab: String? = null,
    onFlipToRear: () -> Unit,
    onTriggerGlyphPulse: () -> Unit,
    onStartRealCall: (String) -> Unit,
    onDismissCallSession: () -> Unit = {}
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(DialerTab.KEYPAD) }
    var isShowingAboutScreen by remember { mutableStateOf(false) }

    LaunchedEffect(initialTab) {
        initialTab?.let {
            currentTab = when (it) {
                "recents" -> DialerTab.RECENTS
                "contacts" -> DialerTab.CONTACTS
                else -> DialerTab.KEYPAD
            }
        }
    }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // Refresh data or UI if needed
        viewModel.refreshData()
    }
    
    val currentDialNumber by viewModel.dialNumber.collectAsState()
    val recentsList by viewModel.recents.collectAsState()
    val contactsList by viewModel.contacts.collectAsState()
    val favoritesList by viewModel.favorites.collectAsState()
    val blockedNumbers by viewModel.blockedNumbers.collectAsState()
    val recordingsList by viewModel.recordings.collectAsState()
    val t9Matches by viewModel.t9Matches.collectAsState()
    
    val triggerAcknowledgement by CallStateManager.triggerAcknowledgement.collectAsState()
    val isGlobalCallActive by CallStateManager.isCallActive.collectAsState()
    val currentCallState by CallStateManager.callState.collectAsState()
    
    val lastCallNameFlow by CallStateManager.lastCallName.collectAsState()
    val lastCallNumberFlow by CallStateManager.lastCallNumber.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current

    var activeFilter by remember { mutableStateOf("all") } // "all" or "missed"
    var contactFilter by remember { mutableStateOf("all") } // "all" or "favorites"
    var contactSearchQuery by remember { mutableStateOf("") }
    
    // History Overlay State
    var historyNumberToShow by remember { mutableStateOf<String?>(null) }
    var historyNameToShow by remember { mutableStateOf<String?>(null) }

    fun isDefaultDialer(): Boolean {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return telecomManager.defaultDialerPackage == context.packageName
    }

    // Auto-refresh data when the app comes to foreground (e.g. after saving a contact)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshData()
    }

    // Active Call State
    var isCallActive by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isKeypadVisible by remember { mutableStateOf(false) }

    var callStatusHeading by remember { mutableStateOf("DIALING...") }
    var callSeconds by remember { mutableIntStateOf(0) }
    var activeCallStartTime by remember { mutableLongStateOf(0L) }
    var activeCallName by remember { mutableStateOf("UNKNOWN") }
    var activeCallNumber by remember { mutableStateOf("") }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(false) }

    fun saveCallRecording(
        ctx: Context,
        vm: DialerViewModel,
        num: String,
        startTime: Long
    ) {
        val cleanNum = if (num.isBlank()) "Unknown" else num
        val callTime = if (startTime > 0) startTime else System.currentTimeMillis()
        val recordingId = "rec_${cleanNum}_${callTime}"
        
        val recordsDir = File(ctx.filesDir, "recordings")
        if (!recordsDir.exists()) recordsDir.mkdirs()
        val recFile = File(recordsDir, "${recordingId}.wav")
        AudioFileGenerator.generateSampleWavFile(recFile)

        val prefs = ctx.getSharedPreferences("nthing_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putString(recordingId, recFile.absolutePath)
            val existing = prefs.getStringSet("all_recordings", mutableSetOf()) ?: mutableSetOf()
            val updated = existing.toMutableSet().apply { add(recordingId) }
            putStringSet("all_recordings", updated)
        }
        vm.refreshData()
    }
    
    // Sync local isCallActive with global state to handle external end-call
    LaunchedEffect(isGlobalCallActive, lastCallNameFlow, lastCallNumberFlow) {
        if (isGlobalCallActive && isDefaultDialer()) {
            activeCallNumber = lastCallNumberFlow ?: ""
            activeCallName = if (lastCallNameFlow.isNullOrBlank() || lastCallNameFlow.equals("Unknown", true)) "" else lastCallNameFlow!!
            activeCallStartTime = CallStateManager.lastCallStartTime.value
            isCallActive = true
        } else if (!isGlobalCallActive) {
            if (isRecording) {
                saveCallRecording(context, viewModel, activeCallNumber, activeCallStartTime)
            }
            isCallActive = false
            isRecording = false
            isKeypadVisible = false
        }
    }

    // Clock
    var currentTime by remember { mutableStateOf("") }
    
    // System Status
    var batteryLevel by remember { mutableIntStateOf(88) }
    var isWifiEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        while (true) {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            currentTime = sdf.format(Date())
            
            // Update Battery
            batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            
            // Update WiFi
            val activeNetwork = connectivityManager.activeNetwork
            val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
            isWifiEnabled = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
            
            delay(5000)
        }
    }

    // Call Timer Effect
    LaunchedEffect(isCallActive, currentCallState) {
        if (isCallActive) {
            when (currentCallState) {
                Call.STATE_ACTIVE -> {
                    callStatusHeading = "CALL IN PROGRESS"
                    // If it just became active, ensure it starts from zero
                    // Note: In a real app, you might want to fetch call duration from system details
                    while (isCallActive && currentCallState == Call.STATE_ACTIVE) {
                        delay(1000)
                        callSeconds++
                    }
                }
                Call.STATE_RINGING -> {
                    callSeconds = 0
                    callStatusHeading = "INCOMING CALL..."
                }
                Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                    callSeconds = 0
                    callStatusHeading = "DIALING..."
                }
                Call.STATE_DISCONNECTED -> {
                    callStatusHeading = "CALL ENDED"
                }
            }
        } else {
            callSeconds = 0
        }
    }

    fun startCall(number: String = "", name: String = "") {
        val numToCall = number.ifEmpty { currentDialNumber.ifEmpty { "+1 (555) 019-2831" } }
        val matched = contactsList.find { it.number == numToCall }
        val nameToCall = name.ifEmpty { matched?.name ?: "UNKNOWN" }

        activeCallNumber = numToCall
        activeCallName = nameToCall
        activeCallStartTime = System.currentTimeMillis()
        
        // ONLY show the internal in-call UI if we are the default dialer
        if (isDefaultDialer()) {
            isCallActive = true
        }

        onStartRealCall(numToCall)
        // Refresh recents after a short delay to show the new call
        viewModel.refreshData()
        
        // Update manager for overlay lookup
        CallStateManager.updateCallState(true, nameToCall, numToCall)

        // EXPLICITLY start the overlay service with name and number for outgoing calls
        val overlayIntent = Intent(context, com.example.nthingdailer.CallOverlayService::class.java).apply {
            putExtra("number", numToCall)
            putExtra("name", nameToCall)
        }
        context.startService(overlayIntent)
    }

    fun endCall() {
        if (isRecording) {
            saveCallRecording(context, viewModel, activeCallNumber, activeCallStartTime)
            isRecording = false
        }
        // Try to disconnect system call if we are the default dialer
        com.example.nthingdailer.NothingInCallService.disconnectCurrentCall()
        
        AudioSynthHelper.playCallEndTone()
        callStatusHeading = "CALL ENDED"
        isCallActive = false
        viewModel.clearDialer()
        CallStateManager.updateCallState(false)
    }

    fun addContact() {
        if (currentDialNumber.isEmpty()) {
            Toast.makeText(context, "Enter a number first!", Toast.LENGTH_SHORT).show()
            return
        }
        // Launch real system "Add Contact" intent
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, currentDialNumber)
        }
        context.startActivity(intent)
    }

    DotMatrixBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                
                // Top Status Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentTime.ifEmpty { "09:41" },
                        style = NothingDotTextStyle,
                        color = Color.White,
                        fontSize = 14.sp
                    )

                    // Glyph Status Pill
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            .clickable { onFlipToRear() }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(NothingRed)
                        )
                        Text(
                            text = "GLYPH READY",
                            style = NothingDotTextStyle,
                            color = NothingOffWhite,
                            fontSize = 10.sp
                        )
                    }

                    // Battery / Signal / Wifi
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SignalCellular4Bar,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        if (isWifiEnabled) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = "$batteryLevel%",
                            style = NothingDotTextStyle,
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                // Tab Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (currentTab) {
                        DialerTab.KEYPAD -> KeypadView(
                            dialNumber = currentDialNumber,
                            contacts = t9Matches,
                            onKeyPress = { key ->
                                if (currentDialNumber.length < 20) {
                                    viewModel.onDialNumberChanged(currentDialNumber + key)
                                    AudioSynthHelper.playKeyTone(key)
                                    onTriggerGlyphPulse()
                                }
                            },
                            onBackspace = {
                                if (currentDialNumber.isNotEmpty()) {
                                    viewModel.onDialNumberChanged(currentDialNumber.dropLast(1))
                                    AudioSynthHelper.playBackspaceTone()
                                }
                            },
                            onClear = { viewModel.clearDialer() },
                            onPaste = { pastedNumber ->
                                viewModel.onDialNumberChanged(pastedNumber)
                                AudioSynthHelper.playKeyTone('0')
                                onTriggerGlyphPulse()
                            },
                            onStartCall = { startCall() },
                            onAddContact = { addContact() }
                        )

                        DialerTab.RECENTS -> RecentsView(
                            recents = recentsList,
                            blockedNumbers = blockedNumbers,
                            activeFilter = activeFilter,
                            onFilterChange = { activeFilter = it },
                            onCallItem = { rec -> startCall(rec.number, rec.name) },
                            onToggleBlock = { viewModel.toggleBlock(it) },
                            onSeeHistory = { num, name -> 
                                historyNumberToShow = num
                                historyNameToShow = name
                            },
                            onRefresh = { viewModel.refreshData() }
                        )

                        DialerTab.CONTACTS -> ContactsView(
                            contacts = contactsList,
                            favorites = favoritesList,
                            blockedNumbers = blockedNumbers,
                            activeFilter = contactFilter,
                            onFilterChange = { contactFilter = it },
                            searchQuery = contactSearchQuery,
                            onSearchQueryChange = { contactSearchQuery = it },
                            onCallItem = { c -> startCall(c.number, c.name) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onToggleBlock = { viewModel.toggleBlock(it) },
                            onSeeHistory = { num, name ->
                                historyNumberToShow = num
                                historyNameToShow = name
                            },
                            onRefresh = { viewModel.refreshData() }
                        )

                        DialerTab.RECORDINGS -> {
                            var playingId by remember { mutableStateOf<String?>(null) }
                            RecordingsView(
                                recordings = recordingsList,
                                playingId = playingId,
                                onPlay = { rec -> 
                                    if (playingId == rec.id) {
                                        RecordingPlayer.stop()
                                        playingId = null
                                    } else {
                                        playingId = rec.id
                                        RecordingPlayer.play(context, rec.id, rec.path) {
                                            playingId = null
                                        }
                                    }
                                },
                                onShare = { rec ->
                                    val file = java.io.File(rec.path)
                                    if (file.exists()) {
                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "audio/mpeg"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share Recording"))
                                    } else {
                                        Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onRefresh = { viewModel.refreshData() }
                            )
                        }

                        DialerTab.SETTINGS -> {
                            if (isShowingAboutScreen) {
                                AboutScreen(
                                    onBack = { isShowingAboutScreen = false }
                                )
                            } else {
                                val hasOverlayPermission = remember { mutableStateOf(Settings.canDrawOverlays(context)) }
                                val isDefault = remember { mutableStateOf(isDefaultDialer()) }
                                val prefs = remember { context.getSharedPreferences("nthing_prefs", Context.MODE_PRIVATE) }
                                val isOverlayEnabled = remember { mutableStateOf(prefs.getBoolean("is_overlay_enabled", true)) }

                                LaunchedEffect(Unit) {
                                    while(true) {
                                        hasOverlayPermission.value = Settings.canDrawOverlays(context)
                                        isDefault.value = isDefaultDialer()
                                        delay(1000)
                                    }
                                }
                                
                                SettingsView(
                                    isDefault = isDefault.value,
                                    hasOverlay = hasOverlayPermission.value,
                                    isOverlayEnabled = isOverlayEnabled.value,
                                    onSetDefault = {
                                        try {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                val roleManager = context.getSystemService(RoleManager::class.java)
                                                if (roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true) {
                                                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                                                    roleLauncher.launch(intent)
                                                } else {
                                                    Toast.makeText(context, "Dialer role not available on this device", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                                                    putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
                                                }
                                                roleLauncher.launch(intent)
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    onRequestOverlay = {
                                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
                                        context.startActivity(intent)
                                    },
                                    onToggleOverlay = { enabled ->
                                        isOverlayEnabled.value = enabled
                                        prefs.edit { putBoolean("is_overlay_enabled", enabled) }
                                    },
                                    onOpenAbout = { isShowingAboutScreen = true }
                                )
                            }
                        }
                    }
                }

                // Bottom Navigation Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(Color.Black.copy(alpha = 0.9f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f)),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // RECENTS
                    NavItem(
                        icon = Icons.Default.History,
                        label = "RECENTS",
                        selected = currentTab == DialerTab.RECENTS,
                        onClick = { 
                            isShowingAboutScreen = false
                            currentTab = DialerTab.RECENTS 
                        }
                    )

                    // KEYPAD
                    NavItem(
                        icon = Icons.Default.Dialpad,
                        label = "KEYPAD",
                        selected = currentTab == DialerTab.KEYPAD,
                        onClick = { 
                            isShowingAboutScreen = false
                            currentTab = DialerTab.KEYPAD 
                        }
                    )

                    // RECORDINGS
                    NavItem(
                        icon = Icons.Default.Mic,
                        label = "RECS",
                        selected = currentTab == DialerTab.RECORDINGS,
                        onClick = { 
                            isShowingAboutScreen = false
                            currentTab = DialerTab.RECORDINGS 
                        }
                    )

                    // CONTACTS
                    NavItem(
                        icon = Icons.Default.Contacts,
                        label = "CONTACTS",
                        selected = currentTab == DialerTab.CONTACTS,
                        onClick = { 
                            isShowingAboutScreen = false
                            currentTab = DialerTab.CONTACTS 
                        }
                    )

                    // SETTINGS
                    NavItem(
                        icon = Icons.Default.Settings,
                        label = "SETTINGS",
                        selected = currentTab == DialerTab.SETTINGS,
                        onClick = { 
                            if (currentTab == DialerTab.SETTINGS && isShowingAboutScreen) {
                                isShowingAboutScreen = false
                            } else {
                                currentTab = DialerTab.SETTINGS 
                            }
                        }
                    )
                }
            }

            // ACTIVE CALL SCREEN OVERLAY
            if (isCallActive) {
                if (currentCallState == Call.STATE_RINGING) {
                    IncomingCallOverlay(
                        name = activeCallName,
                        number = activeCallNumber,
                        onAnswer = { 
                            com.example.nthingdailer.NothingInCallService.answerCurrentCall()
                        },
                        onReject = { 
                            com.example.nthingdailer.NothingInCallService.disconnectCurrentCall()
                        }
                    )
                } else {
                    ActiveCallOverlay(
                        name = activeCallName,
                        number = activeCallNumber,
                        statusHeading = callStatusHeading,
                        callSeconds = callSeconds,
                        isMuted = isMuted,
                        isSpeaker = isSpeaker,
                        isRecording = isRecording,
                        isKeypadVisible = isKeypadVisible,
                        onToggleMute = { 
                            isMuted = !isMuted
                            com.example.nthingdailer.NothingInCallService.setMuted(isMuted)
                        },
                        onToggleSpeaker = { 
                            isSpeaker = !isSpeaker 
                            com.example.nthingdailer.NothingInCallService.setSpeaker(isSpeaker)
                        },
                        onToggleRecording = {
                            if (isRecording) {
                                saveCallRecording(context, viewModel, activeCallNumber, activeCallStartTime)
                                isRecording = false
                                Toast.makeText(context, "Recording saved", Toast.LENGTH_SHORT).show()
                            } else {
                                isRecording = true
                            }
                        },
                        onToggleKeypad = { isKeypadVisible = !isKeypadVisible },
                        onGlyphSync = { onTriggerGlyphPulse() },
                        onEndCall = { endCall() }
                    )
                }
            }

            // HISTORY PAGE OVERLAY
            if (historyNumberToShow != null) {
                CallHistoryOverlay(
                    name = historyNameToShow ?: "UNKNOWN",
                    number = historyNumberToShow!!,
                    recents = recentsList,
                    onDismiss = { historyNumberToShow = null; historyNameToShow = null },
                    onCall = { num -> startCall(num) }
                )
            }

            // CALL ENDED ACKNOWLEDGEMENT OVERLAY
            if (triggerAcknowledgement) {
                AcknowledgementOverlay(
                    name = lastCallNameFlow ?: "UNKNOWN",
                    number = lastCallNumberFlow ?: "",
                    onDismiss = {
                        CallStateManager.clearAcknowledgement()
                        onDismissCallSession()
                    }
                )
            }
        }
    }
}

@Composable
fun CallHistoryOverlay(
    name: String,
    number: String,
    recents: List<RecentItem>,
    onDismiss: () -> Unit,
    onCall: (String) -> Unit
) {
    val context = LocalContext.current
    val filteredHistory = remember(recents, number) {
        val cleanTarget = number.filter { it.isDigit() }
        recents.filter { 
            it.number.filter { c -> c.isDigit() } == cleanTarget ||
            it.number.contains(number) || number.contains(it.number)
        }
    }

    val isUnsaved = remember(name, number) {
        name.isBlank() || name.equals("Unknown", true) || name.equals("Unsaved", true) || name.equals("Unsaved Number", true) || name == number
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (!isUnsaved) name.uppercase() else "HISTORY",
                        style = NothingDotTextStyle,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                    Text(
                        text = number,
                        style = NothingMonoTextStyle,
                        color = NothingLightGray,
                        fontSize = 11.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isUnsaved) {
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_INSERT).apply {
                                    type = ContactsContract.RawContacts.CONTENT_TYPE
                                    putExtra(ContactsContract.Intents.Insert.PHONE, number)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(NothingRed)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Save", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    IconButton(onClick = { onCall(number) }) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.Green)
                    }
                }
            }

            if (filteredHistory.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("NO CALL HISTORY", style = NothingMonoTextStyle, color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredHistory) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(NothingButtonGlass)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(
                                    imageVector = if (item.missed) Icons.Default.CallReceived else if (item.type == "outgoing") Icons.Default.CallMade else Icons.Default.CallReceived,
                                    contentDescription = null,
                                    tint = if (item.missed) NothingRed else if (item.type == "outgoing") Color.Green else Color.Cyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column {
                                    Text(
                                        text = item.type.uppercase(),
                                        style = NothingDotTextStyle,
                                        color = if (item.missed) NothingRed else Color.White,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = item.time,
                                        style = NothingMonoTextStyle,
                                        color = NothingLightGray,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Text(
                                text = item.duration,
                                style = NothingMonoTextStyle,
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp).height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NothingSurface),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text("CLOSE", style = NothingMonoTextStyle, color = Color.White)
            }
        }
    }
}

@Composable
fun AcknowledgementOverlay(
    name: String,
    number: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.98f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(NothingSurface)
                    .border(2.dp, NothingRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = NothingRed,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "CALL COMPLETED",
                style = NothingDotTextStyle,
                color = Color.White,
                fontSize = 24.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (name.isNotBlank() && !name.equals("Unknown", true)) name.uppercase() else number,
                style = NothingMonoTextStyle,
                color = NothingLightGray,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )

            if (name.isNotBlank() && !name.equals("Unknown", true) && number.isNotBlank() && number != name) {
                Text(
                    text = number,
                    style = NothingMonoTextStyle,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Text(
                text = number,
                style = NothingMonoTextStyle,
                color = Color.Gray,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(54.dp)
            ) {
                Text(
                    text = "CLOSE",
                    style = NothingMonoTextStyle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun NavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (selected && label == "KEYPAD") {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = NothingRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color.White else NothingLightGray,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = NothingMonoTextStyle,
            color = if (selected) Color.White else NothingLightGray,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun KeypadView(
    dialNumber: String,
    contacts: List<ContactItem>,
    onKeyPress: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onPaste: (String) -> Unit,
    onStartCall: () -> Unit,
    onAddContact: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showPastePopup by remember { mutableStateOf(false) }

    LaunchedEffect(showPastePopup) {
        if (showPastePopup) {
            delay(4000)
            showPastePopup = false
        }
    }

    fun handlePaste() {
        val clipText = clipboardManager.getText()?.text
            ?: (context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)?.primaryClip?.let { clip ->
                if (clip.itemCount > 0) clip.getItemAt(0)?.coerceToText(context)?.toString() else null
            }

        if (clipText.isNullOrBlank()) {
            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val sanitized = buildString {
            for (c in clipText.trim().removePrefix("tel:")) {
                if (c in "0123456789+*#") {
                    append(c)
                }
            }
        }.take(20)

        if (sanitized.isEmpty()) {
            Toast.makeText(context, "No valid phone number in clipboard", Toast.LENGTH_SHORT).show()
            return
        }

        onPaste(sanitized)
        Toast.makeText(context, "Pasted: $sanitized", Toast.LENGTH_SHORT).show()
    }

    val matchedContact = remember(dialNumber) {
        if (dialNumber.isEmpty()) null
        else contacts.find { it.number.replace("\\D".toRegex(), "").contains(dialNumber.replace("\\D".toRegex(), "")) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "KEYPAD",
                    style = NothingDotTextStyle,
                    color = Color.White,
                    fontSize = 18.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(NothingRed.copy(alpha = 0.15f))
                        .border(1.dp, NothingRed.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "HD VOICE",
                        style = NothingMonoTextStyle,
                        color = NothingRed,
                        fontSize = 9.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                TextButton(
                    onClick = { handlePaste() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            tint = NothingLightGray,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "PASTE",
                            style = NothingMonoTextStyle,
                            color = NothingLightGray,
                            fontSize = 11.sp
                        )
                    }
                }

                TextButton(
                    onClick = {
                        showPastePopup = false
                        onClear()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "CLEAR",
                        style = NothingMonoTextStyle,
                        color = NothingLightGray,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Dialed Number Display Box with Paste interaction
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                showPastePopup = !showPastePopup
                            },
                            onLongPress = {
                                handlePaste()
                                showPastePopup = false
                            }
                        )
                    }
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (dialNumber.isEmpty()) {
                    Text(
                        text = "ENTER NUMBER",
                        style = NothingMonoTextStyle,
                        color = Color.Gray,
                        fontSize = 18.sp
                    )
                } else {
                    val numberFontSize = when {
                        dialNumber.length > 14 -> 20.sp
                        dialNumber.length > 10 -> 24.sp
                        else -> 32.sp
                    }
                    Text(
                        text = dialNumber,
                        style = NothingDotTextStyle,
                        color = Color.White,
                        fontSize = numberFontSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (matchedContact != null) {
                        Text(
                            text = matchedContact.name.uppercase(),
                            style = NothingMonoTextStyle,
                            color = NothingRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Floating Nothing OS-styled Paste Pill
            androidx.compose.animation.AnimatedVisibility(
                visible = showPastePopup,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.clickable {
                        handlePaste()
                        showPastePopup = false
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            tint = NothingRed,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "PASTE",
                            style = NothingMonoTextStyle,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        // 3x4 Keypad Grid
        val keys = listOf(
            Pair('1', ""), Pair('2', "ABC"), Pair('3', "DEF"),
            Pair('4', "GHI"), Pair('5', "JKL"), Pair('6', "MNO"),
            Pair('7', "PQRS"), Pair('8', "TUV"), Pair('9', "WXYZ"),
            Pair('*', ""), Pair('0', "+"), Pair('#', "")
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            keys.chunked(3).forEach { rowKeys ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowKeys.forEach { (charKey, subText) ->
                        KeypadButton(
                            charKey = charKey,
                            subText = subText,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                showPastePopup = false
                                onKeyPress(charKey)
                            }
                        )
                    }
                }
            }
        }

        // Bottom Action Bar (Add Contact, Call, Backspace)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onAddContact,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "Add Contact",
                    tint = Color.White
                )
            }

            // Big White Call Button
            IconButton(
                onClick = onStartCall,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = {
                    showPastePopup = false
                    onBackspace()
                },
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Delete",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun KeypadButton(
    charKey: Char,
    subText: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(62.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(NothingButtonGlass)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = charKey.toString(),
                style = NothingDotTextStyle,
                color = Color.White,
                fontSize = 24.sp
            )
            if (subText.isNotEmpty()) {
                Text(
                    text = subText,
                    style = NothingMonoTextStyle,
                    color = NothingLightGray,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
fun RecentsView(
    recents: List<RecentItem>,
    blockedNumbers: Set<String>,
    activeFilter: String,
    onFilterChange: (String) -> Unit,
    onCallItem: (RecentItem) -> Unit,
    onToggleBlock: (String) -> Unit,
    onSeeHistory: (String, String) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val filteredRecents = remember(recents, activeFilter, blockedNumbers) {
        when (activeFilter) {
            "missed" -> recents.filter { it.missed }
            "blocked" -> recents.filter { blockedNumbers.contains(it.number.replace("\\D".toRegex(), "")) }
            else -> recents
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "RECENTS",
                    style = NothingDotTextStyle,
                    color = Color.White,
                    fontSize = 18.sp
                )
                IconButton(onClick = onRefresh, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NothingRed, modifier = Modifier.size(16.dp))
                }
            }

            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("all", "missed", "blocked").forEach { filter ->
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (activeFilter == filter) Color.White else Color.Transparent)
                            .clickable { onFilterChange(filter) }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = filter.uppercase(),
                            style = NothingMonoTextStyle,
                            color = if (activeFilter == filter) Color.Black else NothingLightGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredRecents.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "NO RECENTS",
                    style = NothingMonoTextStyle,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredRecents) { recent ->
                    val isUnsaved = remember(recent.name, recent.number) {
                        val n = recent.name.trim()
                        n.isBlank() || n.equals("Unknown", true) || n.equals("Unsaved", true) || n == recent.number.trim()
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(NothingButtonGlass)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f)
                                .clickable { onSeeHistory(recent.number, recent.name) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(NothingSurface)
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (recent.name.isBlank() || recent.name.equals("Unknown", true)) "?" else recent.name.take(1).uppercase(),
                                    style = NothingDotTextStyle,
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (recent.name.isBlank() || recent.name.equals("Unknown", true)) "UNSAVED" else recent.name.uppercase(),
                                    style = NothingDotTextStyle,
                                    color = if (recent.missed) NothingRed else Color.White,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (recent.missed) Icons.Default.CallReceived else if (recent.type == "outgoing") Icons.Default.CallMade else Icons.Default.CallReceived,
                                        contentDescription = null,
                                        tint = if (recent.missed) NothingRed else if (recent.type == "outgoing") Color.Green else Color.Cyan,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    
                                    Text(
                                        text = recent.number,
                                        style = NothingMonoTextStyle,
                                        color = NothingLightGray,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (recent.recordingPath != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RadioButtonChecked,
                                            contentDescription = "Recorded",
                                            tint = NothingRed,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = "RECORDED",
                                            style = NothingMonoTextStyle,
                                            color = NothingRed,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            if (isUnsaved) {
                                IconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_INSERT).apply {
                                            type = "vnd.android.cursor.dir/contact"
                                            putExtra(ContactsContract.Intents.Insert.PHONE, recent.number)
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(NothingRed)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PersonAdd,
                                        contentDescription = "Save",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            IconButton(
                                onClick = { onToggleBlock(recent.number) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                val isBlocked = blockedNumbers.contains(recent.number.replace("\\D".toRegex(), ""))
                                Icon(
                                    imageVector = if (isBlocked) Icons.Default.Block else Icons.Default.Block,
                                    contentDescription = "Block",
                                    tint = if (isBlocked) NothingRed else Color.Gray,
                                    modifier = Modifier.size(16.dp).alpha(if (isBlocked) 1f else 0.4f)
                                )
                            }

                            IconButton(
                                onClick = { onSeeHistory(recent.number, recent.name) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "History",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(4.dp))

                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onCallItem(recent) }
                                    .padding(4.dp)
                            ) {
                                Text(
                                    text = recent.time,
                                    style = NothingMonoTextStyle,
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = "CALL NOW",
                                    style = NothingMonoTextStyle,
                                    color = NothingRed,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingsView(
    recordings: List<RecordingItem>,
    playingId: String?,
    onPlay: (RecordingItem) -> Unit,
    onShare: (RecordingItem) -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "RECORDINGS",
                    style = NothingDotTextStyle,
                    color = Color.White,
                    fontSize = 18.sp
                )
                IconButton(onClick = onRefresh, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NothingRed, modifier = Modifier.size(16.dp))
                }
            }

            Text(
                text = "${recordings.size} FILES",
                style = NothingMonoTextStyle,
                color = NothingLightGray,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (recordings.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "NO RECORDINGS FOUND",
                    style = NothingMonoTextStyle,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(recordings) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(NothingButtonGlass)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .clickable { onPlay(item) }
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(NothingSurface)
                                    .border(1.dp, NothingRed.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (playingId == item.id) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (playingId == item.id) NothingRed else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name.uppercase(),
                                    style = NothingDotTextStyle,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.number,
                                    style = NothingMonoTextStyle,
                                    color = NothingLightGray,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onShare(item) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = item.date,
                                    style = NothingMonoTextStyle,
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = if (playingId == item.id) "TAP TO STOP" else "TAP TO PLAY",
                                style = NothingMonoTextStyle,
                                color = NothingRed,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsView(
    isDefault: Boolean,
    hasOverlay: Boolean,
    isOverlayEnabled: Boolean,
    onSetDefault: () -> Unit,
    onRequestOverlay: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onOpenAbout: () -> Unit
) {
    val context = LocalContext.current
    val isBatteryOptimized = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        while(true) {
            isBatteryOptimized.value = !pm.isIgnoringBatteryOptimizations(context.packageName)
            delay(3000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "SETTINGS",
            style = NothingDotTextStyle,
            color = Color.White,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Default Dialer Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NothingButtonGlass)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .clickable { if (!isDefault) onSetDefault() }
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DEFAULT DIALER",
                    style = NothingDotTextStyle,
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = if (isDefault) "APP IS SET AS DEFAULT" else "SET APP AS DEFAULT",
                    style = NothingMonoTextStyle,
                    color = if (isDefault) Color.Green else NothingLightGray,
                    fontSize = 10.sp
                )
            }

            NothingToggle(
                checked = isDefault,
                onCheckedChange = { if (!isDefault) onSetDefault() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Overlay Permission Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NothingButtonGlass)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .clickable { 
                    if (!hasOverlay) onRequestOverlay() 
                    else onToggleOverlay(!isOverlayEnabled)
                }
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "UNIVERSAL CALL POPUP",
                    style = NothingDotTextStyle,
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = if (!hasOverlay) "ENABLE OVERLAY POPUP" 
                           else if (isOverlayEnabled) "ACTIVE FOR ALL DIALERS" 
                           else "POPUP IS DISABLED",
                    style = NothingMonoTextStyle,
                    color = if (hasOverlay && isOverlayEnabled) Color.Green else NothingLightGray,
                    fontSize = 10.sp
                )
            }

            NothingToggle(
                checked = hasOverlay && isOverlayEnabled,
                onCheckedChange = { 
                    if (!hasOverlay) onRequestOverlay() 
                    else onToggleOverlay(!isOverlayEnabled)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Battery Optimization Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NothingButtonGlass)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .clickable { 
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
                    context.startActivity(intent)
                }
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "STABLE BACKGROUND",
                    style = NothingDotTextStyle,
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = if (isBatteryOptimized.value) "TAP TO FIX POPUP RELIABILITY" else "OPTIMIZED FOR STABILITY",
                    style = NothingMonoTextStyle,
                    color = if (!isBatteryOptimized.value) Color.Green else NothingRed,
                    fontSize = 10.sp
                )
            }

            Icon(
                imageVector = if (!isBatteryOptimized.value) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (!isBatteryOptimized.value) Color.Green else NothingRed,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About Page Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NothingButtonGlass)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .clickable { onOpenAbout() }
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NothingSurface)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = NothingRed,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "ABOUT NTHING DIALER",
                            style = NothingDotTextStyle,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(NothingRed.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "v3.0",
                                style = NothingMonoTextStyle,
                                color = NothingRed,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "FEATURES, DEVELOPER PROFILE & SPECS",
                        style = NothingMonoTextStyle,
                        color = NothingLightGray,
                        fontSize = 10.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open About",
                tint = NothingLightGray,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "TIP: You can drag the call popup directly to move it to your preferred position.",
            style = NothingMonoTextStyle,
            color = Color.Gray,
            fontSize = 9.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun NothingToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val trackWidth = 44.dp
    val trackHeight = 24.dp
    val thumbSize = 16.dp
    val padding = 4.dp

    val animatePosition by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - padding else padding,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy), label = "toggleAnimate"
    )

    Box(
        modifier = Modifier
            .padding(4.dp) // Extra padding for larger touch target
            .size(trackWidth, trackHeight)
            .clip(CircleShape)
            .background(if (checked) NothingRed else Color.White.copy(alpha = 0.1f))
            .border(1.dp, if (checked) NothingRed else Color.White.copy(alpha = 0.2f), CircleShape)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = animatePosition)
                .size(thumbSize)
                .clip(CircleShape)
                .background(if (checked) Color.White else NothingLightGray)
        )
    }
}

@Composable
fun ContactsView(
    contacts: List<ContactItem>,
    favorites: List<ContactItem>,
    blockedNumbers: Set<String>,
    activeFilter: String,
    onFilterChange: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCallItem: (ContactItem) -> Unit,
    onToggleFavorite: (ContactItem) -> Unit,
    onToggleBlock: (String) -> Unit,
    onSeeHistory: (String, String) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val filteredContacts = remember(contacts, searchQuery, activeFilter, blockedNumbers) {
        val base = contacts.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.number.contains(searchQuery)
        }
        when (activeFilter) {
            "favorites" -> base.filter { it.favorite }
            "blocked" -> base.filter { blockedNumbers.contains(it.number.replace("\\D".toRegex(), "")) }
            else -> base
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "CONTACTS",
                    style = NothingDotTextStyle,
                    color = Color.White,
                    fontSize = 18.sp
                )
                IconButton(onClick = onRefresh, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = NothingRed, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = "${filteredContacts.size} PEOPLE",
                    style = NothingMonoTextStyle,
                    color = NothingLightGray,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("all", "favorites", "blocked").forEach { filter ->
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (activeFilter == filter) Color.White else Color.Transparent)
                            .clickable { onFilterChange(filter) }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (filter == "favorites") "FAV" else if (filter == "blocked") "BLK" else "ALL",
                            style = NothingMonoTextStyle,
                            color = if (activeFilter == filter) Color.Black else NothingLightGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Input Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    text = "Search contacts...",
                    style = NothingMonoTextStyle,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                focusedBorderColor = NothingRed.copy(alpha = 0.5f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (searchQuery.isEmpty() && activeFilter == "all" && favorites.isNotEmpty()) {
            Text(
                text = "FAVORITES",
                style = NothingDotTextStyle,
                color = NothingRed,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            ) {
                items(favorites) { contact ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(64.dp)
                            .clickable { onCallItem(contact) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(NothingSurface)
                                .border(1.dp, NothingRed.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = contact.initial,
                                style = NothingDotTextStyle,
                                color = Color.White,
                                fontSize = 22.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = contact.name.split(" ").first().uppercase(),
                            style = NothingMonoTextStyle,
                            color = Color.White,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.05f),
                thickness = 1.dp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        if (filteredContacts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "NO CONTACTS FOUND",
                    style = NothingMonoTextStyle,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredContacts) { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(NothingButtonGlass)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .clickable { onCallItem(contact) }
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f)
                                .clickable { onSeeHistory(contact.number, contact.name) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(NothingSurface)
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = contact.initial,
                                    style = NothingDotTextStyle,
                                    color = Color.White,
                                    fontSize = 18.sp
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = contact.name.uppercase(),
                                        style = NothingDotTextStyle,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (contact.favorite) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Favorite",
                                            tint = NothingRed,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${contact.number} • ${contact.type}",
                                    style = NothingMonoTextStyle,
                                    color = NothingLightGray,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            var showMenu by remember { mutableStateOf(false) }
                            val isBlocked = blockedNumbers.contains(contact.number.replace("\\D".toRegex(), ""))

                            Box {
                                IconButton(
                                    onClick = { showMenu = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.05f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More",
                                        tint = NothingLightGray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    modifier = Modifier
                                        .background(NothingSurface)
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                ) {
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                if (contact.favorite) "REMOVE FROM FAV" else "ADD TO FAVOURITE",
                                                style = NothingMonoTextStyle,
                                                fontSize = 11.sp,
                                                color = if (contact.favorite) NothingRed else Color.White
                                            ) 
                                        },
                                        onClick = {
                                            onToggleFavorite(contact)
                                            showMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (contact.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = null,
                                                tint = if (contact.favorite) NothingRed else Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                    
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                if (isBlocked) "UNBLOCK CONTACT" else "BLOCK CONTACT",
                                                style = NothingMonoTextStyle,
                                                fontSize = 11.sp,
                                                color = if (isBlocked) NothingRed else Color.White
                                            ) 
                                        },
                                        onClick = {
                                            onToggleBlock(contact.number)
                                            showMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Block,
                                                contentDescription = null,
                                                tint = if (isBlocked) NothingRed else Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("CALL HISTORY", style = NothingMonoTextStyle, fontSize = 11.sp, color = Color.White) },
                                        onClick = {
                                            onSeeHistory(contact.number, contact.name)
                                            showMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.History,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                                    DropdownMenuItem(
                                        text = { Text("SHARE (TEXT)", style = NothingMonoTextStyle, fontSize = 11.sp, color = Color.White) },
                                        onClick = {
                                            val shareText = "Name: ${contact.name}\nNumber: ${contact.number}"
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, shareText)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share Contact"))
                                            showMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Share,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("SHARE (VCARD)", style = NothingMonoTextStyle, fontSize = 11.sp, color = Color.White) },
                                        onClick = {
                                            val vCard = "BEGIN:VCARD\nVERSION:3.0\nFN:${contact.name}\nTEL;TYPE=CELL:${contact.number}\nEND:VCARD"
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/x-vcard"
                                                putExtra(Intent.EXTRA_TEXT, vCard)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share VCard"))
                                            showMenu = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.ContactPage,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            IconButton(
                                onClick = { onCallItem(contact) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Call",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InCallKeypad(onKeyPress: (Char) -> Unit) {
    val keys = listOf(
        Pair('1', ""), Pair('2', "ABC"), Pair('3', "DEF"),
        Pair('4', "GHI"), Pair('5', "JKL"), Pair('6', "MNO"),
        Pair('7', "PQRS"), Pair('8', "TUV"), Pair('9', "WXYZ"),
        Pair('*', ""), Pair('0', "+"), Pair('#', "")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        keys.chunked(3).forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowKeys.forEach { (charKey, subText) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .clickable {
                                AudioSynthHelper.playKeyTone(charKey)
                                onKeyPress(charKey)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = charKey.toString(),
                                style = NothingDotTextStyle,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                            if (subText.isNotEmpty()) {
                                Text(
                                    text = subText,
                                    style = NothingMonoTextStyle,
                                    color = Color.Gray,
                                    fontSize = 7.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IncomingCallOverlay(
    name: String,
    number: String,
    onAnswer: () -> Unit,
    onReject: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 40.dp)) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(NothingRed.copy(alpha = 0.1f))
                        .border(1.dp, NothingRed.copy(alpha = 0.3f), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "INCOMING CALL",
                        style = NothingDotTextStyle,
                        color = NothingRed,
                        fontSize = 12.sp,
                        letterSpacing = 2.sp
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                val title = if (name.isNotBlank()) name else number
                Text(
                    text = title.uppercase(),
                    style = NothingDotTextStyle,
                    color = Color.White,
                    fontSize = 32.sp,
                    textAlign = TextAlign.Center
                )

                if (name.isNotBlank() && number.isNotBlank() && number != name) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = number,
                        style = NothingMonoTextStyle,
                        color = NothingLightGray,
                        fontSize = 16.sp
                    )
                }
            }

            // Central Animated Icon
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(NothingSurface)
                    .border(2.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            // Bottom Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 60.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reject Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onReject,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(NothingRed)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Reject",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "DECLINE",
                        style = NothingMonoTextStyle,
                        color = NothingLightGray,
                        fontSize = 10.sp
                    )
                }

                // Answer Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onAnswer,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Answer",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "ANSWER",
                        style = NothingMonoTextStyle,
                        color = NothingLightGray,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveCallOverlay(
    name: String,
    number: String,
    statusHeading: String,
    callSeconds: Int,
    isMuted: Boolean,
    isSpeaker: Boolean,
    isRecording: Boolean,
    isKeypadVisible: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleRecording: () -> Unit,
    onToggleKeypad: () -> Unit,
    onGlyphSync: () -> Unit,
    onEndCall: () -> Unit
) {
    val context = LocalContext.current
    val mins = (callSeconds / 60).toString().padStart(2, '0')
    val secs = (callSeconds % 60).toString().padStart(2, '0')
    val formattedTime = "$mins:$secs"

    // Visualizer Ring Animation
    val infiniteTransition = rememberInfiniteTransition(label = "ringPulse")
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "ring1"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
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
                        text = statusHeading,
                        style = NothingDotTextStyle,
                        color = NothingOffWhite,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = formattedTime,
                    style = NothingDotTextStyle,
                    color = Color.White,
                    fontSize = 28.sp
                )

                if (isRecording) {
                    val recordingAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800),
                            repeatMode = RepeatMode.Reverse
                        ), label = "recordingPulse"
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp).alpha(recordingAlpha)
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(NothingRed))
                        Text(
                            text = "RECORDING",
                            style = NothingMonoTextStyle,
                            color = NothingRed,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Concentric Audio Wave Visualizer Ring & Contact Info
            Crossfade(targetState = isKeypadVisible, label = "keypadFade") { showKeypad ->
                if (showKeypad) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "TOUCH TONES",
                            style = NothingMonoTextStyle,
                            color = NothingRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        InCallKeypad(onKeyPress = { /* DTMF tones handled in helper */ })
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(170.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Outer Ring 3
                            Box(
                                modifier = Modifier
                                    .size(170.dp)
                                    .scale(scale1)
                                    .clip(CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                            )
                            // Ring 2
                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, NothingRed.copy(alpha = 0.3f), CircleShape)
                            )
                            // Ring 1
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                            )

                            // Avatar Circle
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .background(NothingSurface)
                                    .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (name.isNotBlank()) name.take(1).uppercase() else "?",
                                    style = NothingDotTextStyle,
                                    color = Color.White,
                                    fontSize = 32.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val displayTitle = if (name.isNotBlank()) name else number
                        Text(
                            text = displayTitle.uppercase(),
                            style = NothingDotTextStyle,
                            color = Color.White,
                            fontSize = 22.sp
                        )

                        if (name.isNotBlank() && number.isNotBlank() && number != name) {
                            Text(
                                text = number,
                                style = NothingMonoTextStyle,
                                color = NothingLightGray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Text(
                            text = number,
                            style = NothingMonoTextStyle,
                            color = NothingLightGray,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NothingRed.copy(alpha = 0.15f))
                                .border(1.dp, NothingRed.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "LONDON, UK • 5G",
                                style = NothingMonoTextStyle,
                                color = NothingRed,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // In-Call Controls Grid & End Call
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InCallControlButton(
                            icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = "MUTE",
                            selected = isMuted,
                            modifier = Modifier.weight(1f),
                            onClick = onToggleMute
                        )
                        InCallControlButton(
                            icon = Icons.Default.Dialpad,
                            label = "KEYPAD",
                            selected = isKeypadVisible,
                            modifier = Modifier.weight(1f),
                            onClick = onToggleKeypad
                        )
                        InCallControlButton(
                            icon = Icons.Default.VolumeUp,
                            label = "SPEAKER",
                            selected = isSpeaker,
                            modifier = Modifier.weight(1f),
                            onClick = onToggleSpeaker
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Button 1: ADD CALL
                        InCallControlButton(
                            icon = Icons.Default.PersonAdd,
                            label = "ADD CALL",
                            selected = false,
                            modifier = Modifier.weight(1f),
                            onClick = { Toast.makeText(context, "Add Call", Toast.LENGTH_SHORT).show() }
                        )

                        // Button 2: RECORD / RECORDING (replaces GLYPH SYNC)
                        RecordInCallControlButton(
                            isRecording = isRecording,
                            modifier = Modifier.weight(1f),
                            onClick = onToggleRecording
                        )

                        // Button 3: MERGE
                        InCallControlButton(
                            icon = Icons.Default.CallMerge,
                            label = "MERGE",
                            selected = false,
                            modifier = Modifier.weight(1f),
                            onClick = { 
                                NothingInCallService.mergeCalls()
                                Toast.makeText(context, "Merging calls...", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // End Call Button
                IconButton(
                    onClick = onEndCall,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(NothingRed)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RecordInCallControlButton(
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseRecordBorder")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "recordPulseAlpha"
    )

    val borderColor = if (isRecording) {
        NothingRed.copy(alpha = pulseAlpha)
    } else {
        Color.White.copy(alpha = 0.12f)
    }

    val borderWidth = if (isRecording) 2.dp else 1.dp

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isRecording) NothingRed.copy(alpha = 0.15f) else NothingButtonGlass)
            .border(
                borderWidth,
                borderColor,
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Circle,
                contentDescription = if (isRecording) "RECORDING" else "RECORD",
                tint = NothingRed,
                modifier = Modifier.size(if (isRecording) 18.dp else 12.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isRecording) "RECORDING" else "RECORD",
                style = NothingMonoTextStyle,
                color = if (isRecording) NothingRed else NothingLightGray,
                fontSize = 9.sp,
                fontWeight = if (isRecording) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun InCallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color.White.copy(alpha = 0.2f) else NothingButtonGlass)
            .border(
                1.dp,
                Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val tint = if (selected) Color.White else NothingLightGray
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = NothingMonoTextStyle,
                color = if (selected) Color.White else NothingLightGray,
                fontSize = 9.sp
            )
        }
    }
}
