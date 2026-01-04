package com.github.frtu.dataprocessing.samples.standalone

import java.util.Arrays
import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.api.common.typeinfo.Types
import org.apache.flink.api.java.tuple.Tuple2
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment

object SplitWordCountWithWebUI

fun main(args: Array<String>) {
    val env = if (args.isNotEmpty() && args[0] == "local") {
        StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(Configuration())
    } else {
        StreamExecutionEnvironment.getExecutionEnvironment()
    }

    val splitWordFunction = FlatMapFunction { line: String, collector: org.apache.flink.util.Collector<String?> ->
        val stream = Arrays.stream(line
            .split("//s+".toRegex())
            .dropLastWhile { it.isEmpty() }
            .toTypedArray())
        stream.forEach { t: String -> collector.collect(t) }
    }
    val toTupleFunction: (String?) -> Tuple2<String, Long> = { word: String? ->
        Tuple2.of(word, 1L)
    }
    env.socketTextStream("localhost", 8081)
        .flatMap(splitWordFunction, Types.STRING)
        .map(toTupleFunction, Types.TUPLE(Types.STRING, Types.LONG))
        .keyBy { t: Tuple2<String, Long> -> t.f0 }
        .sum(1)
        .print()

    env.execute("Split Word Count")
}
