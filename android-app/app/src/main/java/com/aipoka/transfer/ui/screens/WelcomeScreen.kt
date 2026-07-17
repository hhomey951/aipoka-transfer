package com.aipoka.transfer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aipoka.transfer.ui.theme.AipokaTheme

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    val colors = AipokaTheme.colors
    Column(
        modifier = androidx.compose.ui.Modifier
            .fillMaxSize()
            .background(colors.pageBg)
            .padding(PaddingValues(horizontal = 32.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(colors.accentSoft, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CloudUpload,
                contentDescription = null,
                tint = colors.accentGlyph,
                modifier = Modifier.size(40.dp)
            )
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Welcome to Aipoka",
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = colors.ink,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Send your phone photos straight to a folder on your PC over WiFi — " +
                "no cloud, no account needed to move files.",
            fontSize = 14.sp,
            color = colors.sub,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.onAccent
            )
        ) {
            Text("Get started", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
