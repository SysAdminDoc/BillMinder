package com.sysadmindoc.billminder.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.billminder.data.CurrencyFormatter
import com.sysadmindoc.billminder.ui.theme.CatBlue
import com.sysadmindoc.billminder.ui.theme.CatDivider
import com.sysadmindoc.billminder.ui.theme.CatGreen
import com.sysadmindoc.billminder.ui.theme.CatRed
import com.sysadmindoc.billminder.ui.theme.CatSubtext0
import com.sysadmindoc.billminder.ui.theme.CatSurface1
import com.sysadmindoc.billminder.ui.theme.CatSurfaceRaised
import com.sysadmindoc.billminder.ui.theme.CatText
import com.sysadmindoc.billminder.ui.theme.CatYellow
import com.sysadmindoc.billminder.ui.theme.storedBillColor
import com.sysadmindoc.billminder.viewmodel.BillWithStatus
import com.sysadmindoc.billminder.viewmodel.MonthlySummary

@Composable
fun SummaryCard(
    summary: MonthlySummary,
    onMarkNextPaid: (BillWithStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (summary.totalDue > 0) {
        (summary.totalPaid / summary.totalDue).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "monthly progress"
    )
    val usesLargeText = LocalDensity.current.fontScale >= 1.2f

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = CatSurfaceRaised,
        border = BorderStroke(1.dp, CatDivider)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Total due", style = MaterialTheme.typography.bodyMedium, color = CatSubtext0)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = CurrencyFormatter.format(summary.remaining, summary.currency),
                        fontSize = if (usesLargeText) 31.sp else 38.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.9).sp,
                        color = if (summary.allPaid) CatGreen else CatText,
                        maxLines = 1
                    )
                    Text(
                        text = "${summary.paidCount} of ${summary.billCount} paid",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CatSubtext0
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (summary.overdueCount > 0) {
                        Text(
                            text = "${summary.overdueCount} overdue",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = CatRed
                        )
                    }
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CatSubtext0
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(CatSurface1)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .background(if (summary.allPaid) CatGreen else CatBlue)
                )
            }

            summary.nextDueBill?.let { next ->
                HorizontalDivider(color = CatDivider)
                val dueText = when (next.daysUntilDue) {
                    0 -> "Today"
                    1 -> "Tomorrow"
                    else -> "In ${next.daysUntilDue} days"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 76.dp)
                        .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconWell(
                        icon = getCategoryIcon(next.bill.category),
                        contentDescription = null,
                        tint = storedBillColor(next.bill.color)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Next bill", style = MaterialTheme.typography.labelMedium, color = CatSubtext0)
                        Text(
                            text = next.bill.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = CatText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = dueText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (next.daysUntilDue <= 1) CatYellow else CatBlue
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = CurrencyFormatter.format(next.bill.amount, next.bill.currency),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = CatText
                        )
                        IconButton(onClick = { onMarkNextPaid(next) }, modifier = Modifier.size(42.dp)) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Mark ${next.bill.name} paid",
                                tint = CatBlue,
                                modifier = Modifier.size(25.dp)
                            )
                        }
                    }
                }
            }

            if (summary.allPaid && summary.billCount > 0) {
                HorizontalDivider(color = CatDivider)
                Text(
                    text = "All bills paid this cycle",
                    style = MaterialTheme.typography.titleMedium,
                    color = CatGreen,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            }
        }
    }
}
