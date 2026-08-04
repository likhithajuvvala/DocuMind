package com.documind.gateway.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "documind.gateway.downstream")
public class DownstreamServiceProperties {

    private String documentService = "http://localhost:8081";
    private String queryService = "http://localhost:8083";

    public String getDocumentService() {
        return documentService;
    }

    public void setDocumentService(String documentService) {
        this.documentService = documentService;
    }

    public String getQueryService() {
        return queryService;
    }

    public void setQueryService(String queryService) {
        this.queryService = queryService;
    }
}
