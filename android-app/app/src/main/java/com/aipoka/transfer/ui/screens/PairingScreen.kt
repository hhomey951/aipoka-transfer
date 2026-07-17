package com.aipoka.transfer.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aipoka.transfer.NetworkDiscovery
import com.aipoka.transfer.PairingClient
import com.aipoka.transfer.Prefs
import com.aipoka.transfer.Registration
import com.aipoka.transfer.Scheduler
import com.aipoka.transfer.TrustedCert
import com.aipoka.transfer.ui.theme.AipokaTheme
import okhttp3.OkHttpClient
import org.json.JSONObject

private enum class PairTab { CODE, QR }

@Composable
fun PairingScreen(onPaired: () -> Unit) {
    val colors = AipokaTheme.colors
    val context = LocalContext.current
    var tab by remember { mutableStateOf(PairTab.CODE) }
    var serverUrl by remember { mutableStateOf("") }
    var discoveryStatus by remember { mutableStateOf("Searching for PC on this WiFi…") }
    var pairing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(Unit) {
        val discovery = NetworkDiscovery(context)
        discovery.discover(
            onFound = { host, port, name ->
                mainHandler.post {
                    serverUrl = "https://$host:$port"
                    discoveryStatus = "Found: $name"
                }
            },
            onTimeout = {
                mainHandler.post {
                    discoveryStatus = "No PC auto-found. Enter address manually."
                }
            }
        )
        onDispose { discovery.stop() }
    }

    fun completePairing(result: PairingClient.PairResult) {
        mainHandler.post {
            pairing = false
            statusMessage = result.message
            if (result.success && result.serverUrl != null && result.token != null && result.deviceName != null) {
                Prefs.savePairing(context, result.serverUrl, result.token, result.deviceName, result.certFingerprint)
                Scheduler.start(context)

                // Best-effort install tracking, deferred until now because /register needs
                // the server URL, which we only learn from a successful /pair response.
                val registerClient = if (result.certFingerprint != null) {
                    val (sf, tm) = TrustedCert.pinnedSocketFactory(result.certFingerprint)
                    OkHttpClient.Builder().sslSocketFactory(sf, tm).hostnameVerifier(TrustedCert.NO_OP_HOSTNAME_VERIFIER).build()
                } else {
                    OkHttpClient.Builder().build()
                }
                Registration.registerBestEffort(
                    registerClient,
                    result.serverUrl,
                    Prefs.getProfileName(context),
                    Prefs.getProfileEmail(context)
                )

                onPaired()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBg)
            .padding(24.dp)
    ) {
        Text("Pair with PC", fontSize = 26.sp, fontWeight = FontWeight.Black, color = colors.ink)
        Spacer(modifier = Modifier.height(6.dp))
        Text(discoveryStatus, fontSize = 13.sp, color = colors.sub)
        Spacer(modifier = Modifier.height(20.dp))

        // Pill-style tab switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.accentSoft, RoundedCornerShape(14.dp))
                .padding(4.dp)
        ) {
            listOf(PairTab.CODE to "Enter code", PairTab.QR to "Scan QR").forEach { (t, label) ->
                val selected = tab == t
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selected) colors.accent else Color.Transparent)
                        .clickable { tab = t }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (selected) colors.onAccent else colors.sub,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        when (tab) {
            PairTab.CODE -> CodeEntryTab(
                serverUrl = serverUrl,
                onServerUrlChange = { serverUrl = it },
                pairing = pairing,
                statusMessage = statusMessage,
                onPair = { pin ->
                    pairing = true
                    statusMessage = "Pairing…"
                    PairingClient.pair(serverUrl, pin, ::completePairing)
                }
            )
            PairTab.QR -> QrTab(
                pairing = pairing,
                statusMessage = statusMessage,
                onQrPayload = { url, pin ->
                    pairing = true
                    statusMessage = "Pairing…"
                    PairingClient.pair(url, pin, ::completePairing)
                }
            )
        }
    }
}

@Composable
private fun CodeEntryTab(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    pairing: Boolean,
    statusMessage: String,
    onPair: (pin: String) -> Unit
) {
    val colors = AipokaTheme.colors
    var digits by remember { mutableStateOf(List(6) { "" }) }
    val focusRequesters = remember { List(6) { FocusRequester() } }

    Column {
        Text("PC ADDRESS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.sub)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrlChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://192.168.1.42:8443") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = colors.inputBg,
                focusedContainerColor = colors.inputBg,
                unfocusedBorderColor = colors.border,
                focusedBorderColor = colors.accent
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Only needed if your PC isn't auto-discovered on WiFi",
            fontSize = 12.sp,
            color = colors.sub
        )

        Spacer(modifier = Modifier.height(20.dp))
        Text("PAIRING PIN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.sub)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 0 until 6) {
                OutlinedTextField(
                    value = digits[i],
                    onValueChange = { value ->
                        val d = value.filter { it.isDigit() }.take(1)
                        digits = digits.toMutableList().also { it[i] = d }
                        if (d.isNotEmpty() && i < 5) focusRequesters[i + 1].requestFocus()
                        if (d.isEmpty() && i > 0 && value.isEmpty()) focusRequesters[i - 1].requestFocus()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .focusRequester(focusRequesters[i]),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        textAlign = TextAlign.Center,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = colors.inputBg,
                        focusedContainerColor = colors.inputBg,
                        unfocusedBorderColor = colors.border,
                        focusedBorderColor = colors.accent
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        val pin = digits.joinToString("")
        Button(
            onClick = { onPair(pin) },
            enabled = pin.length == 6 && !pairing,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.onAccent
            )
        ) {
            if (pairing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.onAccent, strokeWidth = 2.dp)
            } else {
                Text("Pair device", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        if (statusMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(statusMessage, fontSize = 13.sp, color = colors.sub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun QrTab(
    pairing: Boolean,
    statusMessage: String,
    onQrPayload: (url: String, pin: String) -> Unit
) {
    val colors = AipokaTheme.colors
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        permissionDenied = !granted
    }

    DisposableEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
        onDispose { }
    }

    Column {
        when {
            pairing -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = colors.accent)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Pairing…", color = colors.sub, fontSize = 14.sp)
                }
            }
            hasCameraPermission -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(Color.Black, RoundedCornerShape(16.dp))
            ) {
                QrScannerView(onQrDetected = { raw ->
                    try {
                        val json = JSONObject(raw)
                        val url = json.getString("url")
                        val pin = json.getString("pin")
                        onQrPayload(url, pin)
                    } catch (e: Exception) {
                        // Not a valid Aipoka pairing QR — ignore and keep scanning would
                        // require resetting the "handled" flag; simplest is to let the
                        // user fall back to the code tab.
                    }
                })
            }
            permissionDenied -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(colors.cardBg, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Camera permission was denied. Use \"Enter code\" instead, or grant camera access in system settings.",
                    color = colors.sub,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
            else -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colors.accent)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            "Point your camera at the QR code shown on your PC.",
            fontSize = 13.sp,
            color = colors.sub,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (statusMessage.isNotEmpty() && !pairing) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(statusMessage, fontSize = 13.sp, color = colors.sub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}
