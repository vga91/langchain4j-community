package dev.langchain4j.rag.content.retriever.neo4j;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

import java.util.List;
import java.util.Map;

public class ParentChildGraphRetriever2 extends Neo4jEmbeddingRetriever {
    public final static String DEFAULT_RETRIEVAL = """
            MATCH (node)<-[:HAS_CHILD]-(parent)
            WITH parent, collect(node.text) AS chunks, max(score) AS score
            RETURN parent.title + reduce(r = "", c in chunks | r + "\\n\\n" + c) AS text,
                   score,
                   {source: parent.url} AS metadata
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
    

    // TODO https://chatgpt.com/c/67ff6a72-5fe0-800c-9c12-c50ec8d5ee35
    // todo --> creare un fromLLM
    
    public ParentChildGraphRetriever2(EmbeddingModel embeddingModel, Driver driver,
                                      int maxResults,
                                      double minScore, Neo4jEmbeddingStore embeddingStore) {
        super(embeddingModel, driver, maxResults, minScore, "CREATE (:Parent $metadata)", Map.of(), embeddingStore);
//        this.embeddingModel = embeddingModel;
//        this.driver = driver;
//        this.maxResults = maxResults;
//        this.minScore = minScore;
        
//        if (embeddingStore == null) {
//            this.embeddingStore = getDefaultEmbeddingStore(driver);
//        }
//        else {
//            this.embeddingStore = embeddingStore;
//        }
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



//    @Override
//    public void getDocumentToNeo4jQuery(Session session, Map<String, Object> params) {
//        session.run("""
//            CREATE (:Parent $metadata)
//        """, params);
//    }


}
