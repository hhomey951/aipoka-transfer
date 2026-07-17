package com.aipoka.transfer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aipoka.transfer.Prefs
import com.aipoka.transfer.ui.theme.AipokaTheme

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val colors = AipokaTheme.colors
    val context = LocalContext.current
    var name by remember { mutableStateOf(Prefs.getProfileName(context).orEmpty()) }
    var email by remember { mutableStateOf(Prefs.getProfileEmail(context).orEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBg)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Log in", fontSize = 26.sp, fontWeight = FontWeight.Black, color = colors.ink)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "First time on this phone — create or sign in to your local profile",
            fontSize = 14.sp,
            color = colors.sub
        )
        Spacer(modifier = Modifier.height(28.dp))

        Text("Name", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.sub)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Your name") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = colors.inputBg,
                focusedContainerColor = colors.inputBg,
                unfocusedBorderColor = colors.border,
                focusedBorderColor = colors.accent
            )
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text("Email", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.sub)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("you@example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = colors.inputBg,
                focusedContainerColor = colors.inputBg,
                unfocusedBorderColor = colors.border,
                focusedBorderColor = colors.accent
            )
        )

        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = {
                // /register isn't fired yet — we don't know the PC's server URL until
                // pairing succeeds. Save the profile now; the Pairing screen fires the
                // deferred POST /register after a successful /pair.
                Prefs.setProfile(context, name.trim(), email.trim())
                onLoggedIn()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = name.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.onAccent
            )
        ) {
            Text("Log in", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
