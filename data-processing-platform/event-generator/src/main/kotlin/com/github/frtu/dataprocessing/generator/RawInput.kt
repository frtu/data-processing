package com.github.frtu.dataprocessing.generator

import com.github.frtu.kotlin.utils.data.ValueObject
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.io.Serializable

// This class represents the external, potentially messy API contract
@ValueObject
@JsonIgnoreProperties(ignoreUnknown = true) // Critical: Don't crash on new/unexpected fields
data class RawInput(
    @JsonProperty("event_id")
    val eventId: String?,

    @JsonProperty("user_id")
    val userId: String?,

    @JsonProperty("event_type")
    val eventType: String?,

    @JsonProperty("message")
    val message: String?,

    @JsonProperty("amount")
    val amount: Double? // Nullable allows us to detect missing values vs 0.0
): Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}