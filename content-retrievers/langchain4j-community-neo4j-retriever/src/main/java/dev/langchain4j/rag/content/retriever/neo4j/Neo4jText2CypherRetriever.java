package dev.langchain4j.rag.content.retriever.neo4j;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.rag.transformer.Neo4jUtils.getBacktickText;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.Type;
import org.neo4j.driver.types.TypeSystem;

public class Neo4jText2CypherRetriever implements ContentRetriever {

    public static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = PromptTemplate.from(
            """
                    Based on the Neo4j graph schema below, write a Cypher query that would answer the user's question:
                    {{schema}}

                    Question: {{question}}
                    Cypher query:
                    """);

    public static final PromptTemplate FROM_LLM_PROMPT_TEMPLATE = PromptTemplate.from(
            """
                    Based on the following context and the generated Cypher,
                    write an answer in natural language to the provided user's question:
                    Context: {{context}}
                    
                    Generated Cypher: {{cypher}}

                    Question: {{question}}
                    Cypher query:
                    """);

    private static final Pattern BACKTICKS_PATTERN = Pattern.compile("```(.*?)```", Pattern.MULTILINE | Pattern.DOTALL);
    private static final Type NODE = TypeSystem.getDefault().NODE();
    private static final Type RELATIONSHIP = TypeSystem.getDefault().RELATIONSHIP();
    private static final Type PATH = TypeSystem.getDefault().PATH();

    private final Neo4jGraph graph;

    private final ChatLanguageModel chatLanguageModel;

    private final PromptTemplate promptTemplate;

    public Neo4jText2CypherRetriever(
            Neo4jGraph graph, ChatLanguageModel chatLanguageModel, PromptTemplate promptTemplate) {

        this.graph = ensureNotNull(graph, "graph");
        this.chatLanguageModel = ensureNotNull(chatLanguageModel, "chatLanguageModel");
        this.promptTemplate = getOrDefault(promptTemplate, DEFAULT_PROMPT_TEMPLATE);
    }

    public static Builder builder() {
        return new Builder();
    }

    /*
    Getter methods
    */
    public Neo4jGraph getGraph() {
        return graph;
    }

    public ChatLanguageModel getChatLanguageModel() {
        return chatLanguageModel;
    }

    public PromptTemplate getPromptTemplate() {
        return promptTemplate;
    }

    private record RetrieveResult(String cypherQuery, List<Content> contents) {}

    @Override
    public List<Content> retrieve(Query query) {

        RetrieveResult result = getRetrieveResult(query);
        return result.contents();
    }

    private RetrieveResult getRetrieveResult(Query query) {
        String question = query.text();
        String schema = graph.getSchema();
        String cypherQuery = generateCypherQuery(schema, question);
        List<String> response = executeQuery(cypherQuery);
        final List<Content> list = response.stream().map(Content::from).toList();
        return new RetrieveResult(cypherQuery, list);
    }

    public String fromLLM(Query query) {
        RetrieveResult result = getRetrieveResult(query);
        
        final Prompt apply = FROM_LLM_PROMPT_TEMPLATE.apply(
                Map.of("context", result.contents(), "cypher", result.cypherQuery(), "question", query.text()));
        String cypherQuery = chatLanguageModel.chat(apply.text());
        return getBacktickText(cypherQuery);
    }

    private String generateCypherQuery(String schema, String question) {

        Prompt cypherPrompt = promptTemplate.apply(Map.of("schema", schema, "question", question));
        String cypherQuery = chatLanguageModel.chat(cypherPrompt.text());
        return getBacktickText(cypherQuery);
    }

    private List<String> executeQuery(String cypherQuery) {

        List<Record> records = graph.executeRead(cypherQuery);
        return records.stream()
                .flatMap(r -> r.values().stream())
                .map(value -> {
                    final boolean isEntity =
                            NODE.isTypeOf(value) || RELATIONSHIP.isTypeOf(value) || PATH.isTypeOf(value);
                    if (isEntity) {
                        return value.asMap().toString();
                    }
                    return value.toString();
                })
                .toList();
    }

    public static class Builder<T extends Builder<T>> {

        protected Neo4jGraph graph;
        protected ChatLanguageModel chatLanguageModel;
        protected PromptTemplate promptTemplate;

        /**
         * @param graph the {@link Neo4jGraph} (required)
         */
        public T graph(Neo4jGraph graph) {
            this.graph = graph;
            return self();
        }

        /**
         * @param chatLanguageModel the {@link ChatLanguageModel} (required)
         */
        public T chatLanguageModel(ChatLanguageModel chatLanguageModel) {
            this.chatLanguageModel = chatLanguageModel;
            return self();
        }

        /**
         * @param promptTemplate the {@link PromptTemplate} (optional, default is {@link Neo4jText2CypherRetriever#DEFAULT_PROMPT_TEMPLATE})
         */
        public T promptTemplate(PromptTemplate promptTemplate) {
            this.promptTemplate = promptTemplate;
            return self();
        }

        protected T self() {
            return (T) this;
        }

        Neo4jText2CypherRetriever build() {
            return new Neo4jText2CypherRetriever(graph, chatLanguageModel, promptTemplate);
        }
    }
}
