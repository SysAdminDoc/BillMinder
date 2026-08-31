package com.sysadmindoc.billminder.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
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
import com.sysadmindoc.billminder.data.ReceiptOcr
import com.sysadmindoc.billminder.security.EncryptedAttachment
import com.sysadmindoc.billminder.security.EncryptedAttachmentStore
import com.sysadmindoc.billminder.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MarkPaidDialog(
    bill: Bill,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, confirmationNumber: String, paidAt: Long, attachment: EncryptedAttachment?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf(bill.amount.toBigDecimal().stripTrailingZeros().toPlainString()) }
    var confirmationNumber by remember { mutableStateOf("") }
    var paidDate by remember { mutableStateOf(LocalDate.now().format(PAYMENT_DATE_FORMAT)) }
    var attachment by remember { mutableStateOf<EncryptedAttachment?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var ocrMessage by remember { mutableStateOf<String?>(null) }
    var handedOff by remember { mutableStateOf(false) }
    val currentAttachment by rememberUpdatedState(attachment)

    val attachmentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isImporting = true
                val imported = EncryptedAttachmentStore.importUri(context, uri)
                if (imported != null) {
                    attachment?.let { EncryptedAttachmentStore.delete(context, it.fileName) }
                    attachment = imported
                    ocrMessage = null
                    if (imported.mimeType.startsWith("image/") || imported.mimeType == "application/pdf") {
                        val cached = EncryptedAttachmentStore.decryptToCache(context, imported.fileName)
                        if (cached != null) {
                            val result = runCatching {
                                ReceiptOcr.extract(context, cached, imported.mimeType)
                            }.getOrNull()
                            cached.delete()
                            if (result != null) {
                                result.amount?.let { amount = String.format(Locale.US, "%.2f", it) }
                                result.date?.let { paidDate = it.format(PAYMENT_DATE_FORMAT) }
                                ocrMessage = when {
                                    result.amount != null && result.date != null -> "Amount and date found on device"
                                    result.amount != null -> "Amount found on device"
                                    result.date != null -> "Date found on device"
                                    else -> "No amount or date found; review the receipt"
                                }
                            } else {
                                ocrMessage = "Receipt text could not be read; review the fields"
                            }
                        } else {
                            ocrMessage = "Receipt could not be prepared for text reading"
                        }
                    } else {
                        ocrMessage = "OCR supports receipt images and PDFs"
                    }
                } else {
                    ocrMessage = "Receipt could not be imported"
                }
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
        shape = RoundedCornerShape(12.dp),
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
                OutlinedTextField(
                    value = paidDate,
                    onValueChange = { paidDate = it.take(10) },
                    label = { Text("Payment Date (MM/dd/yyyy)") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = CatSubtext0) },
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            isImporting -> "Encrypting and reading receipt..."
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
                ocrMessage?.let { message ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = CatMauve, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(message, color = CatSubtext0, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = amount.toDoubleOrNull() ?: bill.amount
                    val parsedDate = runCatching {
                        LocalDate.parse(paidDate.trim(), PAYMENT_DATE_FORMAT)
                    }.getOrElse { LocalDate.now() }
                    val today = LocalDate.now()
                    val paidAt = if (parsedDate == today) {
                        System.currentTimeMillis()
                    } else {
                        parsedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                    handedOff = true
                    onConfirm(parsed, confirmationNumber.trim(), paidAt, attachment)
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

private val PAYMENT_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US)
