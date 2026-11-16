package com.example.domain.parser

import com.example.domain.model.ParseResult
import com.example.util.KnownPaymentApps
import java.util.Locale

object PaymentParser {

    private val DEBIT_KEYWORDS = listOf(
        "debited", "deducted", "amount debited", "debited from", "paid from your", "paid from a/c",
        "transferred from your", "sent from your", "you paid", "you sent", "you have paid", "you have sent",
        "failed", "declined", "reversed", "refund initiated", "purchase of", "bill payment",
        "dr in", "dr.", "paid at", "spent on", "withdrawn"
    )

    private val REQUEST_KEYWORDS = listOf(
        "requested", "payment request", "requests", "request from", "request to",
        "has requested", "requested you", "collect request", "request for",
        "is requesting", "asked for", "requesting ₹", "requesting rs"
    )

    /**
     * Rejects promotional notifications, WhatsApp/SMS loan offers, deposit/FD/investment alerts,
     * cashback/scratch-card offers, bill-due reminders, autopay mandates, OTPs, and failed/pending states.
     */
    private val PROMO_LOAN_EXCLUDE_REGEX = Regex(
        """(?i)(?:\b(?:loan|loans|pre-?approved|preapproved|credit\s+limit|credit\s+line|credit\s+score|cibil|pay\s*later|borrow|emi|sanctioned|eligibility|eligible\s+for|apply\s+now|claim\s+now|avail\s+now|unlock\s+up\s+to|cashback\s+up\s+to|win\s+up\s+to|earn\s+up\s+to|get\s+up\s+to|up\s+to\s*(?:₹|rs\.?|inr)|flat\s*(?:₹|rs\.?|inr)|scratch\s*card|voucher|coupon|jackpot|refer\s*(?:&|and)\s*earn|referral|discount|%\s*off|offer|offers|limited\s+(?:time|period)|festive\s+deal|fixed\s+deposit|recurring\s+deposit|term\s+deposit|security\s+deposit|minimum\s+deposit|initial\s+deposit|deposit\s+now|make\s+a\s+deposit|deposit\s+money|deposit\s+to|deposit\s+of|deposit\s+alert|deposit\s+successful|fd\s+booked|fd\s+matured|add\s+money\s+to|mutual\s+fund|sip\s+of|insurance|premium\s+due|bill\s+due|payment\s+due|due\s+date|due\s+on|overdue|pay\s+before|pay\s+by|autopay|mandate|upcoming\s+payment|scheduled\s+payment|reminder|recharge\s+now|expiring|expires\s+on|otp|one\s+time\s+password|verification\s+code|interest\s+rate|processing\s+fee|kyc\s+pending|complete\s+kyc|pending|processing|initiated|cancelled|canceled|unsuccessful|timed\s+out|congratulations|you\s+won|won\s+(?:₹|rs))\b|लोन|ऋण|ऑफर|कैशबैक|ब्याज|किस्त|ईएमआई|बकाया|ओटीपी)"""
    )

    /**
     * Rejects standalone "deposit" prompts (e.g. "Deposit ₹3500...") while still allowing genuine
     * bank credit wording like "Rs 5000 deposited in your A/c XX1234".
     */
    private val STANDALONE_DEPOSIT_NOUN_REGEX = Regex("""(?i)\bdeposit\b""")

    private val BALANCE_ONLY_REGEX = Regex(
        """(?i)\b(?:available\s+balance|avl\.?\s+bal|account\s+balance|wallet\s+balance|total\s+balance|check\s+balance)\b"""
    )

    private val CREDIT_KEYWORD_REGEX = Regex(
        """(?i)(?:\b(?:received|credited|added\s+to|sent\s+to\s+your|sent\s+to\s+you|paid\s+to\s+you|paid\s+to\s+your|transferred\s+to\s+you|transferred\s+to\s+your|money\s+received|amount\s+received|payment\s+received|cr|gained|sent\s+you|paid\s+you|loaded\s+with|money\s+in|payment\s+from|received\s+from|received\s+in\s+your|credited\s+to|credited\s+in|deposited\s+in\s+your|deposited\s+to\s+your|has\s+been\s+deposited)\b|प्राप्त|मिले|भेजे|जमा\s+(?:हुए|हुआ|किए))"""
    )

    private val NON_NAME_KEYWORDS = setOf(
        "account", "acct", "a/c", "bank", "user", "customer", "upi", "payment",
        "soundbox", "credited", "received", "balance", "available", "wallet",
        "merchant", "phonepe", "google", "gpay", "paytm", "bhim", "cred",
        "amazon", "axis", "hdfc", "sbi", "icici", "kotak", "pnb", "bob",
        "canara", "union", "alert", "notification", "message", "transfer",
        "money", "your", "inr", "rs", "rupees", "ref", "vpa", "txn",
        "online", "instant", "successful", "success", "request", "order",
        "business", "bharatpe", "super", "supermoney", "navi", "tataneu",
        "paid", "sent", "done", "paisa", "loan", "offer", "deposit"
    )

    // =========================================================================
    // PRE-COMPILED STATIC REGEX CONSTANTS (ELIMINATES DYNAMIC RUNTIME OVERHEAD)
    // =========================================================================

    private val NAME_FIRST_PATTERN = Regex(
        """(?:^|[\n\r]|(?<=\s))([\p{L}\p{M}0-9\s.]{2,40}?)(?:\s*\([a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\))?\s+(?:has\s+)?(?:sent|paid)\s+(?:you\s+)?(?:₹\s*|Rs\.?\s*|INR\s*)([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val HINDI_NAME_FIRST_PATTERN = Regex(
        """([^\n\r]+?)\s+ने\s+(?:₹\s*|Rs\.?\s*)([\d,]+(?:\.\d{1,2})?)\s+भेजे""",
        RegexOption.IGNORE_CASE
    )

    private val HINDI_FROM_PATTERN = Regex(
        """([^\n\r]+?)\s+से\s+(?:₹\s*|Rs\.?\s*)([\d,]+(?:\.\d{1,2})?)\s+(?:प्राप्त\s+हुए?|मिले)""",
        RegexOption.IGNORE_CASE
    )

    private val RECEIVED_FROM_PATTERN = Regex(
        """(?:(?:money|payment|amount)\s+)?(?:received|credited)(?:\s+(?:payment|money))?\s+from\s+([^\n\r:–-]+?)\s*[:–-]?\s*(?:₹\s*|Rs\.?\s*|INR\s*|rupees?\s*)([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val CREDIT_PATTERNS = listOf(
        // "credited by/with/of Rs 500" or "credited with INR 500.00"
        Regex("""(?:credited|credit)\s+(?:by|with|of|for)?\s*(?:₹\s*|Rs\.?\s*|INR\s*|rupees?\s*)([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // "Rs 500 has been credited/received" or "Rs 500 successfully credited/received"
        Regex("""(?:₹\s*|Rs\.?\s*|INR\s*)([\d,]+(?:\.\d{1,2})?)\s+(?:has\s+been\s+)?(?:successfully\s+)?(?:credited|added\s+to\s+your|deposited\s+(?:in|to)\s+your|received)(?:\s+successfully)?""", RegexOption.IGNORE_CASE),
        // "received / money received / payment received: Rs 500"
        Regex("""(?:(?:payment|money|amount)\s+)?(?:received|got|credited|you've\s+received|you\s+have\s+received)\s*[:!–-]?\s*(?:a\s+)?(?:payment\s+of\s+)?(?:of\s+)?(?:₹\s*|Rs\.?\s*|INR\s*|rupees?\s*)([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // "payment/money/amount received: ₹500"
        Regex("""(?:payment|money|amount)\s+received\s*[:!–-]?\s*(?:of|for)?\s*(?:₹\s*|Rs\.?\s*|INR\s*|rupees?\s*)([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // "Rs 500 received / sent to your / paid to your / transferred to your a/c"
        Regex("""(?:₹\s*|Rs\.?\s*|INR\s*)([\d,]+(?:\.\d{1,2})?)\s+(?:has\s+been\s+)?(?:received|sent\s+to\s+your|paid\s+to\s+your|deposited\s+(?:in|to)\s+your|added\s+to\s+your|transferred\s+to\s+your)""", RegexOption.IGNORE_CASE),
        // "sent/transferred/paid ₹ 500 to your account"
        Regex("""(?:sent|transferred|paid)\s+(?:₹\s*|Rs\.?\s*|INR\s*)([\d,]+(?:\.\d{1,2})?)\s+(?:to\s+your|to\s+you)""", RegexOption.IGNORE_CASE),
        // "₹ 500 received from Rohit Sharma"
        Regex("""(?:₹\s*|Rs\.?\s*|INR\s*)([\d,]+(?:\.\d{1,2})?)\s+(?:received\s+)?(?:from)\s+([\p{L}\p{M}0-9\s.]{2,40})""", RegexOption.IGNORE_CASE),
        // "a/c ending *1234 credited with Rs 500"
        Regex("""a\/c\s*[\w*]*\s*(?:is|has\s+been)?\s*credited\s*(?:by|with|of)?\s*(?:₹\s*|Rs\.?\s*|INR\s*)?([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // "UPI/CR/500" or "IMPS CR Rs. 500"
        Regex("""(?:UPI|NEFT|IMPS|RTGS)[\/\s]*(?:CR|CREDIT)[\/\s:]*(?:₹\s*|Rs\.?\s*|INR\s*)?([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // "sent you Rs. 500" / "paid you Rs 500" / "transferred you ₹ 500"
        Regex("""(?:sent\s+you|paid\s+you|transferred\s+you|paid\s+to\s+you)\s*(?:₹\s*|Rs\.?\s*|INR\s*|rupees?\s*)([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // "Kotak: Rs 500 credited"
        Regex("""Kotak.*?(?:₹\s*|Rs\.?\s*|INR\s*)([\d,]+(?:\.\d{1,2})?)\s+(?:credited|received)""", RegexOption.IGNORE_CASE),
        // "BHIM: ₹ 500 received" or "BHIM UPI: ₹ 500 received"
        Regex("""(?:BHIM|BHIM\s+UPI|UPI)[\s:–-]+(?:₹\s*|Rs\.?\s*|INR\s*)?([\d,]+(?:\.\d{1,2})?)\s*(?:₹\s*|Rs\.?\s*|INR\s*)?\s*(?:received|credited)""", RegexOption.IGNORE_CASE),
        // "500.00 rupees received / credited"
        Regex("""([\d,]+(?:\.\d{1,2})?)\s*(?:₹\s*|rupees?|inr|rs\.?)\s+(?:received|credited)""", RegexOption.IGNORE_CASE),
        // "Payment of Rs 500.00 received"
        Regex("""(?:Payment|Money)\s+of\s+(?:₹\s*|Rs\.?\s*|INR\s*)([\d,]+(?:\.\d{1,2})?)\s+received""", RegexOption.IGNORE_CASE),
        // "Money in! ₹500"
        Regex("""(?:Money\s+in!?|Loaded\s+with)\s*(?:₹\s*|Rs\.?\s*|INR\s*)([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // "credited to account ... ₹500"
        Regex("""(?:credited\s+(?:to|in|into|with|for)\s+[^0-9\n]{0,35}?)(?:₹\s*|Rs\.?\s*|INR\s*|rupees?\s*)([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        // Hindi: "₹ 500 प्राप्त हुए / प्राप्त हुआ / मिले / जमा हुए"
        Regex("""(?:₹\s*|Rs\.?\s*)([\d,]+(?:\.\d{1,2})?)\s+(?:प्राप्त\s+हुए?|मिले|जमा\s+(?:हुए?|किए\s+गए))(?:\s+हैं?)?""", RegexOption.IGNORE_CASE),
        // Hindi: "भुगतान प्राप्त हुआ: ₹ 500"
        Regex("""(?:भुगतान|रुपये|पैसे)\s+प्राप्त\s+हुए?\s*[:!–-]?\s*(?:₹\s*|Rs\.?\s*)([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    )

    private val APP_AMOUNT_PATTERN = Regex(
        """(?:₹\s*|Rs\.?\s*|INR\s*)([\d,]+(?:\.\d{1,2})?)|([\d,]+(?:\.\d{1,2})?)\s*(?:₹|Rs\.?|INR|rupees?)""",
        RegexOption.IGNORE_CASE
    )

    private val SECONDARY_AMOUNT_PATTERNS = listOf(
        Regex("""(?:₹\s*|Rs\.?\s*|INR\s*)([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""([\d,]+(?:\.\d{1,2})?)\s*(?:₹\s*|Rs\.?|rupees?)""", RegexOption.IGNORE_CASE)
    )

    private val PAYER_NAME_PATTERNS = listOf(
        Regex("""(?:from|by\s+transfer\s+from|payment\s+from|transfer\s+from)\s+([\p{L}\p{M}0-9\s.]{2,40}?)(?:\s+(?:via|on|through|using|with|ref|upi|vpa|a\/c|acct|balance|bal|txn|trans|at|dated|for|rupees?)\b|\s*[:–-]?\s*(?:₹|Rs\.?|INR)\b|\s*[:–-]\s*|\s*[\(\)]|\.|\(|-|,|$)""", RegexOption.IGNORE_CASE),
        Regex("""(?:sent|paid)\s+by\s+([\p{L}\p{M}0-9\s.]{2,40}?)(?:\s+(?:via|on|through|using|ref|upi|vpa|rupees?)\b|\s*[:–-]?\s*(?:₹|Rs\.?|INR)\b|\s*[:–-]\s*|\s*[\(\)]|\.|\(|-|,|$)""", RegexOption.IGNORE_CASE),
        Regex("""^([\p{L}\p{M}\s.]{2,30}?)(?:\s*\([a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\))?\s+(?:has\s+)?(?:sent\s+you|paid\s+you|transferred|paid|sent)\s+(?:₹|Rs|INR|[\d])""", RegexOption.IGNORE_CASE),
        Regex("""(?:VPA\s+[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\s*\()([\p{L}\p{M}\s.]{2,30}?)\)""", RegexOption.IGNORE_CASE),
        Regex("""UPI\/(?:CR\/|P2A\/|P2P\/)?(?:\d+\/)?([\p{L}\p{M}\s.]{2,30}?)(?:\/|\.|\$|\n)""", RegexOption.IGNORE_CASE),
        Regex("""UPI-([\p{L}\p{M}\s.]{2,30}?)-(?:[a-zA-Z0-9@.]+)""", RegexOption.IGNORE_CASE),
        Regex("""Info:\s*UPI\/(?:\d+\/)?([\p{L}\p{M}\s.]{2,30}?)(?:\/|\.|\$|\n)""", RegexOption.IGNORE_CASE),
        Regex("""by\s+([\p{L}\p{M}\s.]{2,30}?)(?:\s+UPI|\s+Ref|\s+on|\s+a\/c|\.|\$|$)""", RegexOption.IGNORE_CASE),
        Regex("""([^\n\r]+?)\s+ने\s+(?:₹\s*|Rs\.?\s*|INR\s*)""", RegexOption.IGNORE_CASE),
        Regex("""([^\n\r]+?)\s+से\s+(?:₹\s*|Rs\.?\s*|INR\s*|भुगतान)""", RegexOption.IGNORE_CASE)
    )

    private val REF_ID_PATTERNS = listOf(
        Regex("""(?:UPI\s*Ref(?:\s*No)?|UPI\s*Reference(?:\s*No)?|Ref\s*No|Reference\s*No|RRN|UTR)[\s/:-]+(\d{12})\b""", RegexOption.IGNORE_CASE),
        Regex("""(?:UPI|IMPS|NEFT)[\/](?:CR\/|P2A\/|P2P\/)?(\d{12})\b""", RegexOption.IGNORE_CASE),
        Regex("""(?:Ref(?:\s*No)?|Txn\s*Id|Transaction\s*Id)[\s/:-]+([A-Za-z0-9]{8,18})\b""", RegexOption.IGNORE_CASE)
    )

    private val NAME_CANDIDATE_REGEX = Regex("""[\p{L}\p{M}\s.]{2,}""")
    private val VPA_PAREN_REGEX = Regex("""\([a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+\)""")
    private val VPA_ANGLE_REGEX = Regex("""<[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+>""")
    private val VPA_DASH_REGEX = Regex("""[-–]\s*[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+""")
    private val VPA_PLAIN_REGEX = Regex("""[a-zA-Z0-9._-]+@[a-zA-Z0-9.-]+""")

    private val COMBINED_NOISE_REGEX = Regex(
        """(?i)\b(?:via|on|using|through|upi|gpay|phonepe|paytm|bhim|cred|amazon|ref(?:\s*no)?|vpa)\b.*$"""
    )

    private val WHITESPACE_REGEX = Regex("""\s+""")

    fun parse(text: String, fallbackTitle: String? = null, packageName: String? = null): ParseResult {
        val cleanText = text.replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .replace('\u200B', ' ')
            .replace('\uFEFF', ' ')
            .trim()

        if (cleanText.isBlank()) {
            return ParseResult(amount = null, isCredit = false, rawText = text)
        }

        val cleanFallbackTitle = fallbackTitle?.replace('\u00A0', ' ')
            ?.replace('\u202F', ' ')
            ?.replace('\u200B', ' ')
            ?.trim()

        val normalized = cleanText.lowercase().replace("credit card", "cc_card")

        // 1. Strict rejection of payment requests
        if (REQUEST_KEYWORDS.any { normalized.contains(it) }) {
            return ParseResult(amount = null, isCredit = false, rawText = text)
        }

        // 2. Strict rejection of loans, promotional offers, deposit prompts, FD/RD, bills, OTPs, and pending/failed alerts
        if (PROMO_LOAN_EXCLUDE_REGEX.containsMatchIn(cleanText) || STANDALONE_DEPOSIT_NOUN_REGEX.containsMatchIn(cleanText)) {
            return ParseResult(amount = null, isCredit = false, rawText = text)
        }

        // 3. Reject credit card alerts unless explicitly credited to bank A/c (which never contains cc_card without debit/statement)
        if (normalized.contains("cc_card") && !normalized.contains("credited to")) {
            return ParseResult(amount = null, isCredit = false, rawText = text)
        }

        val hasDebitKeyword = DEBIT_KEYWORDS.any { normalized.contains(it) }
        val hasCreditKeyword = CREDIT_KEYWORD_REGEX.containsMatchIn(cleanText)

        // 4. Strict debit exclusion
        if (hasDebitKeyword && !hasCreditKeyword) {
            return ParseResult(amount = null, isCredit = false, rawText = text)
        }

        // 5. Reject balance-only check alerts (e.g., "Available Balance ₹5,000") when no credit keyword is present
        if (BALANCE_ONLY_REGEX.containsMatchIn(cleanText) && !hasCreditKeyword) {
            return ParseResult(amount = null, isCredit = false, rawText = text)
        }

        // 6. Specific pattern: "Rohit Sharma sent you ₹ 500" or "Rohit Sharma paid ₹ 500"
        if (normalized.contains("sent") || normalized.contains("paid")) {
            val nameFirstMatch = NAME_FIRST_PATTERN.find(cleanText)
            if (nameFirstMatch != null) {
                val potentialName = sanitizeAndFormatName(nameFirstMatch.groupValues[1])
                val amount = nameFirstMatch.groupValues[2].replace(",", "").toDoubleOrNull()
                if (amount != null && amount > 0) {
                    val refId = extractRefId(cleanText)
                    return ParseResult(
                        amount = amount,
                        isCredit = true,
                        payerName = potentialName ?: extractPayerName(cleanText, cleanFallbackTitle),
                        refId = refId,
                        rawText = text
                    )
                }
            }
        }

        // 7. Specific Hindi pattern: "रोहित शर्मा ने ₹ 500 भेजे"
        if (cleanText.contains("भेजे")) {
            val hindiMatch = HINDI_NAME_FIRST_PATTERN.find(cleanText)
            if (hindiMatch != null) {
                val rawName = hindiMatch.groupValues[1].trim()
                val amount = hindiMatch.groupValues[2].replace(",", "").toDoubleOrNull()
                if (amount != null && amount > 0) {
                    val refId = extractRefId(cleanText)
                    return ParseResult(
                        amount = amount,
                        isCredit = true,
                        payerName = sanitizeAndFormatName(rawName) ?: rawName,
                        refId = refId,
                        rawText = text
                    )
                }
            }
        }

        // 8. Specific Hindi pattern with "से" (from): "रोहित शर्मा से ₹ 500 प्राप्त हुए"
        if (cleanText.contains("से") && (cleanText.contains("प्राप्त") || cleanText.contains("मिले"))) {
            val hindiFromMatch = HINDI_FROM_PATTERN.find(cleanText)
            if (hindiFromMatch != null) {
                val rawName = hindiFromMatch.groupValues[1].trim()
                val amount = hindiFromMatch.groupValues[2].replace(",", "").toDoubleOrNull()
                if (amount != null && amount > 0) {
                    val refId = extractRefId(cleanText)
                    return ParseResult(
                        amount = amount,
                        isCredit = true,
                        payerName = sanitizeAndFormatName(rawName) ?: rawName,
                        refId = refId,
                        rawText = text
                    )
                }
            }
        }

        // 9. Specific pattern: "Received from Rohit Sharma ₹ 500"
        if (normalized.contains("from") && (normalized.contains("received") || normalized.contains("credited"))) {
            val receivedFromMatch = RECEIVED_FROM_PATTERN.find(cleanText)
            if (receivedFromMatch != null) {
                val potentialName = sanitizeAndFormatName(receivedFromMatch.groupValues[1])
                val amount = receivedFromMatch.groupValues[2].replace(",", "").toDoubleOrNull()
                if (amount != null && amount > 0) {
                    val refId = extractRefId(cleanText)
                    return ParseResult(
                        amount = amount,
                        isCredit = true,
                        payerName = potentialName ?: extractPayerName(cleanText, cleanFallbackTitle),
                        refId = refId,
                        rawText = text
                    )
                }
            }
        }

        // 10. Explicit credit patterns
        for (pattern in CREDIT_PATTERNS) {
            val match = pattern.find(cleanText)
            if (match != null) {
                val raw = match.groupValues[1].replace(",", "")
                val amount = raw.toDoubleOrNull()
                if (amount != null && amount > 0) {
                    val payerName = extractPayerName(cleanText, cleanFallbackTitle)
                    val refId = extractRefId(cleanText)
                    return ParseResult(amount = amount, isCredit = true, payerName = payerName, refId = refId, rawText = text)
                }
            }
        }

        // 11. High-confidence UPI-only payment app fallback:
        // ONLY allowed for verified pure UPI/merchant packages (NEVER WhatsApp, SMS, or loan/promo apps)
        val isPureUpiPackage = packageName != null && (
            KnownPaymentApps.TRUSTED_UPI_ONLY_PACKAGES.contains(packageName) ||
                packageName.contains("npci") ||
                packageName.contains("bhim") ||
                packageName.contains("paisa")
            )

        if (isPureUpiPackage && !hasDebitKeyword) {
            val match = APP_AMOUNT_PATTERN.find(cleanText)
            if (match != null) {
                val rawGroup = if (match.groupValues[1].isNotBlank()) match.groupValues[1] else match.groupValues[2]
                val raw = rawGroup.replace(",", "")
                val amount = raw.toDoubleOrNull()
                if (amount != null && amount > 0) {
                    val payerName = extractPayerName(cleanText, cleanFallbackTitle)
                    val refId = extractRefId(cleanText)
                    return ParseResult(amount = amount, isCredit = true, payerName = payerName, refId = refId, rawText = text)
                }
            }
        }

        // 12. Secondary fallback only if a verified word-boundary credit keyword is present
        if (hasCreditKeyword && !hasDebitKeyword) {
            for (pattern in SECONDARY_AMOUNT_PATTERNS) {
                val match = pattern.find(cleanText)
                if (match != null) {
                    val raw = match.groupValues[1].replace(",", "")
                    val amount = raw.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        val payerName = extractPayerName(cleanText, cleanFallbackTitle)
                        val refId = extractRefId(cleanText)
                        return ParseResult(amount = amount, isCredit = true, payerName = payerName, refId = refId, rawText = text)
                    }
                }
            }
        }

        return ParseResult(amount = null, isCredit = false, rawText = text)
    }

    fun extractPayerName(text: String, fallbackTitle: String? = null): String? {
        val cleanText = text.replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .replace('\u200B', ' ')
            .replace('\uFEFF', ' ')
            .trim()

        val lowerText = cleanText.lowercase()

        for ((index, pattern) in PAYER_NAME_PATTERNS.withIndex()) {
            val shouldEvaluate = when (index) {
                0 -> lowerText.contains("from")
                1 -> lowerText.contains("by")
                2 -> lowerText.contains("sent") || lowerText.contains("paid") || lowerText.contains("transferred")
                3 -> lowerText.contains("vpa")
                4, 5, 6 -> lowerText.contains("upi")
                7 -> lowerText.contains("by")
                8 -> cleanText.contains("ने")
                9 -> cleanText.contains("से")
                else -> true
            }
            if (!shouldEvaluate) continue

            val match = pattern.find(cleanText)
            if (match != null && match.groupValues[1].length >= 2) {
                val cleaned = sanitizeAndFormatName(match.groupValues[1])
                if (cleaned != null) {
                    return cleaned
                }
            }
        }

        if (!fallbackTitle.isNullOrBlank()) {
            val cleanedTitle = sanitizeAndFormatName(fallbackTitle)
            if (cleanedTitle != null) {
                return cleanedTitle
            }
        }

        return null
    }

    fun extractRefId(text: String): String? {
        val cleanText = text.replace('\u00A0', ' ')
            .replace('\u202F', ' ')
            .replace('\u200B', ' ')
            .replace('\uFEFF', ' ')
            .trim()

        for (pattern in REF_ID_PATTERNS) {
            val match = pattern.find(cleanText)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    private fun sanitizeAndFormatName(raw: String): String? {
        var name = raw.trim()

        if (name.contains("/")) {
            val parts = name.split("/")
            val candidate = parts.firstOrNull { it.trim().matches(NAME_CANDIDATE_REGEX) }
            if (candidate != null) {
                name = candidate.trim()
            }
        }

        name = name.replace(VPA_PAREN_REGEX, "")
        name = name.replace(VPA_ANGLE_REGEX, "")
        name = name.replace(VPA_DASH_REGEX, "")
        name = name.replace(VPA_PLAIN_REGEX, "")

        name = name.replace(COMBINED_NOISE_REGEX, "")

        name = name.trim {
            !it.isLetterOrDigit() &&
                Character.getType(it).toByte() != Character.NON_SPACING_MARK &&
                Character.getType(it).toByte() != Character.COMBINING_SPACING_MARK
        }

        if (name.length < 2 || name.length > 35) return null
        if (!name.any { it.isLetter() }) return null

        val lowerWords = name.lowercase().split(WHITESPACE_REGEX)
        if (lowerWords.any { NON_NAME_KEYWORDS.contains(it) } && lowerWords.size == 1) {
            return null
        }
        if (lowerWords.all { NON_NAME_KEYWORDS.contains(it) }) {
            return null
        }

        return name.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase(Locale.getDefault())
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
    }
}
