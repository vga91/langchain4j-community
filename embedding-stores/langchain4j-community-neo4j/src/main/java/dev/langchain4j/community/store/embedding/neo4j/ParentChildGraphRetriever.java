package dev.langchain4j.community.store.embedding.neo4j;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.DocumentTransformer;
import dev.langchain4j.data.segment.TextSegmentTransformer;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.neo4j.driver.Driver;

import java.util.Map;

public class ParentChildGraphRetriever extends Neo4jEmbeddingStoreIngestor {
    public final static String DEFAULT_RETRIEVAL = """
            MATCH (node)<-[:HAS_CHILD]-(parent)
            WITH parent, collect(node.text) AS chunks, max(score) AS score
            RETURN parent.text + reduce(r = "", c in chunks | r + "\\n\\n" + c) AS text,
                   score,
                   properties(parent) AS metadata
            ORDER BY score DESC
            LIMIT $maxResults""";

    public static final String PARENT_QUERY =
            """
                UNWIND $rows AS row
                MATCH (p:Parent {parentId: $parentId})
                CREATE (p)-[:HAS_CHILD]->(u:%1$s {%2$s: row.%2$s})
                SET u += row.%3$s
                WITH row, u
                CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
                RETURN count(*)""";

    /**
     * Constructs a new {@code Neo4jEmbeddingStoreIngestor}, which processes documents through a transformation
     * and embedding pipeline and stores the results in a Neo4j database. This includes:
     * <ul>
     *   <li>Document transformation</li>
     *   <li>Parent and child document splitting</li>
     *   <li>Optional manipulation of parent text segments using a {@link ChatModel}</li>
     *   <li>Embedding generation and storage of segments into Neo4j</li>
     * </ul>
     *  @param documentTransformer The {@link DocumentTransformer} applied to the original documents.
     *
     * @param documentSplitter            The {@link DocumentSplitter} used to split documents into parent segments.
     * @param textSegmentTransformer      The {@link TextSegmentTransformer} applied to parent segments.
     * @param childTextSegmentTransformer The {@link TextSegmentTransformer} applied to child segments.
     * @param embeddingModel              The {@link EmbeddingModel} used to generate embeddings from text segments.
     * @param embeddingStore              The {@link dev.langchain4j.store.embedding.EmbeddingStore} (specifically {@link Neo4jEmbeddingStore}) used to persist embeddings.
     * @param documentChildSplitter       The {@link DocumentSplitter} used to generate child segments from parent segments.
     * @param driver                      The {@link Driver} used to execute Cypher queries against the Neo4j database.
     * @param query                       The Cypher query used to insert processed segments and metadata into Neo4j.
     * @param parentIdKey                 The metadata key used to extract the parent segment ID; if absent, a UUID will be generated.
     * @param params                      Additional query parameters to include in the Cypher execution, beyond segment metadata and text.
     * @param systemPrompt                A system prompt for manipulating parent segment text via a {@link ChatModel}. Ignored if {@code questionModel} is {@code null}.
     * @param userPrompt                  A user prompt for manipulating parent segment text via a {@link ChatModel}. Ignored if {@code questionModel} is {@code null}.
     * @param questionModel               A {@link ChatModel} used to further transform parent segment text based on provided prompts. If {@code null}, no chat-based manipulation occurs.
     */
    public ParentChildGraphRetriever(final DocumentTransformer documentTransformer, final DocumentSplitter documentSplitter, final TextSegmentTransformer textSegmentTransformer, final TextSegmentTransformer childTextSegmentTransformer, final EmbeddingModel embeddingModel, final Neo4jEmbeddingStore embeddingStore, final DocumentSplitter documentChildSplitter, final Driver driver, final String query, final String parentIdKey, final Map<String, Object> params, final String systemPrompt, final String userPrompt, final ChatModel questionModel) {
        super(documentTransformer, documentSplitter, textSegmentTransformer, childTextSegmentTransformer, embeddingModel, embeddingStore, documentChildSplitter, driver, query, parentIdKey, params, systemPrompt, userPrompt, questionModel);
        neo4jEmbeddingStore = getDefaultEmbeddingStore(driver);
    }

    public ParentChildGraphRetriever(final IngestorConfig config) {
        super(config);
    }

    //    public ParentChildGraphRetriever(EmbeddingModel embeddingModel, Driver driver,
//                                     int maxResults,
//                                     double minScore, Neo4jEmbeddingStore embeddingStore) {
//        super(embeddingModel, driver, maxResults, minScore, "CREATE (:Parent $metadata)", Map.of(), embeddingStore,  null, null, null, null, null, null);
//    }

    // @Override
    public Neo4jEmbeddingStore getDefaultEmbeddingStore(Driver driver) {
        return Neo4jEmbeddingStore.builder()
                .driver(driver)
                .retrievalQuery(DEFAULT_RETRIEVAL)
                .entityCreationQuery(PARENT_QUERY)
                .label("Child")
                .indexName("child_embedding_index")
                .dimension(384)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends Neo4jEmbeddingStoreIngestor.Builder {

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public ParentChildGraphRetriever build() {
            return new ParentChildGraphRetriever(createIngestorConfig());
        }
    }

//        public HypotheticalQuestionRetriever build() {
//            return new HypotheticalQuestionRetriever(
//                    documentTransformer,
//                    documentSplitter,
//                    textSegmentTransformer,
//                    childTextSegmentTransformer,
//                    embeddingModel,
//                    (Neo4jEmbeddingStore) embeddingStore,
//                    documentChildSplitter,
//                    driver,
//                    query,
//                    parentIdKey,
//                    params,
//                    systemPrompt,
//                    userPrompt,
//                    questionModel);
//        }


}
