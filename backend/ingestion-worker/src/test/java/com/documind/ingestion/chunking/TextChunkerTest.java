package com.documind.ingestion.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import com.documind.ingestion.extraction.ExtractedPage;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker(chunkingProperties(100, 20, 10));

    @Test
    void splitsPagesIntoOverlappingChunks() {
        List<TextChunk> chunks = chunker.chunk(List.of(new ExtractedPage(1, "a".repeat(250))));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.pageNumber()).isEqualTo(1));
        assertThat(chunks.get(0).index()).isZero();
    }

    @Test
    void keepsPageNumbersForEachChunk() {
        List<TextChunk> chunks =
                chunker.chunk(
                        List.of(
                                new ExtractedPage(1, "b".repeat(120)),
                                new ExtractedPage(2, "c".repeat(120))));

        assertThat(chunks).extracting(TextChunk::pageNumber).contains(1, 2);
    }

    @Test
    void discardsSegmentsBelowMinimumLength() {
        List<TextChunk> chunks = chunker.chunk(List.of(new ExtractedPage(1, "short")));

        assertThat(chunks).isEmpty();
    }

    private ChunkingProperties chunkingProperties(
            int chunkSize, int overlap, int minimumChunkLength) {
        ChunkingProperties properties = new ChunkingProperties();
        properties.setChunkSize(chunkSize);
        properties.setOverlap(overlap);
        properties.setMinimumChunkLength(minimumChunkLength);
        return properties;
    }
}
