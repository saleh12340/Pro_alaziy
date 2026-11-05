package com.example.domain.einvoicing

import java.util.Base64

/**
 * Interface and reference implementation for electronic invoicing compliance and QR generation.
 * Ready for future tax and Zakat/e-invoicing integrations.
 */
interface EInvoicingExporter {
    fun generateQrCodePayload(
        sellerName: String,
        vatRegistrationNumber: String,
        timestampIso: String,
        invoiceTotalWithVat: Double,
        vatAmount: Double
    ): String
}

class StandardEInvoicingExporter : EInvoicingExporter {
    override fun generateQrCodePayload(
        sellerName: String,
        vatRegistrationNumber: String,
        timestampIso: String,
        invoiceTotalWithVat: Double,
        vatAmount: Double
    ): String {
        // TLV encoding (Tag-Length-Value standard used for electronic tax invoices)
        val sellerBytes = sellerName.toByteArray(Charsets.UTF_8)
        val vatNumBytes = vatRegistrationNumber.toByteArray(Charsets.UTF_8)
        val timeBytes = timestampIso.toByteArray(Charsets.UTF_8)
        val totalBytes = String.format(java.util.Locale.US, "%.2f", invoiceTotalWithVat).toByteArray(Charsets.UTF_8)
        val vatBytes = String.format(java.util.Locale.US, "%.2f", vatAmount).toByteArray(Charsets.UTF_8)

        val buffer = java.io.ByteArrayOutputStream()
        writeTlv(buffer, 1, sellerBytes)
        writeTlv(buffer, 2, vatNumBytes)
        writeTlv(buffer, 3, timeBytes)
        writeTlv(buffer, 4, totalBytes)
        writeTlv(buffer, 5, vatBytes)

        return try {
            java.util.Base64.getEncoder().encodeToString(buffer.toByteArray())
        } catch (e: Throwable) {
            android.util.Base64.encodeToString(buffer.toByteArray(), android.util.Base64.NO_WRAP)
        }
    }

    private fun writeTlv(stream: java.io.ByteArrayOutputStream, tag: Int, value: ByteArray) {
        stream.write(tag)
        stream.write(value.size)
        stream.write(value)
    }
}
