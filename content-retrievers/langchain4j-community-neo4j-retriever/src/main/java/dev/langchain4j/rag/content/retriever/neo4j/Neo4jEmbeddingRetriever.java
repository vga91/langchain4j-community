package dev.langchain4j.rag.content.retriever.neo4j;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;
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



// TODO - customizzarlo simile a https://python.langchain.com/v0.1/docs/modules/data_connection/retrievers/custom_retriever/
    //   con (...DocumentSplitter parentSplitter)
public abstract class Neo4jEmbeddingRetriever implements ContentRetriever {
    protected final EmbeddingModel embeddingModel;
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
                                   Neo4jEmbeddingStore embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.driver = driver;
        this.maxResults = maxResults;
        this.minScore = minScore;
        this.query = query;
        this.params = params;
        final Neo4jEmbeddingStore store = getOrDefault(embeddingStore, getDefaultEmbeddingStore(driver));

        this.embeddingStore = ensureNotNull(store, "embeddingStore");
    }
    
    public Neo4jEmbeddingStore getDefaultEmbeddingStore(Driver driver) {
        return null;
    }

    public void index(Document document,
                      DocumentSplitter parentSplitter,
                      DocumentSplitter childSplitter) {

        List<TextSegment> parentSegments = parentSplitter.split(document);

            for (int i = 0; i < parentSegments.size(); i++) {
                TextSegment parentSegment = parentSegments.get(i);
                String parentId = "parent_" + i;

                // Store parent node
                final Metadata metadata = document.metadata();
                
                final String parentIdKey = "parentId";
                if (this.query != null) {
                    final Map<String, Object> metadataMap = metadata.toMap();
                    metadataMap.put(parentIdKey, parentId);
                    metadataMap.putIfAbsent("text", parentSegment.text());
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
                    return;
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
    //  if `id` metadata is present, we create a new univoc one to prentent this error:
    //  org.neo4j.driver.exceptions.ClientException: Node(1) already exists with label `Child` and property `id` = 'doc-ai'
    public TextSegment getTextSegment(TextSegment segment, String idProperty, String parentId) {
        final Metadata metadata1 = segment.metadata();
        final Object idMeta = metadata1.toMap().get(idProperty);
        String value = parentId + idMeta;
        if (idMeta != null) {
            value += "_" + idMeta;
        }
        metadata1.put(idProperty, value);

        return segment;
    }


    @Override
    public List<Content> retrieve(final Query query) {

        Embedding queryEmbedding = embeddingModel.embed(query.text()).content();
        
        final EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();

        return embeddingStore.search(request)
                .matches()
                .stream()
                .map(i -> {
                    final TextSegment embedded = i.embedded();
                    return Content.from(embedded);
                })
                .toList();
    }
    
}
