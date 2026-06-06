import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.9.22"
    id("io.ktor.plugin") version "2.3.7"
}

group = "com.campus.card"
version = "1.0.0"

application {
    mainClass.set("com.campus.card.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-jackson-jvm")
    implementation("io.ktor:ktor-server-status-pages-jvm")
    implementation("io.ktor:ktor-server-call-logging-jvm")
    implementation("io.ktor:ktor-server-cors-jvm")

    implementation("org.jetbrains.exposed:exposed-core:0.46.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.46.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.46.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.46.0")

    implementation("com.h2database:h2:2.2.224")
    implementation("org.postgresql:postgresql:42.7.1")

    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("io.github.oshai:kotlin-logging-jvm:6.0.3")

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

    testImplementation("io.ktor:ktor-server-tests-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.22")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

tasks.withType<Test> {
    useJUnit()
}

ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_17)
        localImageName.set("campus-card-service")
        imageTag.set("latest")
        portMappings.set(listOf(io.ktor.plugin.features.DockerPortMapping(8080, 8080)))
    }
}
