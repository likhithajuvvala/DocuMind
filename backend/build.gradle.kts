import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

plugins {
    id("org.springframework.boot") version "3.5.16" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.diffplug.spotless") version "7.0.2" apply false
}

allprojects {
    group = "com.documind"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

// Instruction-coverage floors per module, each set a few points below what the module actually
// measures today (see the measurements this was derived from — common and document-service are
// genuinely under-tested relative to gateway-service/ingestion-worker, not a typo). A single
// global threshold would either be meaninglessly low or break half the modules immediately; this
// is a regression gate — it catches a PR that erodes an already-tested module, not a mandate to
// hit one aspirational number everywhere at once. Ratchet a module's floor up as its real coverage
// improves.
val coverageMinimums = mapOf(
    "common" to 0.20,
    "gateway-service" to 0.85,
    "document-service" to 0.15,
    "ingestion-worker" to 0.90,
    "query-service" to 0.50,
)

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "jacoco")
    apply(plugin = "com.diffplug.spotless")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    configure<JacocoPluginExtension> {
        toolVersion = "0.8.12"
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            // AOSP, not the Google default, since it's 4-space-indented — matching this codebase's
            // existing style keeps the one-time adoption diff to formatting only, not a wholesale
            // 2-space reindent of every file.
            googleJavaFormat().aosp()
            importOrder()
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
            target("src/*/java/**/*.java")
        }
    }

    configure<DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.16")
            mavenBom("org.springframework.ai:spring-ai-bom:1.0.9")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.3")
            mavenBom("org.testcontainers:testcontainers-bom:2.0.5")
        }
        dependencies {
            dependency("org.postgresql:postgresql:42.7.13")
            dependency("org.bouncycastle:bcprov-jdk18on:1.85")
            // spring-cloud-starter-gateway-server-webmvc pulls in Netty 4.1.135.Final transitively
            // (via reactor-netty, which declares its own Netty version rather than deferring to
            // the spring-boot-dependencies BOM, so importing a netty-bom here doesn't override it).
            // 4.1.135.Final is affected by CVE-2026-59901 (infinite loop in the bzip2 codec) and
            // fails gateway-service's image vulnerability scan in CI; pin every Netty module
            // individually to force the patched version.
            dependency("io.netty:netty-buffer:4.1.136.Final")
            dependency("io.netty:netty-codec:4.1.136.Final")
            dependency("io.netty:netty-common:4.1.136.Final")
            dependency("io.netty:netty-handler:4.1.136.Final")
            dependency("io.netty:netty-resolver:4.1.136.Final")
            dependency("io.netty:netty-transport:4.1.136.Final")
            dependency("io.netty:netty-transport-native-unix-common:4.1.136.Final")
            // spring-ai-starter-vector-store-qdrant (ingestion-worker, query-service) pulls in the
            // Qdrant Java client, which depends on an old gRPC/protobuf pair affected by
            // CVE-2025-55163 (Netty MadeYouReset HTTP/2 DoS, inside grpc-netty-shaded's bundled
            // Netty) and CVE-2024-7254 (protobuf-java stack overflow on deeply nested messages).
            // Every io.grpc module is pinned together since mixing grpc-java versions across
            // modules risks ABI mismatches.
            dependency("com.google.protobuf:protobuf-java:3.25.5")
            dependency("com.google.protobuf:protobuf-java-util:3.25.5")
            dependency("io.grpc:grpc-api:1.75.0")
            dependency("io.grpc:grpc-context:1.75.0")
            dependency("io.grpc:grpc-core:1.75.0")
            dependency("io.grpc:grpc-netty-shaded:1.75.0")
            dependency("io.grpc:grpc-protobuf:1.75.0")
            dependency("io.grpc:grpc-protobuf-lite:1.75.0")
            dependency("io.grpc:grpc-stub:1.75.0")
            dependency("io.grpc:grpc-util:1.75.0")
        }
    }

    dependencies {
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-parameters")
    }

    // Application entry points and @Configuration classes are pure bean-wiring with no branches
    // worth measuring; excluding them keeps the gate meaningful instead of being padded by classes
    // no unit test could sensibly exercise.
    val coverageExclusions = listOf("**/*Application.class", "**/config/**")

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
        classDirectories.setFrom(
            classDirectories.files.map { fileTree(it) { exclude(coverageExclusions) } }
        )
    }

    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn(tasks.named("jacocoTestReport"))
        classDirectories.setFrom(
            classDirectories.files.map { fileTree(it) { exclude(coverageExclusions) } }
        )
        violationRules {
            rule {
                limit {
                    counter = "INSTRUCTION"
                    value = "COVEREDRATIO"
                    minimum = (coverageMinimums[project.name] ?: 0.10).toBigDecimal()
                }
            }
        }
    }

    tasks.named("check") {
        dependsOn(tasks.named("jacocoTestCoverageVerification"))
    }
}
