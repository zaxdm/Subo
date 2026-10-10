plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    application
}

group = "com.peru.expresdm"
version = "0.1.0"

kotlin {
    // Se compila con el JDK 21 de la PC, pero el resultado tiene que correr
    // también con el Java 17 de Termux, dentro del teléfono.
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("com.peru.expresdm.backend.MainKt")
}

repositories {
    mavenCentral()
}

dependencies {
    // Servidor HTTP
    implementation("io.ktor:ktor-server-core-jvm:3.5.2")
    implementation("io.ktor:ktor-server-netty-jvm:3.5.2")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.5.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.5.2")
    implementation("io.ktor:ktor-server-status-pages-jvm:3.5.2")
    implementation("io.ktor:ktor-server-call-logging-jvm:3.5.2")
    implementation("io.ktor:ktor-server-auth-jvm:3.5.2")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:3.5.2")

    // Cliente HTTP: las llamadas a Gemini salen del servidor, no de la app
    implementation("io.ktor:ktor-client-cio-jvm:3.5.2")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:3.5.2")

    // Base de datos: JDBC directo, sin ORM, para que el SQL esté a la vista
    implementation("org.postgresql:postgresql:42.7.13")
    implementation("com.zaxxer:HikariCP:7.1.0")

    // Contraseñas
    implementation("at.favre.lib:bcrypt:0.10.2")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("ch.qos.logback:logback-classic:1.5.38")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host-jvm:3.5.2")
    testImplementation("io.ktor:ktor-client-content-negotiation-jvm:3.5.2")
}

tasks.test {
    useJUnitPlatform()
}
