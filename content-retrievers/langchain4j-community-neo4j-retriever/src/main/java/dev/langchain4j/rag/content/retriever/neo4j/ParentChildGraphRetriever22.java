package dev.langchain4j.rag.content.retriever.neo4j;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore.CREATE_VECTOR_INDEX;

// TODO - salvare con addDocument e splitters
// retriever simile a text to cypher, parent_document_retrieval

public class ParentChildGraphRetriever22 {

    private final EmbeddingModel embeddingModel;
    private final Driver driver;
    private final String vectorIndex;
    private final int embeddingDimension;

    public ParentChildGraphRetriever22(EmbeddingModel embeddingModel, Driver driver,
                                       String vectorIndex, int embeddingDimension) {
        this.embeddingModel = embeddingModel;
        this.driver = driver;
        this.vectorIndex = vectorIndex;
        this.embeddingDimension = embeddingDimension;
    }

    // TODO - salvare con addDocument e splitters
    public void index(Document document,
                      DocumentSplitter parentSplitter,
                      DocumentSplitter childSplitter) {

        List<TextSegment> parentSegments = parentSplitter.split(document);

        try (Session session = driver.session()) {
            for (int i = 0; i < parentSegments.size(); i++) {
                TextSegment parentSegment = parentSegments.get(i);
                String parentId = "_p" + i;

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

                List<TextSegment> childSegments = childSplitter.split(parentDoc);

                for (int j = 0; j < childSegments.size(); j++) {
                    TextSegment childSegment = childSegments.get(j);
                    String childId = parentId + "_c" + j;

                    Embedding embedding = embeddingModel.embed(childSegment).content();

                    // Store child node and link to parent
                    session.run("""
                    MATCH (p:Parent {id: $parentId})
                    CREATE (p)-[:HAS_CHILD]->(:Child {
                        id: $childId,
                        text: $text,
                        embedding: $embedding
                    })
                """, Map.of(
                            "parentId", parentId,
                            "childId", childId,
                            "text", childSegment.text(),
                            "embedding", embedding.vector()
                    ));
                }
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
                    CREATE_VECTOR_INDEX, vectorIndex, "Child", "embedding", embeddingDimension);
            session.run(createIndexQuery);

            session.run("CALL db.awaitIndexes($timeout)", Map.of("timeout", 20000))
                    .consume();
        }
    }



    public List<Content> retrieve(String query, int topK, double minScore) {
        Embedding queryEmbedding = embeddingModel.embed(TextSegment.from(query)).content();

        // TODO - embeddingStore con retrievalQuery diversa?
        try (Session session = driver.session()) {
            Result result = session.run("""
            CALL db.index.vector.queryNodes($index, $k, $embedding)
            YIELD node AS child, score
            WHERE score >= $minScore
            MATCH (child)<-[:HAS_CHILD]-(parent)
            WITH parent, collect(child.text) AS chunks, max(score) AS score
            RETURN parent.title + reduce(r = "", c in chunks | r + "\\n\\n" + c) AS text,
                   score,
                   {source: parent.url} AS metadata
            ORDER BY score DESC
            LIMIT $k
        """, Map.of(
                    "index", vectorIndex,
                    "k", topK,
                    "embedding", queryEmbedding.vector(),
                    "minScore", minScore
            ));

            List<Content> results = new ArrayList<>();
            while (result.hasNext()) {
                Record record = result.next();
                String mergedText = record.get("text").asString();
                final Map<String, Object> metadata = record.get("metadata").asMap();

                results.add(Content.from(
                        TextSegment.from(mergedText, Metadata.from(metadata))//.withMetadata(metadata.asMap(Function.identity()))
                ));
            }

            return results;
        }
    }

}
