package dev.langchain4j.rag.content.retriever.neo4j;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/*
TODO: fare qualcosa di simile a questo... MAGARI SOLO UN TEST?
# flake8: noqa
from langchain_core.prompts.prompt import PromptTemplate

_template = """Given the following conversation and a follow up question, rephrase the follow up question to be a standalone question.

Chat History:
{chat_history}
Follow Up Input: {question}
Standalone question:"""
CONDENSE_QUESTION_PROMPT = PromptTemplate.from_template(_template)

prompt_template = """Use the following pieces of context to answer the question at the end. If you don't know the answer, just say that you don't know, don't try to make up an answer.

{context}

Question: {question}
Helpful Answer:"""
QA_PROMPT = PromptTemplate(
    template=prompt_template, input_variables=["context", "question"]
)

 */



    /*
    - Instead of indexing entire documents, data is divided into smaller chunks, referred to as Parent and Child documents.

    - Child documents are indexed for better representation of specific concepts, while parent documents are retrieved to ensure context retention.
     */


// TODO - customizzarlo simile a https://python.langchain.com/v0.1/docs/modules/data_connection/retrievers/custom_retriever/
    //   con (...DocumentSplitter parentSplitter)
public abstract class Neo4jEmbeddingRetriever implements ContentRetriever {
    public static final String DEFAULT_PROMPT_ANSWER = """
            You are an assistant that helps to form nice and human
            understandable answers based on the provided information from tools.
            Do not add any other information that wasn't present in the tools, and use
            very concise style in interpreting results!
            """;
    protected final EmbeddingModel embeddingModel;
    protected final ChatLanguageModel model;
    protected final ChatLanguageModel answerModel;
    protected final String promptSystem;
    protected final String promptUser;
    protected final String promptAnswer;
    protected final Driver driver;
    protected final Integer maxResults;
    protected final Double minScore;
    protected final Neo4jEmbeddingStore embeddingStore;
    protected final String query;
    protected final Map<String, Object> params;

    /**
     * // TODO --> EmbeddingSearchRequest javadoc
     * 
     * @param embeddingModel
     * @param driver
     * @param maxResults
     * @param minScore
     * @param embeddingStore
     */
    public Neo4jEmbeddingRetriever(EmbeddingModel embeddingModel,
                                   Driver driver,
                                   int maxResults,
                                   double minScore,
                                   String query,
                                   Map<String, Object> params,
                                   Neo4jEmbeddingStore embeddingStore,
                                   ChatLanguageModel model,
                                   String promptSystem,
                                   String promptUser,
                                   ChatLanguageModel answerModel,
                                   String promptAnswer) {
        this.embeddingModel = embeddingModel;
        this.driver = driver;
        this.maxResults = maxResults;
        this.minScore = minScore;
        this.query = query;
        this.params = params;
        this.model = model;
        this.answerModel = answerModel;//getOrDefault(answerModel, model);
        this.promptSystem = promptSystem;
        this.promptAnswer = getOrDefault(promptAnswer, DEFAULT_PROMPT_ANSWER);
        this.promptUser = promptUser;
        final Neo4jEmbeddingStore store = getOrDefault(embeddingStore, getDefaultEmbeddingStore(driver));

        this.embeddingStore = ensureNotNull(store, "embeddingStore");
    }
    
    public Neo4jEmbeddingStore getDefaultEmbeddingStore(Driver driver) {
        return null;
    }

    public void index(Document document,
                      DocumentSplitter parentSplitter) {
        index(document, parentSplitter, null);
    }
    
    public void index(Document document,
                      DocumentSplitter parentSplitter,
                      DocumentSplitter childSplitter) {

        List<TextSegment> parentSegments = parentSplitter.split(document);

            for (int i = 0; i < parentSegments.size(); i++) {

                TextSegment parentSegment = parentSegments.get(i);
                String parentId = "parent_" + i;
                parentSegment = getTextSegment(parentSegment, embeddingStore.getIdProperty(), null);

                // Store parent node
                final Metadata metadata = document.metadata();
                
                final String parentIdKey = "parentId";
                if (this.query != null) {
                    final Map<String, Object> metadataMap = metadata.toMap();
                    metadataMap.put(parentIdKey, parentId);
                    
                    String textInput =  parentSegment.text();
                    String text;

                    if (this.model != null) {
                        if (promptSystem == null || promptUser == null) {
                            throw new RuntimeException("");
                        }
                        final SystemMessage systemMessage = Prompt.from(promptSystem).toSystemMessage();

                        final PromptTemplate userTemplate = PromptTemplate.from(promptUser);

                        final UserMessage userMessage = userTemplate.apply(Map.of("input", textInput)).toUserMessage();

                        final List<ChatMessage> chatMessages = List.of(systemMessage, userMessage);

                        text = this.model.chat(chatMessages).aiMessage().text();
                    } else {
                        text = textInput;
                    }
                    metadataMap.putIfAbsent("text", text);
                    metadataMap.putIfAbsent("title", "Untitled");
                    final Map<String, Object> params = Map.of("metadata", metadataMap);
                    metadataMap.putAll(this.params);
                    try (Session session = driver.session()) {
                        session.run(this.query, params);
                    }
                }

                // Convert back to Document to apply DocumentSplitter
                Document parentDoc = Document.from(parentSegment.text(), metadata);

                if (childSplitter == null) {
                    final Embedding content = embeddingModel.embed(parentSegment).content();
                    getAdditionalParams(parentIdKey, parentId);
                    embeddingStore.add(content, parentSegment);
                    continue;
                }

                final String idProperty = embeddingStore.getIdProperty();
                List<TextSegment> childSegments = childSplitter.split(parentDoc)
                        .stream()
                        .map(segment -> getTextSegment(segment, idProperty, parentId))
                        .toList();

                final List<Embedding> embeddings = embeddingModel.embedAll(childSegments).content();
                getAdditionalParams(parentIdKey, parentId);
                embeddingStore.addAll(embeddings, childSegments);
        }
    }

    private void getAdditionalParams(String parentIdKey, String parentId) {
        final HashMap<String, Object> params = new HashMap<>(this.params);
        params.put(parentIdKey, parentId);
        embeddingStore.setAdditionalParams(params);
    }
//
//    public void getDocumentToNeo4jQuery(Session session, Map<String, Object> params) {
//        session.run(getQuery(), getParams(params));
//    }
//
//    private static Map<String, Object> getParams(Map<String, Object> params) {
//        return params;
//    }
//
//    private static String getQuery() {
//        return """
//                    CREATE (:Parent $metadata)
//                """;
//    }


    // TODO --> 
    //  if `id` metadata is present, we create a new univocal one to prevent this error:
    //  org.neo4j.driver.exceptions.ClientException: Node(1) already exists with label `Child` and property `id` = 'doc-ai'
    public TextSegment getTextSegment(TextSegment segment, String idProperty, String parentId) {
        final Metadata metadata1 = segment.metadata();
        final Object idMeta = metadata1.toMap().get(idProperty);
        String value = parentId == null 
                ? randomUUID() 
                : parentId + "_" + randomUUID();
        if (idMeta != null) {
            value = idMeta + "_" + value;
        }
        metadata1.put(idProperty, value);

        return segment;
    }

    // TODO - reference FUNCTION_RESPONSE_SYSTEM: langchain/libs/community/langchain_community/chains/graph_qa/cypher.py

    @Override
    public List<Content> retrieve(final Query query) {

        final String question = query.text();
        Embedding queryEmbedding = embeddingModel.embed(question).content();
        
        final EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();
        
        if (answerModel == null) {
            final Function<EmbeddingMatch<TextSegment>, Content> embeddingMapFunction = i -> {
                final TextSegment embedded = i.embedded();
                return Content.from(embedded);
            };

            return getList(request, embeddingMapFunction);
        } else {
            final Function<EmbeddingMatch<TextSegment>, String> embeddingMapFunction = i -> i.embedded().text();
            final List<String> context = getList(request, embeddingMapFunction);

            final String prompt = promptAnswer + """
                    Answer the question based only on the context provided.

                    Context: {{context}}

                    Question: {{question}}

                    Answer:
                    """;
            final String text = PromptTemplate.from(prompt).apply(Map.of("question", question, "context", context))
                    .text();
            final String chat = answerModel.chat(text);
            return List.of(Content.from(chat));
        }
    }

    private <T> List<T> getList(EmbeddingSearchRequest request, Function<EmbeddingMatch<TextSegment>, T> embeddingMapFunction) {
        return embeddingStore.search(request)
                .matches()
                .stream()
                .map(embeddingMapFunction)
                .toList();
    }

}
