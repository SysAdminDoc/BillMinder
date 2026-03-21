package com.sysadmindoc.billminder.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.billminder.ui.theme.*
import com.sysadmindoc.billminder.viewmodel.MonthlySummary
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SummaryCard(summary: MonthlySummary, modifier: Modifier = Modifier) {
    val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
    val progress = if (summary.totalDue > 0) (summary.totalPaid / summary.totalDue).toFloat().coerceIn(0f, 1f) else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CatBase)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = monthName,
                style = MaterialTheme.typography.labelLarge,
                color = CatSubtext0
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Remaining",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CatSubtext0
                    )
                    Text(
                        text = "$${"%,.2f".format(summary.remaining)}",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (summary.remaining > 0) CatText else CatGreen
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${summary.paidCount}/${summary.billCount} paid",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CatSubtext0
                    )
                    if (summary.overdueCount > 0) {
                        Text(
                            text = "${summary.overdueCount} overdue",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = CatRed
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CatSurface1)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(CatBlue, CatGreen)
                            )
                        )
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Paid: $${"%,.2f".format(summary.totalPaid)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = CatGreen
                )
                Text(
                    text = "Total: $${"%,.2f".format(summary.totalDue)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = CatSubtext0
                )
            }
        }
    }
}
