package com.documind.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.documind")
public class IngestionWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestionWorkerApplication.class, args);
    }
}
