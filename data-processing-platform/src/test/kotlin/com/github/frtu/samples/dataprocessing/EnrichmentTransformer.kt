package com.github.frtu.samples.dataprocessing

import com.github.frtu.dataprocessing.framework.Transformer
import com.github.frtu.dataprocessing.framework.TransformContext
import com.github.frtu.samples.dataprocessing.model.input.Email
import com.github.frtu.samples.dataprocessing.model.output.EmailEntity
import com.github.frtu.samples.dataprocessing.model.output.STATUS
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class EnrichmentTransformer: Transformer<Email, EmailEntity> {
    override fun process(input: Email, ctx: TransformContext): EmailEntity? {
        return EmailEntity(
            receiver = enrichReceiver(input),
            subject = input.subject,
            content = enrichContent(input.content),
            status = STATUS.INIT,
            id = null,
            creationTime = convertToLocalDateTime(input.createdTimeMillis),
            updateTime = convertToLocalDateTime(input.updatedTimeMillis)
        )
    }

    private fun enrichReceiver(input: Email): String? {
        // Logic to determine receiver from email content or other enrichment
        return null
    }

    private fun enrichContent(content: String): String {
        // Add enrichment logic here (e.g., add metadata, clean content, etc.)
        return content
    }

    private fun convertToLocalDateTime(epochMillis: Long): LocalDateTime {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
    }
}