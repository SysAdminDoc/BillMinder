package com.sysadmindoc.billminder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.sysadmindoc.billminder.data.BillDatabase
import com.sysadmindoc.billminder.data.DatabaseHealth
import com.sysadmindoc.billminder.security.SecurityPrefs
import com.sysadmindoc.billminder.ui.screens.*
import com.sysadmindoc.billminder.ui.theme.*
import com.sysadmindoc.billminder.viewmodel.BillViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class MainActivity : FragmentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private var isUnlocked = mutableStateOf(false)
    private var biometricAvailable = false
    private var lastActiveTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()

        biometricAvailable = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

        val biometricEnabled = SecurityPrefs.isBiometricEnabled(this)
        val pinSet = SecurityPrefs.hasPin(this)

        // Prevent screenshots in app switcher when security is enabled
        if (biometricEnabled || pinSet) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        if (biometricEnabled && biometricAvailable) {
            promptBiometric()
        } else if (pinSet) {
            // PIN-only mode, will show PIN entry
        } else {
            isUnlocked.value = true
        }

        setContent {
            BillMinderTheme {
                val unlocked by isUnlocked
                var pinConfigured by remember { mutableStateOf(pinSet) }
                var duressMode by remember { mutableStateOf(false) }
                var databaseHealth by remember { mutableStateOf<DatabaseHealth?>(null) }

                LaunchedEffect(Unit) {
                    databaseHealth = withContext(Dispatchers.IO) { BillDatabase.checkHealth(this@MainActivity) }
                }

                val health = databaseHealth
                // Nothing touches the database until the check finishes: building the nav host
                // first would open it through the view model and crash before this can render.
                if (health == null) {
                    Surface(color = CatCrust, modifier = Modifier.fillMaxSize()) {}
                } else if (health is DatabaseHealth.Unusable) {
                    DatabaseRecoveryScreen(health.reason, health.databasePath)
                } else if (unlocked) {
                    if (duressMode) {
                        DecoyScreen()
                    } else {
                        BillMinderNavHost(
                            biometricAvailable = biometricAvailable,
                            isBiometricEnabled = biometricEnabled,
                            onToggleBiometric = { enabled ->
                                SecurityPrefs.setBiometricEnabled(this, enabled)
                            },
                            onPinConfigured = { pinConfigured = true }
                        )
                    }
                } else {
                    LockScreen(
                        onUnlock = {
                            if (biometricAvailable && biometricEnabled) {
                                promptBiometric()
                            }
                        },
                        showBiometric = biometricAvailable && biometricEnabled,
                        showPin = pinConfigured,
                        onPinSubmit = { enteredPin ->
                            if (SecurityPrefs.verifyDuressPin(this, enteredPin)) {
                                duressMode = true
                                isUnlocked.value = true
                                true
                            } else {
                                val isValid = SecurityPrefs.verifyPin(this, enteredPin)
                                if (isValid) {
                                    isUnlocked.value = true
                                }
                                isValid
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        lastActiveTime = System.currentTimeMillis()
        val biometricEnabled = SecurityPrefs.isBiometricEnabled(this)
        val pinSet = SecurityPrefs.hasPin(this)
        val securityEnabled = (biometricEnabled && biometricAvailable) || pinSet
        if (securityEnabled && SecurityPrefs.getAutoLockMinutes(this) == 0) {
            isUnlocked.value = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (isUnlocked.value && lastActiveTime > 0) {
            val autoLockMinutes = SecurityPrefs.getAutoLockMinutes(this)
            val biometricEnabled = SecurityPrefs.isBiometricEnabled(this)
            val pinSet = SecurityPrefs.hasPin(this)
            val securityEnabled = (biometricEnabled && biometricAvailable) || pinSet

            if (securityEnabled && autoLockMinutes >= 0) {
                val elapsed = System.currentTimeMillis() - lastActiveTime
                val timeoutMs = autoLockMinutes * 60 * 1000L
                if (elapsed > timeoutMs) {
                    isUnlocked.value = false
                    if (biometricEnabled && biometricAvailable) {
                        promptBiometric()
                    }
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
                runOnUiThread { isUnlocked.value = false }
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
private fun LockScreen(
    onUnlock: () -> Unit,
    showBiometric: Boolean = true,
    showPin: Boolean = false,
    onPinSubmit: ((String) -> Boolean)? = null
) {
    var pinEntry by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().background(CatCrust),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)) {
            Icon(
                Icons.Filled.Lock,
                null,
                tint = CatBlue,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("BillMinder", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = CatText)
            Spacer(Modifier.height(8.dp))
            Text("Authenticate to continue", color = CatSubtext0)

            if (showBiometric) {
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onUnlock,
                    colors = ButtonDefaults.buttonColors(containerColor = CatBlue, contentColor = CatCrust)
                ) {
                    Icon(Icons.Filled.Fingerprint, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Unlock with Biometric")
                }
            }

            if (showPin) {
                Spacer(Modifier.height(if (showBiometric) 16.dp else 24.dp))
                if (showBiometric) {
                    Text("or enter PIN", color = CatSubtext0, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = pinEntry,
                    onValueChange = { v ->
                        pinEntry = v.filter { it.isDigit() }.take(6)
                        pinError = false
                    },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CatText,
                        unfocusedTextColor = CatText,
                        focusedBorderColor = if (pinError) CatRed else CatBlue,
                        unfocusedBorderColor = if (pinError) CatRed else CatSurface1,
                        cursorColor = CatBlue
                    ),
                    placeholder = { Text("Enter PIN", color = CatOverlay0) },
                    modifier = Modifier.width(200.dp)
                )
                if (pinError) {
                    Spacer(Modifier.height(4.dp))
                    Text("Incorrect PIN", color = CatRed, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val success = onPinSubmit?.invoke(pinEntry) ?: false
                        if (!success) {
                            pinError = true
                            pinEntry = ""
                        }
                    },
                    enabled = pinEntry.length >= 4,
                    colors = ButtonDefaults.buttonColors(containerColor = CatBlue, contentColor = CatCrust)
                ) {
                    Text("Submit")
                }
            }
        }
    }
}

@Composable
private fun DecoyScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(CatCrust),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = CatBlue, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("No bills yet", color = CatText, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("Add a bill to get started", color = CatSubtext0)
        }
    }
}

enum class BottomTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    CALENDAR("Calendar", Icons.Filled.CalendarMonth),
    STATS("Insights", Icons.Filled.PieChart),
    SETTINGS("Settings", Icons.Filled.Settings)
}

@Composable
fun BillMinderNavHost(
    biometricAvailable: Boolean,
    isBiometricEnabled: Boolean,
    onToggleBiometric: (Boolean) -> Unit,
    onPinConfigured: () -> Unit
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
                Column {
                    HorizontalDivider(color = CatDivider)
                    NavigationBar(
                        modifier = Modifier.height(72.dp),
                        containerColor = CatMantle,
                        contentColor = CatText,
                        tonalElevation = 0.dp
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
                                icon = {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            Modifier
                                                .width(24.dp)
                                                .height(2.dp)
                                                .background(if (isSelected) CatBlue else CatMantle)
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Icon(tab.icon, tab.label, modifier = Modifier.size(23.dp))
                                    }
                                },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CatBlue,
                                    selectedTextColor = CatBlue,
                                    indicatorColor = CatMantle,
                                    unselectedIconColor = CatOverlay0,
                                    unselectedTextColor = CatOverlay0
                                )
                            )
                        }
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
                    onBillTap = { navController.navigate("detail/$it") },
                    onAddBill = { navController.navigate("add_edit/0") }
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
                    },
                    onPinConfigured = onPinConfigured
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
