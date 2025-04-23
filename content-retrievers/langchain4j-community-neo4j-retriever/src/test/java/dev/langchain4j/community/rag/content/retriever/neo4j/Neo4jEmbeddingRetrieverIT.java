package dev.langchain4j.community.rag.content.retriever.neo4j;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;

import java.util.Map;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;

// OPENAI_API_KEY=demo;OPENAI_BASE_URL=http://langchain4j.dev/demo/openai/v1
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class Neo4jEmbeddingRetrieverIT extends Neo4jEmbeddingRetrieverBaseTest {

    ChatLanguageModel chatModel = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .logRequests(true)
            .logResponses(true)
            .build();
    
    
    
}
