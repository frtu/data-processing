package com.github.frtu.dataprocessing.framework

interface TransformContext {
    /** Allow to get the output data from the specified step name */
    fun <T> getData(stepName: String): T
}

data class Metadata(
    val sourceType: SourceType,
    val operationType: OperationType,
)

enum class SourceType {
    DEBEZIUM,
    APPLICATION,
}

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
}