plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "com.researchai"
version = "0.0.1"

application {
    mainClass = "com.researchai.cli.ResearchAiCliKt"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // CLI framework
    implementation("com.github.ajalt.clikt:clikt:4.2.1")

    // HTTP Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Logging
    implementation(libs.logback.classic)
}

tasks.register<Jar>("buildFatJar") {
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = "com.researchai.cli.ResearchAiCliKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
