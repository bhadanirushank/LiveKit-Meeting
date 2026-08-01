package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFD1D5DB)) {
                    ConceptPresentationBoard()
                }
            }
        }
    }
}

@Composable
fun ConceptPresentationBoard() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 64.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "REFINED CONCEPT: QUIETLY PREMIUM",
            letterSpacing = 2.sp,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF374151)
        )

        SectionTitle("1. Light Mode")
        MockupContainer(width = 360.dp, height = 780.dp) { HomeConceptScreen(isDark = false) }

        SectionTitle("2. Dark Mode")
        MockupContainer(width = 360.dp, height = 780.dp) { HomeConceptScreen(isDark = true) }
        
        SectionTitle("3. Narrow Screen")
        MockupContainer(width = 280.dp, height = 780.dp) { HomeConceptScreen(isDark = false) }

        SectionTitle("4. 200% Font Scale")
        val currentDensity = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = currentDensity.density,
                fontScale = 2f
            )
        ) {
            MockupContainer(width = 360.dp, height = 780.dp) { HomeConceptScreen(isDark = false) }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = Color(0xFF4B5563),
        modifier = Modifier.padding(top = 16.dp)
    )
}

@Composable
fun MockupContainer(width: Dp, height: Dp, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White)
            .padding(4.dp)
            .clip(RoundedCornerShape(28.dp))
    ) {
        content()
    }
}

@Composable
fun HomeConceptScreen(isDark: Boolean) {
    val bgColor = if (isDark) Color(0xFF0A0A0A) else Color(0xFFFAFAFA)
    val onBgColor = if (isDark) Color(0xFFFAFAFA) else Color(0xFF0A0A0A)
    val onBgSubtle = if (isDark) Color(0xFFA3A3A3) else Color(0xFF525252)
    val primaryBtnBg = if (isDark) Color(0xFFFAFAFA) else Color(0xFF0A0A0A)
    val primaryBtnText = if (isDark) Color(0xFF0A0A0A) else Color(0xFFFAFAFA)
    val secBtnBg = if (isDark) Color(0xFF171717) else Color(0xFFF5F5F5)
    val secBtnBorder = if (isDark) Color(0xFF262626) else Color(0xFFE5E5E5)
    
    val accentColor = Color(0xFF6558D3)
    val accentSurface = if (isDark) Color(0xFF282443) else Color(0xFFEEECFF)
    val successColor = Color(0xFF1E8E68)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .padding(bottom = 24.dp) // Extra safe padding
        ) {
            // Minimal Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Meeting",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = onBgColor,
                    letterSpacing = (-0.5).sp
                )
            }

            Spacer(modifier = Modifier.height(96.dp))

            // Headline
            Text(
                text = "Meet clearly.\nConnect simply.",
                fontSize = 42.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.Light,
                color = onBgColor,
                letterSpacing = (-1.5).sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Subtitle
            Text(
                text = "Start a meeting or join securely with a meeting code.",
                fontSize = 17.sp,
                lineHeight = 26.sp,
                color = onBgSubtle,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(96.dp))

            // Actions
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Primary Action
                Button(
                    onClick = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 64.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryBtnBg),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text(
                        text = "Create Meeting",
                        color = primaryBtnText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Secondary Action
                Surface(
                    onClick = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 64.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = secBtnBg,
                    border = BorderStroke(1.dp, secBtnBorder)
                ) {
                    Box(
                        modifier = Modifier.padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Join with Code",
                            color = onBgColor,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = successColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ready to connect",
                    fontSize = 13.sp,
                    color = onBgSubtle,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
