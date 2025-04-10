package dev.langchain4j.rag.content.retriever.neo4j;

import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import org.neo4j.driver.Driver;

import java.util.List;

public class Neo4jEmbeddingRetriever implements ContentRetriever {
    protected final EmbeddingModel embeddingModel;
    protected final Driver driver;
    protected final int maxResults;
    protected final double minScore;
    protected final Neo4jEmbeddingStore embeddingStore;
    
    public Neo4jEmbeddingRetriever(EmbeddingModel embeddingModel, Driver driver,
                                      int maxResults,
                                      double minScore, Neo4jEmbeddingStore embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.driver = driver;
        this.maxResults = maxResults;
        this.minScore = minScore;
        this.embeddingStore = embeddingStore;

//        if (embeddingStore == null) {
//            this.embeddingStore = getDefaultEmbeddingStore(driver);
//        } else {
//            this.embeddingStore = embeddingStore;
//        }
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
