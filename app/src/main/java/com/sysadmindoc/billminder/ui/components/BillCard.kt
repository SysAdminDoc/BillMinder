package com.sysadmindoc.billminder.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.billminder.data.BillCategory
import com.sysadmindoc.billminder.ui.theme.*
import com.sysadmindoc.billminder.viewmodel.BillWithStatus
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BillCard(
    billWithStatus: BillWithStatus,
    onTap: () -> Unit,
    onMarkPaid: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bill = billWithStatus.bill
    val billColor = Color(bill.color)
    val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

    val bgColor by animateColorAsState(
        targetValue = when {
            billWithStatus.isPaidThisCycle -> CatSurface0.copy(alpha = 0.5f)
            billWithStatus.isOverdue -> CatRed.copy(alpha = 0.1f)
            billWithStatus.daysUntilDue <= 3 -> CatYellow.copy(alpha = 0.08f)
            else -> CatSurface0
        },
        label = "cardBg"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(billColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getCategoryIcon(bill.category),
                    contentDescription = null,
                    tint = billColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = bill.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (billWithStatus.isPaidThisCycle) CatSubtext0 else CatText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (bill.isAutoPay) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = CatGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "AUTO",
                                color = CatGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dueText = when {
                        billWithStatus.isPaidThisCycle -> "Paid"
                        billWithStatus.isOverdue -> "Overdue ${-billWithStatus.daysUntilDue}d"
                        billWithStatus.daysUntilDue == 0 -> "Due today"
                        billWithStatus.daysUntilDue == 1 -> "Due tomorrow"
                        else -> "Due ${dateFormat.format(Date(billWithStatus.nextDueDate))}"
                    }
                    val dueColor = when {
                        billWithStatus.isPaidThisCycle -> CatGreen
                        billWithStatus.isOverdue -> CatRed
                        billWithStatus.daysUntilDue <= 3 -> CatYellow
                        else -> CatSubtext0
                    }
                    Text(
                        text = dueText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = dueColor
                    )
                    Text(
                        text = " | ${bill.recurrence.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CatOverlay0
                    )
                }
            }

            // Amount + pay button
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$${"%,.2f".format(bill.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (billWithStatus.isPaidThisCycle) CatSubtext0 else CatText
                )
                Spacer(Modifier.height(4.dp))
                IconButton(
                    onClick = onMarkPaid,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (billWithStatus.isPaidThisCycle)
                            Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = if (billWithStatus.isPaidThisCycle) "Unmark paid" else "Mark paid",
                        tint = if (billWithStatus.isPaidThisCycle) CatGreen else CatOverlay0,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun getCategoryIcon(category: BillCategory) = when (category) {
    BillCategory.RENT -> Icons.Filled.Home
    BillCategory.UTILITIES -> Icons.Filled.Bolt
    BillCategory.INSURANCE -> Icons.Filled.Shield
    BillCategory.PHONE -> Icons.Filled.Wifi
    BillCategory.SUBSCRIPTION -> Icons.Filled.Repeat
    BillCategory.LOAN -> Icons.Filled.CreditCard
    BillCategory.MEDICAL -> Icons.Filled.LocalHospital
    BillCategory.TRANSPORTATION -> Icons.Filled.DirectionsCar
    BillCategory.GROCERIES -> Icons.Filled.ShoppingCart
    BillCategory.EDUCATION -> Icons.Filled.School
    BillCategory.ENTERTAINMENT -> Icons.Filled.Movie
    BillCategory.CHILDCARE -> Icons.Filled.ChildCare
    BillCategory.OTHER -> Icons.Filled.Receipt
}
