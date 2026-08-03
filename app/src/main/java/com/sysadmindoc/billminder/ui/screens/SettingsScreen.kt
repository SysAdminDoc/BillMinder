package com.sysadmindoc.billminder.ui.screens

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sysadmindoc.billminder.data.CurrencyCatalog
import com.sysadmindoc.billminder.data.CurrencyConverter
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

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            viewModel.exportCsv(it)
            Toast.makeText(context, "CSV exported", Toast.LENGTH_SHORT).show()
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
                Text("BillMinder v2.1.0", style = MaterialTheme.typography.titleMedium, color = CatText)
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
