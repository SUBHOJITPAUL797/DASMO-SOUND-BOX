package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.database.entity.TransactionEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    fun exportAndShare(context: Context, transactions: List<TransactionEntity>) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val fileFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val filename = "SoundBox_Statement_${fileFormat.format(Date())}.csv"

            val file = File(context.cacheDir, filename)
            file.bufferedWriter().use { writer ->
                // CSV Header
                writer.write("ID,Date & Time,Amount (INR),Payer Name,App / Source,Type,UTR / Ref ID,Status,Announcement\n")
                for (tx in transactions) {
                    val dateStr = dateFormat.format(Date(tx.timestamp))
                    val cleanPayer = (tx.payerName ?: "").replace(",", " ")
                    val cleanSource = tx.sourceAppName.replace(",", " ")
                    val cleanRef = (tx.refId ?: "").replace(",", " ")
                    val cleanAnnouncement = (tx.announcementText ?: "").replace(",", " ").replace("\n", " ")
                    writer.write("${tx.id},\"$dateStr\",${tx.amount},\"$cleanPayer\",\"$cleanSource\",\"${tx.sourceType}\",\"$cleanRef\",\"${tx.status}\",\"$cleanAnnouncement\"\n")
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "SoundBox Payment Statement")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share Payment Statement CSV").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            // Fallback plain text share if FileProvider not configured
            sharePlainTextSummary(context, transactions)
        }
    }

    private fun sharePlainTextSummary(context: Context, transactions: List<TransactionEntity>) {
        val total = transactions.filter { it.status == "ANNOUNCED" }.sumOf { it.amount }
        val count = transactions.filter { it.status == "ANNOUNCED" }.size
        val text = buildString {
            append("📊 SoundBox Pro Payment Statement\n")
            append("Total Payments: $count\n")
            append("Total Amount: ₹${String.format(Locale.US, "%.2f", total)}\n\n")
            append("Recent Transactions:\n")
            transactions.take(20).forEach { tx ->
                val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(tx.timestamp))
                append("• ₹${tx.amount} - ${tx.payerName ?: tx.sourceAppName} ($dateStr)\n")
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share Statement").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
