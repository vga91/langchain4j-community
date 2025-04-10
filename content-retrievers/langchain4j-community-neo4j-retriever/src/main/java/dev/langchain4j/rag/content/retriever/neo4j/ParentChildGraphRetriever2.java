package dev.langchain4j.rag.content.retriever.neo4j;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore.CREATE_VECTOR_INDEX;
import static dev.langchain4j.internal.Utils.randomUUID;

public class ParentChildGraphRetriever2 extends Neo4jEmbeddingRetriever {
    public final static String DEFAULT_RETRIEVAL = """
            MATCH (node)<-[:HAS_CHILD]-(parent)
            WITH parent, collect(node.text) AS chunks, max(score) AS score
            RETURN parent.title + reduce(r = "", c in chunks | r + "\\n\\n" + c) AS text,
                   score,
                   {source: parent.url} AS metadata
            ORDER BY score DESC
            LIMIT $maxResults""";
    public static final String PARENT_QUERY =

            """
                UNWIND $rows AS row
                MATCH (p:Parent {id: $parentId})
                CREATE (p)-[:HAS_CHILD]->(u:%1$s {%2$s: row.%2$s})
                SET u += row.%3$s
                WITH row, u
                CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
                RETURN count(*)""";

//    private final EmbeddingModel embeddingModel;
//    private final Driver driver;
//    private final int maxResults;
//    private final double minScore;
//    private final Neo4jEmbeddingStore embeddingStore;
    

    public ParentChildGraphRetriever2(EmbeddingModel embeddingModel, Driver driver,
                                      int maxResults,
                                      double minScore, Neo4jEmbeddingStore embeddingStore) {
        super(embeddingModel, driver, maxResults, minScore, embeddingStore);
//        this.embeddingModel = embeddingModel;
//        this.driver = driver;
//        this.maxResults = maxResults;
//        this.minScore = minScore;
        
        if (embeddingStore == null) {
            this.embeddingStore = getDefaultEmbeddingStore(driver);
        }
//        else {
//            this.embeddingStore = embeddingStore;
//        }
    }

    private static Neo4jEmbeddingStore getDefaultEmbeddingStore(Driver driver) {
        return Neo4jEmbeddingStore.builder()
                .driver(driver)
                .retrievalQuery(DEFAULT_RETRIEVAL)
                .entityCreationQuery(PARENT_QUERY)
                .label("Child")
                .indexName("child_embedding_index")
                .dimension(384)
                .build();
    }

    public void index(Document document,
                      DocumentSplitter parentSplitter,
                      DocumentSplitter childSplitter) {

        List<TextSegment> parentSegments = parentSplitter.split(document);

        try (Session session = driver.session()) {
            for (int i = 0; i < parentSegments.size(); i++) {
                TextSegment parentSegment = parentSegments.get(i);
                String parentId = "parent_" + i;

                // Store parent node
                final Metadata metadata = document.metadata();
                final Map<String, Object> metadataMap = metadata.toMap();
                session.run("""
                CREATE (:Parent {
                    id: $id,
                    title: $title,
                    url: $url,
                    text: $text
                })
            """, Map.of(
                        "id", parentId,
                        "title", metadataMap.getOrDefault("title", "Untitled"),
                        "url", metadataMap.getOrDefault("url", ""),
                        "text", parentSegment.text()
                ));

                // Convert back to Document to apply DocumentSplitter
                Document parentDoc = Document.from(parentSegment.text(), metadata);

                
                
                final String idProperty = embeddingStore.getIdProperty();
                List<TextSegment> childSegments = childSplitter.split(parentDoc)
                        .stream()
                        .map(segment -> {
                            return getTextSegment(segment, idProperty, parentId);
                        })
                        .toList();

                final List<Embedding> embeddings = embeddingModel.embedAll(childSegments).content();
                embeddingStore.setAdditionalParams(Map.of("parentId", parentId));
                embeddingStore.addAll(embeddings, childSegments);
            }
        }

        // create vector index
        try (var session = driver.session()) {

//            Map<String, Object> params = Map.of(
//                    "indexName",
//                    vectorIndex,
//                    "label",
//                    this.label,
//                    "embeddingProperty",
//                    this.embeddingProperty,
//                    "dimension",
//                    this.dimension);
            
            final String createIndexQuery = String.format(
                    CREATE_VECTOR_INDEX, embeddingStore.getIndexName(), 
                    embeddingStore.getSanitizedLabel(), embeddingStore.getSanitizedEmbeddingProperty(), embeddingStore.getDimension());
//                    CREATE_VECTOR_INDEX, vectorIndex, "Child", "embedding", embeddingDimension);
            session.run(createIndexQuery);

            session.run("CALL db.awaitIndexes($timeout)", Map.of("timeout", 20000))
                    .consume();
        }
    }

    // TODO --> 
    //  if `id` metadata is present, we create a new univoc one to prentent this error:
    //  org.neo4j.driver.exceptions.ClientException: Node(1) already exists with label `Child` and property `id` = 'doc-ai'
    private static TextSegment getTextSegment(TextSegment segment, String idProperty, String parentId) {
        final Metadata metadata1 = segment.metadata();
        final Object idMeta = metadata1.toMap().get(idProperty);
        String value = parentId + idMeta;
        if (idMeta != null) {
            value += "_" + idMeta;
        }
        metadata1.put(idProperty, value);

        return segment;
    }


//    @Override
//    public List<Content> retrieve(final Query query) {
//        
//        Embedding queryEmbedding = embeddingModel.embed(query.text()).content();
//
//
//        final EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
//                .queryEmbedding(queryEmbedding)
//                .maxResults(maxResults)
//                .minScore(minScore)
//                .build();
//
//        return embeddingStore.search(request)
//                .matches()
//                .stream()
//                .map(i -> {
//                    final TextSegment embedded = i.embedded();
//                    return Content.from(embedded);
//                })
//                .toList();
//    }

}
