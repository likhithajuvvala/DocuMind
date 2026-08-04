package com.documind.ingestion.extraction;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

@Component
public class TextExtractor {

    private static final int UNLIMITED_CONTENT_LENGTH = -1;
    private static final String PAGE_SEPARATOR = "\f";

    public List<ExtractedPage> extract(InputStream content) {
        BodyContentHandler handler = new BodyContentHandler(UNLIMITED_CONTENT_LENGTH);
        try {
            new AutoDetectParser().parse(content, handler, new Metadata(), new ParseContext());
        } catch (TikaException | SAXException | IOException exception) {
            throw new TextExtractionException("Unable to extract text from the document", exception);
        }

        return toPages(handler.toString());
    }

    private List<ExtractedPage> toPages(String text) {
        List<ExtractedPage> pages = new ArrayList<>();
        String[] rawPages = text.split(PAGE_SEPARATOR);
        for (int index = 0; index < rawPages.length; index++) {
            String pageText = rawPages[index].strip();
            if (!pageText.isEmpty()) {
                pages.add(new ExtractedPage(index + 1, pageText));
            }
        }
        return pages.isEmpty() ? List.of(new ExtractedPage(1, text.strip())) : List.copyOf(pages);
    }
}
