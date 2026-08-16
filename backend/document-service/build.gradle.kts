plugins {
    java
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":common"))
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.apache.tika:tika-core:3.3.2")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
}
