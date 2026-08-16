package com.documind.document;

import com.documind.document.demo.DemoWorkspaceProperties;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.documind")
@EnableConfigurationProperties(DemoWorkspaceProperties.class)
@OpenAPIDefinition(info = @Info(title = "DocuMind Document Service", version = "v1"))
public class DocumentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentServiceApplication.class, args);
    }
}
