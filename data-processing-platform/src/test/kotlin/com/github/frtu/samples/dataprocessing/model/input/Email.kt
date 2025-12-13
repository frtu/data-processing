package com.github.frtu.samples.dataprocessing.model.input

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import java.time.Instant
import java.util.*

@JsonPropertyOrder("id", "subject", "content", "created_time_ms", "updated_time_ms")
data class Email(
    @JsonProperty("subject")
    var subject: String,
    @JsonProperty("content")
    var content: String,
    @JsonProperty("id")
    var id: UUID = UUID.randomUUID(),
    @JsonProperty("created_time_ms")
    val createdTimeMillis: Long = Instant.now().toEpochMilli(),
    @JsonProperty("updated_time_ms")
    var updatedTimeMillis: Long = Instant.now().toEpochMilli(),
)
