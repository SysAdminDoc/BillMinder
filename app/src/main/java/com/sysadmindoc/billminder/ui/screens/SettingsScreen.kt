package com.sysadmindoc.billminder.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sysadmindoc.billminder.data.BillCategory
import com.sysadmindoc.billminder.data.BudgetPrefs
import com.sysadmindoc.billminder.data.CsvField
import com.sysadmindoc.billminder.data.CurrencyCatalog
import com.sysadmindoc.billminder.data.CurrencyConverter
import com.sysadmindoc.billminder.data.CsvImport
import com.sysadmindoc.billminder.data.CsvImportMapping
import com.sysadmindoc.billminder.data.CsvMappingLearning
import com.sysadmindoc.billminder.data.CsvMigrationPreset
import com.sysadmindoc.billminder.data.CsvTable
import com.sysadmindoc.billminder.data.InterchangeFormat
import com.sysadmindoc.billminder.data.SmsBillCandidate
import com.sysadmindoc.billminder.DistributionFeatures
import com.sysadmindoc.billminder.notification.ReminderPrefs
import com.sysadmindoc.billminder.notification.GeofenceManager
import com.sysadmindoc.billminder.notification.GeofencePrefs
import com.sysadmindoc.billminder.security.SecurityPrefs
import com.sysadmindoc.billminder.ui.theme.*
import com.sysadmindoc.billminder.viewmodel.BillViewModel

@Composable
fun SettingsScreen(
    viewModel: BillViewModel,
    isBiometricEnabled: Boolean,
    onToggleBiometric: (Boolean) -> Unit,
    onPinConfigured: () -> Unit = {}
) {
    val context = LocalContext.current
    val displayCurrency by viewModel.displayCurrency.collectAsState()
    var showCurrencyMenu by remember { mutableStateOf(false) }
    var showFxRates by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var csvUri by remember { mutableStateOf<Uri?>(null) }
    var csvTable by remember { mutableStateOf<CsvTable?>(null) }
    var showCsvMapping by remember { mutableStateOf(false) }
    var showInterchangeDialog by remember { mutableStateOf(false) }
    var pendingInterchangeFormat by remember { mutableStateOf<InterchangeFormat?>(null) }
    var fullScreenReminders by remember { mutableStateOf(ReminderPrefs.isFullScreenEnabled(context)) }
    var vacationMode by remember { mutableStateOf(ReminderPrefs.isVacationMode(context)) }
    var showGeofenceDialog by remember { mutableStateOf(false) }
    var homeGeofenceEnabled by remember {
        mutableStateOf(DistributionFeatures.includesPlayServices && GeofencePrefs.get(context)?.enabled == true)
    }
    var smsCandidates by remember { mutableStateOf<List<SmsBillCandidate>>(emptyList()) }
    var showSmsCandidates by remember { mutableStateOf(false) }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showGeofenceDialog = true
        } else {
            Toast.makeText(context, "Background location is required for home reminders", Toast.LENGTH_LONG).show()
        }
    }
    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (!granted) {
            Toast.makeText(context, "Precise location is required for home reminders", Toast.LENGTH_LONG).show()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            showGeofenceDialog = true
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.scanSms { candidates, error ->
                if (error != null) {
                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                } else {
                    smsCandidates = candidates
                    showSmsCandidates = true
                }
            }
        } else {
            Toast.makeText(context, "SMS access was not granted", Toast.LENGTH_SHORT).show()
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            viewModel.exportJson(it)
            Toast.makeText(context, "Backup exported", Toast.LENGTH_SHORT).show()
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importJson(it) { count ->
                Toast.makeText(context, "Imported $count bills", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { selectedUri ->
            viewModel.previewCsv(selectedUri) { table, error ->
                if (table != null) {
                    csvUri = selectedUri
                    csvTable = table
                    showCsvMapping = true
                } else {
                    Toast.makeText(context, error ?: "Unable to read CSV", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            viewModel.exportCsv(it)
            Toast.makeText(context, "CSV exported", Toast.LENGTH_SHORT).show()
        }
    }

    val interchangeExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val format = pendingInterchangeFormat
        pendingInterchangeFormat = null
        if (uri != null && format != null) {
            viewModel.exportInterchange(uri, format)
            Toast.makeText(context, "${format.label} CSV exported", Toast.LENGTH_SHORT).show()
        }
    }

    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    var yearEndYear by remember { mutableIntStateOf(currentYear) }
    var showYearPicker by remember { mutableStateOf(false) }

    val exportYearEndLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            viewModel.exportYearEndCsv(it, yearEndYear)
            Toast.makeText(context, "$yearEndYear year-end report exported", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        Text("Security", style = MaterialTheme.typography.labelLarge, color = CatSubtext0)
        SettingsToggle(
            icon = Icons.Filled.Fingerprint,
            title = "Biometric Lock",
            subtitle = "Require fingerprint/face to open app",
            checked = isBiometricEnabled,
            onCheckedChange = onToggleBiometric
        )
        SettingsToggle(
            icon = Icons.Filled.Alarm,
            title = "Full-Screen Reminders",
            subtitle = "Show an alarm-style screen for due bills",
            checked = fullScreenReminders,
            onCheckedChange = { enabled ->
                fullScreenReminders = enabled
                ReminderPrefs.setFullScreenEnabled(context, enabled)
            }
        )
        SettingsToggle(
            icon = Icons.Filled.EventBusy,
            title = "Vacation Mode",
            subtitle = "Pause reminders for auto-pay bills; manual bills stay active",
            checked = vacationMode,
            onCheckedChange = { enabled ->
                vacationMode = enabled
                viewModel.setVacationMode(enabled)
            }
        )

        if (DistributionFeatures.includesPlayServices) {
            SettingsRow(
                icon = Icons.Filled.LocationOn,
                title = "Home Geofence",
                subtitle = if (homeGeofenceEnabled) {
                    GeofencePrefs.get(context)?.let { "Active · ${it.radiusMeters.toInt()}m radius" } ?: "Active"
                } else {
                    "Remind me when I arrive home"
                }
            ) {
                val hasForeground = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val hasBackground = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (hasForeground && hasBackground) {
                    showGeofenceDialog = true
                } else {
                    foregroundLocationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }

            if (showGeofenceDialog) {
                HomeGeofenceDialog(
                    config = GeofencePrefs.get(context),
                    onSave = { latitude, longitude, radius ->
                        GeofenceManager.register(context, latitude, longitude, radius) { success, error ->
                            if (success) {
                                GeofencePrefs.save(context, latitude, longitude, radius)
                                homeGeofenceEnabled = true
                                showGeofenceDialog = false
                                Toast.makeText(context, "Home geofence enabled", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, error ?: "Unable to enable home geofence", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onDisable = {
                        GeofenceManager.unregister(context) {
                            GeofencePrefs.setEnabled(context, false)
                            homeGeofenceEnabled = false
                            showGeofenceDialog = false
                            Toast.makeText(context, "Home geofence disabled", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDismiss = { showGeofenceDialog = false }
                )
            }
        }

        // PIN fallback
        var hasPinSet by remember { mutableStateOf(SecurityPrefs.hasPin(context)) }
        var hasDuressPin by remember { mutableStateOf(SecurityPrefs.hasDuressPin(context)) }
        var showPinSetup by remember { mutableStateOf(false) }
        var showDuressPinSetup by remember { mutableStateOf(false) }
        var showAutoLockMenu by remember { mutableStateOf(false) }
        var autoLockMinutes by remember { mutableIntStateOf(SecurityPrefs.getAutoLockMinutes(context)) }
        val autoLockLabel = when (autoLockMinutes) {
            0 -> "Immediately"
            1 -> "1 minute"
            5 -> "5 minutes"
            15 -> "15 minutes"
            30 -> "30 minutes"
            else -> "$autoLockMinutes minutes"
        }

        SettingsRow(
            icon = Icons.Filled.Pin,
            title = if (hasPinSet) "Change PIN" else "Set PIN Fallback",
            subtitle = if (hasPinSet) "PIN is set. Tap to change." else "Set a PIN as backup for biometric"
        ) {
            showPinSetup = true
        }

        if (hasPinSet) {
            SettingsRow(
                icon = Icons.Filled.VisibilityOff,
                title = if (hasDuressPin) "Change Duress PIN" else "Set Duress PIN",
                subtitle = if (hasDuressPin) {
                    "Opens an empty decoy view when entered"
                } else {
                    "Optional emergency PIN that hides your bills"
                }
            ) {
                showDuressPinSetup = true
            }
        }

        if (hasPinSet || isBiometricEnabled) {
            Box {
                SettingsRow(
                    icon = Icons.Filled.Timer,
                    title = "Auto-Lock Timeout",
                    subtitle = "Lock after: $autoLockLabel"
                ) {
                    showAutoLockMenu = true
                }
                DropdownMenu(
                    expanded = showAutoLockMenu,
                    onDismissRequest = { showAutoLockMenu = false },
                    containerColor = CatSurface0
                ) {
                    listOf(0, 1, 5, 15, 30).forEach { minutes ->
                        val label = when (minutes) {
                            0 -> "Immediately"
                            1 -> "1 minute"
                            else -> "$minutes minutes"
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    label,
                                    color = if (minutes == autoLockMinutes) CatBlue else CatText
                                )
                            },
                            onClick = {
                                autoLockMinutes = minutes
                                SecurityPrefs.setAutoLockMinutes(context, minutes)
                                showAutoLockMenu = false
                            }
                        )
                    }
                }
            }
        }

        if (showPinSetup) {
            PinSetupDialog(
                onDismiss = { showPinSetup = false },
                onPinSet = { pin ->
                    SecurityPrefs.setPin(context, pin)
                    hasPinSet = true
                    onPinConfigured()
                    showPinSetup = false
                    Toast.makeText(context, "PIN set successfully", Toast.LENGTH_SHORT).show()
                    true
                }
            )
        }

        if (showDuressPinSetup) {
            PinSetupDialog(
                title = "Set Duress PIN",
                onDismiss = { showDuressPinSetup = false },
                onPinSet = { pin ->
                    val saved = SecurityPrefs.setDuressPin(context, pin)
                    if (saved) {
                        hasDuressPin = true
                        showDuressPinSetup = false
                        Toast.makeText(context, "Duress PIN set successfully", Toast.LENGTH_SHORT).show()
                    }
                    saved
                }
            )
        }

        Spacer(Modifier.height(12.dp))
        Text("Currency", style = MaterialTheme.typography.labelLarge, color = CatSubtext0)

        Box {
            SettingsRow(
                icon = Icons.Filled.AttachMoney,
                title = "Display Currency",
                subtitle = "Dashboard totals: ${displayCurrency} - ${CurrencyCatalog.find(displayCurrency).name}"
            ) {
                showCurrencyMenu = true
            }
            DropdownMenu(
                expanded = showCurrencyMenu,
                onDismissRequest = { showCurrencyMenu = false },
                containerColor = CatSurface0
            ) {
                CurrencyCatalog.supported.forEach { info ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "${info.code} - ${info.name}",
                                color = if (info.code == displayCurrency) CatBlue else CatText
                            )
                        },
                        onClick = {
                            viewModel.setDisplayCurrency(info.code)
                            showCurrencyMenu = false
                        }
                    )
                }
            }
        }

        SettingsRow(
            icon = Icons.Filled.Tune,
            title = "Offline FX Rates",
            subtitle = "Bundled snapshot; set manual overrides when needed"
        ) {
            showFxRates = true
        }

        if (showFxRates) {
            FxRatesDialog(
                manualRates = viewModel.getManualRates(),
                onSaveRate = viewModel::setManualRate,
                onDismiss = { showFxRates = false }
            )
        }

        SettingsRow(
            icon = Icons.Filled.PieChart,
            title = "Category Budgets",
            subtitle = "${BudgetPrefs.getAll(context).size} monthly caps configured in $displayCurrency"
        ) {
            showBudgetDialog = true
        }

        if (showBudgetDialog) {
            val existingBudgets = BudgetPrefs.getAll(context)
            CategoryBudgetsDialog(
                displayCurrency = displayCurrency,
                initialValues = existingBudgets.mapValues { (_, budget) ->
                    "%.2f".format(viewModel.convertToDisplay(budget.amount, budget.currency))
                },
                onSave = { values ->
                    BillCategory.entries.forEach { category ->
                        BudgetPrefs.setBudget(
                            context,
                            category,
                            values[category]?.toDoubleOrNull(),
                            displayCurrency
                        )
                    }
                    showBudgetDialog = false
                    Toast.makeText(context, "Category budgets saved", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showBudgetDialog = false }
            )
        }

        Spacer(Modifier.height(12.dp))
        Text("Data", style = MaterialTheme.typography.labelLarge, color = CatSubtext0)

        SettingsRow(
            icon = Icons.Filled.Upload,
            title = "Export Backup (JSON)",
            subtitle = "Save all bills and payments"
        ) {
            exportJsonLauncher.launch("billminder_backup.json")
        }

        SettingsRow(
            icon = Icons.Filled.Download,
            title = "Import Backup (JSON)",
            subtitle = "Restore from a previous backup"
        ) {
            importJsonLauncher.launch(arrayOf("application/json"))
        }

        SettingsRow(
            icon = Icons.Filled.TableChart,
            title = "Export CSV",
            subtitle = "Export payment history as spreadsheet"
        ) {
            exportCsvLauncher.launch("billminder_payments.csv")
        }

        SettingsRow(
            icon = Icons.Filled.SwapHoriz,
            title = "Export for Another App",
            subtitle = "Bluecoins, YNAB, or Actual Budget CSV"
        ) {
            showInterchangeDialog = true
        }

        if (showInterchangeDialog) {
            InterchangeExportDialog(
                onExport = { format ->
                    pendingInterchangeFormat = format
                    showInterchangeDialog = false
                    interchangeExportLauncher.launch(format.fileName)
                },
                onDismiss = { showInterchangeDialog = false }
            )
        }

        SettingsRow(
            icon = Icons.Filled.FileUpload,
            title = "Import CSV",
            subtitle = "Map columns or migrate Mint, Tiller, and Empower exports"
        ) {
            importCsvLauncher.launch(arrayOf("text/csv", "text/plain", "application/vnd.ms-excel"))
        }

        SettingsRow(
            icon = Icons.Filled.Sms,
            title = "Scan Payment SMS",
            subtitle = "Opt-in local scan; propose bills from recent messages"
        ) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                viewModel.scanSms { candidates, error ->
                    if (error != null) {
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    } else {
                        smsCandidates = candidates
                        showSmsCandidates = true
                    }
                }
            } else {
                smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
            }
        }

        if (showSmsCandidates) {
            SmsCandidatesDialog(
                candidates = smsCandidates,
                onAccept = { candidate ->
                    viewModel.importSmsCandidate(candidate)
                    smsCandidates = smsCandidates - candidate
                },
                onDismiss = { showSmsCandidates = false }
            )
        }

        if (showCsvMapping && csvTable != null && csvUri != null) {
            CsvMappingDialog(
                table = csvTable!!,
                onImport = { mapping ->
                    CsvMappingLearning.recordCorrections(
                        context = context,
                        headers = csvTable!!.headers,
                        baseline = CsvMigrationPreset.detect(csvTable!!.headers)
                            .mapping(csvTable!!.headers).columns,
                        selected = mapping.columns
                    )
                    viewModel.importCsv(csvUri!!, mapping) { result, error ->
                        if (result != null) {
                            showCsvMapping = false
                            csvUri = null
                            csvTable = null
                            Toast.makeText(
                                context,
                                "Imported ${result.billsImported} bills and ${result.paymentsImported} payments" +
                                    if (result.rowsSkipped > 0) " (${result.rowsSkipped} rows skipped)" else "",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(context, error ?: "Unable to import CSV", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onDismiss = {
                    showCsvMapping = false
                    csvUri = null
                    csvTable = null
                }
            )
        }

        SettingsRow(
            icon = Icons.Filled.Summarize,
            title = "Year-End Report ($yearEndYear)",
            subtitle = "Tax-ready CSV grouped by category"
        ) {
            showYearPicker = true
        }

        if (showYearPicker) {
            AlertDialog(
                onDismissRequest = { showYearPicker = false },
                containerColor = CatSurface0,
                title = { Text("Select Year", color = CatText) },
                text = {
                    Column {
                        (currentYear downTo (currentYear - 5)).forEach { year ->
                            TextButton(
                                onClick = {
                                    yearEndYear = year
                                    showYearPicker = false
                                    exportYearEndLauncher.launch("billminder_${year}_year_end.csv")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    year.toString(),
                                    color = if (year == yearEndYear) CatBlue else CatText,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showYearPicker = false }) {
                        Text("Cancel", color = CatSubtext0)
                    }
                }
            )
        }

        Spacer(Modifier.height(12.dp))
        Text("About", style = MaterialTheme.typography.labelLarge, color = CatSubtext0)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CatSurface0)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("BillMinder v2.2.0", style = MaterialTheme.typography.titleMedium, color = CatText)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Never miss a payment. Track bills, get reminders, visualize spending.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CatSubtext0
                )
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CatSurface0)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = CatBlue, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = CatText)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CatSubtext0)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = CatOverlay0)
        }
    }
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CatSurface0)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = CatBlue, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = CatText)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CatSubtext0)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CatCrust,
                    checkedTrackColor = CatBlue,
                    uncheckedThumbColor = CatOverlay0,
                    uncheckedTrackColor = CatSurface1
                )
            )
        }
    }
}

@Composable
private fun PinSetupDialog(
    title: String = "Set PIN",
    onDismiss: () -> Unit,
    onPinSet: (String) -> Boolean
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(1) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CatSurface0,
        title = {
            Text(
                if (step == 1) title else "Confirm PIN",
                color = CatText
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (step == 1) "Choose a 4-6 digit PIN" else "Re-enter your PIN to confirm",
                    color = CatSubtext0,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = if (step == 1) pin else confirmPin,
                    onValueChange = { v ->
                        val filtered = v.filter { it.isDigit() }.take(6)
                        if (step == 1) pin = filtered else confirmPin = filtered
                        error = null
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CatText,
                        unfocusedTextColor = CatText,
                        focusedBorderColor = CatBlue,
                        unfocusedBorderColor = CatSurface1,
                        cursorColor = CatBlue
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("****", color = CatOverlay0, textAlign = TextAlign.Center) }
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = CatRed, style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (step == 1) {
                        if (pin.length < 4) {
                            error = "PIN must be at least 4 digits"
                        } else {
                            step = 2
                        }
                    } else {
                        if (confirmPin != pin) {
                            error = "PINs don't match"
                            confirmPin = ""
                        } else if (!onPinSet(pin)) {
                            error = "Duress PIN must differ from your regular PIN"
                            confirmPin = ""
                        }
                    }
                }
            ) {
                Text(if (step == 1) "Next" else "Set PIN", color = CatBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CatSubtext0)
            }
        }
    )
}

@Composable
private fun FxRatesDialog(
    manualRates: Map<String, Double>,
    onSaveRate: (String, Double?) -> Unit,
    onDismiss: () -> Unit
) {
    val drafts = remember(manualRates) {
        mutableStateMapOf<String, String>().apply {
            manualRates.forEach { (code, rate) -> put(code, rate.toString()) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CatSurface0,
        title = { Text("Offline FX Rates", color = CatText) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Rates are units of currency per 1 USD. Leave a field blank to use the bundled snapshot.",
                    color = CatSubtext0,
                    style = MaterialTheme.typography.bodySmall
                )
                CurrencyCatalog.supported.filter { it.code != "USD" }.forEach { info ->
                    OutlinedTextField(
                        value = drafts[info.code] ?: "",
                        onValueChange = { value ->
                            if (value.matches(Regex("^\\d*(\\.\\d{0,6})?$"))) drafts[info.code] = value
                        },
                        label = { Text("1 USD = ${info.code}") },
                        supportingText = {
                            Text("Bundled: ${CurrencyConverter.bundledUsdRates[info.code]}", color = CatOverlay0)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = CatText,
                            unfocusedTextColor = CatText,
                            focusedBorderColor = CatBlue,
                            unfocusedBorderColor = CatSurface1,
                            focusedLabelColor = CatBlue,
                            unfocusedLabelColor = CatSubtext0,
                            cursorColor = CatBlue
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    CurrencyCatalog.supported.filter { it.code != "USD" }.forEach { info ->
                        onSaveRate(info.code, drafts[info.code]?.toDoubleOrNull())
                    }
                    onDismiss()
                }
            ) {
                Text("Save", color = CatBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CatSubtext0)
            }
        }
    )
}

@Composable
private fun HomeGeofenceDialog(
    config: com.sysadmindoc.billminder.notification.HomeGeofenceConfig?,
    onSave: (Double, Double, Float) -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit
) {
    var latitude by remember { mutableStateOf(config?.latitude?.toString() ?: "") }
    var longitude by remember { mutableStateOf(config?.longitude?.toString() ?: "") }
    var radius by remember { mutableStateOf(config?.radiusMeters?.toString() ?: "150") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CatSurface0,
        title = { Text("Home Geofence", color = CatText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Enter the center point for home. The reminder fires when you enter and stay for five minutes.",
                    color = CatSubtext0,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = latitude,
                    onValueChange = { latitude = it },
                    label = { Text("Latitude (-90 to 90)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = settingsFieldColors()
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { longitude = it },
                    label = { Text("Longitude (-180 to 180)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = settingsFieldColors()
                )
                OutlinedTextField(
                    value = radius,
                    onValueChange = { radius = it.filter { char -> char.isDigit() } },
                    label = { Text("Radius in meters (50 to 1000)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = settingsFieldColors()
                )
                error?.let { Text(it, color = CatRed, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val lat = latitude.toDoubleOrNull()
                    val lon = longitude.toDoubleOrNull()
                    val meters = radius.toFloatOrNull()
                    when {
                        lat == null || lat !in -90.0..90.0 -> error = "Enter a valid latitude"
                        lon == null || lon !in -180.0..180.0 -> error = "Enter a valid longitude"
                        meters == null || meters !in 50f..1000f -> error = "Radius must be between 50 and 1000 meters"
                        else -> onSave(lat, lon, meters)
                    }
                }
            ) {
                Text("Enable", color = CatBlue)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (config?.enabled == true) {
                    TextButton(onClick = onDisable) {
                        Text("Disable", color = CatRed)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = CatSubtext0)
                }
            }
        }
    )
}

@Composable
private fun CategoryBudgetsDialog(
    displayCurrency: String,
    initialValues: Map<BillCategory, String>,
    onSave: (Map<BillCategory, String>) -> Unit,
    onDismiss: () -> Unit
) {
    val values = remember {
        mutableStateMapOf<BillCategory, String>().apply {
            BillCategory.entries.forEach { category ->
                put(category, initialValues[category] ?: "")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CatSurface0,
        title = { Text("Category Budgets", color = CatText) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Set a monthly cap for each category. Leave a field blank to remove its cap.",
                    color = CatSubtext0,
                    style = MaterialTheme.typography.bodySmall
                )
                BillCategory.entries.forEach { category ->
                    OutlinedTextField(
                        value = values[category].orEmpty(),
                        onValueChange = { input ->
                            values[category] = input.filter { it.isDigit() || it == '.' }
                        },
                        label = { Text(category.label) },
                        suffix = { Text(displayCurrency, color = CatSubtext0) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        colors = settingsFieldColors()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(values.toMap()) }) {
                Text("Save", color = CatBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CatSubtext0)
            }
        }
    )
}

@Composable
private fun InterchangeExportDialog(
    onExport: (InterchangeFormat) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(InterchangeFormat.BLUECOINS) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CatSurface0,
        title = { Text("Export for Another App", color = CatText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "YNAB and Actual amounts use the selected display currency. Bluecoins keeps each payment's native currency.",
                    color = CatSubtext0,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(6.dp))
                InterchangeFormat.entries.forEach { format ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = format },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == format,
                            onClick = { selected = format },
                            colors = RadioButtonDefaults.colors(selectedColor = CatBlue)
                        )
                        Column {
                            Text(format.label, color = CatText, fontWeight = FontWeight.Medium)
                            Text(format.description, color = CatSubtext0, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onExport(selected) }) {
                Text("Export", color = CatBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CatSubtext0)
            }
        }
    )
}

@Composable
private fun CsvMappingDialog(
    table: CsvTable,
    onImport: (CsvImportMapping) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val fields = listOf(
        CsvField.NAME,
        CsvField.AMOUNT,
        CsvField.DUE_DATE,
        CsvField.DUE_DAY,
        CsvField.CATEGORY,
        CsvField.RECURRENCE,
        CsvField.AUTO_PAY,
        CsvField.CURRENCY,
        CsvField.NOTES,
        CsvField.PAYMENT_DATE,
        CsvField.PAYMENT_AMOUNT,
        CsvField.PAYMENT_CURRENCY,
        CsvField.CONFIRMATION
    )
    val initialPreset = remember { CsvMigrationPreset.detect(table.headers) }
    var selectedPreset by remember { mutableStateOf(initialPreset) }
    val baseline = remember { initialPreset.mapping(table.headers).columns }
    val learnedMapping = remember {
        CsvMappingLearning.suggestedMapping(context, table.headers, baseline)
    }
    val learnedCount = remember {
        CsvMappingLearning.learnedCount(context, table.headers)
    }
    val selected = remember {
        mutableStateMapOf<CsvField, Int?>().apply {
            putAll(learnedMapping)
        }
    }
    var expandedField by remember { mutableStateOf<CsvField?>(null) }
    var showPresetMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CatSurface0,
        title = { Text("Map CSV Columns", color = CatText) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Source preset", color = CatSubtext0, style = MaterialTheme.typography.labelMedium)
                Box {
                    TextButton(onClick = { showPresetMenu = true }) {
                        Text(selectedPreset.label, color = CatBlue)
                    }
                    DropdownMenu(
                        expanded = showPresetMenu,
                        onDismissRequest = { showPresetMenu = false },
                        containerColor = CatSurface0
                    ) {
                        CsvMigrationPreset.entries.forEach { preset ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        preset.label,
                                        color = if (preset == selectedPreset) CatBlue else CatText
                                    )
                                },
                                onClick = {
                                    selectedPreset = preset
                                    selected.clear()
                                    val presetMapping = preset.mapping(table.headers).columns
                                    selected.putAll(
                                        if (preset == CsvMigrationPreset.AUTO_DETECT) {
                                            CsvMappingLearning.suggestedMapping(context, table.headers, presetMapping)
                                        } else {
                                            presetMapping
                                        }
                                    )
                                    error = null
                                    showPresetMenu = false
                                }
                            )
                        }
                    }
                }
                Text(selectedPreset.description, color = CatSubtext0, style = MaterialTheme.typography.bodySmall)
                Text(
                    if (learnedCount > 0) {
                        "$learnedCount learned column mapping${if (learnedCount == 1) "" else "s"} active; new corrections apply after 3 confirmations"
                    } else {
                        "Correct a column three times to have it remembered for future imports"
                    },
                    color = CatMauve,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "${table.rows.size} rows detected. Required fields are marked with *.",
                    color = CatSubtext0,
                    style = MaterialTheme.typography.bodySmall
                )
                fields.forEach { field ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            field.label + if (field.required) " *" else "",
                            color = if (field.required) CatText else CatSubtext1,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(0.9f)
                        )
                        Box(modifier = Modifier.weight(1.1f)) {
                            TextButton(onClick = { expandedField = field }) {
                                Text(
                                    selected[field]?.let { table.headers.getOrNull(it) } ?: "Skip",
                                    color = CatBlue,
                                    maxLines = 1
                                )
                            }
                            DropdownMenu(
                                expanded = expandedField == field,
                                onDismissRequest = { expandedField = null },
                                containerColor = CatSurface0
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Skip", color = CatSubtext0) },
                                    onClick = {
                                        selected[field] = null
                                        expandedField = null
                                    }
                                )
                                table.headers.forEachIndexed { index, header ->
                                    DropdownMenuItem(
                                        text = { Text(header, color = CatText) },
                                        onClick = {
                                            selected[field] = index
                                            expandedField = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                error?.let { Text(it, color = CatRed, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val mapping = CsvImportMapping(selected.toMap())
                    val preset = selectedPreset.mapping(table.headers)
                    if (mapping.isReady()) {
                        error = null
                        onImport(
                            mapping.copy(
                                defaultRecurrence = preset.defaultRecurrence,
                                absoluteAmounts = preset.absoluteAmounts
                            )
                        )
                    } else {
                        error = "Map Bill name, Bill amount, and Due date or Due day before importing."
                    }
                }
            ) {
                Text("Import", color = CatBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CatSubtext0)
            }
        }
    )
}

@Composable
private fun SmsCandidatesDialog(
    candidates: List<SmsBillCandidate>,
    onAccept: (SmsBillCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CatSurface0,
        title = { Text("SMS bill proposals", color = CatText) },
        text = {
            if (candidates.isEmpty()) {
                Text("No bill-shaped messages were found in the recent inbox.", color = CatSubtext0)
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Review each proposal before it is added. Message text stays on this device.",
                        color = CatSubtext0,
                        style = MaterialTheme.typography.bodySmall
                    )
                    candidates.forEach { candidate ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CatSurface1),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(candidate.name, color = CatText, fontWeight = FontWeight.Bold)
                                Text(
                                    "${candidate.amount} ${candidate.currency} · due ${candidate.dueDate}",
                                    color = CatBlue,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(candidate.preview, color = CatSubtext0, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                                TextButton(onClick = { onAccept(candidate) }) {
                                    Text("Add one-time bill", color = CatGreen)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = CatBlue) }
        }
    )
}

@Composable
private fun settingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = CatText,
    unfocusedTextColor = CatText,
    focusedBorderColor = CatBlue,
    unfocusedBorderColor = CatSurface1,
    focusedLabelColor = CatBlue,
    unfocusedLabelColor = CatSubtext0,
    cursorColor = CatBlue
)
