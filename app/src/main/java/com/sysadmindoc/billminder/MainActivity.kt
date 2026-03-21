package com.sysadmindoc.billminder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.sysadmindoc.billminder.ui.screens.*
import com.sysadmindoc.billminder.ui.theme.*
import com.sysadmindoc.billminder.viewmodel.BillViewModel
import java.util.concurrent.Executors

class MainActivity : FragmentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private var isUnlocked = mutableStateOf(false)
    private var biometricAvailable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()

        biometricAvailable = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

        val prefs = getSharedPreferences("billminder_prefs", Context.MODE_PRIVATE)
        val biometricEnabled = prefs.getBoolean("biometric_enabled", false)

        if (biometricEnabled && biometricAvailable) {
            promptBiometric()
        } else {
            isUnlocked.value = true
        }

        setContent {
            BillMinderTheme {
                val unlocked by isUnlocked

                if (unlocked) {
                    BillMinderNavHost(
                        biometricAvailable = biometricAvailable,
                        isBiometricEnabled = biometricEnabled,
                        onToggleBiometric = { enabled ->
                            prefs.edit { putBoolean("biometric_enabled", enabled) }
                        }
                    )
                } else {
                    LockScreen(onUnlock = { promptBiometric() })
                }
            }
        }
    }

    private fun promptBiometric() {
        val executor = Executors.newSingleThreadExecutor()
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                runOnUiThread { isUnlocked.value = true }
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                    // User cancelled, stay on lock screen
                } else {
                    runOnUiThread { isUnlocked.value = true }
                }
            }
        })
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("BillMinder")
            .setSubtitle("Authenticate to access your bills")
            .setNegativeButtonText("Cancel")
            .build()
        prompt.authenticate(promptInfo)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(CatCrust),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Lock,
                null,
                tint = CatBlue,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("BillMinder", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = CatText)
            Spacer(Modifier.height(8.dp))
            Text("Tap to unlock", color = CatSubtext0)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onUnlock,
                colors = ButtonDefaults.buttonColors(containerColor = CatBlue, contentColor = CatCrust)
            ) {
                Icon(Icons.Filled.Fingerprint, null)
                Spacer(Modifier.width(8.dp))
                Text("Unlock")
            }
        }
    }
}

enum class BottomTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    CALENDAR("Calendar", Icons.Filled.CalendarMonth),
    STATS("Stats", Icons.Filled.PieChart),
    SETTINGS("Settings", Icons.Filled.Settings)
}

@Composable
fun BillMinderNavHost(
    biometricAvailable: Boolean,
    isBiometricEnabled: Boolean,
    onToggleBiometric: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val viewModel: BillViewModel = viewModel()

    var biometricState by remember { mutableStateOf(isBiometricEnabled) }
    var selectedTab by remember { mutableStateOf(BottomTab.HOME) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in listOf("home", "calendar", "stats", "settings")

    Scaffold(
        containerColor = CatCrust,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = CatMantle,
                    contentColor = CatText
                ) {
                    BottomTab.entries.forEach { tab ->
                        val isSelected = when (tab) {
                            BottomTab.HOME -> currentRoute == "home"
                            BottomTab.CALENDAR -> currentRoute == "calendar"
                            BottomTab.STATS -> currentRoute == "stats"
                            BottomTab.SETTINGS -> currentRoute == "settings"
                        }
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                selectedTab = tab
                                val route = tab.name.lowercase()
                                navController.navigate(route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CatBlue,
                                selectedTextColor = CatBlue,
                                indicatorColor = CatBlue.copy(alpha = 0.12f),
                                unselectedIconColor = CatOverlay0,
                                unselectedTextColor = CatOverlay0
                            )
                        )
                    }
                }
            }
        }
    ) { scaffoldPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(scaffoldPadding)
        ) {
            composable("home") {
                HomeScreen(
                    viewModel = viewModel,
                    onAddBill = { navController.navigate("add_edit/0") },
                    onBillTap = { navController.navigate("detail/$it") },
                    onEditBill = { navController.navigate("add_edit/$it") }
                )
            }

            composable("calendar") {
                CalendarScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onBillTap = { navController.navigate("detail/$it") }
                )
            }

            composable("stats") {
                StatsScreen(viewModel = viewModel)
            }

            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    isBiometricEnabled = biometricState && biometricAvailable,
                    onToggleBiometric = { enabled ->
                        biometricState = enabled
                        onToggleBiometric(enabled)
                    }
                )
            }

            composable(
                "add_edit/{billId}",
                arguments = listOf(navArgument("billId") { type = NavType.LongType })
            ) { backStackEntry ->
                val billId = backStackEntry.arguments?.getLong("billId")
                AddEditBillScreen(
                    viewModel = viewModel,
                    billId = if (billId == 0L) null else billId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                "detail/{billId}",
                arguments = listOf(navArgument("billId") { type = NavType.LongType })
            ) { backStackEntry ->
                val billId = backStackEntry.arguments?.getLong("billId") ?: return@composable
                BillDetailScreen(
                    viewModel = viewModel,
                    billId = billId,
                    onNavigateBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("add_edit/$it") }
                )
            }
        }
    }
}
