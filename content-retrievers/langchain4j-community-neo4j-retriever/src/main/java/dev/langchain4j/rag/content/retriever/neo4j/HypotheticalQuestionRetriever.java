package dev.langchain4j.rag.content.retriever.neo4j;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.neo4j.driver.Driver;

import java.util.Map;

public class HypotheticalQuestionRetriever extends Neo4jEmbeddingRetriever {

    // TODO - metadata
    public final static String DEFAULT_RETRIEVAL = """
            MATCH (node)<-[:HAS_QUESTION]-(parent)
            WITH parent, max(score) AS score, node // deduplicate parents
            RETURN parent.text AS text, score, properties(node) AS metadata
             ORDER BY score DESC
            LIMIT $maxResults""";
//            
//            MATCH (node)<-[:HAS_CHILD]-(parent)
//            WITH parent, collect(node.text) AS chunks, max(score) AS score
//            RETURN parent.title + reduce(r = "", c in chunks | r + "\\n\\n" + c) AS text,
//                   score,
//                   {source: parent.url} AS metadata
//            ORDER BY score DESC
//            LIMIT $maxResults""";
    public static final String PARENT_QUERY =
            """
                        UNWIND $rows AS question
                        MATCH (p:Parent {parentId: $parentId})
                        // MERGE (p:Parent {id: $parentId})
                        WITH p, question
                        // UNWIND $questions AS question
                        CREATE (q:%1$s {%2$s: question.%2$s})
                        SET q += question.%3$s
                        MERGE (q)<-[:HAS_QUESTION]-(p)
                        WITH q, question
                        CALL db.create.setNodeVectorProperty(q, $embeddingProperty, question.%4$s)
                        // YIELD node
                        RETURN count(*)
                    """;
/*            """
                UNWIND $rows AS row
                MATCH (p:Parent {id: $parentId})
                CREATE (p)-[:HAS_CHILD]->(u:%1$s {%2$s: row.%2$s})
                SET u += row.%3$s
                WITH row, u
                CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
                RETURN count(*)""";*/
    
    public HypotheticalQuestionRetriever(final EmbeddingModel embeddingModel, final Driver driver, final int maxResults, final double minScore, final Neo4jEmbeddingStore embeddingStore) {
        super(embeddingModel, driver, maxResults, minScore, "CREATE (:Parent $metadata)", Map.of(), embeddingStore);
    }

    @Override
    public Neo4jEmbeddingStore getDefaultEmbeddingStore(final Driver driver) {
        return Neo4jEmbeddingStore.builder()
                .driver(driver)
                .retrievalQuery(DEFAULT_RETRIEVAL)
                .entityCreationQuery(PARENT_QUERY)
                .label("Child")
                .indexName("child_embedding_index")
                .dimension(384)
                .build();
    }
}
