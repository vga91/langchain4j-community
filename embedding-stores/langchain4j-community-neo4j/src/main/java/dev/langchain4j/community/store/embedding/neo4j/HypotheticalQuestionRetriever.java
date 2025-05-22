//package dev.langchain4j.community.store.embedding.neo4j;
//
//import org.neo4j.driver.Driver;
//
//public class HypotheticalQuestionRetriever extends Neo4jEmbeddingStoreIngestor {
//
//    public final static String DEFAULT_RETRIEVAL = """
//            MATCH (node)<-[:HAS_QUESTION]-(parent)
//            WITH parent, max(score) AS score, node // deduplicate parents
//            RETURN parent.text AS text, score, properties(node) AS metadata
//             ORDER BY score DESC
//            LIMIT $maxResults""";
//
//    public static final String PARENT_QUERY =
//            """
//                        UNWIND $rows AS question
//                        MATCH (p:Parent {parentId: $parentId})
//                        // MERGE (p:Parent {id: $parentId})
//                        WITH p, question
//                        // UNWIND $questions AS question
//                        CREATE (q:%1$s {%2$s: question.%2$s})
//                        SET q += question.%3$s
//                        MERGE (q)<-[:HAS_QUESTION]-(p)
//                        WITH q, question
//                        CALL db.create.setNodeVectorProperty(q, $embeddingProperty, question.%4$s)
//                        // YIELD node
//                        RETURN count(*)
//                    """;
//
//    public final static String SYSTEM_PROMPT = """
//            You are generating hypothetical questions based on the information found in the text.
//            Make sure to provide full context in the generated questions.
//            """;
//
//    public final static String USER_PROMPT = """
//            Use the given format to generate hypothetical questions from the following input:
//            {{input}}
//            
//            Hypothetical questions:
//            """;
//
//    public HypotheticalQuestionRetriever(final IngestorConfig config) {
//        super(config);
//    }
//
//    @Override
//    public Neo4jEmbeddingStore getDefaultEmbeddingStore() {
//        return Neo4jEmbeddingStore.builder()
//                .driver(driver)
//                .retrievalQuery(DEFAULT_RETRIEVAL)
//                .entityCreationQuery(PARENT_QUERY)
//                .label("Child")
//                .indexName("child_embedding_index")
//                .dimension(384)
//                .build();
//    }
//
//    public static Builder builder() {
//        return new Builder();
//    }
//
//    public static class Builder extends Neo4jEmbeddingStoreIngestor.Builder {
////        public Builder(final Class clazz) {
////            super(clazz);
////        }
//
//
//        @Override
//        protected Builder self() {
//            return this;
//        }
//
//        @Override
//        public HypotheticalQuestionRetriever build() {
//            return new HypotheticalQuestionRetriever(createIngestorConfig());
//        }
//    }
//    
//}
