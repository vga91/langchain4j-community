package dev.langchain4j.community.rag.content.retriever.neo4j;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.neo4j.driver.Driver;

import java.util.Map;


public class BasicRetriever extends Neo4jEmbeddingRetriever {


    /**
     * Creates an instance of Neo4jEmbeddingRetriever
     *
     * @param embeddingModel
     * @param driver
     * @param maxResults
     * @param minScore
     * @param query
     * @param params
     * @param embeddingStore
     * @param model
     * @param promptSystem
     * @param promptUser
     * @param answerModel
     * @param promptAnswer
     * @param parentIdKey
     */
    public BasicRetriever(final EmbeddingModel embeddingModel, final Driver driver, final int maxResults, final double minScore, final String query, final Map<String, Object> params, final Neo4jEmbeddingStore embeddingStore, final ChatLanguageModel model, final String promptSystem, final String promptUser, final ChatLanguageModel answerModel, final String promptAnswer, final String parentIdKey) {
        super(embeddingModel, driver, maxResults, minScore, query, params, embeddingStore, model, promptSystem, promptUser, answerModel, promptAnswer, parentIdKey);
    }

    @Override
    public Neo4jEmbeddingStore getDefaultEmbeddingStore(final Driver driver) {
        return Neo4jEmbeddingStore.builder()
                .driver(driver)
                .dimension(384)
                .build();
    }

    public static Builder builder() {
        return new Builder(BasicRetriever.class);
    }

    public static class Builder extends Neo4jEmbeddingRetriever.Builder<Builder, BasicRetriever> {
        public Builder(final Class clazz) {
            super(clazz);
        }

        @Override
        public BasicRetriever build() {
            return super.build();
        }
    }
}
