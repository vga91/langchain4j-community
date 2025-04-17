package dev.langchain4j.rag.content.retriever.neo4j;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.neo4j.driver.Driver;

import java.util.Map;

// todo - Neo4jGraph instead of session!!! <-- https://python.langchain.com/docs/integrations/graphs/neo4j_cypher/

// todo - impostazione per query embedding e question, di default query embedding è ugaule a question
//  TODO : altra classe??? eh a sto punto si...
        // https://neo4j.com/labs/genai-ecosystem/langchain/#_cypherqachain
public class BasicRetriever extends Neo4jEmbeddingRetriever {

    public BasicRetriever(final EmbeddingModel embeddingModel, final Driver driver, final int maxResults, final double minScore, final String query, final Map<String, Object> params, final Neo4jEmbeddingStore embeddingStore) {
        super(embeddingModel, driver, maxResults, minScore, query, params, embeddingStore, null, null, null, null, null);
    }

    @Override
    public Neo4jEmbeddingStore getDefaultEmbeddingStore(final Driver driver) {
        return Neo4jEmbeddingStore.builder()
                .driver(driver)
                .dimension(384)
                .build();
    }
    
    // TODO - test with answerModel
}
