package dev.langchain4j.community.rag.content.retriever.neo4j;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import org.junit.jupiter.api.BeforeAll;

public class Neo4jEmbeddingRetrieverBaseTest extends Neo4jRetrieverBaseTest {
    protected static Neo4jEmbeddingStore embeddingStore;
    protected static EmbeddingModel embeddingModel;
    
    @BeforeAll
    public static void beforeAll() {
        Neo4jRetrieverBaseTest.beforeAll();
        
        embeddingStore = Neo4jEmbeddingStore.builder()
                .driver(driver)
                .dimension(384)
                .build();
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    }
}
