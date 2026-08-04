package com.documind.common.config;

import com.documind.common.storage.MinioObjectStorage;
import com.documind.common.storage.ObjectStorage;
import com.documind.common.storage.ObjectStorageProperties;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
@ConditionalOnProperty(prefix = "documind.storage", name = "enabled", havingValue = "true")
public class StorageConfiguration {

    @Bean
    public MinioClient minioClient(ObjectStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .region(properties.getRegion())
                .build();
    }

    @Bean
    public ObjectStorage objectStorage(MinioClient client, ObjectStorageProperties properties) {
        return new MinioObjectStorage(client, properties);
    }
}
