plugins {
    var kotlin = "1.9.25"
    var springBoot = "3.3.8"

    // Spring
    kotlin("plugin.spring") version kotlin
    id("org.springframework.boot") version springBoot
    id("io.spring.dependency-management") version "1.1.7"

    // Core
    kotlin("jvm") version kotlin
    kotlin("plugin.noarg") version kotlin
    `java-library`
    `maven-publish`
    // shadow plugin to produce fat JARs
    id("com.github.johnrengelman.shadow") version "8.1.1"

    // Application
    application
}

group = "com.github.frtu.vm"
description = "flink-pipeline"
val mainClassName = "com.github.frtu.dataprocessing.framework.EventSanitizerKt"

noArg {
    // Apply this magic constructor generation to any class with this annotation
    annotation("com.github.frtu.kotlin.utils.data.ValueObject")
}
dependencies {
    // frtu libs
    implementation(libs.frtu.utils)
    implementation(libs.frtu.logs)

    // flink
    api(libs.flink.java)
    api(libs.flink.streaming.java)
    implementation(libs.flink.runtime.web)
    api(libs.flink.clients)

    // Kafka
    api(libs.flink.connector.base)
    api(libs.flink.connector.kafka)
    api(libs.kafka.clients)

    // Elasticsearch 8 client (ES)
    implementation("co.elastic.clients:elasticsearch-java:8.16.0")
    api(libs.flink.connector.es)

    // ClickHouse (CH)
    api(libs.flink.connector.jdbc)
    implementation("ru.yandex.clickhouse:clickhouse-jdbc:0.3.2")

    // Kryo serialization support
    implementation("com.esotericsoftware:kryo:5.4.0")
    api(libs.flink.runtime)
    // Kotlin serialization for Flink
    implementation("org.jetbrains.kotlin:kotlin-reflect:${libs.versions.kotlin.get()}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")

    // spring
    implementation("org.springframework:spring-context")
    implementation("org.springframework.kafka:spring-kafka")
    // testImplementation("org.springframework.kafka:spring-kafka-test")

    // apache beam
//    api("org.apache.beam:beam-sdks-java-core:2.57.0")
//    api("org.apache.beam:beam-runners-direct-java:2.57.0")
//    api("org.apache.beam:beam-runners-flink:2.57.0")
//    api("org.apache.beam:beam-sdks-java-io-kafka:2.57.0")
//    testImplementation("org.apache.beam:beam-sdks-java-test-utils:2.57.0")

    // core
    implementation(libs.jackson.kotlin)
    implementation("ch.qos.logback:logback-classic")

    // base & test
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.test.runner)
    testImplementation(libs.test.assertions)
    testImplementation(libs.test.mockk)
}

// Configure Jar main class
application {
    mainClass.set(mainClassName)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<Javadoc>() {
    options.encoding = "UTF-8"
}

tasks.shadowJar {
    manifest {
        attributes("Main-Class" to mainClassName)
    }

    // Exclude Flink core dependencies (provided by Cluster)
    dependencies {
        exclude(dependency("org.apache.flink:flink-java"))
        exclude(dependency("org.apache.flink:flink-streaming-java"))
        exclude(dependency("org.apache.flink:flink-clients"))
        exclude(dependency("org.apache.flink:flink-connector-base"))
    }

    // Include Kafka connector and other runtime dependencies
    mergeServiceFiles()

    archiveClassifier.set("")
    archiveVersion.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        url = uri("https://jcenter.bintray.com")
    }

    maven {
        url = uri("https://dl.bintray.com/kotlin/ktor/")
    }

    maven {
        url = uri("https://dl.bintray.com/kotlin/kotlin-eap")
    }

    maven {
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}
