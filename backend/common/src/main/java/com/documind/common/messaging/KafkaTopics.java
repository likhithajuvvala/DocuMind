package com.documind.common.messaging;

public final class KafkaTopics {

    public static final String DOCUMENT_UPLOADED = "documind.document.uploaded";
    public static final String DOCUMENT_INDEXED = "documind.document.indexed";
    public static final String DOCUMENT_FAILED = "documind.document.failed";
    public static final String DOCUMENT_DELETED = "documind.document.deleted";
    public static final String DOCUMENT_UPLOADED_DEAD_LETTER = "documind.document.uploaded.dlt";

    private KafkaTopics() {
    }
}
