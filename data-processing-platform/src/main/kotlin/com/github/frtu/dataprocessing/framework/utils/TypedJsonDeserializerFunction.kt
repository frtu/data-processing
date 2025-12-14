package com.github.frtu.dataprocessing.framework.utils

import com.github.frtu.kotlin.utils.io.toJsonObj

class TypedJsonDeserializerFunction<OUTPUT>(
    val targetClass: Class<OUTPUT>,
) : ConverterFunction<String, OUTPUT> {
    override fun convert(input: String): OUTPUT = input.toJsonObj(targetClass)
}