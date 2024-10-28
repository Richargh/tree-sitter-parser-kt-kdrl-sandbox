plugins {
    kotlin("jvm") version "2.0.21"
    id("application")
}

group = "de.richargh.sandbox"
version = "1.0-SNAPSHOT"

application.mainClass = "de.richargh.sandbox.treesitter.MainKt"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))

    implementation("io.github.bonede:tree-sitter:0.24.3")
    implementation("io.github.bonede:tree-sitter-java:0.21.0a")
    implementation("io.github.bonede:tree-sitter-json:0.23.0")
}

tasks.test {
    useJUnitPlatform()
}