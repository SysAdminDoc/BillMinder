package com.sysadmindoc.billminder.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.billminder.data.BillCategory
import com.sysadmindoc.billminder.data.HolidayCalendar
import com.sysadmindoc.billminder.ui.theme.CatBlue
import com.sysadmindoc.billminder.ui.theme.CatGreen
import com.sysadmindoc.billminder.ui.theme.CatRed
import com.sysadmindoc.billminder.ui.theme.CatSubtext0
import com.sysadmindoc.billminder.ui.theme.CatSurfaceRaised
import com.sysadmindoc.billminder.ui.theme.CatText
import com.sysadmindoc.billminder.ui.theme.CatYellow
import com.sysadmindoc.billminder.ui.theme.storedBillColor
import com.sysadmindoc.billminder.ui.theme.privateAmount
import com.sysadmindoc.billminder.viewmodel.BillWithStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BillCard(
    billWithStatus: BillWithStatus,
    onTap: () -> Unit,
    onMarkPaid: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPressPaid: (() -> Unit)? = null
) {
    val bill = billWithStatus.bill
    val billColor = storedBillColor(bill.color)
    val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
    val dayFormat = SimpleDateFormat("d", Locale.getDefault())
    val dueDate = Date(billWithStatus.nextDueDate)
    val holidayNote = if (!billWithStatus.isPaidThisCycle && !billWithStatus.isOverdue) {
        HolidayCalendar.getHolidayNote(billWithStatus.nextDueDate)
    } else {
        null
    }
    val stateColor = when {
        billWithStatus.isPaidThisCycle -> CatGreen
        billWithStatus.isOverdue -> CatRed
        billWithStatus.daysUntilDue <= 1 -> CatYellow
        else -> CatBlue
    }
    val dueText = when {
        billWithStatus.isPaidThisCycle -> "Paid"
        billWithStatus.isOverdue -> "${-billWithStatus.daysUntilDue} days overdue"
        billWithStatus.daysUntilDue == 0 -> "Today"
        billWithStatus.daysUntilDue == 1 -> "Tomorrow"
        else -> monthFormat.format(dueDate) + " " + dayFormat.format(dueDate)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .background(CatSurfaceRaised)
            .combinedClickable(onClick = onTap)
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.width(44.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                monthFormat.format(dueDate).uppercase(Locale.getDefault()),
                color = stateColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                dayFormat.format(dueDate),
                color = stateColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.width(8.dp))
        IconWell(
            icon = getCategoryIcon(bill.category),
            contentDescription = bill.category.label,
            tint = billColor,
            modifier = Modifier.size(42.dp)
        )
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bill.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (billWithStatus.isPaidThisCycle) CatSubtext0 else CatText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.size(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (bill.isAutoPay) "AUTO-PAY" else bill.recurrence.label.uppercase(Locale.getDefault()),
                    color = if (bill.isAutoPay) CatBlue else CatSubtext0,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (holidayNote != null) {
                    Icon(
                        Icons.Filled.Warning,
                        holidayNote,
                        tint = CatYellow,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            Text(
                dueText,
                color = stateColor,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }

        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                privateAmount(bill.amount, bill.currency),
                color = if (billWithStatus.isPaidThisCycle) CatSubtext0 else CatText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            if (bill.isVariableAmount && bill.amountMin != null && bill.amountMax != null) {
                Text(
                    "${privateAmount(bill.amountMin, bill.currency)} to ${privateAmount(bill.amountMax, bill.currency)}",
                    color = CatSubtext0,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .combinedClickable(
                    onClick = onMarkPaid,
                    onLongClick = onLongPressPaid
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (billWithStatus.isPaidThisCycle) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                if (billWithStatus.isPaidThisCycle) "Unmark paid" else "Mark paid",
                tint = if (billWithStatus.isPaidThisCycle) CatGreen else CatBlue,
                modifier = Modifier.size(26.dp)
            )
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
