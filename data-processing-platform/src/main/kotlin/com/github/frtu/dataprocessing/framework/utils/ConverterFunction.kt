package com.github.frtu.dataprocessing.framework.utils

import com.github.frtu.dataprocessing.framework.ProcessFunction

interface ConverterFunction<INPUT, OUTPUT> : ProcessFunction<INPUT, OUTPUT> {
    fun convert(parameter: INPUT): OUTPUT
}