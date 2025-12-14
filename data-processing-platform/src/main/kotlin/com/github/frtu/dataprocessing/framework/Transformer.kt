package com.github.frtu.dataprocessing.framework

interface Transformer<I, O> {
    fun process(input: I, ctx: TransformContext): O?
}