plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "documind"

include("common")
include("gateway-service")
include("document-service")
include("ingestion-worker")
include("query-service")
