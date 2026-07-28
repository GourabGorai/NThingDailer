package com.example.nthingdailer.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nthingdailer.ui.components.DotMatrixBackground
import com.example.nthingdailer.ui.theme.*

@Composable
fun RearGlyphScreen(
    isGlyphActive: Boolean,
    onFlipToFront: () -> Unit
) {
    // Pulse animation state
    val activeGlowColor by animateColorAsState(
        targetValue = if (isGlyphActive) Color.White else Color.White.copy(alpha = 0.15f),
        animationSpec = tween(300), label = "glyphGlow"
    )

    val redDotGlowColor by animateColorAsState(
        targetValue = if (isGlyphActive) NothingRed else NothingRed.copy(alpha = 0.3f),
        animationSpec = tween(300), label = "redGlow"
    )

    DotMatrixBackground(
        dotColor = Color.White.copy(alpha = 0.15f),
        spacing = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section (Camera Module & Top Strip)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Dual Camera Bump
                Box(
                    modifier = Modifier
                        .width(76.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFF1A1A1E))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(32.dp))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Camera 1 Lens with Glyph LED Ring
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Black)
                                .border(
                                    2.dp,
                                    if (isGlyphActive) Color.White else Color.White.copy(alpha = 0.2f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF08182B))
                                    .border(1.dp, Color(0x801E40AF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x803B82F6))
                                )
                            }
                        }

                        // Camera 2 Lens
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Black)
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF08182B))
                                    .border(1.dp, Color(0x801E40AF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x803B82F6))
                                )
                            }
                        }
                    }
                }

                // Top Diagonal Glyph Strip
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .height(32.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .rotate(-12f)
                            .clip(CircleShape)
                            .background(activeGlowColor)
                            .shadow(if (isGlyphActive) 16.dp else 0.dp, CircleShape)
                    )
                }
            }

            // Central Wireless Charging Coil Interface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer Dashed Coil Ring
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.12f),
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        )
                    }

                    // Inner Ring
                    Box(
                        modifier = Modifier
                            .size(170.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.2f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "NOTHING (1)",
                                style = NothingDotTextStyle,
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Central Glyph LED Ring Segment
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = activeGlowColor,
                            style = Stroke(width = 5.dp.toPx())
                        )
                    }
                }
            }

            // Bottom Section (Progress Glyph & Red Recording LED)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Bottom Bar Glyph
                    Column(modifier = Modifier.width(140.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(activeGlowColor)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "GLYPH PROGRESS BAR",
                            style = NothingMonoTextStyle,
                            color = Color.Gray,
                            fontSize = 8.sp
                        )
                    }

                    // Red Recording LED
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(redDotGlowColor)
                                .shadow(if (isGlyphActive) 8.dp else 0.dp, CircleShape)
                        )
                        Text(
                            text = "REC",
                            style = NothingMonoTextStyle,
                            color = NothingLightGray,
                            fontSize = 10.sp
                        )
                    }
                }

                // Back to Front Dialer Button
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = onFlipToFront,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "BACK TO FRONT DIALER",
                            style = NothingMonoTextStyle,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
