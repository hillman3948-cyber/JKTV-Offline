package com.jktv.offline.remote

/**
 * Self-contained ISO/IEC 18004 QR Code Generator (Error Correction Level L, Byte Mode, Versions 1..6)
 * for offline Wi-Fi Remote Control pairing without external third-party dependencies.
 */
object QrCodeMatrix {

    // Total data codewords and ECC codewords (Single-block versions 1..6 at ECC Level L)
    private data class VersionSpec(
        val version: Int,
        val size: Int,
        val dataCodewords: Int,
        val eccCodewords: Int,
        val alignmentCenters: IntArray
    )

    private val specs = listOf(
        VersionSpec(1, 21, 19, 7, intArrayOf()),
        VersionSpec(2, 25, 34, 10, intArrayOf(6, 18)),
        VersionSpec(3, 29, 55, 15, intArrayOf(6, 22)),
        VersionSpec(4, 33, 80, 20, intArrayOf(6, 26)),
        VersionSpec(5, 37, 108, 26, intArrayOf(6, 30))
    )

    fun encodeUrl(text: String): Array<BooleanArray> {
        val payload = text.toByteArray(Charsets.UTF_8)
        // Byte mode header: 4 bits (0100) + 8 bits length + payload + 4 bits terminator = payload.size + 2 bytes
        val spec = specs.firstOrNull { payload.size + 2 <= it.dataCodewords } ?: specs.last()
        val maxPayload = spec.dataCodewords - 2
        val safeBytes = if (payload.size > maxPayload) payload.copyOf(maxPayload) else payload

        val bitBuffer = BitBuffer()
        bitBuffer.appendBits(0b0100, 4) // Byte mode indicator
        bitBuffer.appendBits(safeBytes.size, 8)
        for (b in safeBytes) {
            bitBuffer.appendBits(b.toInt() and 0xFF, 8)
        }
        val capacityBits = spec.dataCodewords * 8
        bitBuffer.appendBits(0, minOf(4, capacityBits - bitBuffer.bitLength))
        while (bitBuffer.bitLength % 8 != 0) {
            bitBuffer.appendBits(0, 1)
        }
        val padBytes = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (bitBuffer.bitLength < capacityBits) {
            bitBuffer.appendBits(padBytes[padIdx % 2], 8)
            padIdx++
        }

        val dataBytes = bitBuffer.toByteArray()
        val eccBytes = reedSolomonRemainder(dataBytes, spec.eccCodewords)
        val fullCodewords = ByteArray(dataBytes.size + eccBytes.size)
        System.arraycopy(dataBytes, 0, fullCodewords, 0, dataBytes.size)
        System.arraycopy(eccBytes, 0, fullCodewords, dataBytes.size, eccBytes.size)

        val size = spec.size
        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        drawFunctionPatterns(spec, modules, isFunction)
        drawCodewords(fullCodewords, size, modules, isFunction)

        // Pick best mask (0..7) with lowest penalty score
        var bestMask = 0
        var minPenalty = Int.MAX_VALUE
        for (mask in 0..7) {
            val copy = Array(size) { r -> modules[r].clone() }
            applyMask(mask, size, copy, isFunction)
            drawFormatBits(mask, size, copy, isFunction)
            val penalty = computePenalty(size, copy)
            if (penalty < minPenalty) {
                minPenalty = penalty
                bestMask = mask
            }
        }
        applyMask(bestMask, size, modules, isFunction)
        drawFormatBits(bestMask, size, modules, isFunction)
        return modules
    }

    private fun drawFunctionPatterns(
        spec: VersionSpec,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ) {
        val size = spec.size
        // Timing patterns
        for (i in 0 until size) {
            setFunctionModule(6, i, i % 2 == 0, modules, isFunction)
            setFunctionModule(i, 6, i % 2 == 0, modules, isFunction)
        }
        // Finder patterns (3 corners)
        drawFinderPattern(3, 3, size, modules, isFunction)
        drawFinderPattern(size - 4, 3, size, modules, isFunction)
        drawFinderPattern(3, size - 4, size, modules, isFunction)

        // Alignment patterns
        val align = spec.alignmentCenters
        for (r in align) {
            for (c in align) {
                if ((r == 6 && c == 6) || (r == 6 && c == size - 7) || (r == size - 7 && c == 6)) continue
                drawAlignmentPattern(r, c, modules, isFunction)
            }
        }
        // Reserve format bits areas
        drawFormatBits(0, size, modules, isFunction)
    }

    private fun drawFinderPattern(
        centerR: Int,
        centerC: Int,
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ) {
        for (dr in -4..4) {
            for (dc in -4..4) {
                val r = centerR + dr
                val c = centerC + dc
                if (r in 0 until size && c in 0 until size) {
                    val dist = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc))
                    val dark = dist != 2 && dist != 4
                    setFunctionModule(r, c, dark, modules, isFunction)
                }
            }
        }
    }

    private fun drawAlignmentPattern(
        centerR: Int,
        centerC: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ) {
        for (dr in -2..2) {
            for (dc in -2..2) {
                val dist = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc))
                setFunctionModule(centerR + dr, centerC + dc, dist != 1, modules, isFunction)
            }
        }
    }

    private fun drawFormatBits(
        mask: Int,
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ) {
        // ECC Level L = 01 in QR format bits
        val data = (0b01 shl 3) or mask
        var rem = data
        repeat(10) {
            rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        }
        val bits = ((data shl 10) or rem) xor 0x5412

        for (i in 0..5) setFunctionModule(i, 8, getBit(bits, i), modules, isFunction)
        setFunctionModule(7, 8, getBit(bits, 6), modules, isFunction)
        setFunctionModule(8, 8, getBit(bits, 7), modules, isFunction)
        setFunctionModule(8, 7, getBit(bits, 8), modules, isFunction)
        for (i in 9..14) setFunctionModule(8, 14 - i, getBit(bits, i), modules, isFunction)

        for (i in 0..7) setFunctionModule(8, size - 1 - i, getBit(bits, i), modules, isFunction)
        for (i in 8..14) setFunctionModule(size - 15 + i, 8, getBit(bits, i), modules, isFunction)
        setFunctionModule(size - 8, 8, true, modules, isFunction) // Dark module
    }

    private fun drawCodewords(
        codewords: ByteArray,
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ) {
        var bitIdx = 0
        val totalBits = codewords.size * 8
        var right = size - 1
        var upward = true
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) {
                val r = if (upward) size - 1 - vert else vert
                for (j in 0..1) {
                    val c = right - j
                    if (!isFunction[r][c]) {
                        val dark = if (bitIdx < totalBits) {
                            val b = codewords[bitIdx ushr 3].toInt() and 0xFF
                            ((b ushr (7 - (bitIdx and 7))) and 1) != 0
                        } else false
                        modules[r][c] = dark
                        bitIdx++
                    }
                }
            }
            upward = !upward
            right -= 2
        }
    }

    private fun applyMask(
        mask: Int,
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ) {
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (isFunction[r][c]) continue
                val invert = when (mask) {
                    0 -> (r + c) % 2 == 0
                    1 -> r % 2 == 0
                    2 -> c % 3 == 0
                    3 -> (r + c) % 3 == 0
                    4 -> (r / 2 + c / 3) % 2 == 0
                    5 -> (r * c) % 2 + (r * c) % 3 == 0
                    6 -> ((r * c) % 2 + (r * c) % 3) % 2 == 0
                    else -> ((r + c) % 2 + (r * c) % 3) % 2 == 0
                }
                if (invert) modules[r][c] = !modules[r][c]
            }
        }
    }

    private fun computePenalty(size: Int, modules: Array<BooleanArray>): Int {
        var penalty = 0
        for (r in 0 until size) {
            var runColor = modules[r][0]
            var runLen = 1
            for (c in 1 until size) {
                if (modules[r][c] == runColor) {
                    runLen++
                    if (runLen == 5) penalty += 3
                    else if (runLen > 5) penalty += 1
                } else {
                    runColor = modules[r][c]
                    runLen = 1
                }
            }
        }
        return penalty
    }

    private fun setFunctionModule(
        r: Int,
        c: Int,
        dark: Boolean,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ) {
        modules[r][c] = dark
        isFunction[r][c] = true
    }

    private fun getBit(x: Int, i: Int): Boolean = ((x ushr i) and 1) != 0

    private fun reedSolomonRemainder(data: ByteArray, degree: Int): ByteArray {
        val divisor = ByteArray(degree)
        divisor[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in 0 until degree) {
                divisor[j] = gfMultiply(divisor[j].toInt() and 0xFF, root).toByte()
                if (j + 1 < degree) {
                    divisor[j] = (divisor[j].toInt() xor divisor[j + 1].toInt()).toByte()
                }
            }
            root = gfMultiply(root, 0x02)
        }

        val result = ByteArray(degree)
        for (b in data) {
            val factor = (b.toInt() xor result[0].toInt()) and 0xFF
            System.arraycopy(result, 1, result, 0, degree - 1)
            result[degree - 1] = 0
            for (i in 0 until degree) {
                result[i] = (result[i].toInt() xor gfMultiply(divisor[i].toInt() and 0xFF, factor)).toByte()
            }
        }
        return result
    }

    private fun gfMultiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    private class BitBuffer {
        private val bytes = ArrayList<Int>()
        var bitLength = 0
            private set

        fun appendBits(value: Int, len: Int) {
            for (i in len - 1 downTo 0) {
                val bit = (value ushr i) and 1
                val byteIdx = bitLength ushr 3
                if (byteIdx >= bytes.size) bytes.add(0)
                bytes[byteIdx] = bytes[byteIdx] or (bit shl (7 - (bitLength and 7)))
                bitLength++
            }
        }

        fun toByteArray(): ByteArray = ByteArray(bytes.size) { bytes[it].toByte() }
    }
}
