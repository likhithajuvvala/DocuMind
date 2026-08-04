package com.documind.ingestion.chunking;

import com.documind.ingestion.extraction.ExtractedPage;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TextChunker {

    private final ChunkingProperties properties;

    public TextChunker(ChunkingProperties properties) {
        this.properties = properties;
    }

    public List<TextChunk> chunk(List<ExtractedPage> pages) {
        List<TextChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (ExtractedPage page : pages) {
            for (String segment : splitPage(page.text())) {
                chunks.add(new TextChunk(chunkIndex++, page.pageNumber(), segment));
            }
        }

        return List.copyOf(chunks);
    }

    private List<String> splitPage(String text) {
        List<String> segments = new ArrayList<>();
        int chunkSize = properties.getChunkSize();
        int step = Math.max(1, chunkSize - properties.getOverlap());

        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(text.length(), start + chunkSize);
            String segment = text.substring(start, end).strip();
            if (segment.length() >= properties.getMinimumChunkLength()) {
                segments.add(segment);
            }
            if (end == text.length()) {
                break;
            }
        }

        return segments;
    }
}
