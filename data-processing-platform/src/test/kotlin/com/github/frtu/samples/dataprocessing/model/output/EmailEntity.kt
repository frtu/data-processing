package com.github.frtu.samples.dataprocessing.model.output

import java.time.LocalDateTime

data class EmailEntity(
    var receiver: String? = null,
    var subject: String = "",
    var content: String = "",
    var status: STATUS = STATUS.INIT,

    var id: Long? = null,
    val creationTime: LocalDateTime = LocalDateTime.now(),

    var updateTime: LocalDateTime = creationTime
)

enum class STATUS {
    INIT, SENT, ERROR
}