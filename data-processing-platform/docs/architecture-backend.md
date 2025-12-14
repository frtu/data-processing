# Backend Architecture Rules

## Technology Stack
- **Language**: Kotlin 1.9.25 with Java 17
- **Stream Processing**: Apache Flink 1.20.1
- **Messaging**: Apache Kafka 3.9.1 with Flink Kafka connector
- **Serialization**: Jackson with Kotlin module
- **Testing**: JUnit 5, Kotest, MockK

# Packages 
- should be split by domain, not layer (we use DDD style)
- Inside folder 'src', always use a subfolder of `com.github.frtu.samples.dataprocessing` package for new Kotlin classes

# Backend Coding practices
- as much as possible, functions should be single responsibility. If a function is doing more than one thing, split it into several functions
- Each `Transform`, use method action words such as `find`, `save`, `delete`. Do not use 'create' or `remove`

# Refactoring
- When refactoring code, don't forget to refactor the JUnit tests

## Database
- schema should be defined in data.sql
- when having a data creation script inside data.sql, always make sure that data is populated in the right order, so we do not assign null values into columns which are "not null".
- as of now, only use HSQL (test environment is enough)

## For JPA entities
- there should be no setters by default 
- there should be 3 constructors: an empty one, one with all parameters, one with all parameters except the id
- Avoid using @Column and @Table unless really necessary
- When mapping relationships, please do not state explicit attributes unless really needed
- Do not use bidirectional relationships unless really needed
- imports should be jakarta.persistence.* (not javax.persistence.*)
- Whenever possible, use @OneToMany rather than @ManyToOne
- Annotations such as @OneToMany should be minimalistic. Do not use mappedBy, cascade etc unless explicitly stated.

## For Repository
- When using Spring Data JPA, there is no need for @Repository annotation as it is not mandatory

## For Controllers
- use URL such as /api/v1/ENTITY (eg: /api/v1/pet)
- avoid using ResponseEntity unless really needed

## JUnit tests
- All JUnit tests should follow the convention `<ClassName>Test.kt`. For instance, the test for TransformService should be named `TransformServiceTest.kt`
- Inside test methods, use the `given/when/then` structure with a blank line between each section
- Use Kotest for assertions in tests
