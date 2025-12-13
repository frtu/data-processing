plugins {
    id("com.frtu.dataprocessing.kotlin-application-conventions")
}

dependencies {
    implementation("org.apache.commons:commons-text")
    implementation(project(":utilities"))
}

application {
    // Define the main class for the application.
    mainClass.set("com.frtu.dataprocessing.app.AppKt")
}
