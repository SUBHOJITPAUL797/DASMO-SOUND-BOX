package com.example.domain.parser

import com.example.domain.model.ParseResult
import java.util.Locale

object PaymentParser {

    private val DEBIT_KEYWORDS = listOf(
        "debited", "deducted", "payment done", "payment successful", "payment made",
        "money sent", "amount debited", "sent to", "paid from your", "paid from a/c",
        "transferred from your", "failed", "declined", "reversed", "refund initiated",
        "purchase of", "bill payment", "sent rs", "paid rs", "dr in", "dr."
    )

    private val CREDIT_KEYWORDS = listOf(
        "received", "credited", "credit", "added", "deposited",
        "paid to your", "transferred to your", "money received",
        "amount received", "payment received", "cr", "cr.", "cr in", "gained",
        "sent you", "paid you", "loaded with", "money in!"
    )

    private val NON_NAME_KEYWORDS = setOf(
        "account", "acct", "a/c", "bank", "user", "customer", "upi", "payment",
        "soundbox", "credited", "received", "balance", "available", "wallet",
        "merchant", "phonepe", "google", "gpay", "paytm", "bhim", "cred",
        "amazon", "axis", "hdfc", "sbi", "icici", "kotak", "pnb", "bob",
        "canara", "union", "alert", "notification", "message", "transfer",
        "money", "your", "inr", "rs", "rupees", "ref", "vpa", "txn",
        "online", "instant", "successful", "success", "request", "order",
        "business", "bharatpe", "super", "supermoney", "navi", "tataneu"
    )

    fun parse(text: String, fallbackTitle: String? = null): ParseResult {
        // Normalize "credit card" to prevent false positive credit matches on credit card debits
        val normalized = text.lowercase().replace("credit card", "cc_card")
        
        val hasDebitKeyword = DEBIT_KEYWORDS.any { normalized.contains(it) }
        val hasCreditKeyword = CREDIT_KEYWORDS.any { normalized.contains(it) }
        
        // Strict debit exclusion: if contains debit keyword and no credit keyword
        if (hasDebitKeyword && !hasCreditKeyword) {
            return ParseResult(amount = null, isCredit = false, rawText = text)
        }
        
        val creditPatterns = listOf(
            // "credited by/with/of Rs 500" or "credited with INR 500.00"
            Regex("""(?:credited|credit)\s+(?:by|with|of|for)?\s*(?:₹|Rs\.?\s*|INR\s*|rupees?\s*)([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // "Rs 500 has been credited/deposited/added" or "Rs 500 successfully credited/received"
            Regex("""(?:₹|Rs\.?\s*|INR\s*)([\d,]+(?:\.\d{1,2})?)\s+(?:has\s+been\s+)?(?:successfully\s+)?(?:credited|credit|added|deposited|received)(?:\s+successfully)?""", RegexOption.IGNORE_CASE),
            // "received payment of Rs 500" / "got Rs 500" / "you've received Rs 500"
            Regex("""(?:received|got|credit|you've\s+received|you\s+have\s+received)\s+(?:a\s+)?(?:payment\s+of\s+)?(?:of\s+)?(?:₹|Rs\.?\s*|INR\s*|rupees?\s*)([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // "payment received: ₹500" or "payment received of ₹500"
            Regex("""payment\s+received\s*(?::|of|for)?\s*(?:₹|Rs\.?\s*|INR\s*|rupees?\s*)([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // "Rs 500 received / deposited / added to your a/c"
            Regex("""(?:₹|Rs\.?\s*|INR\s*)([\d,]+(?:\.\d{1,2})?)\s+(?:received|paid\s+to\s+your|deposited|added\s+to|transferred\s+to)""", RegexOption.IGNORE_CASE),
            // "a/c ending *1234 credited with Rs 500"
            Regex("""a\/c\s*[\w*]*\s*(?:is|has\s+been)?\s*credited\s*(?:by|with|of)?\s*(?:₹|Rs\.?\s*|INR\s*)?([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // "UPI/CR/500" or "IMPS CR Rs. 500"
            Regex("""(?:UPI|NEFT|IMPS|RTGS)[\/\s]*(?:CR|CREDIT)[\/\s:]*(?:₹|Rs\.?\s*|INR\s*)?([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // "sent you Rs. 500" / "paid you Rs 500"
            Regex("""(?:sent\s+you|paid\s+you|transferred)\s*(?:₹|Rs\.?\s*|INR\s*|rupees?\s*)([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // "Kotak: Rs 500 credited"
            Regex("""Kotak.*?(?:₹|Rs\.?\s*|INR\s*)([\d,]+(?:\.\d{1,2})?)\s+(?:credited|received)""", RegexOption.IGNORE_CASE),
            // "500.00 rupees received / credited"
            Regex("""([\d,]+(?:\.\d{1,2})?)\s*(?:₹|rupees?|inr|rs\.?)\s+(?:received|credited|deposited|added)""", RegexOption.IGNORE_CASE),
            // "Payment of Rs 500.00 received"
            Regex("""Payment\s+of\s+(?:₹|Rs\.?\s*|INR\s*)([\d,]+(?:\.\d{1,2})?)\s+received""", RegexOption.IGNORE_CASE),
            // "Money in! ₹500"
            Regex("""(?:Money\s+in!?|Loaded\s+with)\s*(?:₹|Rs\.?\s*|INR\s*)([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // "deposited / added ₹500"
            Regex("""(?:deposited|added)\s+(?:₹|Rs\.?\s*|INR\s*|rupees?\s*)([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // "credited to account ... ₹500"
            Regex("""(?:credited\s+(?:to|in|into|with|for)\s+[^0-9\n]{0,35}?)(?:₹|Rs\.?\s*|INR\s*|rupees?\s*)([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in creditPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val raw = match.groupValues[1].replace(",", "")
                val amount = raw.toDoubleOrNull()
                if (amount != null && amount > 0) {
                    val payerName = extractPayerName(text, fallbackTitle)
                    val refId = extractRefId(text)
                    return ParseResult(amount = amount, isCredit = true, payerName = payerName, refId = refId, rawText = text)
                }
            }
        }
        
        // Secondary fallback if credit keyword present
        if (hasCreditKeyword) {
            val amountPatterns = listOf(
                Regex("""(?:₹|Rs\.?\s*|INR\s*)([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
                Regex("""([\d,]+(?:\.\d{1,2})?)\s*(?:₹|Rs\.?|rupees?)""", RegexOption.IGNORE_CASE)
            )
            for (pattern in amountPatterns) {
                val match = pattern.find(text)
                if (match != null) {
                    val raw = match.groupValues[1].replace(",", "")
                    val amount = raw.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        val payerName = extractPayerName(text, fallbackTitle)
                        val refId = extractRefId(text)
                        return ParseResult(amount = amount, isCredit = true, payerName = payerName, refId = refId, rawText = text)
                    }
                }
            }
        }
        
        return ParseResult(amount = null, isCredit = false, rawText = text)
    }

    fun extractPayerName(text: String, fallbackTitle: String? = null): String? {
        val patterns = listOf(
            // "from Rohit Sharma via Google Pay" or "from Suresh Kumar on PhonePe"
            Regex("""(?:from|by\s+transfer\s+from|payment\s+from|transfer\s+from)\s+([A-Za-z0-9\s.]{2,40}?)(?:\s+(?:via|on|through|using|with|ref|upi|vpa|a\/c|acct|balance|bal|txn|trans|at|dated|\.|\$|\(|\)|-|,)|$)""", RegexOption.IGNORE_CASE),
            // "sent by Rohit Sharma" or "paid by Rohit Sharma"
            Regex("""(?:sent|paid)\s+by\s+([A-Za-z0-9\s.]{2,40}?)(?:\s+(?:via|on|through|using|ref|upi|vpa|\.|\$|\(|\)|-|,)|$)""", RegexOption.IGNORE_CASE),
            // "Rohit Sharma sent you ₹500" / "Rohit Sharma has paid"
            Regex("""([A-Za-z\s.]{2,30}?)\s+(?:has\s+)?(?:sent\s+you|paid\s+you|transferred|paid)\s+(?:₹|Rs|INR|[\d])""", RegexOption.IGNORE_CASE),
            // "VPA rohit@upi (Rohit Sharma)"
            Regex("""(?:VPA\s+[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\s*\()([A-Za-z\s.]{2,30}?)\)""", RegexOption.IGNORE_CASE),
            // "UPI/423456789012/Pooja Jain/Paytm" or "UPI/CR/423456789012/Sunita"
            Regex("""UPI\/(?:CR\/|P2A\/|P2P\/)?(?:\d+\/)?([A-Za-z\s.]{2,30}?)(?:\/|\.|\$|\n)""", RegexOption.IGNORE_CASE),
            // "UPI-ASHISH KUMAR-ashish@oksbi"
            Regex("""UPI-([A-Za-z\s.]{2,30}?)-(?:[a-zA-Z0-9@.]+)""", RegexOption.IGNORE_CASE),
            // "Info: UPI/423456789012/Pooja Jain"
            Regex("""Info:\s*UPI\/(?:\d+\/)?([A-Za-z\s.]{2,30}?)(?:\/|\.|\$|\n)""", RegexOption.IGNORE_CASE),
            // "by Suresh Kumar UPI Ref"
            Regex("""by\s+([A-Za-z\s.]{2,30}?)(?:\s+UPI|\s+Ref|\s+on|\s+a\/c|\.|\$|$)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null && match.groupValues[1].length >= 2) {
                val cleaned = sanitizeAndFormatName(match.groupValues[1])
                if (cleaned != null) {
                    return cleaned
                }
            }
        }

        // If not found in body, check fallback title (e.g. Google Pay notification title = sender name)
        if (!fallbackTitle.isNullOrBlank()) {
            val cleanedTitle = sanitizeAndFormatName(fallbackTitle)
            if (cleanedTitle != null) {
                return cleanedTitle
            }
        }

        return null
    }

    fun extractRefId(text: String): String? {
        val patterns = listOf(
            Regex("""(?:UPI\s*Ref(?:\s*No)?|UPI\s*Reference(?:\s*No)?|Ref\s*No|Reference\s*No|RRN|UTR)[\s/:-]+(\d{12})\b""", RegexOption.IGNORE_CASE),
            Regex("""(?:UPI|IMPS|NEFT)[\/](?:CR\/|P2A\/|P2P\/)?(\d{12})\b""", RegexOption.IGNORE_CASE),
            Regex("""(?:Ref(?:\s*No)?|Txn\s*Id|Transaction\s*Id)[\s/:-]+([A-Za-z0-9]{8,18})\b""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    private fun sanitizeAndFormatName(raw: String): String? {
        var name = raw.trim()
        
        // If it contains phone number slash name: "9876543210/Rajesh" or "Rajesh / 9876543210"
        if (name.contains("/")) {
            val parts = name.split("/")
            val candidate = parts.firstOrNull { it.trim().matches(Regex("""[A-Za-z\s.]{2,}""")) }
            if (candidate != null) {
                name = candidate.trim()
            }
        }

        // Remove email/VPA addresses (e.g. user@okhdfcbank)
        name = name.replace(Regex("""\([a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\)"""), "")
        name = name.replace(Regex("""[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+"""), "")

        // Remove unwanted trailing noise words
        val noiseWords = listOf("via", "on", "using", "through", "upi", "gpay", "phonepe", "paytm", "bhim", "cred", "amazon", "ref", "ref no", "vpa")
        for (nw in noiseWords) {
            name = name.replace(Regex("""(?i)\b$nw\b.*$"""), "")
        }

        // Strip non-letter characters from ends
        name = name.trim { !it.isLetter() }

        if (name.length < 2 || name.length > 35) return null

        // Check if name is purely numeric or special chars
        if (!name.any { it.isLetter() }) return null

        // Check against known system/banking words
        val lowerWords = name.lowercase().split(Regex("""\s+"""))
        if (lowerWords.any { NON_NAME_KEYWORDS.contains(it) } && lowerWords.size == 1) {
            return null
        }
        if (lowerWords.all { NON_NAME_KEYWORDS.contains(it) }) {
            return null
        }

        // Format to Title Case for smooth and natural TTS pronunciation
        return name.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase(Locale.getDefault())
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
    }
}

