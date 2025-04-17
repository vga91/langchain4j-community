package dev.langchain4j.rag.content.retriever.neo4j;

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
            
    
    /*
        MERGE (p:Parent {id: $parent_id})
    SET p.text = $parent_text
    WITH p
    CALL db.create.setVectorProperty(p, 'embedding', $parent_embedding)
    YIELD node
    WITH p 
    UNWIND $children AS child
    MERGE (c:Child {id: child.id})
    SET c.text = child.text
    MERGE (c)<-[:HAS_CHILD]-(p)
    WITH c, child
    CALL db.create.setVectorProperty(c, 'embedding', child.embedding)
    YIELD node
    RETURN count(*)
     */
//    public static final String PARENT_QUERY =
//            """
//                        UNWIND $rows AS question
//                        MERGE (p:Parent {id: $parentId})
//                        // SET p.text = $parent_text
//                        WITH p
//                        CALL db.create.setVectorProperty(p, 'embedding', $parent_embedding)
//                        YIELD node
//                        WITH p\s
//                        UNWIND $children AS child
//                        MERGE (c:Child {id: child.id})
//                        SET c.text = child.text
//                        MERGE (c)<-[:HAS_CHILD]-(p)
//                        WITH c, child
//                        CALL db.create.setVectorProperty(c, 'embedding', child.embedding)
//                        YIELD node
//                        RETURN count(*)
//                    """;

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
            super(embeddingModel, driver, maxResults, minScore, "CREATE (:Parent $metadata)", Map.of(), embeddingStore, chatModel, SYSTEM_PROMPT, USER_PROMPT, null, null);
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
}
