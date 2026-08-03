package com.sysadmindoc.billminder.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sysadmindoc.billminder.data.Bill
import com.sysadmindoc.billminder.data.CurrencyCatalog
import com.sysadmindoc.billminder.security.EncryptedAttachment
import com.sysadmindoc.billminder.security.EncryptedAttachmentStore
import com.sysadmindoc.billminder.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun MarkPaidDialog(
    bill: Bill,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, confirmationNumber: String, attachment: EncryptedAttachment?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf(bill.amount.toBigDecimal().stripTrailingZeros().toPlainString()) }
    var confirmationNumber by remember { mutableStateOf("") }
    var attachment by remember { mutableStateOf<EncryptedAttachment?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var handedOff by remember { mutableStateOf(false) }
    val currentAttachment by rememberUpdatedState(attachment)

    val attachmentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isImporting = true
                attachment?.let { EncryptedAttachmentStore.delete(context, it.fileName) }
                attachment = EncryptedAttachmentStore.importUri(context, uri)
                isImporting = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!handedOff) currentAttachment?.let { EncryptedAttachmentStore.delete(context, it.fileName) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CatBase,
        titleContentColor = CatText,
        textContentColor = CatText,
        title = { Text("Mark ${bill.name} as Paid") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { v -> if (v.matches(Regex("^\\d*\\.?\\d{0,2}$"))) amount = v },
                    label = { Text("Amount Paid (${bill.currency})") },
                    singleLine = true,
                    leadingIcon = { Text(CurrencyCatalog.find(bill.currency).symbol.trim(), color = CatSubtext0) },
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
                OutlinedTextField(
                    value = confirmationNumber,
                    onValueChange = { confirmationNumber = it },
                    label = { Text("Confirmation # (optional)") },
                    singleLine = true,
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
                OutlinedButton(
                    onClick = { attachmentPicker.launch("*/*") },
                    enabled = !isImporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            isImporting -> "Encrypting receipt..."
                            attachment != null -> "Receipt attached"
                            else -> "Attach receipt (image or PDF)"
                        }
                    )
                }
                attachment?.let { selected ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selected.displayName, color = CatSubtext0, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            EncryptedAttachmentStore.delete(context, selected.fileName)
                            attachment = null
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove receipt", tint = CatRed)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = amount.toDoubleOrNull() ?: bill.amount
                    handedOff = true
                    onConfirm(parsed, confirmationNumber.trim(), attachment)
                },
                enabled = !isImporting,
                colors = ButtonDefaults.buttonColors(containerColor = CatGreen, contentColor = CatCrust),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Paid")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CatSubtext0)
            }
        }
    )
}
