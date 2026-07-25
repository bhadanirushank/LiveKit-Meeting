import java.time.Duration

plugins {
    kotlin("jvm") version "2.4.10"
    id("io.ktor.plugin") version "2.3.11"
    kotlin("plugin.serialization") version "2.4.10"
    id("org.owasp.dependencycheck") version "12.1.0"
}

group = "com.jbcoder.meeting"
version = "0.0.1"

application {
    mainClass.set("com.jbcoder.meeting.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.4.10"))
    implementation("io.ktor:ktor-server-core-jvm:2.3.13")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.13")
    implementation("io.ktor:ktor-server-cors-jvm:2.3.13")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.13")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.13")
    implementation("io.ktor:ktor-server-status-pages-jvm:2.3.13")
    implementation("io.ktor:ktor-server-call-logging-jvm:2.3.13")
    implementation("io.ktor:ktor-server-call-id-jvm:2.3.13")
    implementation("io.ktor:ktor-server-request-validation:2.3.13")
    implementation("io.ktor:ktor-server-compression-jvm:2.3.13")
    implementation("io.ktor:ktor-server-auth-jwt:2.3.13")
    implementation("io.ktor:ktor-client-core-jvm:2.3.13")
    implementation("io.ktor:ktor-client-cio-jvm:2.3.13")

    // Database & Migrations
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.jetbrains.exposed:exposed-core:0.48.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.48.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.48.0")
    implementation("org.flywaydb:flyway-core:10.10.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.10.0")

    // Redis (Lettuce)
    implementation("io.lettuce:lettuce-core:6.8.2.RELEASE")

    // LiveKit & Crypto
    implementation("io.livekit:livekit-server:0.8.2")
    implementation("de.mkammerer:argon2-jvm:2.11")

    // Environment and Config
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.3")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Testing
    testImplementation("io.ktor:ktor-server-tests-jvm:2.3.13")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.23")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("eu.rekawek.toxiproxy:toxiproxy-java:2.1.7")

}
dependencyCheck {
    failBuildOnCVSS = 7.0f
    suppressionFile = "dependency-check-suppression.xml"
    failOnError = true
    formats = listOf("HTML", "JSON", "SARIF")
    val nvdKey = System.getenv("NVD_API_KEY")
    if (!nvdKey.isNullOrBlank()) {
        nvd.apiKey = nvdKey
    }
    analyzers.ossIndex.enabled = false
    data.directory = "$projectDir/nvd-data"
}

tasks.named("dependencyCheckAnalyze") {
    doFirst {
        if (System.getenv("NVD_API_KEY").isNullOrBlank()) {
            throw GradleException("NVD_API_KEY environment variable is required for dependencyCheckAnalyze but was not found.")
        }
    }
}

val npmCiLiveKitTest by tasks.registering(Exec::class) {
    workingDir = file("src/test/resources/livekit-client-test")
    val nodeModulesDir = file("src/test/resources/livekit-client-test/node_modules")
    
    // Only run if node_modules is missing or package.json changed
    inputs.file(file("src/test/resources/livekit-client-test/package.json"))
    outputs.dir(nodeModulesDir)

    if (System.getProperty("os.name").toLowerCase().contains("windows")) {
        commandLine("cmd", "/c", "npm", "install")
    } else {
        commandLine("npm", "install")
    }
}

tasks.named("processTestResources") {
    dependsOn(npmCiLiveKitTest)
}

tasks.named<Test>("test") {
    // Exclude integration tests from the standard 'test' task
    exclude("**/*IntegrationTest*")
    exclude("**/*SmokeTest*")
    exclude("**/DependencyRecoveryTest*")
    exclude("**/OutboxRecoveryTest*")
    exclude("**/LoadTest*")
    exclude("**/IdempotencyTest*")
    exclude("**/JwtSecurityTest*")
    exclude("**/LifecycleAndWebhookTest*")
    exclude("**/OpenApiParityTest*")
    exclude("**/WebhookMonotonicityTest*")
    exclude("**/MigrationTest*")
}

tasks.withType<Test> {
    dependsOn(npmCiLiveKitTest)
    useJUnitPlatform()
    environment("POSTGRES_HOST", "localhost")
    environment("REDIS_HOST", "localhost")
    environment("LIVEKIT_URL", "ws://localhost:7880")
}

tasks.register<Test>("composeIntegrationTest") {
    description = "Runs integration tests against the local Docker Compose stack"
    group = "verification"
    useJUnitPlatform()
    
    // Include all tests excluded from the standard 'test' task (except Dependency/Load tests which run separately)
    include("**/*IntegrationTest*")
    include("**/*SmokeTest*")
    include("**/IdempotencyTest*")
    include("**/JwtSecurityTest*")
    include("**/LifecycleAndWebhookTest*")
    include("**/OpenApiParityTest*")
    include("**/WebhookMonotonicityTest*")
    include("**/MigrationTest*")
    include("**/OutboxRecoveryTest*")
    
    // Set a system property so tests know they are running in strict compose integration mode
    systemProperty("COMPOSE_INTEGRATION_TEST", "true")
    environment("POSTGRES_HOST", "localhost")
    environment("REDIS_HOST", "localhost")
    environment("LIVEKIT_URL", "ws://localhost:7880")
    
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}

tasks.register<Test>("dependencyRecoveryTest") {
    description = "Runs the dependency chaos recovery tests using Toxiproxy"
    group = "verification"
    useJUnitPlatform()
    include("**/DependencyRecoveryTest*")
    
    // Isolation and timeout settings
    maxParallelForks = 1
    timeout.set(Duration.ofMinutes(30))
    
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}

tasks.register<Test>("loadTest") {
    description = "Runs normal load and rate limit tests"
    group = "verification"
    useJUnitPlatform()
    include("**/LoadTest*")
    
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
    
    systemProperty("POSTGRES_USER", "postgres")
    systemProperty("POSTGRES_PASSWORD", "postgres_password_placeholder")
    systemProperty("POSTGRES_HOST", "localhost")
    systemProperty("POSTGRES_PORT", "5432")
    systemProperty("POSTGRES_DB", "livekit_meeting")
    systemProperty("REDIS_HOST", "localhost")
    systemProperty("REDIS_PORT", "6379")
    systemProperty("REDIS_PASSWORD", "redis_password_placeholder")
    systemProperty("COMPOSE_INTEGRATION_TEST", "true")
}
dependencies { testImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.16.1") }
