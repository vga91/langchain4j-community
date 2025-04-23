package dev.langchain4j.community.rag.content.retriever.neo4j;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.neo4j.driver.Driver;

import java.util.Map;

public class SummaryRetriever extends Neo4jEmbeddingRetriever {
    public final static String DEFAULT_RETRIEVAL = """
            MATCH (node)<-[:HAS_SUMMARY]-(parent)
            WITH parent, max(score) AS score, node // deduplicate parents
            RETURN parent.text AS text, score, properties(node) AS metadata
            ORDER BY score DESC
            LIMIT $maxResults""";

    public final static String SYSTEM_PROMPT = """
            You are generating concise and accurate summaries based on the information found in the text.
            """;

    public final static String USER_PROMPT = """
            Generate a summary of the following input:
            {{input}}
            
            Summary:
            """;

    public static final String PARENT_QUERY =

            """
                UNWIND $rows AS row
                MATCH (p:Parent {parentId: $parentId})
                CREATE (p)-[:HAS_SUMMARY]->(u:%1$s {%2$s: row.%2$s})
                SET u += row.%3$s
                WITH row, u
                CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
                RETURN count(*)""";
    
    public SummaryRetriever(final EmbeddingModel embeddingModel, final Driver driver, final int maxResults, final double minScore, final Neo4jEmbeddingStore embeddingStore, final ChatLanguageModel chatModel) {
            super(embeddingModel, driver, maxResults, minScore, "CREATE (:Parent $metadata)", Map.of(), embeddingStore, chatModel, SYSTEM_PROMPT, USER_PROMPT, null, null, null);
    }

    public static Builder builder() {
        return new Builder(SummaryRetriever.class);
    }

    @Override
    public Neo4jEmbeddingStore getDefaultEmbeddingStore(final Driver driver) {
        return Neo4jEmbeddingStore.builder()
                .driver(driver)
                .retrievalQuery(DEFAULT_RETRIEVAL)
                .entityCreationQuery(PARENT_QUERY)
                .label("Summary")
                .indexName("summary_embedding_index")
                .dimension(384)
                .build();
    }
    
    public static class Builder extends Neo4jEmbeddingRetriever.Builder<Builder, SummaryRetriever> {
        public Builder(final Class clazz) {
            super(clazz);
        }
    }
}
