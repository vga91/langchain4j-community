package dev.langchain4j.community.data.document.transformer.graph;

import dev.langchain4j.community.chain.RetrievalQAChain;
import dev.langchain4j.community.store.embedding.ParentChildEmbeddingStoreIngestor;
import dev.langchain4j.community.store.embedding.neo4j.HypotheticalQuestionGraphIngestor;
import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStoreIngestor;
import dev.langchain4j.community.store.embedding.neo4j.ParentChildGraphIngestor;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.document.splitter.DocumentByWordSplitter;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Value;
import org.testcontainers.containers.Neo4jContainer;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;

public class Neo4jEmbeddingStoreIngestorExample {

    public static class RagTools {

        private final Neo4jEmbeddingStoreIngestor ingestor;
        private final RetrievalQAChain qaChain;

        public RagTools(Neo4jEmbeddingStoreIngestor ingestor, RetrievalQAChain qaChain) {
            this.ingestor = ingestor;
            this.qaChain = qaChain;
        }

        @Tool("Ingest from document")
        public String ingest(String text) {
            // TODO --> 
            Document document = FileSystemDocumentLoader.loadDocument(toPath(text));
            
            System.out.println("text1 = " + text);
            System.out.println("text2 = " + document.text());
            ingestor.ingest(document);
            return "Document ingested";
        }

//        @Tool("Return the answer for the question in a single sentence : ")
        @Tool("Answer the question based only on the context provided from the ingested documents.")
//        @Tool("Answer the question based only on the context provided.: ")
        public String ask(String question) {
            System.out.println("question = " + question);
            return qaChain.execute(Query.from(question));
            
            // ((EmbeddingStoreContentRetriever) ((DefaultQueryRouter) ((DefaultRetrievalAugmentor) qaChain.retrievalAugmentor).queryRouter).contentRetrievers.iterator().next()).embeddingStore.search(EmbeddingSearchRequest.builder().queryEmbedding(Embedding.from(List.of())).build()).matches()
            
        }
    } 
    
    private static Path toPath(String fileName) {
        try {
            return Paths.get(Neo4jEmbeddingStoreIngestorExample.class.getClassLoader().getResource(fileName).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    interface Assistant {

        String chat(String userMessage);
    }

//    public interface MyAssistant {
//
////        @Tool
//        String ingest(String text);
//
////        @Tool
//        String ask(String question);
//    }

    // Tool class wrapping Neo4jEmbeddingStoreIngestor
//    public static class Neo4jIngestionTool {
//
//        private final Neo4jEmbeddingStoreIngestor ingestor;
//
//        public Neo4jIngestionTool(Neo4jEmbeddingStoreIngestor ingestor) {
//            this.ingestor = ingestor;
//        }
//
//        @Tool
//        public String ingest(String text) {
//            System.out.println("text = " + text);
//            Document doc = Document.from(text);
//            ingestor.ingest(doc);
//            return "Document ingested successfully.";
//        }
//    }

//    protected static Document getDocumentAI() {
//        return Document.from(
//                """
//                        Artificial Intelligence (AI) is a field of computer science. It focuses on creating intelligent agents capable of performing tasks that require human intelligence.
//
//                        Machine Learning (ML) is a subset of AI. It uses data to learn patterns and make predictions. Deep Learning is a specialized form of ML based on neural networks.
//                        """);
//    }

    protected static final String CUSTOM_RETRIEVAL =
            """
            MATCH (node)<-[:REFERS_TO]-(parent)
            WITH parent, collect(node.text) AS chunks, max(score) AS score
            RETURN parent.text + reduce(r = "", c in chunks | r + "\\n\\n" + c) AS text,
                   score,
                   properties(parent) AS metadata
            ORDER BY score DESC
            LIMIT $maxResults""";

    protected static final String CUSTOM_CREATION_QUERY =
            """
                UNWIND $rows AS row
                MATCH (p:MainDoc {parentId: $parentId})
                CREATE (p)-[:REFERS_TO]->(u:%1$s {%2$s: row.%2$s})
                SET u += row.%3$s
                WITH row, u
                CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
                RETURN count(*)""";
    

    public static void main(String[] args) {
        try (Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5.26").withAdminPassword("pass1234")) {
            neo4j.start();
            // Setup OpenAI chat model
            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .baseUrl(System.getenv("OPENAI_BASE_URL"))
                    .apiKey(System.getenv("OPENAI_API_KEY"))
                    .modelName(GPT_4_O_MINI)
                    .timeout(Duration.ofSeconds(60))
                    .build();
            final AllMiniLmL6V2QuantizedEmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
            

//            // Setup Neo4j embedding store connection
//            Neo4jEmbeddingStore embeddingStore = Neo4jEmbeddingStore.builder()
//                    .withBasicAuth(neo4j.getBoltUrl(), "neo4j", "pass1234")
//                    .dimension(384)
//                    //.embeddingModel(OpenAiChatModel.asEmbeddingModel(chatModel))
//                    .build();

            // MainDoc splitter splits on paragraphs (double newlines)
            final String expectedQuery = "\\n\\n";
            int maxSegmentSize = 250;
            DocumentSplitter parentSplitter = new DocumentByRegexSplitter(expectedQuery, expectedQuery, maxSegmentSize, 0);

            // Child splitter splits on periods (sentences)
            final String expectedQueryChild = "\\. ";
            DocumentSplitter childSplitter =
                    new DocumentByRegexSplitter(expectedQueryChild, expectedQuery, maxSegmentSize, 0);
            
//            DocumentSplitter splitter = new DocumentByWordSplitter(60, 0);
//                    new DocumentByRegexSplitter("\n\n", "\n\n", 150, 0); 
//                    new DocumentByRegexSplitter("\n\n", "\n\n", 100, 0);
            
            // Create ingestor
            
            
            
            final Driver driver = GraphDatabase.driver(neo4j.getBoltUrl(), AuthTokens.basic("neo4j", "pass1234"));

            final Neo4jEmbeddingStore neo4jEmbeddingStore = Neo4jEmbeddingStore.builder()
                    .driver(driver)
                    .retrievalQuery(CUSTOM_RETRIEVAL)
                    .entityCreationQuery(CUSTOM_CREATION_QUERY)
                    .label("Chunk")
                    .indexName("chunk_embedding_index")
                    .dimension(384)
                    .build();
            
            Neo4jEmbeddingStoreIngestor ingestor = ParentChildGraphIngestor.builder()
//                    .embeddingStore(embeddingStore)
                    .driver(driver)
//                    .query("CREATE (:MainDoc $metadata)")
                    .documentSplitter(parentSplitter)
                    .documentSplitter(childSplitter)
                    .embeddingModel(embeddingModel)
//                    .embeddingStore(neo4jEmbeddingStore)
//                    .query("CREATE (:MainDoc $metadata)")
                    .build();
//
//            // Wrap ingestor as tool
////            Neo4jIngestionTool ingestionTool = new Neo4jIngestionTool(ingestor);
//
//            // Retriever from Neo4j embeddings
            ContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(ingestor.getEmbeddingStore())
                    .embeddingModel(embeddingModel)
                    
                    .maxResults(5)
                    .minScore(0.4)
                    .build();
//
//            // Retrieval QA chain using retriever and LLM
            RetrievalQAChain retrievalQAChain = RetrievalQAChain.builder()
                    .contentRetriever(retriever)
                    .chatModel(chatModel)
                    .build();
//
//
//
//
//            final Neo4jEmbeddingStoreIngestor ingestor2 = HypotheticalQuestionGraphIngestor.builder()
//                    .driver(driver)
//                    .embeddingModel(new AllMiniLmL6V2QuantizedEmbeddingModel())
//                    .questionModel(chatModel)
//                    //.documentSplitter(parentSplitter)
//                    .build();
//
//
//            ingestor2.ingest(FileSystemDocumentLoader.loadDocument(toPath("myname.txt")));
//
//            driver.session().run("MATCH (n) RETURN n").stream().map(i -> {
//                final Value n1 = i.get("n");
//                final HashMap map = new HashMap<>();
//                map.put("label", n1.asNode().labels());
//                map.putAll(n1.asMap());
//                return map;
//            }).toList();
//
//            final EmbeddingStoreContentRetriever retrieverDio = EmbeddingStoreContentRetriever.builder()
//                    .embeddingModel(new AllMiniLmL6V2QuantizedEmbeddingModel())
//                    .maxResults(5)
//                    .minScore(0.6)
//                    .embeddingStore(ingestor2.getEmbeddingStore())
//                    .build();
//
//
//            RetrievalQAChain retrievalQAChain2 = RetrievalQAChain.builder()
//                    .contentRetriever(retrieverDio)
//                    .chatModel(chatModel)
//                    .build();
//
//            String execute = retrievalQAChain2.execute(Query.from("what is my name?"));
//
//            System.out.println("execute = " + execute);
            
            
            RagTools tools = new RagTools(ingestor, retrievalQAChain);

            // Build assistant with ingestion tool and retrieval QA tool
            Assistant assistant = AiServices.builder(Assistant.class)
                    .tools(tools)
//                            new Object() {
//                                @Tool
//                                public String ingest(String text) {
//                                    ingestor.ingest(Document.from(text));
//                                    return "Document ingested!";
//                                }
//
//                                @Tool
//                                public String ask(String question) {
//                                    return retrievalQAChain.execute(Query.from(question));
//                                }
//                            }
//                    )
//                    .tools(ingestionTool)
                    //.tools(ingestionTool, retrievalQAChain.asTool())
                    .chatModel(chatModel)
                    .build();

            // Ingest some documents
//        System.out.println(assistant.invokeTool("ingest", "Barack Obama was born in Hawaii."));
//        System.out.println(assistant.invokeTool("ingest", "The Eiffel Tower is located in Paris."));


//            System.out.println("assistant.ask1: " + assistant.ask("What is my name?"));
            
            // Ask a question answered by retrieval QA chain
            // Test it
//            System.out.println("assistant.ingest1: " + assistant.ingest("My name is Osvaldo"));
//            System.out.println("assistant.ingest2: " + assistant.ingest("The capital of France is Paris."));
            final List n = driver.session().run("MATCH (n) RETURN n").stream().map(i -> {
                final Value n1 = i.get("n");
                final HashMap map = new HashMap<>();
                map.put("label", n1.asNode().labels());
                map.putAll(n1.asMap());
                return map;
            }).toList();
//            System.out.println("n = " + n);
            // todo - NON FUNZIONA
//            System.out.println("assistant.ask2: " + assistant.chat("""
//        Ingest from document 'myname.txt', and then return the answer for the question 'Tell me about machine learning'"""));


            final String chat = assistant.chat("""
                    Ingest from document 'myname.txt'.
                    
                    Answer the question based only on the context provided from the ingested documents.
                    Question: 'Who is John Doe?'.
                    Answer:
                    """);
            System.out.println("ANSWER: " + chat);
            // output: `ANSWER: John Doe is a Super Saiyan...`
            
            // (Optional) Close resources here if needed
        }
    }
//    interface Assistant {
//
//        String chat(String userMessage);
//    }
}
