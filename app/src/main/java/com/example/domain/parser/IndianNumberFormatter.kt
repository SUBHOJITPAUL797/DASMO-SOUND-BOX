package com.example.domain.parser

import java.util.Locale
import kotlin.math.roundToInt

object IndianNumberFormatter {

    private val onesEn = arrayOf(
        "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen",
        "sixteen", "seventeen", "eighteen", "nineteen"
    )
    private val tensEn = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    fun format(amount: Double, language: String = "en-IN"): String {
        val rupees = amount.toLong()
        val paise = ((amount - rupees) * 100).roundToInt()

        if (language.lowercase().startsWith("en")) {
            val rupeePart = numberToWordsEn(rupees)
            return if (paise > 0) {
                val paisePart = numberToWordsEn(paise.toLong())
                "$rupeePart rupees and $paisePart paise"
            } else {
                rupeePart
            }
        }

        // For regional languages (Hindi, Bengali, Marathi, Tamil, Telugu, Gujarati, Kannada, Punjabi, Malayalam),
        // providing clean numeric values allows native TTS engines to speak numbers in their native accent & words.
        return if (paise > 0) {
            String.format(Locale.US, "%.2f", amount)
        } else {
            rupees.toString()
        }
    }

    private fun numberToWordsEn(n: Long): String {
        if (n == 0L) return "zero"
        if (n < 0) return "minus ${numberToWordsEn(-n)}"
        
        return when {
            n < 20 -> onesEn[n.toInt()]
            n < 100 -> "${tensEn[(n / 10).toInt()]}${if (n % 10 > 0) " ${onesEn[(n % 10).toInt()]}" else ""}"
            n < 1000 -> "${onesEn[(n / 100).toInt()]} hundred${if (n % 100 > 0) " ${numberToWordsEn(n % 100)}" else ""}"
            n < 100_000 -> "${numberToWordsEn(n / 1000)} thousand${if (n % 1000 > 0) " ${numberToWordsEn(n % 1000)}" else ""}"
            n < 10_000_000 -> "${numberToWordsEn(n / 100_000)} lakh${if (n % 100_000 > 0) " ${numberToWordsEn(n % 100_000)}" else ""}"
            else -> "${numberToWordsEn(n / 10_000_000)} crore${if (n % 10_000_000 > 0) " ${numberToWordsEn(n % 10_000_000)}" else ""}"
        }
    }
}
