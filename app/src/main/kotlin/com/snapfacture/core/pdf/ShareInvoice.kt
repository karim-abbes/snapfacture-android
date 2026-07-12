package com.snapfacture.core.pdf

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.snapfacture.R
import java.io.File

object ShareInvoice {

    fun intent(
        context: Context,
        file: File,
        invoiceNumber: Int,
        companyName: String,
        recipientEmail: String? = null,
        isQuote: Boolean = false,
        mimeType: String = "application/pdf",
    ): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val sender = companyName.ifBlank { context.getString(R.string.app_name) }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            val subjectRes = if (isQuote) R.string.share_subject_quote else R.string.share_subject
            val bodyRes = if (isQuote) R.string.share_body_quote else R.string.share_body
            putExtra(Intent.EXTRA_SUBJECT, context.getString(subjectRes, sender, invoiceNumber))
            putExtra(Intent.EXTRA_TEXT, context.getString(bodyRes, invoiceNumber, sender))
            recipientEmail?.takeIf { it.isNotBlank() }?.let {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(it))
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, context.getString(R.string.share_chooser_title)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
