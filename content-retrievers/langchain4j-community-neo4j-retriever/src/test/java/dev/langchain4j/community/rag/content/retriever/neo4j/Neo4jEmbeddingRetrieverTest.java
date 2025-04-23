package dev.langchain4j.community.rag.content.retriever.neo4j;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class Neo4jEmbeddingRetrieverTest extends Neo4jEmbeddingRetrieverBaseTest {
    
    @Mock
    private ChatLanguageModel chatLanguageModel;

    @Test
    public void testBasicRetriever() {
        final Neo4jEmbeddingRetriever retriever = Neo4jEmbeddingRetriever.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .driver(driver)
                .maxResults(1)
                .minScore(0.4)
                .build();

        Document parentDoc = getDocument();

        // Child splitter: splits into sentences using OpenNLP
        final String expectedQuery = "\\n\\n";
        int maxSegmentSize = 250;
        DocumentSplitter splitter = new DocumentByRegexSplitter(expectedQuery, expectedQuery, maxSegmentSize, 0);

        retriever.index(parentDoc, splitter);

        // Query and validate results
        final String retrieveQuery = "fundamental theory";
        List<Content> results = retriever.retrieve(Query.from(retrieveQuery));
        commonResults(results, retrieveQuery);
    }


    @Test
    public void testRetrieverWithChatModel() {
        String customRetrieval = """
            MATCH (node)<-[:HAS_CHILD]-(parent)
            WITH parent, collect(node.text) AS chunks, max(score) AS score
            RETURN parent.text + reduce(r = "", c in chunks | r + "\\n\\n" + c) AS text,
                   score,
                   properties(parent) AS metadata
            ORDER BY score DESC
            LIMIT $maxResults""";

        String creationQuery =
                """
                    UNWIND $rows AS row
                    MATCH (p:Parent {parentId: $parentId})
                    CREATE (p)-[:HAS_CHILD]->(u:%1$s {%2$s: row.%2$s})
                    SET u += row.%3$s
                    WITH row, u
                    CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
                    RETURN count(*)""";


        final Neo4jEmbeddingStore neo4jEmbeddingStore = Neo4jEmbeddingStore.builder()
                .driver(driver)
                .retrievalQuery(customRetrieval)
                .entityCreationQuery(creationQuery)
                .label("Chunk")
                .indexName("chunk_embedding_index")
                .dimension(384)
                .build();
        
        when(chatLanguageModel.chat(anyList()))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.aiMessage("Naruto")).build());

        final Neo4jEmbeddingRetriever retriever = Neo4jEmbeddingRetriever.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(neo4jEmbeddingStore)
                .driver(driver)
                .maxResults(2)
                .minScore(0.4)
                .chatModel(chatLanguageModel)
                .query("CREATE (:Parent $metadata)")
                .promptUser("mock prompt user")
                .promptSystem("mock prompt system")
                .promptAnswer("mock prompt anwser")
                .build();

        Document parentDoc = getDocument();

        // Child splitter: splits into sentences using OpenNLP
        final String expectedQuery = "\\n\\n";
        int maxSegmentSize = 250;
        DocumentSplitter splitter = new DocumentByRegexSplitter(expectedQuery, expectedQuery, maxSegmentSize, 0);

        retriever.index(parentDoc, splitter);
        final String retrieveQuery = "naruto";
        List<Content> results = retriever.retrieve(Query.from(retrieveQuery));
        commonResults(results, retrieveQuery);
        
    }

    @Test
    public void testBasicRetrieverWithChatQuestionAndAnswerModel() {
        String customRetrieval = """
            MATCH (node)<-[:HAS_CHILD]-(parent)
            WITH parent, collect(node.text) AS chunks, max(score) AS score
            RETURN parent.text + reduce(r = "", c in chunks | r + "\\n\\n" + c) AS text,
                   score,
                   properties(parent) AS metadata
            ORDER BY score DESC
            LIMIT $maxResults""";

        String creationQuery =
                """
                    UNWIND $rows AS row
                    MATCH (p:Parent {parentId: $parentId})
                    CREATE (p)-[:HAS_CHILD]->(u:%1$s {%2$s: row.%2$s})
                    SET u += row.%3$s
                    WITH row, u
                    CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
                    RETURN count(*)""";


        final Neo4jEmbeddingStore neo4jEmbeddingStore = Neo4jEmbeddingStore.builder()
                .driver(driver)
                .retrievalQuery(customRetrieval)
                .entityCreationQuery(creationQuery)
                .label("Chunk")
                .indexName("chunk_embedding_index")
                .dimension(384)
                .build();
        
        when(chatLanguageModel.chat(anyList()))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.aiMessage("Naruto")).build());


        final String chatResponse = "dattebayo";
        when(chatLanguageModel.chat(anyString()))
                .thenReturn(chatResponse);

        final Neo4jEmbeddingRetriever retriever = Neo4jEmbeddingRetriever.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(neo4jEmbeddingStore)
                .driver(driver)
                .maxResults(2)
                .minScore(0.4)
                .chatModel(chatLanguageModel)
                .answerModel(chatLanguageModel)
                .query("CREATE (:Parent $metadata)")
                .promptUser("mock prompt user")
                .promptSystem("mock prompt system")
                .promptAnswer("mock prompt anwser")
                .build();

        Document parentDoc = getDocument();

        // Child splitter: splits into sentences using OpenNLP
        final String expectedQuery = "\\n\\n";
        int maxSegmentSize = 250;
        DocumentSplitter splitter = new DocumentByRegexSplitter(expectedQuery, expectedQuery, maxSegmentSize, 0);

        retriever.index(parentDoc, splitter);
        final String retrieveQuery = "naruto";
        List<Content> results = retriever.retrieve(Query.from(retrieveQuery));
        assertThat(results).hasSize(1);

        Content result = results.get(0);
        assertTrue(result.textSegment().text().toLowerCase().contains(chatResponse));
    }
    
    // TODO - parentIdKey
    
    // TODO - params
    @Test
    void testRetrieverWithCustomRetrievalAndEmbeddingCreationQuery() {
        String customRetrieval = """
            MATCH (node)<-[:HAS_CHILD]-(parent)
            WITH parent, collect(node.text) AS chunks, max(score) AS score
            RETURN parent.text + reduce(r = "", c in chunks | r + "\\n\\n" + c) AS text,
                   score,
                   properties(parent) AS metadata
            ORDER BY score DESC
            LIMIT $maxResults""";

        String creationQuery =
                """
                    UNWIND $rows AS row
                    MATCH (p:Parent {parentId: $parentId})
                    CREATE (p)-[:HAS_CHILD]->(u:%1$s {%2$s: row.%2$s})
                    SET u += row.%3$s
                    WITH row, u
                    CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
                    RETURN count(*)""";

        final Neo4jEmbeddingStore neo4jEmbeddingStore = Neo4jEmbeddingStore.builder()
                .driver(driver)
                .retrievalQuery(customRetrieval)
                .entityCreationQuery(creationQuery)
                .label("Chunk")
                .indexName("chunk_embedding_index")
                .dimension(384)
                .build();

        final Neo4jEmbeddingRetriever retriever = Neo4jEmbeddingRetriever.builder()
                .embeddingModel(embeddingModel)
                .driver(driver)
                .query("CREATE (:Parent $metadata)")
                .maxResults(5)
                .minScore(0.4)
                .embeddingStore(neo4jEmbeddingStore)
                .build();

        Document doc = Document.from(
                """
                Artificial Intelligence (AI) is a field of computer science. It focuses on creating intelligent agents capable of performing tasks that require human intelligence.
        
                Machine Learning (ML) is a subset of AI. It uses data to learn patterns and make predictions. Deep Learning is a specialized form of ML based on neural networks.
                """,
                getMetadata()
        );

        // Parent splitter splits on paragraphs (double newlines)
        final String expectedQuery = "\\n\\n";
        int maxSegmentSize = 250;
        DocumentSplitter parentSplitter = new DocumentByRegexSplitter(expectedQuery, expectedQuery, maxSegmentSize, 0);

        // Child splitter splits on periods (sentences)
        final String expectedQueryChild = "\\. ";
        DocumentSplitter childSplitter = new DocumentByRegexSplitter(expectedQueryChild, expectedQuery, maxSegmentSize, 0);
        
        // Index the document into Neo4j as parent-child nodes
        retriever.index(doc, parentSplitter, childSplitter);

        final String retrieveQuery = "Machine Learning";
        List<Content> results = retriever.retrieve(Query.from(retrieveQuery));
        assertThat(results).hasSize(2);
    }

    @Test
    void testRetrieverWithCustomRetrievalAndEmbeddingCreationQueryAndPreInsertedData() {
        String customRetrieval = """
            MATCH (node)<-[:HAS_CHILD]-(parent)
            WITH parent, collect(node.text) AS chunks, max(score) AS score
            RETURN parent.text + reduce(r = "", c in chunks | r + "\\n\\n" + c) AS text,
                   score,
                   properties(parent) AS metadata
            ORDER BY score DESC
            LIMIT $maxResults""";

        String creationQuery =
                """
                    UNWIND $rows AS row
                    MATCH (p:Parent {parentId: $parentId})
                    CREATE (p)-[:HAS_CHILD]->(u:%1$s {%2$s: row.%2$s})
                    SET u += row.%3$s
                    WITH row, u
                    CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
                    RETURN count(*)""";

        final Neo4jEmbeddingStore neo4jEmbeddingStore = Neo4jEmbeddingStore.builder()
                .driver(driver)
                .retrievalQuery(customRetrieval)
                .entityCreationQuery(creationQuery)
                .label("Chunk")
                .indexName("chunk_embedding_index")
                .dimension(384)
                .build();

        seedTestParentChildData();

        final Neo4jEmbeddingRetriever retriever = Neo4jEmbeddingRetriever.builder()
                .embeddingModel(embeddingModel)
                .driver(driver)
                .maxResults(5)
                .minScore(0.6)
                .embeddingStore(neo4jEmbeddingStore)
                .build();

        // Act
        List<Content> results = retriever.retrieve(new Query("quantum physics"));

        // Assert
        assertEquals(1, results.size());
        Content parent = results.get(0);

        assertTrue(parent.textSegment().text().contains("quantum physics"));
        assertEquals("science", parent.textSegment().metadata().getString("source"));
    }
    
}
