package com.sysadmindoc.billminder.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.billminder.data.CurrencyFormatter
import com.sysadmindoc.billminder.ui.theme.*
import com.sysadmindoc.billminder.viewmodel.BillWithStatus
import com.sysadmindoc.billminder.viewmodel.MonthlySummary
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun SummaryCard(
    summary: MonthlySummary,
    onMarkNextPaid: (BillWithStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    val monthName = SimpleDateFormat("MMMM 'overview'", Locale.getDefault()).format(Date())
    val progress = if (summary.totalDue > 0) (summary.totalPaid / summary.totalDue).toFloat().coerceIn(0f, 1f) else 0f
    val usesLargeText = LocalDensity.current.fontScale >= 1.2f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Box {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = CatSurfaceRaised,
            border = BorderStroke(1.dp, CatDivider)
        ) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = monthName,
                    style = MaterialTheme.typography.labelLarge,
                    color = CatSubtext0,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = CurrencyFormatter.format(summary.remaining, summary.currency),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.8).sp,
                            color = when {
                                summary.allPaid -> CatGreen
                                summary.remaining > 0 -> CatText
                                else -> CatGreen
                            }
                        )
                        Text(
                            text = "remaining",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CatSubtext0
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${summary.paidCount} of ${summary.billCount} paid",
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

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
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
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = CatSubtext0
                    )
                }

                summary.nextDueBill?.let { next ->
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = CatDivider)
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dueText = when (next.daysUntilDue) {
                            0 -> "Today"
                            1 -> "Tomorrow"
                            else -> "In ${next.daysUntilDue} days"
                        }
                        Icon(Icons.Filled.CalendarMonth, null, tint = CatBlue, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        if (usesLargeText) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Next · ${next.bill.name}",
                                    color = CatText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = CurrencyFormatter.format(next.bill.amount, next.bill.currency),
                                    color = CatSubtext0,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    dueText,
                                    color = if (next.daysUntilDue <= 1) CatYellow else CatSubtext0,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                IconButton(onClick = { onMarkNextPaid(next) }) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        "Mark ${next.bill.name} paid",
                                        tint = CatBlue,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        } else {
                            Text("Next · ", color = CatSubtext0, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                next.bill.name,
                                color = CatText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                                " · ${CurrencyFormatter.format(next.bill.amount, next.bill.currency)}",
                                color = CatSubtext0,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                dueText,
                                color = if (next.daysUntilDue <= 1) CatYellow else CatSubtext0,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(onClick = { onMarkNextPaid(next) }) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    "Mark ${next.bill.name} paid",
                                    tint = CatBlue,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }
                }

                if (summary.allPaid && summary.billCount > 0) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = CatDivider)
                    Text(
                        "All bills paid this cycle!",
                        style = MaterialTheme.typography.titleMedium,
                        color = CatGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                } else if (summary.nextDueBill == null) {
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        // Confetti overlay when all paid
        if (summary.allPaid && summary.billCount > 0) {
            ConfettiOverlay(modifier = Modifier.matchParentSize())
        }
    }
}

@Composable
private fun ConfettiOverlay(modifier: Modifier = Modifier) {
    val particles = remember {
        List(30) {
            ConfettiParticle(
                x = Random.nextFloat(),
                startY = -Random.nextFloat() * 0.3f,
                speed = 0.3f + Random.nextFloat() * 0.7f,
                size = 3f + Random.nextFloat() * 5f,
                color = listOf(CatGreen, CatBlue, CatMauve, CatYellow, CatPeach, CatTeal).random(),
                wobble = Random.nextFloat() * 6.28f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "confetti")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "confettiTime"
    )

    Canvas(modifier = modifier) {
        particles.forEach { p ->
            val y = (p.startY + time * p.speed) % 1.1f
            val x = p.x + sin((time * 6.28f + p.wobble).toDouble()).toFloat() * 0.03f
            if (y in 0f..1f) {
                drawCircle(
                    color = p.color.copy(alpha = (1f - y).coerceIn(0f, 0.7f)),
                    radius = p.size,
                    center = Offset(x * size.width, y * size.height)
                )
            }
        }
    }
}

private data class ConfettiParticle(
    val x: Float,
    val startY: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val wobble: Float
)
