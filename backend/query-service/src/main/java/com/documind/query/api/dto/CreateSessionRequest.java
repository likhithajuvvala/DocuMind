package com.documind.query.api.dto;

import java.util.UUID;

public record CreateSessionRequest(UUID documentId, String title) {}
