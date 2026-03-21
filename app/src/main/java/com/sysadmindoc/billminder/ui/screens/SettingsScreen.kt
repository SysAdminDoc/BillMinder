package com.sysadmindoc.billminder.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sysadmindoc.billminder.ui.theme.*
import com.sysadmindoc.billminder.viewmodel.BillViewModel

@Composable
fun SettingsScreen(
    viewModel: BillViewModel,
    isBiometricEnabled: Boolean,
    onToggleBiometric: (Boolean) -> Unit
) {
    val context = LocalContext.current

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

        Spacer(Modifier.height(12.dp))
        Text("About", style = MaterialTheme.typography.labelLarge, color = CatSubtext0)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CatSurface0)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("BillMinder v2.0.0", style = MaterialTheme.typography.titleMedium, color = CatText)
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
