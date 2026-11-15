package com.example.presentation.screens.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.domain.model.AnnouncementStatus
import com.example.presentation.screens.history.HistoryViewModel
import android.widget.Toast
import com.example.ui.components.AnimatedRupeeText
import com.example.ui.components.Floating3DObjectBox
import com.example.ui.components.PdfStatementPreviewDialog
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.ThreeDActionButton
import com.example.ui.components.ThreeDTactileCard
import com.example.ui.theme.AppSemanticColors
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.BrandEmeraldDark
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIsDarkTheme
import com.example.util.PdfStatementExporter
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DailyTotal(
    val dayLabel: String,
    val fullDateLabel: String,
    val dateStr: String,
    val amount: Double,
    val count: Int
)

data class AppShare(
    val appName: String,
    val count: Int,
    val amount: Double,
    val color: Color
)

@Composable
fun AnalyticsScreen(
    viewModel: HistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    var isExportingPdf by remember { mutableStateOf(false) }
    var previewPdfFile by remember { mutableStateOf<File?>(null) }

    val announcedTxs = remember(transactions) {
        transactions.filter { it.status == AnnouncementStatus.ANNOUNCED.name }
    }

    // 7 Days Trend
    val last7Days = remember(announcedTxs) {
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val fullDateFormat = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
        val keyFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

        val list = mutableListOf<DailyTotal>()
        for (i in 6 downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dayKey = keyFormat.format(cal.time)
            val label = dayFormat.format(cal.time)
            val fullDate = fullDateFormat.format(cal.time)
            val dayTxs = announcedTxs.filter { keyFormat.format(Date(it.timestamp)) == dayKey }
            val total = dayTxs.sumOf { it.amount }
            list.add(DailyTotal(label, fullDate, dayKey, total, dayTxs.size))
        }
        list
    }

    var selectedDayIndex by remember(last7Days) { mutableIntStateOf(last7Days.lastIndex.coerceAtLeast(0)) }

    val maxDayAmount = remember(last7Days) {
        last7Days.maxOfOrNull { it.amount }?.coerceAtLeast(100.0) ?: 100.0
    }

    val weekRevenue = remember(last7Days) { last7Days.sumOf { it.amount } }
    val weekCount = remember(last7Days) { last7Days.sumOf { it.count } }

    // Payment App Share
    val appShares = remember(announcedTxs) {
        val palette = listOf(
            Color(0xFF7C3AED), // PhonePe Purple
            Color(0xFF2563EB), // GPay Blue
            Color(0xFF0284C7), // Paytm Cyan
            Color(0xFF059669), // Bank Green
            Color(0xFFF59E0B), // Orange
            Color(0xFFEC4899)  // Pink
        )
        val grouped = announcedTxs.groupBy {
            val lower = it.sourceAppName.lowercase()
            when {
                lower.contains("phonepe") -> "PhonePe"
                lower.contains("gpay") || lower.contains("google") -> "Google Pay"
                lower.contains("paytm") -> "Paytm"
                lower.contains("sms") || lower.contains("bank") -> "Bank SMS"
                lower.contains("bhim") -> "BHIM UPI"
                lower.contains("whatsapp") -> "WhatsApp Pay"
                else -> "Other UPI"
            }
        }
        var colorIdx = 0
        grouped.map { (name, list) ->
            val col = palette[colorIdx % palette.size]
            colorIdx++
            AppShare(name, list.size, list.sumOf { it.amount }, col)
        }.sortedByDescending { it.amount }
    }

    val totalRevenue = remember(announcedTxs) { announcedTxs.sumOf { it.amount } }
    val totalCount = remember(announcedTxs) { announcedTxs.size }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Sleek Top Title & PDF Statement Action
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = "Sales & Analytics",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Track weekly growth & UPI channels",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    ThreeDActionButton(
                        onClick = {
                            if (!isExportingPdf) {
                                isExportingPdf = true
                                scope.launch {
                                    try {
                                        PdfStatementExporter.generatePdfFileAsync(
                                            context = context,
                                            transactions = transactions,
                                            shopName = appSettings.shopName,
                                            shopUpiId = appSettings.shopUpiId
                                        ).onSuccess { file ->
                                            previewPdfFile = file
                                        }.onFailure {
                                            Toast.makeText(
                                                context,
                                                "Failed to generate PDF statement",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } finally {
                                        isExportingPdf = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.testTag("analytics_export_pdf_btn"),
                        height = 42.dp,
                        containerColor = BrandIndigo,
                        depthColor = Color(0xFF3730A3),
                        cornerRadius = 14.dp,
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        if (isExportingPdf) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = "Export PDF Statement",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("PDF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // 2. 3D Isometric Analytics Hero Card
            item {
                val isDark = LocalIsDarkTheme.current
                val cardGradient = if (isDark) {
                    listOf(Color(0xFF0D1117), Color(0xFF151C28))
                } else {
                    listOf(Color.White, Color(0xFFEEF2FF))
                }
                ThreeDTactileCard(
                    containerColor = if (isDark) Color(0xFF0D1117) else Color.White,
                    depthColor = if (isDark) Color(0xFF020305) else Color(0xFFC7D2FE),
                    borderColor = BrandIndigo.copy(alpha = if (isDark) 0.45f else 0.35f),
                    cornerRadius = 26.dp,
                    depthDp = 4.dp,
                    elevationDp = if (isDark) 8.dp else 5.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.linearGradient(cardGradient))
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StatusPillBadge(
                                    text = "ALL-TIME COLLECTIONS",
                                    color = if (isDark) Color(0xFFA7F3D0) else BrandEmeraldDark,
                                    containerColor = if (isDark) BrandEmerald.copy(alpha = 0.2f) else Color(0xFFD1FAE5)
                                )
                                AnimatedRupeeText(
                                    amount = totalRevenue,
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$totalCount verified receipts • 7d: ₹${String.format(Locale.US, "%,.0f", weekRevenue)} ($weekCount)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDark) Color(0xFFCBD5E1) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Floating3DObjectBox(bobDistanceDp = 4f, durationMs = 2700) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_3d_analytics_banner),
                                    contentDescription = "3D Revenue Growth Chart",
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .border(
                                            1.dp,
                                            if (isDark) BrandEmerald.copy(alpha = 0.45f) else BrandIndigo.copy(alpha = 0.28f),
                                            RoundedCornerShape(20.dp)
                                        ),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }

            // 3. Interactive 7-Day Trend Bar Chart
            item {
                val selectedDay = last7Days.getOrNull(selectedDayIndex)
                ThreeDTactileCard(
                    containerColor = MaterialTheme.colorScheme.surface,
                    depthColor = MaterialTheme.colorScheme.outline,
                    cornerRadius = 24.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.TrendingUp,
                                        contentDescription = null,
                                        tint = AppSemanticColors.indigoText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "7-Day Sales Trend",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (weekRevenue > 0) "Tap any bar to inspect day" else "Live 7-day sales graph",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (selectedDay != null) {
                                StatusPillBadge(
                                    text = "${selectedDay.fullDateLabel}: ₹${String.format(Locale.US, "%,.0f", selectedDay.amount)}",
                                    color = AppSemanticColors.indigoText,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            }
                        }

                        if (weekRevenue <= 0.0) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No weekly collections yet — bars rise automatically as payments arrive.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (weekRevenue > 0) 172.dp else 72.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            last7Days.forEachIndexed { index, day ->
                                val isSelected = index == selectedDayIndex
                                val targetFraction = if (day.amount > 0) {
                                    (day.amount / maxDayAmount).toFloat().coerceIn(0.12f, 1.0f)
                                } else {
                                    0.14f
                                }
                                val heightFraction by animateFloatAsState(
                                    targetValue = targetFraction,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    ),
                                    label = "barHeight"
                                )
                                val barColor by animateColorAsState(
                                    targetValue = when {
                                        isSelected -> AppSemanticColors.indigoText
                                        day.amount > 0 -> AppSemanticColors.indigoText.copy(alpha = 0.45f)
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    },
                                    animationSpec = tween(250),
                                    label = "barColor"
                                )

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { selectedDayIndex = index }
                                        .padding(horizontal = 2.dp)
                                ) {
                                    if (day.amount > 0) {
                                        Text(
                                            text = "₹${day.amount.toInt()}",
                                            fontSize = 9.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            color = if (isSelected) AppSemanticColors.indigoText else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.58f)
                                            .weight(1f, fill = false)
                                            .fillMaxHeight(heightFraction)
                                            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                                            .background(barColor)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = day.dayLabel,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isSelected) AppSemanticColors.indigoText else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = selectedDay != null) {
                            selectedDay?.let { day ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = day.fullDateLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${day.count} payments • ₹${String.format(Locale.US, "%,.2f", day.amount)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = JetBrainsMono,
                                            fontWeight = FontWeight.Bold,
                                            color = AppSemanticColors.emeraldText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. App & Bank Breakdown
            item {
                ThreeDTactileCard(
                    containerColor = MaterialTheme.colorScheme.surface,
                    depthColor = MaterialTheme.colorScheme.outline,
                    cornerRadius = 24.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PieChart,
                                    contentDescription = null,
                                    tint = AppSemanticColors.emeraldText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "App & Bank Distribution",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (appShares.isEmpty()) {
                            Text(
                                text = "No payment breakdown available yet. Receive or simulate a payment to see channel shares.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            appShares.forEach { share ->
                                val percent = if (totalRevenue > 0) (share.amount / totalRevenue * 100).toInt() else 0
                                val animatedProgress by animateFloatAsState(
                                    targetValue = (percent / 100f).coerceIn(0f, 1f),
                                    animationSpec = tween(550),
                                    label = "shareProgress"
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(share.color)
                                            )
                                            Text(share.appName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(
                                                "(${share.count})",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            "₹${String.format(Locale.US, "%,.0f", share.amount)} • $percent%",
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = JetBrainsMono,
                                            fontSize = 13.sp
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { animatedProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(50)),
                                        color = share.color,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    previewPdfFile?.let { file ->
        PdfStatementPreviewDialog(
            pdfFile = file,
            shopName = appSettings.shopName,
            onDismiss = { previewPdfFile = null }
        )
    }
}
