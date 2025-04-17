package dev.langchain4j.rag.content.retriever.neo4j;

//import dev.langchain4j.community.model.xinference.XinferenceChatModel;
import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
//import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
//import dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingStore;
//import dev.langchain4j.graph.Neo4jGraph;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.rag.content.retriever.neo4j.Neo4jEmbeddingRetriever.DEFAULT_PROMPT_ANSWER;
import static dev.langchain4j.rag.content.retriever.neo4j.ParentChildGraphRetriever2.DEFAULT_RETRIEVAL;
import static dev.langchain4j.rag.content.retriever.neo4j.ParentChildGraphRetriever2.PARENT_QUERY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

// OPENAI_API_KEY=demo;OPENAI_BASE_URL=http://langchain4j.dev/demo/openai/v1
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class ParentChildRetrieverTest2 extends Neo4jRetrieverBaseTest {

    ChatLanguageModel chatModel = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .logRequests(true)
            .logResponses(true)
            .build();
    
//    private static Neo4jContainer<?> neo4jContainer;
    private static Neo4jEmbeddingStore embeddingStore;
//    private static Neo4jGraph graph;
    private static EmbeddingModel embeddingModel;
//    private static Driver driver;
    private static ParentChildRetriever retriever;
    
    
    // TODO - test with Neo4jEmbeddingRetriever
    // TODO - builder
    

//    private static EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    public static void setUp() {
        // todo - NEEDED?
        Neo4jRetrieverBaseTest.beforeAll();
        
        
        // Start Neo4j Testcontainer
//        neo4jContainer = new Neo4jContainer<>("neo4j:" + NEO4J_VERSION)
//                .withPlugins("apoc")
//                .withoutAuthentication(); // Disable authentication for testing
//        //.withAdminPassword("test1234"); /
//        neo4jContainer.start();

//        driver = GraphDatabase.driver(neo4jContainer.getBoltUrl(), AuthTokens.none());

        // Initialize Neo4jEmbeddingStore
        embeddingStore = Neo4jEmbeddingStore.builder()
                .driver(driver)
                .databaseName("neo4j")
                .dimension(384)
                .build();

//        // Initialize Neo4jGraph
//        graph = Neo4jGraph.builder()
//                .driver(driver)
//                .build();

        // Initialize Embedding Model
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

//        retriever = new ParentChildRetriever(
//                embeddingModel,
//                embeddingStore,
//                graph,
//                5,
//                0.6
//        );
        
        // seedTestData();
    }
    
    // TODO --> https://medium.com/data-science/langchains-parent-document-retriever-revisited-1fca8791f5a0
    



//    @AfterEach
//    void afterEach() {
//        try (Session session = driver.session()) {
//            session.run("MATCH (n) DETACH DELETE n");
//        }
////        graph.close();
////        driver.close();
//    }


    @Test
    public void testSummaryRetriever_withDocumentByRegexSplitter() {
        final SummaryRetriever retriever = new SummaryRetriever(embeddingModel, driver, 3, 0.5, null, chatModel);

        // Parent splitter splits on paragraphs (double newlines)
        final String expectedQuery = "\\n\\n";
        int maxSegmentSize = 250;
        DocumentSplitter parentSplitter = new DocumentByRegexSplitter(expectedQuery, expectedQuery, maxSegmentSize, 0);
        
        Document doc = Document.from(
                """
                Artificial Intelligence (AI) is a field of computer science. It focuses on creating intelligent agents capable of performing tasks that require human intelligence.
        
                Machine Learning (ML) is a subset of AI. It uses data to learn patterns and make predictions. Deep Learning is a specialized form of ML based on neural networks.
                """,
                Metadata.from(Map.of("title", "AI Overview", "url", "https://example.com/ai", "id", "doc-ai"))
        );

        // Index the document into Neo4j as parent-child nodes
        retriever.index(doc, parentSplitter);

        // Query and validate results
        List<Content> results = retriever.retrieve(Query.from("What is Machine Learning?"));

        assertFalse(results.isEmpty(), "Should retrieve at least one parent document");

        Content result = results.get(0);
        System.out.println("Retrieved Text:\n" + result.textSegment().text());
        System.out.println("Metadata: " + result.textSegment().metadata());

        assertTrue(result.textSegment().text().toLowerCase().contains("machine learning"));
        assertThat(result.textSegment().metadata().getString("url")).isEqualTo("https://example.com/ai");
    }


    @Test
    public void testParentChildRetriever_withDocumentByRegexSplitter1() {
        EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        String vectorIndex = "child_embedding_index";
        int embeddingDimensions = 384;


        final Neo4jEmbeddingStore build = Neo4jEmbeddingStore.builder()
                .driver(driver)
                .retrievalQuery(DEFAULT_RETRIEVAL)
                .label("Child")
                .indexName("child_embedding_index")
                .dimension(384)
                .build();

        ParentChildGraphRetriever2 retriever = new ParentChildGraphRetriever2(
                embeddingModel, driver/*, vectorIndex, embeddingDimensions*/,3, 0.5, null
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
                Metadata.from(Map.of("title", "AI Overview", "url", "https://example.com/ai", "id", "doc-ai", "source", "Wikipedia link"))
        );

        // Index the document into Neo4j as parent-child nodes
        retriever.index(doc, parentSplitter, childSplitter);

        // Query and validate results
        List<Content> results = retriever.retrieve(Query.from("What is Machine Learning?"));

        assertFalse(results.isEmpty(), "Should retrieve at least one parent document");

        Content result = results.get(0);
        System.out.println("Retrieved Text:\n" + result.textSegment().text());
        System.out.println("Metadata: " + result.textSegment().metadata());

        assertTrue(result.textSegment().text().toLowerCase().contains("machine learning"));
        assertEquals("Wikipedia link", result.textSegment().metadata().getString("url"));
    }

    @Test
    public void testBasicRetriever() {
        BasicRetriever retriever = new BasicRetriever(embeddingModel, driver, 1, 0.5, null, Map.of(), null);

        Document parentDoc = Document.from(
                """
                Quantum mechanics studies how particles behave. It is a fundamental theory in physics.
                
                Gradient descent and backpropagation algorithms.
                
                Spaghetti carbonara and Italian dishes.
                """,
                Metadata.from(Map.of("title", "Quantum Mechanics", "source", "Wikipedia link"))
        );
        
        // Child splitter: splits into sentences using OpenNLP
        DocumentSplitter splitter = new DocumentBySentenceSplitter(250, 0);

        retriever.index(parentDoc, splitter, null);
        final List<Content> retrieve = retriever.retrieve(Query.from("fundamental theory"));

        // Query and validate results
        List<Content> results = retriever.retrieve(Query.from("What is Machine Learning?"));
        assertFalse(results.isEmpty(), "Should retrieve at least one parent document");

        Content result = results.get(0);

        assertTrue(result.textSegment().text().toLowerCase().contains("fundamental theory"));
        assertEquals("Wikipedia link", result.textSegment().metadata().getString("source"));
    }

    @Test
    public void testParentChildRetriever_withDocumentByRegexSplitter() {
        EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        String vectorIndex = "child_embedding_index";
        int embeddingDimensions = 384;

        ParentChildGraphRetriever2 retriever = new ParentChildGraphRetriever2(
                embeddingModel, driver/*, vectorIndex, embeddingDimensions*/,1, 0.5, null
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
        List<Content> results = retriever.retrieve(Query.from("What is Machine Learning?"));

        assertFalse(results.isEmpty(), "Should retrieve at least one parent document");

        Content result = results.get(0);
        System.out.println("Retrieved Text:\n" + result.textSegment().text());
        System.out.println("Metadata: " + result.textSegment().metadata());

        assertTrue(result.textSegment().text().toLowerCase().contains("machine learning"));
        assertEquals("https://example.com/ai", result.textSegment().metadata().getString("source"));
    }
    
    // TODO - creare esempio tipo così https://github.com/neo4j-examples/rag-demo/blob/main/rag_demo/vector_chain.py


    // TODO
    // TODO
    // TODO
    // TODO -- custom retriever
    // TODO -- base retriever
    // TODO -- the index method should be inside the constructor
    // TODO
    // TODO
//    @Test
//    void testIndexBaseRetriever() {
//        // Step 1: Document with metadata
//        Document parentDoc = Document.from(
//                """
//                Quantum mechanics studies how particles behave. It is a fundamental theory in physics.
//                
//                Gradient descent and backpropagation algorithms.
//                
//                Spaghetti carbonara and Italian dishes.
//                """,
//                Metadata.from(Map.of("title", "Quantum Mechanics", "source", "Wikipedia link"))
//        );
//
//        // Step 2: Splitter & embedder
//        int maxSegmentSize = 250;
//
//        // Parent splitter: splits on paragraphs (double newlines)
////        final String expectedQuery = "\\n\\n";
////        DocumentSplitter parentSplitter = new DocumentByRegexSplitter(expectedQuery, expectedQuery, maxSegmentSize, 0);
//
//
//        final Neo4jEmbeddingStore neo4jEmbeddingStore = Neo4jEmbeddingStore.builder()
//                .driver(driver)
//                .retrievalQuery(DEFAULT_RETRIEVAL)
//                .entityCreationQuery(PARENT_QUERY)
//                .label("Child")
//                .indexName("child_embedding_index")
//                .dimension(384)
//                .build();
//
//        // Child splitter: splits into sentences using OpenNLP
//        DocumentSplitter splitter = new DocumentBySentenceSplitter(maxSegmentSize, 0);
//
////        // Index the document into Neo4j as parent-child nodes
////        EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
////                .embeddingModel(embeddingModel)
////                .embeddingStore(neo4jEmbeddingStore)
////                .maxResults(2)
////                .minScore(0.5)
////                .build();
////               // embeddingModel, driver/*, vectorIndex, embeddingDimensions*/,2, 0.5, null
////        //);
//
//        retriever.index(parentDoc, splitter, null);
//
//        // Query and validate results
//        List<Content> results = retriever.retrieve(Query.from("Tell me about quantum mechanics and cooking\""));
//
//        assertFalse(results.isEmpty(), "Should retrieve at least one parent document");
//
//        Content result = results.get(0);
//        System.out.println("Retrieved Text:\n" + result.textSegment().text());
//        System.out.println("Metadata: " + result.textSegment().metadata());
//
//        assertTrue(result.textSegment().text().toLowerCase().contains("quantum mechanics"));
//        assertEquals("Wikipedia link", result.textSegment().metadata().getString("source"));
////        }
//    }



    @Test
    void testIndexHypotheticalQuestionsWithPromptAnswer() {
        // Step 1: Document with metadata
        Document parentDoc = Document.from(
                """
                Quantum mechanics studies how particles behave. It is a fundamental theory in physics.
                
                Gradient descent and backpropagation algorithms.
                
                Spaghetti carbonara and Italian dishes.
                """,
                Metadata.from(Map.of("title", "Quantum Mechanics", "source", "Wikipedia link"))
        );
        String parentId = UUID.randomUUID().toString();

        // Step 2: Splitter & embedder
        int maxSegmentSize = 250;

        // Parent splitter: splits on paragraphs (double newlines)
//        final String expectedQuery = "\\n\\n";
//        DocumentSplitter parentSplitter = new DocumentByRegexSplitter(expectedQuery, expectedQuery, maxSegmentSize, 0);

        // Child splitter: splits into sentences using OpenNLP
        DocumentSplitter splitter = new DocumentBySentenceSplitter(maxSegmentSize, 0);

        String promptAnswer = """
            You are an assistant that helps to form nice and human
            understandable answers based on the provided information from tools.
            Do not add any other information that wasn't present in the tools, and use
            very concise style in interpreting results!
            
            Answer like Naruto, saying his typical expression `dattebayo`.
                """;
        // Index the document into Neo4j as parent-child nodes
        HypotheticalQuestionRetriever retriever = new HypotheticalQuestionRetriever(
                embeddingModel, driver/*, vectorIndex, embeddingDimensions*/,2, 0.5, null, chatModel, chatModel, DEFAULT_PROMPT_ANSWER 
        );

        retriever.index(parentDoc, splitter, null);

        // Query and validate results
        List<Content> results = retriever.retrieve(Query.from("Tell me about quantum mechanics and cooking\""));

        assertFalse(results.isEmpty(), "Should retrieve at least one parent document");

        Content result = results.get(0);
        System.out.println("Retrieved Text:\n" + result.textSegment().text());
        System.out.println("Metadata: " + result.textSegment().metadata());
 
        assertTrue(result.textSegment().text().toLowerCase().contains("quantum mechanics"));
        assertThat(result.textSegment().text().toLowerCase()).doesNotContainIgnoringCase("dattebayo");


        HypotheticalQuestionRetriever retriever2 = new HypotheticalQuestionRetriever(
                embeddingModel, driver/*, vectorIndex, embeddingDimensions*/,2, 0.5, null, chatModel, chatModel, promptAnswer
                );

        // retriever.index(parentDoc, splitter, null);

        // Query and validate results
        List<Content> results2 = retriever2.retrieve(Query.from("Tell me about quantum mechanics and cooking\""));

        assertFalse(results2.isEmpty(), "Should retrieve at least one parent document");

        Content result2 = results2.get(0);
        System.out.println("Retrieved Text:\n" + result2.textSegment().text());
        System.out.println("Metadata: " + result2.textSegment().metadata());

        assertTrue(result2.textSegment().text().toLowerCase().contains("quantum mechanics"));
        assertThat(result2.textSegment().text()).containsIgnoringCase("dattebayo");
        
        
        // assertEquals("Wikipedia link", result.textSegment().metadata().getString("source"));
    }
    
    

    @Test
    void testIndexHypotheticalQuestions() {
        // Step 1: Document with metadata
        Document parentDoc = Document.from(
                """
                Quantum mechanics studies how particles behave. It is a fundamental theory in physics.
                
                Gradient descent and backpropagation algorithms.
                
                Spaghetti carbonara and Italian dishes.
                """,
                Metadata.from(Map.of("title", "Quantum Mechanics", "source", "Wikipedia link"))
        );
        String parentId = UUID.randomUUID().toString();

        // Step 2: Splitter & embedder
        int maxSegmentSize = 250;

        // Parent splitter: splits on paragraphs (double newlines)
//        final String expectedQuery = "\\n\\n";
//        DocumentSplitter parentSplitter = new DocumentByRegexSplitter(expectedQuery, expectedQuery, maxSegmentSize, 0);

        // Child splitter: splits into sentences using OpenNLP
        DocumentSplitter splitter = new DocumentBySentenceSplitter(maxSegmentSize, 0);

        // Index the document into Neo4j as parent-child nodes
        HypotheticalQuestionRetriever retriever = new HypotheticalQuestionRetriever(
                embeddingModel, driver/*, vectorIndex, embeddingDimensions*/,2, 0.5, null, chatModel, chatModel, DEFAULT_PROMPT_ANSWER
        );

        retriever.index(parentDoc, splitter, null);

        // Query and validate results
        List<Content> results = retriever.retrieve(Query.from("Tell me about quantum mechanics and cooking\""));

        assertFalse(results.isEmpty(), "Should retrieve at least one parent document");

        Content result = results.get(0);
        System.out.println("Retrieved Text:\n" + result.textSegment().text());
        System.out.println("Metadata: " + result.textSegment().metadata());

        assertTrue(result.textSegment().text().toLowerCase().contains("quantum mechanics"));
        assertEquals("Wikipedia link", result.textSegment().metadata().getString("source"));
    }

//    @Test
//    public void testHypoteticalRetriever_withDocumentByRegexSplitter() {
//        EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
//        String vectorIndex = "child_embedding_index";
//        int embeddingDimensions = 384;
//
//        HypotheticalQuestionRetriever retriever = new HypotheticalQuestionRetriever(
//                embeddingModel, driver/*, vectorIndex, embeddingDimensions*/,1, 0.5, null
//        );
//
//        // Parent splitter splits on paragraphs (double newlines)
//        final String expectedQuery = "\\n\\n";
//        int maxSegmentSize = 250;
//        DocumentSplitter parentSplitter = new DocumentByRegexSplitter(expectedQuery, expectedQuery, maxSegmentSize, 0);
//
//        // Child splitter splits on periods (sentences)
//        final String expectedQuery1 = "\\. ";
//        DocumentSplitter childSplitter = new DocumentByRegexSplitter(expectedQuery1, expectedQuery, maxSegmentSize, 0);
//
//        Document doc = Document.from(
//                """
//                Artificial Intelligence (AI) is a field of computer science. It focuses on creating intelligent agents capable of performing tasks that require human intelligence.
//        
//                Machine Learning (ML) is a subset of AI. It uses data to learn patterns and make predictions. Deep Learning is a specialized form of ML based on neural networks.
//                """,
//                Metadata.from(Map.of("title", "AI Overview", "url", "https://example.com/ai", "id", "doc-ai"))
//        );
//
//        // Index the document into Neo4j as parent-child nodes
//        retriever.index(doc, parentSplitter, childSplitter);
//
//        // Query and validate results
//        List<Content> results = retriever.retrieve(Query.from("What is Machine Learning?"));
//
//        assertFalse(results.isEmpty(), "Should retrieve at least one parent document");
//
//        Content result = results.get(0);
//        System.out.println("Retrieved Text:\n" + result.textSegment().text());
//        System.out.println("Metadata: " + result.textSegment().metadata());
//
//        assertTrue(result.textSegment().text().toLowerCase().contains("machine learning"));
//        assertEquals("https://example.com/ai", result.textSegment().metadata().getString("source"));
//    }
//    
    
//import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
//import dev.langchain4j.data.document.splitter.DocumentSplitter;

    @Test
    public void testParentChildRetriever_withDocumentBySentenceSplitter() {
        EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        String vectorIndex = "child_embedding_index_sentence";
        int embeddingDimensions = 384;

        ParentChildGraphRetriever2 retriever = new ParentChildGraphRetriever2(
                embeddingModel, driver/*, vectorIndex, embeddingDimensions*/, 3, 0.5, null
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

        List<Content> results = retriever.retrieve(Query.from("What is Machine Learning?"));

        assertFalse(results.isEmpty(), "Should retrieve at least one parent document");
        assertTrue(results.get(0).textSegment().text().toLowerCase().contains("machine learning"));
        assertEquals("https://example.com/ai-ml", results.get(0).textSegment().metadata().getString("url"));
    }
    


    // TODO - esempio senza metodo index, partendo direttamente da un dataset e facendo il retrieve
    static void seedTestParentChildData() {
        try (var session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n");

            final String text1 = "Gradient descent and backpropagation algorithms";
            final Value embedding1 = Values.value(embeddingModel.embed(text1).content().vector());
            final String text2 = "Spaghetti carbonara and Italian dishes";
            final Value embedding2 = Values.value(embeddingModel.embed(text2).content().vector());
            final String text3 = "Quantum entanglement and uncertainty principles";
            final Value embedding3 = Values.value(embeddingModel.embed(text3).content().vector());

            session.run("""
                CREATE (p1:Document {id: 'p1', text: 'Parent about machine learning', source: 'ml'})
                CREATE (p2:Document {id: 'p2', text: 'Parent about cooking', source: 'food'})
                CREATE (p3:Document {id: 'p3', text: 'Parent about quantum physics', source: 'science'})

                CREATE (c1:Chunk {id: 'c1', text: $text1, embedding: $embedding1})
                CREATE (c2:Chunk {id: 'c2', text: $text2, embedding: $embedding2})
                CREATE (c3:Chunk {id: 'c3', text: $text3, embedding: $embedding3})

                CREATE (p1)-[:HAS_CHILD]->(c1)
                CREATE (p2)-[:HAS_CHILD]->(c2)
                CREATE (p3)-[:HAS_CHILD]->(c3)
            """, 
                    Map.of("text1", text1, "embedding1", embedding1,
                            "text2", text2, "embedding2", embedding2,
                            "text3", text3, "embedding3", embedding3));
        }
    }



    static void addChunkEmbedding(String text, String id) {
        Embedding embedding = embeddingModel.embed(text).content();
        TextSegment segment = new TextSegment(text, Metadata.from(Map.of("id", id)));
        embeddingStore.add(embedding, segment);
    }

    //  TODO - this one without index(..) method
    @Test
    void testRetrieverReturnsOnlyRelevantParent() {
        
        final Neo4jEmbeddingStore neo4jEmbeddingStore = Neo4jEmbeddingStore.builder()
                .driver(driver)
                .retrievalQuery(DEFAULT_RETRIEVAL)
                .entityCreationQuery(PARENT_QUERY)
                .label("Chunk")
                .indexName("chunk_embedding_index")
                .dimension(384)
                .build();

        seedTestParentChildData();
        
        final ParentChildGraphRetriever2 parentChildGraphRetriever2 = new ParentChildGraphRetriever2(
                embeddingModel,
                driver,
                5,
                0.6,
                neo4jEmbeddingStore
        );

        // Act
        List<Content> results = parentChildGraphRetriever2.retrieve(new Query("quantum physics"));
//        List<Content> results = parentChildGraphRetriever2.retrieve(new Query("Tell me about training neural networks"));

        // Assert
        assertEquals(1, results.size());
        Content parent = results.get(0);

        assertTrue(parent.textSegment().text().contains("quantum physics"));
        assertEquals("science", parent.textSegment().metadata().getString("source"));
    }

//    @Test
//    void testRetrieverReturnsNothingForUnrelatedQuery() {
//        seedTestParentChildData();
//        List<Content> results = retriever.retrieve(new Query("Soccer match results and player stats"));
//
//        assertTrue(results.isEmpty(), "No relevant parents should match soccer topics");
//    }
//
//    @Test
//    void testRetrieverReturnsMultipleParents() {
//        seedTestParentChildData();
//        List<Content> results = retriever.retrieve(new Query("Tell me about quantum mechanics and cooking"));
//
//        Set<String> sources = new HashSet<>();
//        for (Content c : results) {
//            sources.add((String) c.metadata().get("source"));
//        }
//
//        assertTrue(sources.contains("food"));
//        assertTrue(sources.contains("science"));
//    }
    
    

//    @Test
//    void retrievesParentGivenQueryAboutEmbeddings() {
////        final ParentChildRetriever retriever = new ParentChildRetriever(
////                embeddingModel,
////                embeddingStore,
////                graph,
////                5,
////                0.0
////        );
//
//        seedTestData();
//        List<Content> result = retriever.retrieve(new Query("Tell me about embeddings"));
//
//        assertFalse(result.isEmpty());
//        Content content = result.get(0);
//        assertTrue(content.textSegment().text().contains("parent doc"));
//        assertEquals("test", content.metadata().get("source"));
//    }

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

/*    @Test
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
    }*/
}
