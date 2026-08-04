package com.documind.ingestion.config;

import com.documind.ingestion.chunking.ChunkingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ChunkingProperties.class)
public class IngestionConfiguration {
}
