package dev.langchain4j.rag.content.retriever.neo4j;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
TODO
def parent_document_retrieval(
    query: str, client: Milvus | Chroma, window_size: int = 4
):
    top_1 = client.similarity_search(query=query, k=1)[0]
    doc_id = top_1.metadata["document_id"]
    seq_num = top_1.metadata["sequence_number"]
    ids_window = [seq_num + i for i in range(-window_size, window_size, 1)]
    # ...
 */
public class ParentChildRetriever implements ContentRetriever {

    /*
    retriever = ParentDocumentRetriever(
    vectorstore=vectorstore,
    docstore=store,
    child_splitter=child_splitter,
    parent_splitter=parent_splitter,
)
     */
    
    private final EmbeddingModel embeddingModel;
    private final Neo4jEmbeddingStore embeddingStore;
    private final Neo4jGraph graph;

    private final int maxResults;
    private final double minScore;

    // TODO - get Neo4jGraph from Neo4jEmbeddingStore
    public ParentChildRetriever(EmbeddingModel embeddingModel, Neo4jEmbeddingStore embeddingStore, Neo4jGraph graph,
                                int maxResults,
                                double minScore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.graph = graph;
        this.maxResults = maxResults;
        this.minScore = minScore;
    }

    @Override
    public List<Content> retrieve(Query query) {
        // Step 1: Embed the user query
        // TODO - METADATA?
        Embedding queryEmbedding = embeddingModel.embed(query.text()).content();

        // Step 2: Perform vector similarity search on child embeddings
        final EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .build();
        final List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();

        // Step 3: Filter by minScore and collect child IDs
        // todo - needed?
        List<String> childIds = new ArrayList<>();

        for (EmbeddingMatch<TextSegment> match : matches) {
            if (match.score() >= minScore) {
                String id = match.embeddingId(); 
                        // (String) match.embedded().metadata().getString("id"); // TODO - assumes ID is in metadata
                if (id != null) {
                    childIds.add(id);
                }
                
//                childIds.add(match.embedded().metadata().get("id").toString()); // Assuming "id" is in metadata
            }
        }
        
        if (childIds.isEmpty()) return List.of();
         
        
        // todo --> https://github.com/damiangilgonzalez1995/AdvancedRetrievalRags/blob/main/2_parent_document_retriever.ipynb
        // TODO --> https://graphrag.com/reference/graphrag/parent-child-retriever/
        // TODO --> https://blog.langchain.dev/implementing-advanced-retrieval-rag-strategies-with-neo4j/
        // TODO - https://www.youtube.com/watch?v=wSi0fxkH6e0
        // TODO: https://towardsdatascience.com/langchains-parent-document-retriever-revisited-1fca8791f5a0/
//        if (matches.)


        // Step 4: Query parent documents from Neo4j
        List<Content> parentContents = new ArrayList<>();
        try (Session session = graph.driver.session()) {
            String cypherQuery = "MATCH (child)<-[:HAS_CHILD]-(parent) " +
                    "WHERE child.id IN $childIds " +
                    "RETURN parent.text AS text, parent AS metadata";
            Result result = session.run(cypherQuery, Map.of("childIds", childIds));
            while (result.hasNext()) {
                Record record = result.next();
                final TextSegment text = TextSegment.from(record.get("text").asString());
//                Map<String, Object> metadata = record.get("metadata").asMap();
                // TODO - HANDLE MAP.OF
                parentContents.add(Content.from(text, Map.of()));
            }
        }
        return parentContents;
    }
}
