package dev.langchain4j.community.rag.content.retriever.neo4j;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Neo4jEmbeddingRetrieverTest extends Neo4jEmbeddingRetrieverBaseTest {
    
    // TODO - mocked chatModel
    @Mock
    private ChatLanguageModel chatLanguageModel;

    @Test
    public void testBasicRetriever() {
        final Neo4jEmbeddingRetriever retriever = Neo4jEmbeddingRetriever.builder()
                .embeddingModel(embeddingModel)
                .driver(driver)
                .maxResults(1)
                .minScore(0.4)
                .build();

        Document parentDoc = Document.from(
                """
                Quantum mechanics studies how particles behave. It is a fundamental theory in physics.
                
                Gradient descent and backpropagation algorithms.
                
                Spaghetti carbonara and Italian dishes.
                """,
                getMetadata()
        );

        // Child splitter: splits into sentences using OpenNLP
        final String expectedQuery = "\\n\\n";
        int maxSegmentSize = 250;
        DocumentSplitter splitter = new DocumentByRegexSplitter(expectedQuery, expectedQuery, maxSegmentSize, 0);

        retriever.index(parentDoc, splitter, null);

        // Query and validate results
        List<Content> results = retriever.retrieve(Query.from("fundamental theory"));

        commonResults(results);
    }

    
    
    // TODO -- other retriever to be moved in another PR
    @Test
    public void testParentChildRetrieverWithDocumentByRegexSplitter() {

        ParentChildGraphRetriever retriever = ParentChildGraphRetriever.builder()
                .embeddingModel(embeddingModel)
                .driver(driver)
                .maxResults(1)
                .minScore(0.8)
                .build();

        // Parent splitter splits on paragraphs (double newlines)
        final String expectedQuery = "\\n\\n";
        int maxSegmentSize = 250;
        DocumentSplitter parentSplitter = new DocumentByRegexSplitter(expectedQuery, expectedQuery, maxSegmentSize, 0);

        // Child splitter splits on periods (sentences)
        final String expectedQuery1 = "\\. ";
        DocumentSplitter childSplitter = new DocumentByRegexSplitter(expectedQuery1, expectedQuery, maxSegmentSize, 0);

        Document doc = Document.from(
                """
                Artificial Intelligence (AI) is a field of computer science. It focuses on creating intelligent agents capable of performing tasks that require human intelligence.
        
                Machine Learning (ML) is a subset of AI. It uses data to learn patterns and make predictions. Deep Learning is a specialized form of ML based on neural networks.
                """,
                Metadata.from(Map.of("title", "AI Overview", "url", "https://example.com/ai", "id", "doc-ai"))
        );

        // Index the document into Neo4j as parent-child nodes
        retriever.index(doc, parentSplitter, childSplitter);

        // Query and validate results
        List<Content> results = retriever.retrieve(Query.from("What is Machine Learning?"));
        commonResults(results);
    }
    
}
