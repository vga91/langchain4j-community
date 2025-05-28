package agent;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.community.store.memory.chat.neo4j.Neo4jChatMemoryStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

@SpringBootApplication
public class CustomerSupportAgentNeo4jWithSpringBoot {

    private static final Logger log = LoggerFactory.getLogger(CustomerSupportAgentNeo4jWithSpringBoot.class);

    public static void main(String[] args) {
        SpringApplication.run(CustomerSupportAgentNeo4jWithSpringBoot.class, args);
    }

    @Bean
    public Neo4jEmbeddingStore embeddingStore() {
        // store something, e.g.:
        /*
                // 1. Create an in-memory embedding store
        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        // 2. Load an example document ("Miles of Smiles" terms of use)
        Resource resource = resourceLoader.getResource("classpath:miles-of-smiles-terms-of-use.txt");
        Document document = loadDocument(resource.getFile().toPath(), new TextDocumentParser());

        // 3. Split the document into segments 100 tokens each
        // 4. Convert segments into embeddings
        // 5. Store embeddings into embedding store
        // All this can be done manually, but we will use EmbeddingStoreIngestor to automate this:
        DocumentSplitter documentSplitter = DocumentSplitters.recursive(100, 0, new AzureOpenAiTokenCountEstimator("gpt-4o-mini"));
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
         */
        
        return Neo4jEmbeddingStore.builder()
                .withBasicAuth("bolt://localhost:7687", "neo4j", "password")
                .dimension(1536) // OpenAI embedding dimension
                .build();
    }

    @Bean
    public Neo4jChatMemoryStore chatMemoryStore() {
        return Neo4jChatMemoryStore.builder()
                .withBasicAuth("bolt://localhost:7687", "neo4j", "password")
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2QuantizedEmbeddingModel();
//        OpenAiEmbeddingModel.builder()
//                .apiKey(System.getenv("OPENAI_API_KEY"))
//                .build();
    }

    @Bean
    public ChatModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .build();
    }

    @Bean
    public Assistant assistant(ChatModel chatLanguageModel, Neo4jChatMemoryStore chatMemoryStore) {
        return AiServices.builder(Assistant.class)
                .chatModel(chatLanguageModel)
                .chatMemoryProvider(sessionId -> MessageWindowChatMemory.builder()
                        .id(sessionId)
                        .chatMemoryStore(chatMemoryStore)
                        .maxMessages(10)
                        .build())
                .build();
    }

    @Bean
    public AssistantService assistantService(Assistant assistant, Neo4jEmbeddingStore embeddingStore,
                                             EmbeddingModel embeddingModel) {
        return new AssistantService(assistant, embeddingStore, embeddingModel);
    }

    @Bean
    public ApplicationRunner runner(AssistantService assistantService) {
        return args -> {
            try (Scanner scanner = new Scanner(System.in)) {
                String sessionId = "user-123";
                while (true) {
                    log.info("==================================================");
                    log.info("User: ");
                    String userQuery = scanner.nextLine();
                    if ("exit".equalsIgnoreCase(userQuery)) break;
                    log.info("==================================================");
                    String response = assistantService.chat(sessionId, userQuery);
                    log.info("==================================================");
                    log.info("Assistant: " + response);
                }
            }
        };
    }

    @AiService
    public interface Assistant {
        String chat(String userMessage);
    }

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
            var queryEmbedding = embeddingModel.embed(userMessage).content();
            final EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(3)
                    .build();
            final List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();
            String context = matches.stream().map(i -> i.embedded().text()).collect(Collectors.joining("\n---\n"));
            String enrichedPrompt = """
                    You are a helpful customer support agent.
                    Use the following context to answer the user:
                    %s
                    User: %s
                    """.formatted(context, userMessage);
            return assistant.chat(enrichedPrompt);
        }
    }
}
