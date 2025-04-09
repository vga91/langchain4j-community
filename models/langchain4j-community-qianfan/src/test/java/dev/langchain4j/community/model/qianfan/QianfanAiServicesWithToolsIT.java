package dev.langchain4j.community.model.qianfan;

import static java.util.Collections.singletonList;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.common.AbstractAiServiceWithToolsIT;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * FIXME: there are some failed test.
 */
@EnabledIfEnvironmentVariable(named = "QIANFAN_API_KEY", matches = ".+")
class QianfanAiServicesWithToolsIT extends AbstractAiServiceWithToolsIT {

    @Override
    protected List<ChatLanguageModel> models() {
        return singletonList(QianfanChatModel.builder()
                .apiKey(System.getenv("QIANFAN_API_KEY"))
                .secretKey(System.getenv("QIANFAN_SECRET_KEY"))
                .modelName("ERNIE-Bot 4.0")
                .temperature(0.01)
                .logRequests(true)
                .logResponses(true)
                .build());
    }
}
