package com.example.nthingdailer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nthingdailer.ui.theme.*

@Composable
fun PermissionRationaleScreen(
    isDefaultDialer: Boolean,
    hasContactPermission: Boolean,
    hasOverlayPermission: Boolean,
    onRequestRole: () -> Unit,
    onRequestPermissions: () -> Unit,
    onRequestOverlay: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NothingBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo / Icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(NothingRed.copy(alpha = 0.1f))
                .border(2.dp, NothingRed, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(NothingRed)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "SETUP NThing DIALER",
            style = NothingDotTextStyle,
            color = Color.White,
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "To provide the best experience on Android 16, we need a few configurations.",
            style = NothingMonoTextStyle,
            color = NothingLightGray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Step 1: Default Dialer (Optional)
        SetupStepItem(
            icon = Icons.Default.Phone,
            title = "DEFAULT DIALER (OPTIONAL)",
            description = "Recommended for the full Nothing UI experience.",
            isCompleted = isDefaultDialer,
            onClick = onRequestRole
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Step 2: Contacts (Mandatory)
        SetupStepItem(
            icon = Icons.Default.Contacts,
            title = "CONTACTS & PHONE (REQUIRED)",
            description = "Mandatory to show names and manage your calling logs.",
            isCompleted = hasContactPermission,
            onClick = onRequestPermissions
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Step 3: Overlay (Mandatory)
        SetupStepItem(
            icon = Icons.Default.Layers,
            title = "OVERLAY POPUP (REQUIRED)",
            description = "Mandatory to show the Nothing call popup over other apps.",
            isCompleted = hasOverlayPermission,
            onClick = onRequestOverlay
        )

        Spacer(modifier = Modifier.height(48.dp))
        
        // Continue Button
        Button(
            onClick = onContinue,
            enabled = hasContactPermission && hasOverlayPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = NothingRed,
                contentColor = Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.1f),
                disabledContentColor = Color.Gray
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = "CONTINUE",
                style = NothingMonoTextStyle,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (isDefaultDialer && hasContactPermission && hasOverlayPermission) {
             Spacer(modifier = Modifier.height(16.dp))
             Text(
                text = "SYSTEM READY",
                style = NothingMonoTextStyle,
                color = NothingRed,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SetupStepItem(
    icon: ImageVector,
    title: String,
    description: String,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isCompleted) Color.White.copy(alpha = 0.05f) else NothingButtonGlass)
            .border(1.dp, if (isCompleted) NothingRed.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isCompleted) NothingRed.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isCompleted) NothingRed else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = NothingDotTextStyle,
                color = Color.White,
                fontSize = 14.sp
            )
            Text(
                text = description,
                style = NothingMonoTextStyle,
                color = NothingLightGray,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isCompleted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Completed",
                tint = NothingRed,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = "SET",
                    style = NothingMonoTextStyle,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
