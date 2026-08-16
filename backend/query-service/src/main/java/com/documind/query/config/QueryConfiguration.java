package com.documind.query.config;

import com.documind.query.pii.PiiRedactionPolicy;
import com.documind.query.pii.PiiRedactionProperties;
import com.documind.query.pii.PiiRedactor;
import com.documind.query.rag.ChunkReranker;
import com.documind.query.rag.LexicalOverlapReranker;
import com.documind.query.rag.PassThroughReranker;
import com.documind.query.rag.RetrievalProperties;
import com.documind.query.usage.ModelPricingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    RetrievalProperties.class,
    ModelPricingProperties.class,
    PiiRedactionProperties.class
})
public class QueryConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryConfiguration.class);

    private static final String LEXICAL = "lexical";

    @Bean
    public ChunkReranker chunkReranker(RetrievalProperties properties) {
        if (!LEXICAL.equalsIgnoreCase(properties.getReranker())) {
            LOGGER.info("Re-ranking is disabled, chunks stay in embedding similarity order");
            return new PassThroughReranker();
        }
        LOGGER.info(
                "Re-ranking retrieved chunks with lexical overlap, weighting embedding similarity at {}",
                properties.getRerankVectorWeight());
        return new LexicalOverlapReranker(properties.getRerankVectorWeight());
    }

    @Bean
    public PiiRedactor piiRedactor() {
        return new PiiRedactor();
    }

    @Bean
    public PiiRedactionPolicy piiRedactionPolicy(
            PiiRedactionProperties properties,
            @Value("${spring.ai.model.chat:openai}") String chatProvider) {
        return new PiiRedactionPolicy(properties, chatProvider);
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
