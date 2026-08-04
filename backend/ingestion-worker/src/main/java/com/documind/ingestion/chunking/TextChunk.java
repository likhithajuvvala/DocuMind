package com.documind.ingestion.chunking;

public record TextChunk(int index, int pageNumber, String text) {
}
