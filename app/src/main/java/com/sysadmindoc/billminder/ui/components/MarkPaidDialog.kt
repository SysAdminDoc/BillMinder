package com.sysadmindoc.billminder.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sysadmindoc.billminder.data.Bill
import com.sysadmindoc.billminder.ui.theme.*

@Composable
fun MarkPaidDialog(
    bill: Bill,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, confirmationNumber: String) -> Unit
) {
    var amount by remember { mutableStateOf(bill.amount.toBigDecimal().stripTrailingZeros().toPlainString()) }
    var confirmationNumber by remember { mutableStateOf("") }

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
                    label = { Text("Amount Paid") },
                    singleLine = true,
                    leadingIcon = { Text("$", color = CatSubtext0) },
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = amount.toDoubleOrNull() ?: bill.amount
                    onConfirm(parsed, confirmationNumber.trim())
                },
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
