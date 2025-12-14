# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a data processing platform built with Kotlin and Apache Flink. The project provides a transformation framework for stream processing with Kafka connectors.

## Architecture

### Core Framework
- **Transform Interface**: Located in `src/main/kotlin/com/github/frtu/dataprocessing/framework/Transform.kt`
  - Generic interface `Transform<I, O>` for data transformations
  - Takes input type I, processes with TransformContext, returns output type O
- **TransformContext**: Provides execution context for transformations
- **Domain-Driven Package Structure**: Packages organized by domain, not layers (DDD style)

### Package Structure
- `com.github.frtu.dataprocessing.framework` - Core transformation framework
- `com.github.frtu.samples.dataprocessing` - Sample implementations
- `model/input` - Input data classes
- `model/output` - Output data classes

## Development Commands

### Build and Test
```bash
# Build the entire project
./gradlew build

# Run tests only
./gradlew test

# Run a specific test class
./gradlew test --tests "FlinkTransformOperatorTest"

# Build fat JAR for deployment
./gradlew shadowJar

# Clean build artifacts
./gradlew clean
```

### Application
```bash
# Run the application
./gradlew run

# Build and run with shadow JAR
./gradlew shadowJar
java -jar build/libs/data-processing-platform.jar
```

### Development Workflow
- CI/CD: GitHub Actions workflow builds on JDK 17 with `./gradlew build`

## Coding Standards

### Package Organization
- New Kotlin classes must be placed under `com.github.frtu.samples.dataprocessing` package structure
- Use domain-based packaging (DDD style), not layer-based

### Transform Implementation
- Use action words: `find`, `save`, `delete` (not `create` or `remove`)
- Follow single responsibility principle - split functions doing multiple things
- Implement the `Transform<I, O>` interface for data transformations

### Testing Standards
- Test files: `<ClassName>Test.kt` convention (e.g., `TransformServiceTest.kt`)
- Use **Kotest** for test assertions
- Test structure: `given/when/then` with blank lines between sections
- Extensions: Use `@ExtendWith(MockKExtension::class)` for MockK

### Refactoring Rules
- Always refactor corresponding JUnit tests when refactoring code
- Maintain package structure and naming conventions

## Project Rules

### Dependencies
- Flink core dependencies excluded from shadow JAR (provided by cluster)
- Kafka connector and runtime dependencies included in fat JAR

## Build Configuration

The project uses Gradle with Kotlin DSL and includes:
- Composite build with `build-support` module
- Shadow plugin for fat JAR creation
- Maven publishing configuration
- Multi-repository setup for dependency resolution