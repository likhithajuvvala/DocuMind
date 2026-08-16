package com.documind.ingestion.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * A deterministic stand-in for a real embedding provider, so integration tests exercise the real
 * vector store without calling out to OpenAI or Ollama. Text is hashed into a fixed-size
 * bag-of-words vector: texts that share vocabulary end up with a higher cosine similarity, which is
 * enough to make similarity search behave meaningfully without needing a trained model.
 */
public class HashingEmbeddingModel implements EmbeddingModel {

    private final int dimensions;

    public HashingEmbeddingModel(int dimensions) {
        this.dimensions = dimensions;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> instructions = request.getInstructions();
        for (int index = 0; index < instructions.size(); index++) {
            embeddings.add(new Embedding(vectorFor(instructions.get(index)), index));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vectorFor(document.getText());
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    private float[] vectorFor(String text) {
        float[] vector = new float[dimensions];
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{Alnum}]+")) {
            if (token.isBlank()) {
                continue;
            }
            int index = Math.floorMod(token.hashCode(), dimensions);
            vector[index] += 1f;
        }
        normalize(vector);
        return vector;
    }

    private void normalize(float[] vector) {
        double sumOfSquares = 0;
        for (float value : vector) {
            sumOfSquares += value * value;
        }
        if (sumOfSquares == 0) {
            vector[0] = 1f;
            return;
        }
        double norm = Math.sqrt(sumOfSquares);
        for (int index = 0; index < vector.length; index++) {
            vector[index] = (float) (vector[index] / norm);
        }
    }
}
