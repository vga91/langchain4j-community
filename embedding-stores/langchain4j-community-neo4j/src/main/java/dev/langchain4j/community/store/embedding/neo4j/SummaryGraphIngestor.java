package dev.langchain4j.community.store.embedding.neo4j;

// import dev.langchain4j.model.chat.ChatLanguageModel;
import org.neo4j.driver.Driver;

public class SummaryGraphIngestor extends Neo4jEmbeddingStoreIngestor {
    public final static String DEFAULT_RETRIEVAL = """
            MATCH (node)<-[:HAS_SUMMARY]-(parent)
            WITH parent, max(score) AS score, node // deduplicate parents
            RETURN parent.text AS text, score, properties(node) AS metadata
            ORDER BY score DESC
            LIMIT $maxResults""";

    public final static String SYSTEM_PROMPT = """
            You are generating concise and accurate summaries based on the information found in the text.
            """;

    public final static String USER_PROMPT = """
            Generate a summary of the following input:
            {{input}}
            
            Summary:
            """;

    public static final String PARENT_QUERY =

            """
                UNWIND $rows AS row
                MATCH (p:Parent {parentId: $parentId})
                CREATE (p)-[:HAS_SUMMARY]->(u:%1$s {%2$s: row.%2$s})
                SET u += row.%3$s
                WITH row, u
                CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
                RETURN count(*)""";

    public SummaryGraphIngestor(final IngestorConfig config) {
        super(config);
    }

    //    /**
//     * Constructs a new {@code Neo4jEmbeddingStoreIngestor}, which processes documents through a transformation
//     * and embedding pipeline and stores the results in a Neo4j database. This includes:
//     * <ul>
//     *   <li>Document transformation</li>
//     *   <li>Parent and child document splitting</li>
//     *   <li>Optional manipulation of parent text segments using a {@link ChatModel}</li>
//     *   <li>Embedding generation and storage of segments into Neo4j</li>
//     * </ul>
//     *  @param documentTransformer The {@link DocumentTransformer} applied to the original documents.
//     *
//     * @param documentSplitter            The {@link DocumentSplitter} used to split documents into parent segments.
//     * @param textSegmentTransformer      The {@link TextSegmentTransformer} applied to parent segments.
//     * @param childTextSegmentTransformer The {@link TextSegmentTransformer} applied to child segments.
//     * @param embeddingModel              The {@link EmbeddingModel} used to generate embeddings from text segments.
//     * @param embeddingStore              The {@link dev.langchain4j.store.embedding.EmbeddingStore} (specifically {@link Neo4jEmbeddingStore}) used to persist embeddings.
//     * @param documentChildSplitter       The {@link DocumentSplitter} used to generate child segments from parent segments.
//     * @param driver                      The {@link Driver} used to execute Cypher queries against the Neo4j database.
//     * @param query                       The Cypher query used to insert processed segments and metadata into Neo4j.
//     * @param parentIdKey                 The metadata key used to extract the parent segment ID; if absent, a UUID will be generated.
//     * @param params                      Additional query parameters to include in the Cypher execution, beyond segment metadata and text.
//     * @param systemPrompt                A system prompt for manipulating parent segment text via a {@link ChatModel}. Ignored if {@code questionModel} is {@code null}.
//     * @param userPrompt                  A user prompt for manipulating parent segment text via a {@link ChatModel}. Ignored if {@code questionModel} is {@code null}.
//     * @param questionModel               A {@link ChatModel} used to further transform parent segment text based on provided prompts. If {@code null}, no chat-based manipulation occurs.
//     */
//    public SummaryRetriever(final DocumentTransformer documentTransformer, final DocumentSplitter documentSplitter, final TextSegmentTransformer textSegmentTransformer, final TextSegmentTransformer childTextSegmentTransformer, final EmbeddingModel embeddingModel, final Neo4jEmbeddingStore embeddingStore, final DocumentSplitter documentChildSplitter, final Driver driver, final String query, final String parentIdKey, final Map<String, Object> params, final String systemPrompt, final String userPrompt, final ChatModel questionModel) {
//        super(documentTransformer, documentSplitter, textSegmentTransformer, childTextSegmentTransformer, embeddingModel, embeddingStore, documentChildSplitter, driver, query, parentIdKey, params, systemPrompt, userPrompt, questionModel);
//        neo4jEmbeddingStore = getDefaultEmbeddingStore(driver);
//    }


    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Neo4jEmbeddingStore getEmbeddingStore() {
        return getNeo4jEmbeddingStore(driver);
    }

    private static Neo4jEmbeddingStore getNeo4jEmbeddingStore(Driver driver) {
        return Neo4jEmbeddingStore.builder()
                .driver(driver)
                .retrievalQuery(DEFAULT_RETRIEVAL)
                .entityCreationQuery(PARENT_QUERY)
                .label("Summary")
                .indexName("summary_embedding_index")
                .dimension(384)
                .build();
    }

//    private static Neo4jEmbeddingStore getNeo4jEmbeddingStore(Driver driver) {
//        return 
//    }

    public static class Builder extends Neo4jEmbeddingStoreIngestor.Builder {

        @Override
        protected String getSystemPrompt() {
            return SYSTEM_PROMPT;
        }

        @Override
        protected String getUserPrompt() {
            return USER_PROMPT;
        }

        @Override
        protected String getQuery() {
            return "CREATE (:Parent $metadata)";
        }

        // TODO - change it?????
        @Override
        protected Neo4jEmbeddingStore getEmbeddingStore() {
            return getNeo4jEmbeddingStore(driver);
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public SummaryGraphIngestor build() {
            return new SummaryGraphIngestor(createIngestorConfig());
        }
    }
}
