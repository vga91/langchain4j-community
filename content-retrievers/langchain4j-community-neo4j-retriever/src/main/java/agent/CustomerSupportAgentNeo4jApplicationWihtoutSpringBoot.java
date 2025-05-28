package agent;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.community.store.memory.chat.neo4j.Neo4jChatMemoryStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
//import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.testcontainers.containers.Neo4jContainer;

import java.io.File;
import java.net.URI;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.stream.Collectors;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;

// TODO --> CustomerSupportAgentApplicationTest di azure-openai

/**
 * example prompt: `What is the cancellation policy?`
 */
public class CustomerSupportAgentNeo4jApplicationWihtoutSpringBoot {

    public static void main(String[] args) {
        try (Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5.26.6-enterprise").withPlugins("apoc").withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes").withAdminPassword("pass1234")) {
            neo4j.start();

            // Setup
            String openAiApiKey = System.getenv("OPENAI_API_KEY");
            String baseUrl = System.getenv("OPENAI_BASE_URL");

            ChatModel chatModel = OpenAiChatModel.builder()
                    .apiKey(openAiApiKey)
                    .baseUrl(baseUrl)
                    .modelName(GPT_4_O_MINI)
                    .build();

            AllMiniLmL6V2QuantizedEmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

            Neo4jChatMemoryStore chatMemoryStore = Neo4jChatMemoryStore.builder()
                    .withBasicAuth(neo4j.getBoltUrl(), "neo4j", neo4j.getAdminPassword())
                    .build();

            Neo4jEmbeddingStore embeddingStore = Neo4jEmbeddingStore.builder()
                    .withBasicAuth(neo4j.getBoltUrl(), "neo4j", neo4j.getAdminPassword())
                    .dimension(384)
                    .build();

            final URI uri = new File("content-retrievers/langchain4j-community-neo4j-retriever/src/main/java/resources/miles-of-smiles-terms-of-use.txt").toURI();
            Document document = loadDocument(Paths.get(uri), new TextDocumentParser());
            DocumentSplitter documentSplitter = DocumentSplitters.recursive(100, 0);
            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .documentSplitter(documentSplitter)
                    .embeddingModel(embeddingModel)
                    .embeddingStore(embeddingStore)
                    .build();
            ingestor.ingest(document);
            
            
            // Create assistant with memory support
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(chatModel)
                    .chatMemoryProvider(sessionId -> MessageWindowChatMemory.builder()
                            .id(sessionId)
                            .chatMemoryStore(chatMemoryStore)
                            .maxMessages(10)
                            .build())
                    .build();

            AssistantService service = new AssistantService(assistant, embeddingStore, embeddingModel);

            // Interactive loop
            try (Scanner scanner = new Scanner(System.in)) {
                String sessionId = UUID.randomUUID().toString();

                System.out.println("Welcome to the Customer Support Assistant!");
                System.out.println("Type 'exit' to quit.");

                while (true) {
                    System.out.print("\nYou: ");
                    String input = scanner.nextLine();

                    if ("exit".equalsIgnoreCase(input)) {
                        break;
                    }

                    String response = service.chat(sessionId, input);
                    System.out.println("Assistant: " + response);
                }
            }
        }
    }

    // AI service interface
    // @AiService
    public interface Assistant {
        String chat(String userMessage);
    }

    // Business logic
    public static class AssistantService {

        private final Assistant assistant;
        private final Neo4jEmbeddingStore embeddingStore;
        private final EmbeddingModel embeddingModel;

        public AssistantService(Assistant assistant,
                                Neo4jEmbeddingStore embeddingStore,
                                EmbeddingModel embeddingModel) {
            this.assistant = assistant;
            this.embeddingStore = embeddingStore;
            this.embeddingModel = embeddingModel;
        }

        public String chat(String sessionId, String userMessage) {
            Embedding queryEmbedding = embeddingModel.embed(userMessage).content();
            final EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(3)
                    .build();
            final List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
            String context = matches.stream().map(i -> i.embedded().text()).collect(Collectors.joining("\n---\n"));

            String prompt = """
                You are a helpful customer support agent.
                Use the following context to answer the user:
                %s
                User: %s
                """.formatted(context, userMessage);

            
            return assistant.chat(prompt);
        }
    }
}
