package com.sysadmindoc.billminder.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.billminder.ui.theme.*
import com.sysadmindoc.billminder.viewmodel.MonthlySummary
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun SummaryCard(summary: MonthlySummary, modifier: Modifier = Modifier) {
    val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
    val progress = if (summary.totalDue > 0) (summary.totalPaid / summary.totalDue).toFloat().coerceIn(0f, 1f) else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Box {
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
                            color = when {
                                summary.allPaid -> CatGreen
                                summary.remaining > 0 -> CatText
                                else -> CatGreen
                            }
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
                                    colors = if (summary.allPaid)
                                        listOf(CatGreen, CatTeal)
                                    else
                                        listOf(CatBlue, CatGreen)
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

                // Next due bill info
                summary.nextDueBill?.let { next ->
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = CatSurface1)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val dueText = when (next.daysUntilDue) {
                            0 -> "Due today"
                            1 -> "Due tomorrow"
                            else -> "Due in ${next.daysUntilDue} days"
                        }
                        Column {
                            Text("Next Up", style = MaterialTheme.typography.labelMedium, color = CatSubtext0)
                            Text(next.bill.name, style = MaterialTheme.typography.bodyMedium, color = CatText, fontWeight = FontWeight.Medium)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(dueText, style = MaterialTheme.typography.labelMedium,
                                color = when {
                                    next.daysUntilDue <= 1 -> CatYellow
                                    next.daysUntilDue <= 3 -> CatPeach
                                    else -> CatSubtext0
                                }
                            )
                            Text("$${"%,.2f".format(next.bill.amount)}", style = MaterialTheme.typography.bodyMedium, color = CatText, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // All paid celebration
                if (summary.allPaid && summary.billCount > 0) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = CatSurface1)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "All bills paid this cycle!",
                        style = MaterialTheme.typography.titleMedium,
                        color = CatGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
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
