package dev.langchain4j.community.rag.content.retriever.neo4j;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.neo4j.driver.Driver;

import java.util.Map;

public class ParentChildGraphRetriever extends Neo4jEmbeddingRetriever {
    public final static String DEFAULT_RETRIEVAL = """
            MATCH (node)<-[:HAS_CHILD]-(parent)
            WITH parent, collect(node.text) AS chunks, max(score) AS score
            RETURN parent.text + reduce(r = "", c in chunks | r + "\\n\\n" + c) AS text,
                   score,
                   properties(parent) AS metadata
            ORDER BY score DESC
            LIMIT $maxResults""";

    public static final String PARENT_QUERY =
            """
                UNWIND $rows AS row
                MATCH (p:Parent {parentId: $parentId})
                CREATE (p)-[:HAS_CHILD]->(u:%1$s {%2$s: row.%2$s})
                SET u += row.%3$s
                WITH row, u
                CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
                RETURN count(*)""";

    
    public ParentChildGraphRetriever(EmbeddingModel embeddingModel, Driver driver,
                                     int maxResults,
                                     double minScore, Neo4jEmbeddingStore embeddingStore) {
        super(embeddingModel, driver, maxResults, minScore, "CREATE (:Parent $metadata)", Map.of(), embeddingStore,  null, null, null, null, null, null);
    }

    @Override
    public Neo4jEmbeddingStore getDefaultEmbeddingStore(Driver driver) {
        return Neo4jEmbeddingStore.builder()
                .driver(driver)
                .retrievalQuery(DEFAULT_RETRIEVAL)
                .entityCreationQuery(PARENT_QUERY)
                .label("Child")
                .indexName("child_embedding_index")
                .dimension(384)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends Neo4jEmbeddingRetriever.Builder<Builder, ParentChildGraphRetriever> {

        @Override
        public ParentChildGraphRetriever build() {
            return new ParentChildGraphRetriever(
                    embeddingModel,
                    driver,
                    maxResults,
                    minScore,
                    embeddingStore);
        }
    }


}
