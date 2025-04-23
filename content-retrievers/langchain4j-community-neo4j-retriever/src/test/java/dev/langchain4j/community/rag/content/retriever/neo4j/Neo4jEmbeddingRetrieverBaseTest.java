package dev.langchain4j.community.rag.content.retriever.neo4j;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // todo - RIMUOVERE BASIC RETRIEVER, NON SERVE, SI PUO FARE DIRETTAMENTE CON IL NEO4JEMBEDDINGRETRIEVER


    protected static Metadata getMetadata() {
        return Metadata.from(Map.of("title", "Quantum Mechanics", "source", "Wikipedia link", "url", "https://example.com/ai"));
    }


    protected static void commonResults(List<Content> results) {
        assertThat(results).hasSize(1);

        Content result = results.get(0);

        assertTrue(result.textSegment().text().toLowerCase().contains("fundamental theory"));
        assertEquals("Wikipedia link", result.textSegment().metadata().getString("source"));
        assertEquals("https://example.com/ai", result.textSegment().metadata().getString("source"));
    }


}
