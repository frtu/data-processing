package com.github.frtu.dataprocessing.framework

interface Transform<I, O> {
    fun process(input: I, ctx: TransformContext): O?
}