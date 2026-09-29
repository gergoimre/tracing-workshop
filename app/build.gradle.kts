plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "com.workshop"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    // Spring MVC + virtual threads (Java 21) — blocking code, no coroutines needed
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Micrometer Tracing — Spring Boot's first-class tracing abstraction.
    // The bridge wires Micrometer's Observation API to the OTel SDK under the hood.
    // No Java agent required; everything is managed as normal Spring Boot auto-config.
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    // OTLP exporter — sends spans to the OTel Collector over HTTP/protobuf
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    // Spring AOP — required for @Observed to create spans via ObservedAspect
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // Structured JSON logging — emits trace_id/span_id in every log line
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
