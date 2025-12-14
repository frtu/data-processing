package com.github.frtu.dataprocessing.framework

interface TransformContext {
    /** Allow to get the output data from the specified step name */
    fun <T> getData(stepName: String): T
}