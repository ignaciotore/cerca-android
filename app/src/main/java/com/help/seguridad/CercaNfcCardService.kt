package com.help.seguridad

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import java.nio.charset.StandardCharsets

/**
 * CERCA ID - prueba experimental teléfono a teléfono.
 * Emula una etiqueta NFC Forum Type 4 con un registro NDEF URI.
 * No transmite datos médicos: solo una URL pública de prueba.
 */
class CercaNfcCardService : HostApduService() {
    private var selectedFile = 0

    private val ccFile = byteArrayOf(
        0x00, 0x0F, 0x20, 0x00, 0xFF.toByte(), 0x00, 0xFF.toByte(),
        0x04, 0x06, 0xE1.toByte(), 0x04, 0x04, 0x00, 0x00, 0xFF.toByte()
    )

    private val ndefFile: ByteArray by lazy {
        buildNdefFile("https://yduoxeqgxolkzvjexlqk.supabase.co/functions/v1/cerca-nfc-test")
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        if (isSelectNdefApplication(commandApdu)) {
            selectedFile = 0
            return SW_OK
        }

        if (isSelectFile(commandApdu)) {
            val fileId = ((commandApdu[5].toInt() and 0xFF) shl 8) or (commandApdu[6].toInt() and 0xFF)
            selectedFile = when (fileId) {
                0xE103 -> 1
                0xE104 -> 2
                else -> 0
            }
            return if (selectedFile != 0) SW_OK else SW_FILE_NOT_FOUND
        }

        if (isReadBinary(commandApdu)) {
            val data = when (selectedFile) {
                1 -> ccFile
                2 -> ndefFile
                else -> return SW_CONDITIONS_NOT_SATISFIED
            }
            val offset = ((commandApdu[2].toInt() and 0xFF) shl 8) or (commandApdu[3].toInt() and 0xFF)
            var length = commandApdu[4].toInt() and 0xFF
            if (length == 0) length = 256
            if (offset >= data.size) return SW_WRONG_PARAMS
            val end = minOf(offset + length, data.size)
            return data.copyOfRange(offset, end) + SW_OK
        }

        return SW_INS_NOT_SUPPORTED
    }

    override fun onDeactivated(reason: Int) {
        selectedFile = 0
    }

    private fun isSelectNdefApplication(c: ByteArray): Boolean {
        if (c.size < 12) return false
        if ((c[0].toInt() and 0xFF) != 0x00 || (c[1].toInt() and 0xFF) != 0xA4) return false
        if ((c[2].toInt() and 0xFF) != 0x04 || (c[4].toInt() and 0xFF) != 0x07) return false
        val aid = byteArrayOf(0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01)
        return c.copyOfRange(5, 12).contentEquals(aid)
    }

    private fun isSelectFile(c: ByteArray): Boolean =
        c.size >= 7 && (c[0].toInt() and 0xFF) == 0x00 &&
            (c[1].toInt() and 0xFF) == 0xA4 && (c[4].toInt() and 0xFF) == 0x02

    private fun isReadBinary(c: ByteArray): Boolean =
        c.size >= 5 && (c[0].toInt() and 0xFF) == 0x00 && (c[1].toInt() and 0xFF) == 0xB0

    private fun buildNdefFile(url: String): ByteArray {
        val suffix = url.removePrefix("https://")
        val suffixBytes = suffix.toByteArray(StandardCharsets.UTF_8)
        val payload = byteArrayOf(0x04) + suffixBytes // NDEF URI prefix 0x04 = https://
        require(payload.size < 256)
        val record = byteArrayOf(
            0xD1.toByte(), // MB + ME + SR + TNF well-known
            0x01,
            payload.size.toByte(),
            0x55 // 'U' = URI record
        ) + payload
        val nlen = record.size
        return byteArrayOf(((nlen ushr 8) and 0xFF).toByte(), (nlen and 0xFF).toByte()) + record
    }

    companion object {
        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_FILE_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
        private val SW_WRONG_PARAMS = byteArrayOf(0x6B, 0x00)
        private val SW_CONDITIONS_NOT_SATISFIED = byteArrayOf(0x69, 0x85.toByte())
        private val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D, 0x00)
    }
}
