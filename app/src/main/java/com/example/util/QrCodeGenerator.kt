package com.example.util

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Lightweight, zero-dependency QR Code generator for UPI payment URLs.
 * Implements ISO/IEC 18004 standard QR Code generation for Byte Mode.
 */
object QrCodeGenerator {

    fun generateUpiUrl(vpa: String, name: String, amount: Double? = null): String {
        val cleanVpa = vpa.trim()
        val cleanName = name.trim().replace(" ", "%20")
        return buildString {
            append("upi://pay?pa=").append(cleanVpa)
            append("&pn=").append(cleanName)
            append("&cu=INR")
            if (amount != null && amount > 0) {
                append("&am=").append(String.format(java.util.Locale.US, "%.2f", amount))
            }
        }
    }

    fun createQrBitmap(content: String, sizePx: Int = 512): Bitmap {
        val matrix = SimpleQrEncoder.encode(content)
        val matrixSize = matrix.size
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val scale = sizePx / matrixSize
        val offset = (sizePx - (scale * matrixSize)) / 2

        // Fill background white
        val pixels = IntArray(sizePx * sizePx) { Color.WHITE }

        for (r in 0 until matrixSize) {
            for (c in 0 until matrixSize) {
                if (matrix[r][c]) {
                    val startX = offset + c * scale
                    val startY = offset + r * scale
                    for (py in startY until (startY + scale)) {
                        for (px in startX until (startX + scale)) {
                            if (px in 0 until sizePx && py in 0 until sizePx) {
                                pixels[py * sizePx + px] = Color.BLACK
                            }
                        }
                    }
                }
            }
        }

        bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        return bitmap
    }
}

/**
 * Minimalist QR code matrix encoder supporting Byte Mode (ISO 18004).
 */
private object SimpleQrEncoder {

    // Reed-Solomon GF(256) Log and Exp tables
    private val EXP_TABLE = IntArray(512)
    private val LOG_TABLE = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP_TABLE[i] = x
            EXP_TABLE[i + 255] = x
            LOG_TABLE[x] = i
            x = (x shl 1) xor if (x and 0x80 != 0) 0x11D else 0
        }
    }

    private fun gfMul(x: Int, y: Int): Int {
        if (x == 0 || y == 0) return 0
        return EXP_TABLE[LOG_TABLE[x] + LOG_TABLE[y]]
    }

    fun encode(data: String): Array<BooleanArray> {
        val bytes = data.toByteArray(Charsets.ISO_8859_1)
        val dataLen = bytes.size

        // Determine smallest QR version (V2 = 25x25 up to 32 bytes, V3 = 29x29 up to 55 bytes, V4 = 33x33 up to 80 bytes, V5 = 37x37 up to 108 bytes, V6 = 41x41 up to 136 bytes)
        val (version, totalCodewords, dataCodewords, ecCodewords) = when {
            dataLen <= 26 -> Quad(2, 44, 28, 16)
            dataLen <= 42 -> Quad(3, 70, 44, 26)
            dataLen <= 62 -> Quad(4, 100, 64, 36)
            dataLen <= 84 -> Quad(5, 134, 86, 48)
            dataLen <= 106 -> Quad(6, 172, 108, 64)
            dataLen <= 130 -> Quad(7, 196, 124, 72)
            else -> Quad(8, 242, 154, 88)
        }

        val size = 17 + version * 4
        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        // Draw Finder Patterns
        fun drawFinder(top: Int, left: Int) {
            for (r in -1..7) {
                for (c in -1..7) {
                    val row = top + r
                    val col = left + c
                    if (row in 0 until size && col in 0 until size) {
                        isFunction[row][col] = true
                        val isBlack = (r in 0..6 && (c == 0 || c == 6)) ||
                                      (c in 0..6 && (r == 0 || r == 6)) ||
                                      (r in 2..4 && c in 2..4)
                        modules[row][col] = isBlack
                    }
                }
            }
        }

        drawFinder(0, 0)
        drawFinder(0, size - 7)
        drawFinder(size - 7, 0)

        // Alignment Pattern for Version >= 2
        val alignPos = when (version) {
            2 -> listOf(6, 18)
            3 -> listOf(6, 22)
            4 -> listOf(6, 26)
            5 -> listOf(6, 30)
            6 -> listOf(6, 34)
            7 -> listOf(6, 22, 38)
            8 -> listOf(6, 24, 42)
            else -> listOf(6, 18)
        }

        for (r in alignPos) {
            for (c in alignPos) {
                if (isFunction[r][c]) continue
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        val row = r + dr
                        val col = c + dc
                        isFunction[row][col] = true
                        modules[row][col] = (dr == -2 || dr == 2 || dc == -2 || dc == 2 || (dr == 0 && dc == 0))
                    }
                }
            }
        }

        // Timing patterns
        for (i in 8 until (size - 8)) {
            isFunction[6][i] = true
            modules[6][i] = (i % 2 == 0)
            isFunction[i][6] = true
            modules[i][6] = (i % 2 == 0)
        }

        // Dark module
        isFunction[size - 8][8] = true
        modules[size - 8][8] = true

        // Reserve Format info areas
        for (i in 0..8) {
            isFunction[8][i] = true
            isFunction[i][8] = true
        }
        for (i in 0..7) {
            isFunction[8][size - 1 - i] = true
            isFunction[size - 1 - i][8] = true
        }

        // Encode Bitstream: Byte Mode (0100) + Character Count + Data + Terminator
        val bitBuffer = mutableListOf<Int>()
        fun putBits(value: Int, numBits: Int) {
            for (i in numBits - 1 downTo 0) {
                bitBuffer.add((value ushr i) and 1)
            }
        }

        putBits(0b0100, 4) // Byte Mode
        putBits(dataLen, 8) // Length for V1-9 byte mode is 8 bits
        for (b in bytes) {
            putBits(b.toInt() and 0xFF, 8)
        }

        // Terminator up to 4 zeroes
        val padTarget = dataCodewords * 8
        val termLen = (padTarget - bitBuffer.size).coerceIn(0, 4)
        repeat(termLen) { bitBuffer.add(0) }

        // Byte alignment
        while (bitBuffer.size % 8 != 0) {
            bitBuffer.add(0)
        }

        // Pad bytes (0xEC, 0x11)
        var padToggle = 0
        while (bitBuffer.size < padTarget) {
            val padByte = if (padToggle == 0) 0xEC else 0x11
            putBits(padByte, 8)
            padToggle = 1 - padToggle
        }

        // Convert data bits to bytes
        val dataBytes = IntArray(dataCodewords)
        for (i in 0 until dataCodewords) {
            var b = 0
            for (j in 0 until 8) {
                b = (b shl 1) or bitBuffer[i * 8 + j]
            }
            dataBytes[i] = b
        }

        // Reed-Solomon Error Correction Code
        val ecBytes = calculateReedSolomon(dataBytes, ecCodewords)
        val allCodewords = dataBytes + ecBytes

        // Place Data Codewords in zigzag pattern
        var bitIndex = 0
        val totalBits = allCodewords.size * 8

        var right = size - 1
        var upward = true

        while (right > 0) {
            if (right == 6) right-- // Skip vertical timing column

            val rows = if (upward) (size - 1 downTo 0) else (0 until size)
            for (row in rows) {
                for (colOffset in 0..1) {
                    val col = right - colOffset
                    if (!isFunction[row][col]) {
                        var bit = 0
                        if (bitIndex < totalBits) {
                            val byteIdx = bitIndex / 8
                            val bitInByte = 7 - (bitIndex % 8)
                            bit = (allCodewords[byteIdx] ushr bitInByte) and 1
                            bitIndex++
                        }
                        // Apply Mask Pattern 0: (row + col) % 2 == 0
                        val mask = (row + col) % 2 == 0
                        modules[row][col] = (bit == 1) xor mask
                    }
                }
            }
            right -= 2
            upward = !upward
        }

        // Write Format Information (Mask 000, ECC Level L: 01)
        // Format bits for L-0: 0b111011111000100
        val formatBits = 0b111011111000100
        for (i in 0..14) {
            val bit = (formatBits ushr (14 - i)) and 1 == 1
            if (i < 6) modules[8][i] = bit
            else if (i == 6) modules[8][7] = bit
            else if (i == 7) modules[8][8] = bit
            else if (i == 8) modules[7][8] = bit
            else modules[14 - i][8] = bit

            if (i < 8) modules[size - 1 - i][8] = bit
            else modules[8][size - 15 + i] = bit
        }

        return modules
    }

    private fun calculateReedSolomon(data: IntArray, ecCount: Int): IntArray {
        // Generator polynomial for ecCount
        var gen = intArrayOf(1)
        for (i in 0 until ecCount) {
            val factor = intArrayOf(1, EXP_TABLE[i])
            val nextGen = IntArray(gen.size + 1)
            for (j in gen.indices) {
                for (k in factor.indices) {
                    nextGen[j + k] = nextGen[j + k] xor gfMul(gen[j], factor[k])
                }
            }
            gen = nextGen
        }

        val remainder = IntArray(ecCount)
        for (b in data) {
            val factor = b xor remainder[0]
            for (i in 0 until ecCount - 1) {
                remainder[i] = remainder[i + 1] xor gfMul(gen[i + 1], factor)
            }
            remainder[ecCount - 1] = gfMul(gen[ecCount], factor)
        }
        return remainder
    }

    private data class Quad(val v: Int, val tc: Int, val dc: Int, val ec: Int)
}
