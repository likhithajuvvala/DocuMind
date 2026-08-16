import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

plugins {
    id("org.springframework.boot") version "3.5.16" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.documind"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    configure<DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.16")
            mavenBom("org.springframework.ai:spring-ai-bom:1.0.9")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.3")
            mavenBom("org.testcontainers:testcontainers-bom:1.20.4")
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
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-parameters")
    }
}
