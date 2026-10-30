plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.openapi.generator)
    alias(libs.plugins.spotless)
    jacoco
}

group = "com.example"
version = "0.1.0-SNAPSHOT"

// Spring Boot's BOM manages an older Kotlin; keep the stdlib aligned with the compiler plugin.
extra["kotlin.version"] = libs.versions.kotlin.get()

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Web + validation
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Persistence
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Observability: health/metrics, tracing (OpenTelemetry bridge + OTLP export), @Observed
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // API docs UI (serves the contract-first spec, does not generate it)
    implementation(libs.springdoc.ui)

    // Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation(libs.mockk)
    testImplementation(libs.springmockk)
    testImplementation(libs.swagger.request.validator) // contract tests: responses are validated against the OpenAPI spec
    testImplementation(libs.archunit)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// ---------------------------------------------------------------------------
// Contract-first: src/main/resources/openapi/payments-api.yaml is the source of truth.
// The generator emits the Spring interfaces + request/response models; controllers implement them.
// ---------------------------------------------------------------------------
openApiGenerate {
    generatorName.set("kotlin-spring")
    inputSpec.set(layout.projectDirectory.file("src/main/resources/openapi/payments-api.yaml").asFile.path)
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.path)
    apiPackage.set("com.example.payments.adapter.web.api")
    modelPackage.set("com.example.payments.adapter.web.model")
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    globalProperties.set(mapOf("apis" to "", "models" to ""))
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "skipDefaultInterface" to "true",
            "useSpringBoot3" to "true",
            "useTags" to "true",
            "useBeanValidation" to "true",
            "documentationProvider" to "none",
            "annotationLibrary" to "none",
            "enumPropertyNaming" to "UPPERCASE",
            "exceptionHandler" to "false",
            "serviceInterface" to "false",
        ),
    )
}

kotlin.sourceSets.getByName("main").kotlin.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/kotlin"))

tasks.named("compileKotlin") { dependsOn("openApiGenerate") }

// ---------------------------------------------------------------------------
// Tests: `test` = fast unit/slice tests, `integrationTest` = Testcontainers (needs Docker).
// ---------------------------------------------------------------------------
tasks.test {
    useJUnitPlatform { excludeTags("integration") }
}

val integrationTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs integration tests (Testcontainers, requires Docker)."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
    shouldRunAfter(tasks.test)
}

tasks.withType<Test>().configureEach {
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.check { dependsOn(integrationTest) }

// ---------------------------------------------------------------------------
// Coverage (generated API code excluded). Add a verification rule once the team agrees a floor.
// ---------------------------------------------------------------------------
jacoco {
    toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
    mustRunAfter(tasks.test, integrationTest)
    executionData.setFrom(fileTree(layout.buildDirectory.dir("jacoco")) { include("*.exec") })
    classDirectories.setFrom(
        sourceSets["main"].output.asFileTree.matching {
            exclude("**/adapter/web/api/**", "**/adapter/web/model/**")
        },
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.check { finalizedBy(tasks.jacocoTestReport) }

// ---------------------------------------------------------------------------
// Formatting. Run `./gradlew spotlessApply` once after generating the project, then flip
// isEnforceCheck to true so `./gradlew check` fails on unformatted code.
// ---------------------------------------------------------------------------
spotless {
    isEnforceCheck = false
    kotlin {
        target("src/**/*.kt")
        ktlint()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}
