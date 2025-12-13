# Backend Architecture Rules

# Packages 
- should be split by domain, not layer (we use DDD style)
- Inside folder 'src', always use a subfolder of `com.github.frtu.samples.dataprocessing` package for new Kotlin classes

# Backend Coding practices
- as much as possible, functions should be single responsibility. If a function is doing more than one thing, split it into several functions
- Each `Transform`, use method action words such as `find`, `save`, `delete`. Do not use 'create' or `remove`

# Refactoring
- When refactoring code, don't forget to refactor the JUnit tests

## JUnit tests
- Use AssertJ for assertions in tests
- All JUnit tests should follow the convention `<ClassName>Test.kt`. For instance, the test for VetService should be named `VetServiceTest.kt`
- inside test methods, use the `given/when/then` structure with a blank line between each section
- When asked to generate a JUnit Integration test, you should create a @SpringBootTest with JUnit 5, using an embedded database (H2). 
- when generating test data, make sure there is no conflict with the test data inside data.sql
