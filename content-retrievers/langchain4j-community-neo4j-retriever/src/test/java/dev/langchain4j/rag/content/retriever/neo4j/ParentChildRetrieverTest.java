package dev.langchain4j.rag.content.retriever.neo4j;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
//import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
//import dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingStore;
//import dev.langchain4j.graph.Neo4jGraph;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.*;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.testcontainers.containers.Neo4jContainer;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.langchain4j.rag.content.retriever.neo4j.Neo4jRetrieverBaseTest.NEO4J_VERSION;
import static org.junit.jupiter.api.Assertions.*;

public class ParentChildRetrieverTest {

    private static Neo4jContainer<?> neo4jContainer;
    private static Neo4jEmbeddingStore embeddingStore;
    private static Neo4jGraph graph;
    private static EmbeddingModel embeddingModel;
    private static Driver driver;
    private static ParentChildRetriever retriever;


    @BeforeAll
    public static void setUp() {
        // Start Neo4j Testcontainer
        neo4jContainer = new Neo4jContainer<>("neo4j:" + NEO4J_VERSION)
                .withPlugins("apoc")
                .withoutAuthentication(); // Disable authentication for testing
        //.withAdminPassword("test1234"); /
        neo4jContainer.start();

        driver = GraphDatabase.driver(neo4jContainer.getBoltUrl(), AuthTokens.none());

        // Initialize Neo4jEmbeddingStore
        embeddingStore = Neo4jEmbeddingStore.builder()
                .driver(driver)
                .databaseName("neo4j")
                .dimension(384)
                .build();

        // Initialize Neo4jGraph
        graph = Neo4jGraph.builder()
                .driver(driver)
                .build();

        // Initialize Embedding Model
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

        retriever = new ParentChildRetriever(
                embeddingModel,
                embeddingStore,
                graph,
                5,
                0.6
        );
        
        // seedTestData();
    }
    
    // TODO --> https://medium.com/data-science/langchains-parent-document-retriever-revisited-1fca8791f5a0
    
    /*
    - Instead of indexing entire documents, data is divided into smaller chunks, referred to as Parent and Child documents.

    - Child documents are indexed for better representation of specific concepts, while parent documents are retrieved to ensure context retention.
     */


    @AfterEach
    void afterEach() {
        try (Session session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n");
        }
//        graph.close();
//        driver.close();
    }


    @Test
    public void testParentChildRetriever_withDocumentByRegexSplitter() {
        EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        String vectorIndex = "child_embedding_index";
        int embeddingDimensions = 384;

        ParentChildGraphRetriever2 retriever = new ParentChildGraphRetriever2(
                embeddingModel, driver, vectorIndex, embeddingDimensions
        );

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
        List<Content> results = retriever.retrieve("What is Machine Learning?", 3, 0.5);

        assertFalse(results.isEmpty(), "Should retrieve at least one parent document");

        Content result = results.get(0);
        System.out.println("Retrieved Text:\n" + result.textSegment().text());
        System.out.println("Metadata: " + result.textSegment().metadata());

        assertTrue(result.textSegment().text().toLowerCase().contains("machine learning"));
        assertEquals("https://example.com/ai", result.textSegment().metadata().getString("source"));
    }
    
    
//import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
//import dev.langchain4j.data.document.splitter.DocumentSplitter;

    @Test
    public void testParentChildRetriever_withDocumentBySentenceSplitter() {
        EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        String vectorIndex = "child_embedding_index_sentence";
        int embeddingDimensions = 384;

        ParentChildGraphRetriever2 retriever = new ParentChildGraphRetriever2(
                embeddingModel, driver, vectorIndex, embeddingDimensions
        );

        int maxSegmentSize = 250;

        // Parent splitter: splits on paragraphs (double newlines)
        final String expectedQuery = "\\n\\n";
        DocumentSplitter parentSplitter = new DocumentByRegexSplitter(expectedQuery, expectedQuery, maxSegmentSize, 0);

        // Child splitter: splits into sentences using OpenNLP
        DocumentSplitter childSplitter = new DocumentBySentenceSplitter(maxSegmentSize, 0);

        Document doc = Document.from(
                """
                Artificial Intelligence (AI) is transforming various industries. It enables machines to learn from data and make decisions.
        
                Machine Learning (ML) is a subset of AI. It focuses on developing algorithms that allow computers to learn from and make predictions based on data.
                """,
                Metadata.from(Map.of("title", "AI and ML Overview", "url", "https://example.com/ai-ml", "id", "doc-ai-ml"))
        );

        retriever.index(doc, parentSplitter, childSplitter);

        List<Content> results = retriever.retrieve("What is Machine Learning?", 3, 0.5);

        assertFalse(results.isEmpty(), "Should retrieve at least one parent document");
        assertTrue(results.get(0).textSegment().text().toLowerCase().contains("machine learning"));
        assertEquals("https://example.com/ai-ml", results.get(0).textSegment().metadata().getString("source"));
    }



    static void seedTestData() {
        try (var session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n");

            // Create parent and child
            session.run("""
                CREATE (parent:Document {id: 'parent-1', text: 'This is the parent doc.', source: 'test'})
                CREATE (child:Chunk {id: 'child-1', text: 'This is a detailed chunk about embeddings.', chunk: 1})
                CREATE (parent)-[:HAS_CHILD]->(child)
            """);

            // Embed and store child embedding
            Embedding embedding = embeddingModel.embed("This is a detailed chunk about embeddings.").content();
            TextSegment segment = new TextSegment("This is a detailed chunk about embeddings.",
                    Metadata.from(Map.of("id", "child-1"))
            );

            embeddingStore.add(embedding, segment);
        }
    }


    static void seedTestData1() {
        try (var session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n");

            session.run("""
                CREATE (p1:Document {id: 'p1', text: 'Parent about machine learning', source: 'ml'})
                CREATE (p2:Document {id: 'p2', text: 'Parent about cooking', source: 'food'})
                CREATE (p3:Document {id: 'p3', text: 'Parent about quantum physics', source: 'science'})

                CREATE (c1:Chunk {id: 'c1', text: 'Gradient descent and backpropagation algorithms', chunk: 1})
                CREATE (c2:Chunk {id: 'c2', text: 'Spaghetti carbonara and Italian dishes', chunk: 2})
                CREATE (c3:Chunk {id: 'c3', text: 'Quantum entanglement and uncertainty principles', chunk: 3})

                CREATE (p1)-[:HAS_CHILD]->(c1)
                CREATE (p2)-[:HAS_CHILD]->(c2)
                CREATE (p3)-[:HAS_CHILD]->(c3)
            """);

            // Embed and store all children
            addChunkEmbedding("Gradient descent and backpropagation algorithms", "c1");
            addChunkEmbedding("Spaghetti carbonara and Italian dishes", "c2");
            addChunkEmbedding("Quantum entanglement and uncertainty principles", "c3");
        }
    }



    static void addChunkEmbedding(String text, String id) {
        Embedding embedding = embeddingModel.embed(text).content();
        TextSegment segment = new TextSegment(text, Metadata.from(Map.of("id", id)));
        embeddingStore.add(embedding, segment);
    }

    @Test
    void testRetrieverReturnsOnlyRelevantParent() {
        seedTestData1();
        // Act
        List<Content> results = retriever.retrieve(new Query("Tell me about training neural networks"));

        // Assert
        assertEquals(1, results.size());
        Content parent = results.get(0);

        assertTrue(parent.textSegment().text().contains("machine learning"));
        assertEquals("ml", parent.metadata().get("source"));
    }

    @Test
    void testRetrieverReturnsNothingForUnrelatedQuery() {
        seedTestData1();
        List<Content> results = retriever.retrieve(new Query("Soccer match results and player stats"));

        assertTrue(results.isEmpty(), "No relevant parents should match soccer topics");
    }

    @Test
    void testRetrieverReturnsMultipleParents() {
        seedTestData1();
        List<Content> results = retriever.retrieve(new Query("Tell me about quantum mechanics and cooking"));

        Set<String> sources = new HashSet<>();
        for (Content c : results) {
            sources.add((String) c.metadata().get("source"));
        }

        assertTrue(sources.contains("food"));
        assertTrue(sources.contains("science"));
    }
    
    

    @Test
    void retrievesParentGivenQueryAboutEmbeddings() {
//        final ParentChildRetriever retriever = new ParentChildRetriever(
//                embeddingModel,
//                embeddingStore,
//                graph,
//                5,
//                0.0
//        );

        seedTestData();
        List<Content> result = retriever.retrieve(new Query("Tell me about embeddings"));

        assertFalse(result.isEmpty());
        Content content = result.get(0);
        assertTrue(content.textSegment().text().contains("parent doc"));
        assertEquals("test", content.metadata().get("source"));
    }

    @Test
    public void testParentChildRetrievalWithMultipleRelationships() {
        // Step 1: Document Chunking
        String parentText1 = "Artificial Intelligence is a branch of computer science.";
        String childText1 = "AI is a branch.";

        String parentText2 = "Machine Learning is a subset of Artificial Intelligence.";
        String childText2 = "ML is a subset of AI.";

        // Step 2: Embedding Storage
        TextSegment childSegment1 = TextSegment.from(childText1);
        Embedding childEmbedding1 = embeddingModel.embed(childSegment1).content();
        String childId1 = embeddingStore.add(childEmbedding1, childSegment1);

        TextSegment childSegment2 = TextSegment.from(childText2);
        Embedding childEmbedding2 = embeddingModel.embed(childSegment2).content();
        String childId2 = embeddingStore.add(childEmbedding2, childSegment2);

        // Step 3: Graph Relationships
        try (Session session = driver.session()) {
            String createParentChildQuery = "CREATE (p:Parent {text: $parentText})-[:HAS_CHILD]->(c:Child {id: $childId, text: $childText})";
            session.run(createParentChildQuery, Map.of("parentText", parentText1, "childId", childId1, "childText", childText1));
            session.run(createParentChildQuery, Map.of("parentText", parentText2, "childId", childId2, "childText", childText2));
        }

        // Step 4: Retrieval Testing
        ParentChildRetriever retriever = new ParentChildRetriever(embeddingModel, embeddingStore, graph, 10, 0.0);
        Query query = new Query("What is AI?");
        List<Content> results = retriever.retrieve(query);

        // Verify that the correct parent documents are retrieved
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(content -> content.textSegment().text().equals(parentText1)));
        assertTrue(results.stream().anyMatch(content -> content.textSegment().text().equals(parentText2)));

        // Verify that the similarity scores are above a threshold
        float threshold = 0.8f;
        for (Content content : results) {
            System.out.println("content = " + content);
        }
//            assertTrue(content() >= threshold, "Similarity score is below threshold");
    }

    @Test
    public void testParentChildRetrievalWithNoMatches() {
        seedTestData();
        // Step 1: Document Chunking
        String parentText = "Quantum Computing is a field of computing focused on quantum mechanics.";
        String childText = "Quantum Computing uses qubits.";

        // Step 2: Embedding Storage
        TextSegment childSegment = TextSegment.from(childText);
        Embedding childEmbedding = embeddingModel.embed(childSegment).content();
        String childId = embeddingStore.add(childEmbedding, childSegment);

        // Step 3: Graph Relationships
        try (Session session = driver.session()) {
            String createParentChildQuery = "CREATE (p:Parent {text: $parentText})-[:HAS_CHILD]->(c:Child {id: $childId, text: $childText})";
            session.run(createParentChildQuery, Map.of("parentText", parentText, "childId", childId, "childText", childText));
        }

        // Step 4: Retrieval Testing
        ParentChildRetriever retriever = new ParentChildRetriever(embeddingModel, embeddingStore, graph, 10, 0.0);
        Query query = new Query("What is classical computing?");
        List<Content> results = retriever.retrieve(query);

        // Verify that no parent documents are retrieved
        assertTrue(results.isEmpty(), "Expected no results but found some");
    }
}
