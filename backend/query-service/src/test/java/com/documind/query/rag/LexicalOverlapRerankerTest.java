package com.documind.query.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LexicalOverlapRerankerTest {

    private static final UUID DOCUMENT_ID = UUID.randomUUID();

    private final LexicalOverlapReranker reranker = new LexicalOverlapReranker(0.6);

    @Test
    void promotesThePassageThatAnswersTheQuestionOverTheOneThatMerelyDiscussesIt() {
        // Taken from a real retrieval: the renewal paragraph outscored the termination clause
        // on embedding similarity alone, so the answer was cited third.
        RetrievedChunk renewal =
                chunk(
                        "The Agreement renews automatically for successive twelve month periods unless either party "
                                + "gives written notice of non-renewal at least sixty days before the end of the term.",
                        0.738);
        RetrievedChunk termination =
                chunk(
                        "Termination for convenience. Either party may terminate this Agreement without cause by "
                                + "providing ninety days prior written notice to the other party.",
                        0.622);

        List<RetrievedChunk> reranked =
                reranker.rerank(
                        "What is the termination clause in the vendor agreement?",
                        List.of(renewal, termination));

        assertThat(reranked.get(0).text()).startsWith("Termination for convenience");
    }

    @Test
    void keepsEmbeddingOrderWhenNoQuestionTermsAppearInEitherChunk() {
        RetrievedChunk first = chunk("Backups are retained for thirty five days.", 0.80);
        RetrievedChunk second = chunk("Expenses must be submitted within sixty days.", 0.60);

        List<RetrievedChunk> reranked =
                reranker.rerank("quarterly revenue forecast", List.of(first, second));

        assertThat(reranked).containsExactly(first, second);
    }

    @Test
    void doesNotLetLexicalOverlapAloneOutweighAStrongEmbeddingMatch() {
        RetrievedChunk strongEmbedding =
                chunk("Annual leave accrues at twenty five days per year.", 0.95);
        RetrievedChunk keywordStuffed = chunk("leave leave leave policy leave", 0.30);

        List<RetrievedChunk> reranked =
                reranker.rerank("annual leave", List.of(strongEmbedding, keywordStuffed));

        assertThat(reranked.get(0)).isEqualTo(strongEmbedding);
    }

    @Test
    void leavesASingleChunkUntouched() {
        RetrievedChunk only = chunk("Only one candidate.", 0.5);

        assertThat(reranker.rerank("anything", List.of(only))).containsExactly(only);
    }

    @Test
    void ignoresQuestionsMadeEntirelyOfCommonWords() {
        RetrievedChunk first = chunk("Alpha content", 0.4);
        RetrievedChunk second = chunk("Beta content", 0.9);

        assertThat(reranker.rerank("what are the", List.of(first, second)))
                .containsExactly(first, second);
    }

    @Test
    void weightOfOneKeepsPureEmbeddingOrder() {
        LexicalOverlapReranker embeddingOnly = new LexicalOverlapReranker(1.0);
        RetrievedChunk weakButRelevantWording = chunk("termination clause details", 0.40);
        RetrievedChunk strongEmbedding = chunk("unrelated wording", 0.90);

        List<RetrievedChunk> reranked =
                embeddingOnly.rerank(
                        "termination clause", List.of(strongEmbedding, weakButRelevantWording));

        assertThat(reranked).containsExactly(strongEmbedding, weakButRelevantWording);
    }

    private RetrievedChunk chunk(String text, double relevance) {
        return new RetrievedChunk(
                0, DOCUMENT_ID, "vendor-services-agreement.md", 1, text, relevance);
    }
}
