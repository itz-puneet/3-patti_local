package com.threepatti.core

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/** A QR code as a square grid of dark (true) and light (false) modules, without the quiet zone. */
class QrMatrix(val size: Int, private val dark: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = dark[y * size + x]
}

object QrCode {
    fun encode(text: String): QrMatrix {
        val matrix = Encoder.encode(text, ErrorCorrectionLevel.M).matrix
        val size = matrix.width
        val dark = BooleanArray(size * size) { i -> matrix.get(i % size, i / size).toInt() == 1 }
        return QrMatrix(size, dark)
    }
}
