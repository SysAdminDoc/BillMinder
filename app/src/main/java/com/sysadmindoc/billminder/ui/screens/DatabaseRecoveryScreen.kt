package com.sysadmindoc.billminder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sysadmindoc.billminder.ui.components.GroupedSurface
import com.sysadmindoc.billminder.ui.components.SectionHeading
import com.sysadmindoc.billminder.ui.theme.CatCrust
import com.sysadmindoc.billminder.ui.theme.CatSubtext0
import com.sysadmindoc.billminder.ui.theme.CatText
import com.sysadmindoc.billminder.ui.theme.CatYellow

/**
 * Shown when the saved database cannot be opened with this build. Nothing is deleted: the file is
 * still on disk, so installing a matching version of the app brings the data back.
 */
@Composable
fun DatabaseRecoveryScreen(reason: String, databasePath: String) {
    Scaffold(containerColor = CatCrust) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.Warning, null, tint = CatYellow)
            Text(
                "BillMinder can't open your data",
                style = MaterialTheme.typography.titleLarge,
                color = CatText
            )
            Text(
                "Your bills and payments have not been touched. This usually means the app was " +
                    "downgraded, so the saved data is newer than this version understands. " +
                    "Install the latest version of BillMinder and open it again.",
                style = MaterialTheme.typography.bodyMedium,
                color = CatSubtext0
            )
            SectionHeading("Details")
            GroupedSurface {
                Text(reason, style = MaterialTheme.typography.bodySmall, color = CatText)
                Text(
                    databasePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = CatSubtext0,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
