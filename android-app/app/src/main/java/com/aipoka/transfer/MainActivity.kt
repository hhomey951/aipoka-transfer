package com.aipoka.transfer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aipoka.transfer.ui.screens.HomeScreen
import com.aipoka.transfer.ui.screens.LoginScreen
import com.aipoka.transfer.ui.screens.PairingScreen
import com.aipoka.transfer.ui.screens.WelcomeScreen
import com.aipoka.transfer.ui.theme.AipokaAppTheme

/**
 * Single Compose host for the whole app: welcome -> login -> pairing -> home tabs.
 * Once paired the app always starts at "home" (mirrors the old Prefs.isPaired() gate
 * that used to live in onCreate before PairingActivity was folded into this NavHost).
 */
class MainActivity : ComponentActivity() {

    private val permissionRequestCode = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNeededPermissions()

        if (Prefs.isPaired(this)) {
            Scheduler.start(this)
        }

        setContent {
            var darkOverride by remember { mutableStateOf(Prefs.getAppearanceOverride(this)) }

            AipokaAppTheme(darkOverride = darkOverride) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val startDestination = if (Prefs.isPaired(this)) "home" else "welcome"

                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("welcome") {
                            WelcomeScreen(onGetStarted = { navController.navigate("login") })
                        }
                        composable("login") {
                            LoginScreen(onLoggedIn = { navController.navigate("pairing") })
                        }
                        composable("pairing") {
                            PairingScreen(onPaired = {
                                navController.navigate("home") {
                                    popUpTo("welcome") { inclusive = true }
                                }
                            })
                        }
                        composable("home") {
                            HomeScreen(
                                darkOverride = darkOverride,
                                onDarkOverrideChange = { newValue ->
                                    darkOverride = newValue
                                    Prefs.setAppearanceOverride(this@MainActivity, newValue)
                                },
                                onUnpaired = {
                                    navController.navigate("welcome") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()

        val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, mediaPermission) != PackageManager.PERMISSION_GRANTED) {
            needed.add(mediaPermission)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), permissionRequestCode)
        }
    }
}
