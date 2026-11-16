package com.example.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.database.entity.TransactionEntity
import com.example.domain.model.AnnouncementStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates wide A4-Landscape multi-page PDF merchant statements with:
 * - Prominent custom Shop Name & UPI ID header
 * - Executive KPI cards & UPI/Bank channel breakdown
 * - Detailed 8-column structured payment table
 * - Consistent "Dasmo Sound Box" footer branding on every page
 */
object PdfStatementExporter {

    // Wide A4 Landscape (points at 72 DPI)
    private const val PAGE_WIDTH = 842
    private const val PAGE_HEIGHT = 595
    private const val MARGIN_X = 36f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_X * 2f) // 770f
    private const val FOOTER_TOP_Y = PAGE_HEIGHT - 44f
    private const val TABLE_HEADER_HEIGHT = 28f
    private const val ROW_HEIGHT = 24f
    private const val TOTAL_BAR_HEIGHT = 30f

    // Palette
    private val COLOR_INK = Color.parseColor("#0F172A")
    private val COLOR_SLATE = Color.parseColor("#475569")
    private val COLOR_MUTED = Color.parseColor("#64748B")
    private val COLOR_BORDER = Color.parseColor("#E2E8F0")
    private val COLOR_BORDER_STRONG = Color.parseColor("#CBD5E1")
    private val COLOR_SURFACE_SOFT = Color.parseColor("#F8FAFC")
    private val COLOR_INDIGO = Color.parseColor("#4F46E5")
    private val COLOR_INDIGO_SOFT = Color.parseColor("#EEF2FF")
    private val COLOR_INDIGO_DARK = Color.parseColor("#312E81")
    private val COLOR_EMERALD = Color.parseColor("#059669")
    private val COLOR_EMERALD_SOFT = Color.parseColor("#DCFCE7")
    private val COLOR_EMERALD_TEXT = Color.parseColor("#166534")
    private val COLOR_BLUE = Color.parseColor("#0284C7")
    private val COLOR_BLUE_SOFT = Color.parseColor("#E0F2FE")
    private val COLOR_AMBER = Color.parseColor("#D97706")
    private val COLOR_AMBER_SOFT = Color.parseColor("#FEF3C7")
    private val COLOR_AMBER_TEXT = Color.parseColor("#92400E")
    private val COLOR_ORANGE_SOFT = Color.parseColor("#FFEDD5")
    private val COLOR_ORANGE = Color.parseColor("#EA580C")

    private data class ColumnSpec(
        val title: String,
        val width: Float,
        val alignRight: Boolean = false
    )

    // Sum of widths = 770f exact fit to CONTENT_WIDTH
    private val COLUMNS = listOf(
        ColumnSpec("#", 34f),
        ColumnSpec("Date & Time", 118f),
        ColumnSpec("Customer / Sender", 146f),
        ColumnSpec("Payment Channel", 112f),
        ColumnSpec("Source", 68f),
        ColumnSpec("UTR / Reference", 120f),
        ColumnSpec("Status", 76f),
        ColumnSpec("Amount (₹)", 96f, alignRight = true)
    )

    internal data class ChannelSummary(
        val name: String,
        val count: Int,
        val total: Double
    )

    suspend fun generatePdfFileAsync(
        context: Context,
        transactions: List<TransactionEntity>,
        shopName: String,
        shopUpiId: String = ""
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            generatePdfFile(
                context = context,
                transactions = transactions,
                shopName = shopName,
                shopUpiId = shopUpiId
            )
        }
    }

    suspend fun exportAndShare(
        context: Context,
        transactions: List<TransactionEntity>,
        shopName: String,
        shopUpiId: String = ""
    ) {
        val result = generatePdfFileAsync(
            context = context,
            transactions = transactions,
            shopName = shopName,
            shopUpiId = shopUpiId
        )
        result.onSuccess { pdfFile ->
            sharePdfFile(context, pdfFile, shopName)
        }.onFailure {
            Toast.makeText(
                context,
                "Failed to generate PDF statement",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun sharePdfFile(context: Context, pdfFile: File, shopName: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val displayShop = shopName.trim().ifBlank { "My Shop" }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "$displayShop — Dasmo Sound Box Payment Statement")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Payment collection statement for $displayShop generated by Dasmo Sound Box."
                )
                clipData = ClipData.newRawUri("Dasmo Sound Box PDF Statement", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, "Share PDF Statement").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "Unable to open share sheet for PDF",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun openPdfExternally(context: Context, pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                clipData = ClipData.newRawUri("Dasmo Sound Box PDF Statement", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(viewIntent)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "No external PDF viewer app found — you can view or save it here",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun generatePdfFile(
        context: Context,
        transactions: List<TransactionEntity>,
        shopName: String,
        shopUpiId: String = ""
    ): File {
        cleanupStaleStatements(context.cacheDir)

        val resolvedShop = shopName.trim().ifBlank { "My Shop" }
        val safeShopSlug = sanitizeShopSlug(resolvedShop)
        val fileStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(context.cacheDir, "Dasmo_SoundBox_${safeShopSlug}_$fileStamp.pdf")

        val document = buildPdfDocument(
            transactions = transactions,
            shopName = resolvedShop,
            shopUpiId = shopUpiId.trim()
        )
        try {
            FileOutputStream(outFile).use { fos ->
                document.writeTo(fos)
                fos.flush()
            }
        } finally {
            document.close()
        }
        return outFile
    }

    internal fun buildPdfDocument(
        transactions: List<TransactionEntity>,
        shopName: String,
        shopUpiId: String = ""
    ): PdfDocument {
        val resolvedShop = shopName.trim().ifBlank { "My Shop" }
        val sortedTxs = transactions.sortedByDescending { it.timestamp }
        val announcedTxs = sortedTxs.filter { it.status == AnnouncementStatus.ANNOUNCED.name }

        val totalAmount = announcedTxs.sumOf { it.amount }
        val totalCount = announcedTxs.size
        val nowMs = System.currentTimeMillis()
        val sevenDaysAgoMs = nowMs - (7L * 24L * 60L * 60L * 1000L)
        val weekTxs = announcedTxs.filter { it.timestamp >= sevenDaysAgoMs }
        val weekAmount = weekTxs.sumOf { it.amount }
        val avgTicket = if (totalCount > 0) totalAmount / totalCount else 0.0

        val channelSummaries = computeChannelSummaries(announcedTxs)
        val pageSlices = computePageSlices(sortedTxs.size)
        val totalPages = pageSlices.size

        val document = PdfDocument()

        for (pageIdx in pageSlices.indices) {
            val (startIdx, endIdx, drawTotalsOnThisPage) = pageSlices[pageIdx]
            val pageNumber = pageIdx + 1
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            // White canvas background
            canvas.drawColor(Color.WHITE)

            var cursorY = if (pageIdx == 0) {
                drawFirstPageHeader(
                    canvas = canvas,
                    shopName = resolvedShop,
                    shopUpiId = shopUpiId,
                    transactions = sortedTxs,
                    totalAmount = totalAmount,
                    totalCount = totalCount,
                    weekAmount = weekAmount,
                    weekCount = weekTxs.size,
                    avgTicket = avgTicket,
                    channelSummaries = channelSummaries
                )
            } else {
                drawContinuationHeader(
                    canvas = canvas,
                    shopName = resolvedShop,
                    shopUpiId = shopUpiId
                )
            }

            if (sortedTxs.isEmpty()) {
                drawTableHeader(canvas, cursorY)
                cursorY += TABLE_HEADER_HEIGHT
                drawEmptyTableState(canvas, cursorY)
            } else if (startIdx < endIdx) {
                drawTableHeader(canvas, cursorY)
                cursorY += TABLE_HEADER_HEIGHT

                for (txIdx in startIdx until endIdx) {
                    val tx = sortedTxs[txIdx]
                    drawTransactionRow(
                        canvas = canvas,
                        rowNumber = txIdx + 1,
                        tx = tx,
                        topY = cursorY,
                        isAltRow = (txIdx - startIdx) % 2 == 1
                    )
                    cursorY += ROW_HEIGHT
                }
            }

            if (drawTotalsOnThisPage && sortedTxs.isNotEmpty()) {
                drawTotalsSummaryRow(
                    canvas = canvas,
                    topY = cursorY + 6f,
                    totalRecords = sortedTxs.size,
                    verifiedCount = totalCount,
                    totalAmount = totalAmount
                )
            }

            drawDasmoFooter(
                canvas = canvas,
                shopName = resolvedShop,
                pageNumber = pageNumber,
                totalPages = totalPages
            )

            document.finishPage(page)
        }

        return document
    }

    internal data class PageSlice(
        val startIdx: Int,
        val endIdx: Int,
        val drawTotals: Boolean
    )

    internal fun computePageSlices(totalRows: Int): List<PageSlice> {
        if (totalRows == 0) {
            return listOf(PageSlice(0, 0, drawTotals = false))
        }

        val firstPageTableTop = 244f
        val nextPageTableTop = 76f
        val firstPageMaxRows = ((FOOTER_TOP_Y - 10f - firstPageTableTop - TABLE_HEADER_HEIGHT) / ROW_HEIGHT).toInt().coerceAtLeast(1)
        val nextPageMaxRows = ((FOOTER_TOP_Y - 10f - nextPageTableTop - TABLE_HEADER_HEIGHT) / ROW_HEIGHT).toInt().coerceAtLeast(1)

        val slices = mutableListOf<PageSlice>()
        var idx = 0
        var page = 0
        while (idx < totalRows) {
            val tableTop = if (page == 0) firstPageTableTop else nextPageTableTop
            val maxRows = if (page == 0) firstPageMaxRows else nextPageMaxRows
            val remaining = totalRows - idx
            val take = remaining.coerceAtMost(maxRows)
            val end = idx + take
            val isLastBatch = end == totalRows

            if (isLastBatch) {
                val endY = tableTop + TABLE_HEADER_HEIGHT + (take * ROW_HEIGHT) + 6f + TOTAL_BAR_HEIGHT
                if (endY <= FOOTER_TOP_Y - 6f) {
                    slices.add(PageSlice(idx, end, drawTotals = true))
                } else {
                    // Put the final row + totals neatly onto one extra page if tight
                    if (take > 1) {
                        slices.add(PageSlice(idx, end - 1, drawTotals = false))
                        slices.add(PageSlice(end - 1, end, drawTotals = true))
                    } else {
                        slices.add(PageSlice(idx, end, drawTotals = false))
                        slices.add(PageSlice(end, end, drawTotals = true))
                    }
                }
            } else {
                slices.add(PageSlice(idx, end, drawTotals = false))
            }
            idx = end
            page++
        }
        return slices
    }

    private fun drawFirstPageHeader(
        canvas: Canvas,
        shopName: String,
        shopUpiId: String,
        transactions: List<TransactionEntity>,
        totalAmount: Double,
        totalCount: Int,
        weekAmount: Double,
        weekCount: Int,
        avgTicket: Double,
        channelSummaries: List<ChannelSummary>
    ): Float {
        // 1. Wide Executive Header Card
        val headerRect = RectF(MARGIN_X, 24f, PAGE_WIDTH - MARGIN_X, 108f)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_INDIGO_SOFT }
        canvas.drawRoundRect(headerRect, 12f, 12f, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C7D2FE")
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        canvas.drawRoundRect(headerRect, 12f, 12f, borderPaint)

        // Left vertical Indigo accent stripe
        val stripeRect = RectF(MARGIN_X, 24f, MARGIN_X + 7f, 108f)
        val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_INDIGO }
        canvas.drawRoundRect(stripeRect, 6f, 6f, stripePaint)

        // Small eyebrow label
        val eyebrowPaint = makePaint(9f, COLOR_INDIGO, bold = true)
        canvas.drawText("MERCHANT PAYMENT STATEMENT", MARGIN_X + 20f, 44f, eyebrowPaint)

        // Big Shop Name
        val shopPaint = makeTextPaint(20f, COLOR_INK, bold = true)
        val ellipsizedShop = TextUtils.ellipsize(shopName, shopPaint, 430f, TextUtils.TruncateAt.END).toString()
        canvas.drawText(ellipsizedShop, MARGIN_X + 20f, 69f, shopPaint)

        // Subtitle with UPI ID & Report Info
        val subText = buildString {
            if (shopUpiId.isNotBlank()) {
                append("UPI ID: ")
                append(shopUpiId)
                append("  •  ")
            }
            append("Sales & Verified Collections Report")
        }
        val subPaint = makeTextPaint(10f, COLOR_SLATE, bold = false)
        val ellipsizedSub = TextUtils.ellipsize(subText, subPaint, 440f, TextUtils.TruncateAt.END).toString()
        canvas.drawText(ellipsizedSub, MARGIN_X + 20f, 89f, subPaint)

        // Right metadata block
        val genDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        val periodText = if (transactions.isNotEmpty()) {
            val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val oldest = fmt.format(Date(transactions.last().timestamp))
            val newest = fmt.format(Date(transactions.first().timestamp))
            if (oldest == newest) oldest else "$oldest – $newest"
        } else {
            "All Recorded Alerts"
        }

        val rightX = PAGE_WIDTH - MARGIN_X - 16f
        val rightLabelPaint = makePaint(8.5f, COLOR_MUTED, bold = true, alignRight = true)
        val rightValPaint = makePaint(10.5f, COLOR_INK, bold = true, alignRight = true)
        canvas.drawText("GENERATED ON", rightX, 44f, rightLabelPaint)
        canvas.drawText(genDate, rightX, 58f, rightValPaint)
        canvas.drawText("STATEMENT PERIOD", rightX, 78f, rightLabelPaint)
        canvas.drawText(periodText, rightX, 93f, rightValPaint)

        // 2. Four Structured KPI Cards (y = 120 .. 184)
        val kpiGap = 10f
        val kpiWidth = (CONTENT_WIDTH - (kpiGap * 3f)) / 4f
        val kpiTop = 120f
        val kpiBottom = 184f

        drawKpiCard(
            canvas = canvas,
            left = MARGIN_X,
            top = kpiTop,
            right = MARGIN_X + kpiWidth,
            bottom = kpiBottom,
            label = "TOTAL COLLECTIONS",
            value = formatInr(totalAmount),
            subtitle = "Verified credit alerts",
            accentColor = COLOR_EMERALD,
            pillBg = COLOR_EMERALD_SOFT
        )
        drawKpiCard(
            canvas = canvas,
            left = MARGIN_X + (kpiWidth + kpiGap),
            top = kpiTop,
            right = MARGIN_X + (kpiWidth * 2f) + kpiGap,
            bottom = kpiBottom,
            label = "VERIFIED PAYMENTS",
            value = "$totalCount",
            subtitle = "${transactions.size} total entries logged",
            accentColor = COLOR_INDIGO,
            pillBg = COLOR_INDIGO_SOFT
        )
        drawKpiCard(
            canvas = canvas,
            left = MARGIN_X + (kpiWidth + kpiGap) * 2f,
            top = kpiTop,
            right = MARGIN_X + (kpiWidth * 3f) + (kpiGap * 2f),
            bottom = kpiBottom,
            label = "LAST 7 DAYS",
            value = formatInr(weekAmount),
            subtitle = "$weekCount receipts in 7d",
            accentColor = COLOR_BLUE,
            pillBg = COLOR_BLUE_SOFT
        )
        drawKpiCard(
            canvas = canvas,
            left = MARGIN_X + (kpiWidth + kpiGap) * 3f,
            top = kpiTop,
            right = PAGE_WIDTH - MARGIN_X,
            bottom = kpiBottom,
            label = "AVERAGE RECEIPT",
            value = formatInr(avgTicket),
            subtitle = "Per announced payment",
            accentColor = COLOR_ORANGE,
            pillBg = COLOR_ORANGE_SOFT
        )

        // 3. Channel Breakdown Bar (y = 196 .. 232)
        val channelBar = RectF(MARGIN_X, 196f, PAGE_WIDTH - MARGIN_X, 232f)
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_SURFACE_SOFT }
        canvas.drawRoundRect(channelBar, 8f, 8f, barPaint)
        val barStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(channelBar, 8f, 8f, barStroke)

        val chTitlePaint = makePaint(9f, COLOR_SLATE, bold = true)
        canvas.drawText("CHANNEL BREAKDOWN:", MARGIN_X + 12f, 218f, chTitlePaint)

        val channelLine = if (channelSummaries.isEmpty()) {
            "No verified payment channels recorded yet"
        } else {
            channelSummaries.take(5).joinToString("   •   ") {
                "${it.name}: ${formatInr(it.total)} (${it.count})"
            }
        }
        val chValPaint = makeTextPaint(9.5f, COLOR_INK, bold = false)
        val ellipsizedChannel = TextUtils.ellipsize(
            channelLine,
            chValPaint,
            CONTENT_WIDTH - 145f,
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(ellipsizedChannel, MARGIN_X + 132f, 218f, chValPaint)

        return 244f
    }

    private fun drawContinuationHeader(
        canvas: Canvas,
        shopName: String,
        shopUpiId: String
    ): Float {
        val headerRect = RectF(MARGIN_X, 24f, PAGE_WIDTH - MARGIN_X, 62f)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_SURFACE_SOFT }
        canvas.drawRoundRect(headerRect, 8f, 8f, bgPaint)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(headerRect, 8f, 8f, strokePaint)

        val shopPaint = makeTextPaint(13f, COLOR_INK, bold = true)
        val title = TextUtils.ellipsize(
            "$shopName — Payment Statement (Continued)",
            shopPaint,
            520f,
            TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(title, MARGIN_X + 14f, 47f, shopPaint)

        if (shopUpiId.isNotBlank()) {
            val upiPaint = makePaint(9.5f, COLOR_MUTED, bold = false, alignRight = true)
            canvas.drawText("UPI: $shopUpiId", PAGE_WIDTH - MARGIN_X - 14f, 47f, upiPaint)
        }

        return 76f
    }

    private fun drawKpiCard(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        label: String,
        value: String,
        subtitle: String,
        accentColor: Int,
        pillBg: Int
    ) {
        val cardRect = RectF(left, top, right, bottom)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawRoundRect(cardRect, 10f, 10f, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(cardRect, 10f, 10f, borderPaint)

        // Top subtle pill accent bar
        val topDot = RectF(left + 12f, top + 10f, left + 18f, top + 16f)
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
        canvas.drawRoundRect(topDot, 3f, 3f, dotPaint)

        val lblPaint = makePaint(8f, COLOR_MUTED, bold = true)
        canvas.drawText(label, left + 23f, top + 16f, lblPaint)

        val valPaint = makeTextPaint(16f, COLOR_INK, bold = true)
        val valText = TextUtils.ellipsize(value, valPaint, right - left - 24f, TextUtils.TruncateAt.END).toString()
        canvas.drawText(valText, left + 12f, top + 38f, valPaint)

        // Subtle colored subtitle chip/text
        val subPaint = makeTextPaint(8.5f, accentColor, bold = true)
        val subText = TextUtils.ellipsize(subtitle, subPaint, right - left - 24f, TextUtils.TruncateAt.END).toString()
        val chipBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pillBg }
        val textWidth = subPaint.measureText(subText)
        val chipRect = RectF(left + 12f, top + 44f, (left + 22f + textWidth).coerceAtMost(right - 10f), top + 57f)
        canvas.drawRoundRect(chipRect, 4f, 4f, chipBg)
        canvas.drawText(subText, left + 17f, top + 53.5f, subPaint)
    }

    private fun drawTableHeader(canvas: Canvas, topY: Float) {
        val headerRect = RectF(MARGIN_X, topY, PAGE_WIDTH - MARGIN_X, topY + TABLE_HEADER_HEIGHT)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_INK }
        canvas.drawRoundRect(headerRect, 6f, 6f, bgPaint)

        val textPaintLeft = makePaint(9f, Color.WHITE, bold = true)
        val textPaintRight = makePaint(9f, Color.WHITE, bold = true, alignRight = true)

        var colX = MARGIN_X
        val baselineY = topY + 17.5f
        for (col in COLUMNS) {
            if (col.alignRight) {
                canvas.drawText(col.title, colX + col.width - 10f, baselineY, textPaintRight)
            } else {
                canvas.drawText(col.title, colX + 8f, baselineY, textPaintLeft)
            }
            colX += col.width
        }
    }

    private fun drawTransactionRow(
        canvas: Canvas,
        rowNumber: Int,
        tx: TransactionEntity,
        topY: Float,
        isAltRow: Boolean
    ) {
        val rowRect = RectF(MARGIN_X, topY, PAGE_WIDTH - MARGIN_X, topY + ROW_HEIGHT)
        val bgPaint = Paint().apply {
            color = if (isAltRow) COLOR_SURFACE_SOFT else Color.WHITE
        }
        canvas.drawRect(rowRect, bgPaint)

        // Bottom hairline divider + outer side border
        val linePaint = Paint().apply {
            color = COLOR_BORDER
            strokeWidth = 0.8f
        }
        canvas.drawLine(MARGIN_X, topY + ROW_HEIGHT, PAGE_WIDTH - MARGIN_X, topY + ROW_HEIGHT, linePaint)
        canvas.drawLine(MARGIN_X, topY, MARGIN_X, topY + ROW_HEIGHT, linePaint)
        canvas.drawLine(PAGE_WIDTH - MARGIN_X, topY, PAGE_WIDTH - MARGIN_X, topY + ROW_HEIGHT, linePaint)

        val dateStr = SimpleDateFormat("dd MMM yy, hh:mm a", Locale.getDefault()).format(Date(tx.timestamp))
        val payer = tx.payerName?.trim()?.takeIf { it.isNotBlank() } ?: "Walk-in Customer"
        val channel = normalizeChannelName(tx.sourceAppName)
        val sourceMode = if (tx.sourceType.equals("SMS", ignoreCase = true)) "BANK SMS" else "UPI APP"
        val utr = tx.refId?.trim()?.takeIf { it.isNotBlank() } ?: "—"
        val amountStr = formatInr(tx.amount)

        val idxPaint = makePaint(8.5f, COLOR_MUTED, bold = true)
        val bodyPaint = makeTextPaint(9f, COLOR_INK, bold = false)
        val mutedPaint = makeTextPaint(8.5f, COLOR_SLATE, bold = false)
        val monoPaint = makeTextPaint(8.5f, COLOR_SLATE, bold = false, mono = true)
        val amtPaint = makePaint(
            9.5f,
            if (tx.status == AnnouncementStatus.ANNOUNCED.name) COLOR_EMERALD else COLOR_SLATE,
            bold = true,
            alignRight = true
        )

        val baselineY = topY + 15.5f
        var colX = MARGIN_X

        // Col 0: #
        canvas.drawText(rowNumber.toString(), colX + 8f, baselineY, idxPaint)
        colX += COLUMNS[0].width

        // Col 1: Date & Time
        val dateClipped = TextUtils.ellipsize(dateStr, mutedPaint, COLUMNS[1].width - 12f, TextUtils.TruncateAt.END).toString()
        canvas.drawText(dateClipped, colX + 8f, baselineY, mutedPaint)
        colX += COLUMNS[1].width

        // Col 2: Payer
        val payerClipped = TextUtils.ellipsize(payer, bodyPaint, COLUMNS[2].width - 12f, TextUtils.TruncateAt.END).toString()
        canvas.drawText(payerClipped, colX + 8f, baselineY, bodyPaint)
        colX += COLUMNS[2].width

        // Col 3: Payment Channel
        val channelClipped = TextUtils.ellipsize(channel, bodyPaint, COLUMNS[3].width - 12f, TextUtils.TruncateAt.END).toString()
        canvas.drawText(channelClipped, colX + 8f, baselineY, bodyPaint)
        colX += COLUMNS[3].width

        // Col 4: Mode
        canvas.drawText(sourceMode, colX + 8f, baselineY, mutedPaint)
        colX += COLUMNS[4].width

        // Col 5: UTR / Ref
        val utrClipped = TextUtils.ellipsize(utr, monoPaint, COLUMNS[5].width - 12f, TextUtils.TruncateAt.END).toString()
        canvas.drawText(utrClipped, colX + 8f, baselineY, monoPaint)
        colX += COLUMNS[5].width

        // Col 6: Status Pill
        val (statusLabel, pillBg, pillText) = when (tx.status) {
            AnnouncementStatus.ANNOUNCED.name -> Triple("VERIFIED", COLOR_EMERALD_SOFT, COLOR_EMERALD_TEXT)
            AnnouncementStatus.DUPLICATE_SKIPPED.name -> Triple("DUPLICATE", COLOR_AMBER_SOFT, COLOR_AMBER_TEXT)
            AnnouncementStatus.BELOW_THRESHOLD.name -> Triple("BELOW MIN", COLOR_AMBER_SOFT, COLOR_AMBER_TEXT)
            else -> Triple("IGNORED", COLOR_SURFACE_SOFT, COLOR_MUTED)
        }
        val pillRect = RectF(colX + 6f, topY + 5f, colX + COLUMNS[6].width - 8f, topY + ROW_HEIGHT - 5f)
        val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pillBg }
        canvas.drawRoundRect(pillRect, 4f, 4f, pillBgPaint)
        val pillTextPaint = makePaint(7.5f, pillText, bold = true)
        canvas.drawText(statusLabel, colX + 12f, topY + 14.5f, pillTextPaint)
        colX += COLUMNS[6].width

        // Col 7: Amount (Right Aligned)
        canvas.drawText(amountStr, colX + COLUMNS[7].width - 10f, baselineY, amtPaint)
    }

    private fun drawEmptyTableState(canvas: Canvas, topY: Float) {
        val boxRect = RectF(MARGIN_X, topY, PAGE_WIDTH - MARGIN_X, topY + 64f)
        val bgPaint = Paint().apply { color = COLOR_SURFACE_SOFT }
        canvas.drawRect(boxRect, bgPaint)
        val strokePaint = Paint().apply {
            color = COLOR_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        canvas.drawRect(boxRect, strokePaint)

        val msgPaint = makePaint(11f, COLOR_MUTED, bold = false).apply {
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "No payment transactions recorded yet. Incoming UPI & bank credit alerts will appear here.",
            PAGE_WIDTH / 2f,
            topY + 36f,
            msgPaint
        )
    }

    private fun drawTotalsSummaryRow(
        canvas: Canvas,
        topY: Float,
        totalRecords: Int,
        verifiedCount: Int,
        totalAmount: Double
    ) {
        val totalRect = RectF(MARGIN_X, topY, PAGE_WIDTH - MARGIN_X, topY + TOTAL_BAR_HEIGHT)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_INDIGO_SOFT }
        canvas.drawRoundRect(totalRect, 6f, 6f, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_INDIGO
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        canvas.drawRoundRect(totalRect, 6f, 6f, borderPaint)

        val labelPaint = makePaint(10f, COLOR_INDIGO_DARK, bold = true)
        canvas.drawText(
            "GRAND TOTAL — $verifiedCount Verified Payment(s) (out of $totalRecords logged alert(s))",
            MARGIN_X + 12f,
            topY + 18.5f,
            labelPaint
        )

        val totalPaint = makePaint(12f, COLOR_EMERALD, bold = true, alignRight = true)
        canvas.drawText(
            formatInr(totalAmount),
            PAGE_WIDTH - MARGIN_X - 10f,
            topY + 19f,
            totalPaint
        )
    }

    private fun drawDasmoFooter(
        canvas: Canvas,
        shopName: String,
        pageNumber: Int,
        totalPages: Int
    ) {
        // Hairline separator
        val linePaint = Paint().apply {
            color = COLOR_BORDER_STRONG
            strokeWidth = 1f
        }
        canvas.drawLine(MARGIN_X, FOOTER_TOP_Y, PAGE_WIDTH - MARGIN_X, FOOTER_TOP_Y, linePaint)

        // Bottom brand badge: "DASMO SOUND BOX"
        val badgeRect = RectF(MARGIN_X, FOOTER_TOP_Y + 9f, MARGIN_X + 132f, FOOTER_TOP_Y + 29f)
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_INK }
        canvas.drawRoundRect(badgeRect, 5f, 5f, badgePaint)

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#10B981") }
        canvas.drawCircle(MARGIN_X + 11f, FOOTER_TOP_Y + 19f, 3.5f, dotPaint)

        val brandPaint = makePaint(8.5f, Color.WHITE, bold = true)
        canvas.drawText("DASMO SOUND BOX", MARGIN_X + 20f, FOOTER_TOP_Y + 22f, brandPaint)

        val taglinePaint = makePaint(8.5f, COLOR_MUTED, bold = false)
        canvas.drawText(
            "Official Merchant Payment Statement • Generated by Dasmo Sound Box",
            MARGIN_X + 142f,
            FOOTER_TOP_Y + 22f,
            taglinePaint
        )

        val pageTextPaint = makePaint(8.5f, COLOR_SLATE, bold = true, alignRight = true)
        val shopShort = if (shopName.length > 26) shopName.take(25) + "…" else shopName
        canvas.drawText(
            "$shopShort  •  Page $pageNumber of $totalPages",
            PAGE_WIDTH - MARGIN_X,
            FOOTER_TOP_Y + 22f,
            pageTextPaint
        )
    }

    internal fun sanitizeShopSlug(shopName: String): String {
        return shopName.trim()
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .take(28)
            .ifBlank { "My_Shop" }
    }

    internal fun computeChannelSummaries(transactions: List<TransactionEntity>): List<ChannelSummary> {
        return transactions
            .filter { it.status == AnnouncementStatus.ANNOUNCED.name }
            .groupBy { normalizeChannelName(it.sourceAppName) }
            .map { (name, list) ->
                ChannelSummary(
                    name = name,
                    count = list.size,
                    total = list.sumOf { it.amount }
                )
            }
            .sortedByDescending { it.total }
    }

    internal fun normalizeChannelName(raw: String): String {
        val lower = raw.lowercase(Locale.ROOT)
        return when {
            lower.contains("phonepe") -> "PhonePe"
            lower.contains("gpay") || lower.contains("google") -> "Google Pay"
            lower.contains("paytm") -> "Paytm"
            lower.contains("bhim") -> "BHIM UPI"
            lower.contains("whatsapp") -> "WhatsApp Pay"
            lower.contains("sms") || lower.contains("bank") -> "Bank SMS"
            raw.isBlank() -> "UPI App"
            else -> raw
        }
    }

    internal fun formatInr(amount: Double): String {
        val nf = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "₹${nf.format(amount)}"
    }

    private fun makePaint(
        sizeSp: Float,
        color: Int,
        bold: Boolean,
        alignRight: Boolean = false
    ): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizeSp
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
            if (alignRight) textAlign = Paint.Align.RIGHT
        }
    }

    private fun makeTextPaint(
        sizeSp: Float,
        color: Int,
        bold: Boolean,
        mono: Boolean = false
    ): TextPaint {
        return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizeSp
            this.color = color
            val family = if (mono) Typeface.MONOSPACE else Typeface.SANS_SERIF
            typeface = Typeface.create(family, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun cleanupStaleStatements(cacheDir: File) {
        val cutoff = System.currentTimeMillis() - (24L * 60L * 60L * 1000L)
        cacheDir.listFiles { file ->
            file.isFile &&
                file.name.startsWith("Dasmo_SoundBox_") &&
                file.name.endsWith(".pdf") &&
                file.lastModified() < cutoff
        }?.forEach { file ->
            runCatching { file.delete() }
        }
    }
}
