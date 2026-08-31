package com.sysadmindoc.billminder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.core.util.Consumer
import com.sysadmindoc.billminder.data.BillDatabase
import com.sysadmindoc.billminder.data.CurrencyFormatter
import com.sysadmindoc.billminder.data.SmsBillCandidate
import com.sysadmindoc.billminder.data.SmsBillParser
import com.sysadmindoc.billminder.data.DatabaseHealth
import com.sysadmindoc.billminder.security.PinUnlockResult
import com.sysadmindoc.billminder.security.SecurityPrefs
import com.sysadmindoc.billminder.security.SecurityState
import com.sysadmindoc.billminder.ui.screens.*
import com.sysadmindoc.billminder.ui.theme.*
import com.sysadmindoc.billminder.viewmodel.BillViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class MainActivity : FragmentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private var isUnlocked = mutableStateOf(false)
    private var biometricAvailable = false
    private var lastActiveTime = 0L
    private val securityPreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread { applyWindowSecurity() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        SecurityPrefs.prefs(this).registerOnSharedPreferenceChangeListener(securityPreferenceListener)
        applyWindowSecurity()

        biometricAvailable = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

        setContent {
            BillMinderTheme {
                val unlocked by isUnlocked
                val securityState by SecurityPrefs.observe(this@MainActivity).collectAsStateWithLifecycle(
                    initialValue = SecurityPrefs.readState(this@MainActivity)
                )
                var duressMode by remember { mutableStateOf(false) }
                var databaseHealth by remember { mutableStateOf<DatabaseHealth?>(null) }

                LaunchedEffect(securityState.lockConfigured) {
                    if (!securityState.lockConfigured) duressMode = false
                }
                LaunchedEffect(securityState.biometricEnabled, biometricAvailable) {
                    if (!isUnlocked.value && securityState.biometricEnabled && biometricAvailable) {
                        promptBiometric()
                    }
                }

                LaunchedEffect(Unit) {
                    databaseHealth = withContext(Dispatchers.IO) { BillDatabase.checkHealth(this@MainActivity) }
                }

                var sharedMessage by remember { mutableStateOf(sharedTextFrom(intent)) }
                DisposableEffect(Unit) {
                    val listener = Consumer<Intent> { sharedMessage = sharedTextFrom(it) }
                    addOnNewIntentListener(listener)
                    onDispose { removeOnNewIntentListener(listener) }
                }

                CompositionLocalProvider(LocalHideAmounts provides securityState.hideAmountsInApp) {
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
                                securityState = securityState,
                                onToggleBiometric = { enabled ->
                                    SecurityPrefs.setBiometricEnabled(this@MainActivity, enabled)
                                },
                                sharedMessage = sharedMessage,
                                onSharedMessageHandled = { sharedMessage = null }
                            )
                        }
                    } else {
                        LockScreen(
                            onUnlock = {
                                if (biometricAvailable && securityState.biometricEnabled) {
                                    promptBiometric()
                                }
                            },
                            showBiometric = biometricAvailable && securityState.biometricEnabled,
                            showPin = securityState.hasPin,
                            blockedUntilMillis = securityState.pinBlockedUntilMillis,
                            onPinSubmit = { enteredPin ->
                                val result = withContext(Dispatchers.Default) {
                                    SecurityPrefs.attemptUnlock(this@MainActivity, enteredPin)
                                }
                                when (result) {
                                    PinUnlockResult.Duress -> {
                                        duressMode = true
                                        isUnlocked.value = true
                                    }
                                    PinUnlockResult.Unlocked -> {
                                        duressMode = false
                                        isUnlocked.value = true
                                    }
                                    is PinUnlockResult.Blocked,
                                    is PinUnlockResult.Incorrect -> Unit
                                }
                                result
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        lastActiveTime = System.currentTimeMillis()
        val securityState = SecurityPrefs.readState(this)
        if (securityState.lockConfigured && securityState.autoLockMinutes == 0) {
            isUnlocked.value = false
        }
    }

    override fun onDestroy() {
        SecurityPrefs.prefs(this).unregisterOnSharedPreferenceChangeListener(securityPreferenceListener)
        super.onDestroy()
    }

    private fun applyWindowSecurity() {
        if (SecurityPrefs.readState(this).lockConfigured) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            isUnlocked.value = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (isUnlocked.value && lastActiveTime > 0) {
            val securityState = SecurityPrefs.readState(this)
            val autoLockMinutes = securityState.autoLockMinutes

            if (securityState.lockConfigured && autoLockMinutes >= 0) {
                val elapsed = System.currentTimeMillis() - lastActiveTime
                val timeoutMs = autoLockMinutes * 60 * 1000L
                if (elapsed > timeoutMs) {
                    isUnlocked.value = false
                    if (securityState.biometricEnabled && biometricAvailable) {
                        promptBiometric()
                    }
                }
            }
        }
    }

    private fun promptBiometric() {
        if (!SecurityPrefs.isBiometricEnabled(this) || !biometricAvailable) return
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                SecurityPrefs.resetFailedAttempts(this@MainActivity)
                isUnlocked.value = true
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                isUnlocked.value = false
            }
        })
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("BillMinder")
            .setSubtitle("Authenticate to access your bills")
            .setNegativeButtonText("Use PIN")
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
    blockedUntilMillis: Long = 0L,
    onPinSubmit: (suspend (String) -> PinUnlockResult)? = null
) {
    var pinEntry by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var authenticating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isBlocked = blockedUntilMillis > nowMillis
    val retrySeconds = ((blockedUntilMillis - nowMillis + 999L) / 1_000L).coerceAtLeast(0L)

    LaunchedEffect(blockedUntilMillis) {
        nowMillis = System.currentTimeMillis()
        while (blockedUntilMillis > nowMillis) {
            delay(1_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

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
                    enabled = !isBlocked && !authenticating,
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
                if (pinError || isBlocked) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (isBlocked) "Try again in ${retrySeconds}s" else "Incorrect PIN",
                        color = CatRed,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            authenticating = true
                            when (onPinSubmit?.invoke(pinEntry)) {
                                PinUnlockResult.Unlocked,
                                PinUnlockResult.Duress -> pinError = false
                                is PinUnlockResult.Blocked,
                                is PinUnlockResult.Incorrect,
                                null -> pinError = true
                            }
                            pinEntry = ""
                            authenticating = false
                        }
                    },
                    enabled = pinEntry.length >= 4 && !isBlocked && !authenticating,
                    colors = ButtonDefaults.buttonColors(containerColor = CatBlue, contentColor = CatCrust)
                ) {
                    if (authenticating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = CatCrust
                        )
                    } else {
                        Text("Submit")
                    }
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
    securityState: SecurityState,
    onToggleBiometric: (Boolean) -> Unit,
    sharedMessage: String? = null,
    onSharedMessageHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val viewModel: BillViewModel = viewModel()

    var selectedTab by remember { mutableStateOf(BottomTab.HOME) }

    // A message the user shared into the app. Reading one is how the Play build proposes a bill
    // from a payment text without asking for access to the whole SMS inbox.
    var sharedCandidate by remember { mutableStateOf<SmsBillCandidate?>(null) }
    var sharedMessageRejected by remember { mutableStateOf(false) }
    LaunchedEffect(sharedMessage) {
        val message = sharedMessage ?: return@LaunchedEffect
        val candidate = SmsBillParser.parse(sender = null, body = message, today = LocalDate.now())
        if (candidate == null) sharedMessageRejected = true else sharedCandidate = candidate
        onSharedMessageHandled()
    }

    sharedCandidate?.let { candidate ->
        SharedMessageDialog(
            candidate = candidate,
            onAccept = {
                viewModel.importSmsCandidate(candidate)
                sharedCandidate = null
            },
            onDismiss = { sharedCandidate = null }
        )
    }
    if (sharedMessageRejected) {
        SharedMessageRejectedDialog(onDismiss = { sharedMessageRejected = false })
    }

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
                    biometricAvailable = biometricAvailable,
                    securityState = securityState,
                    onToggleBiometric = onToggleBiometric
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

/** The text of a message the user shared into the app, if this intent carries one. */
private fun sharedTextFrom(intent: Intent?): String? =
    intent?.takeIf { it.action == Intent.ACTION_SEND && it.type == "text/plain" }
        ?.getStringExtra(Intent.EXTRA_TEXT)
        ?.takeIf { it.isNotBlank() }

@Composable
private fun SharedMessageDialog(
    candidate: SmsBillCandidate,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CatSurface0,
        title = { Text("Add this bill?", color = CatText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${candidate.name} · ${CurrencyFormatter.format(candidate.amount, candidate.currency)}",
                    color = CatText,
                    fontWeight = FontWeight.SemiBold
                )
                Text("Due ${candidate.dueDate}", color = CatSubtext0)
                Text(candidate.preview, color = CatSubtext0, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Add bill", color = CatBlue) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now", color = CatSubtext0) } }
    )
}

@Composable
private fun SharedMessageRejectedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CatSurface0,
        title = { Text("Nothing to add", color = CatText) },
        text = {
            Text(
                "That message did not contain an amount and a due date, so there is nothing to " +
                    "propose. You can still add the bill by hand.",
                color = CatSubtext0
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = CatBlue) } }
    )
}
