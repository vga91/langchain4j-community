package dev.langchain4j.community.rag.content.retriever.neo4j;

import static dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jUtils.generateMD5;
import static dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jUtils.sanitizeOrThrows;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.community.data.document.graph.GraphDocument;
import dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Experimental
public class KnowledgeGraphWriter {

    /* default configs */
    public static final String DEFAULT_ID_PROP = "id";
    public static final String DEFAULT_TEXT_PROP = "text";
    public static final String DEFAULT_LABEL = "__Entity__";
    public static final String DEFAULT_CONS_NAME = "knowledge_cons";
    public static final String DEFAULT_REL_TYPE = "HAS_ENTITY";

    final String label;
    final String relType;
    final String sanitizedLabel;
    final String sanitizedRelType;
    final String constraintName;

    final String idProperty;
    final String sanitizedIdProperty;
    final String textProperty;
    final String sanitizedTextProperty;

    private final Neo4jGraph graph;
    private final Neo4jEmbeddingStore embeddingStore;
    private EmbeddingModel embeddingModel = null;

    public KnowledgeGraphWriter(
            Neo4jGraph graph,
            String idProperty,
            String label,
            String textProperty,
            String relType,
            String constraintName,
            Neo4jEmbeddingStore embeddingStore,
            EmbeddingModel embeddingModel) {
        this.graph = ensureNotNull(graph, "graph");
        
        // TODO - if embeddingStore then label is taken from there --> getSanitizedLabel()
        this.embeddingStore = embeddingStore;
        if (this.embeddingStore != null) {
            this.embeddingModel = ensureNotNull(embeddingModel, "embeddingModel");
        }
        this.label = this.embeddingStore == null 
                ? getOrDefault(label, DEFAULT_LABEL) 
                : this.embeddingStore.getSanitizedLabel();
        this.relType = getOrDefault(relType, DEFAULT_REL_TYPE);
        this.idProperty = getOrDefault(idProperty, DEFAULT_ID_PROP);
        this.textProperty = getOrDefault(textProperty, DEFAULT_TEXT_PROP);
        this.constraintName = getOrDefault(constraintName, DEFAULT_CONS_NAME);

        /* sanitize labels and property names, to prevent from Cypher Injections */
        this.sanitizedLabel = sanitizeOrThrows(this.label, "label");
        this.sanitizedRelType = sanitizeOrThrows(this.relType, "relType");
        this.sanitizedIdProperty = sanitizeOrThrows(this.idProperty, "idProperty");
        this.sanitizedTextProperty = sanitizeOrThrows(this.textProperty, "textProperty");

        createConstraint();
    }

    public static Builder builder() {
        return new Builder();
    }

    private void createConstraint() {
        // Check and create constraint if not exists
        final String constraintQuery = String.format(
                "CREATE CONSTRAINT %s IF NOT EXISTS FOR (b:%s) REQUIRE b.%s IS UNIQUE;",
                constraintName, sanitizedLabel, sanitizedIdProperty);
        graph.executeWrite(constraintQuery);
        graph.refreshSchema();
    }

    public void addGraphDocuments(List<GraphDocument> graphDocuments, boolean includeSource) {

        for (GraphDocument graphDoc : graphDocuments) {
            Document source = graphDoc.source();

            // Import nodes
            Map<String, Object> nodeParams = new HashMap<>();
            nodeParams.put(
                    "data", graphDoc.nodes().stream().map(Neo4jUtils::toMap).toList());

            if (includeSource) {
                // create a copyOf metadata, not to update existing graphDoc,
                // subsequent tests could potentially fail
                final Map<String, Object> metadata =
                        new HashMap<>(Map.copyOf(source.metadata().toMap()));
                if (!metadata.containsKey(idProperty)) {
                    metadata.put(idProperty, generateMD5(source.text()));
                }
                final Map<String, Object> document = Map.of("metadata", metadata, "text", source.text());
                nodeParams.put("document", document);
            }


//            EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

//            List<Map> data = (List<Map>) nodeParams.get("data");

//            List<Embedding> embeddings = new ArrayList<>();
//            List<TextSegment> segments = new ArrayList<>();
//            for (GraphNode node: graphDoc.nodes()) {
//                final Map<String, String> properties = node.properties();
//                properties.put("type", node.type());
//                final String text = node.id();
//                final TextSegment segment = TextSegment.from(text, Metadata.from(properties));
//                final Embedding embedding = embeddingModel.embed(text).content();
//                
//                embeddings.addAl
//            }
            
            if (embeddingStore != null) {
                if (includeSource) {
                    final String creationQuery = getIncludeDocsQuery(true)
                            + "UNWIND $rows AS row\n"
                            + mergeDocsWithSource()
                            + """
                            SET source += row.%3$s
                            WITH row, source
                            CALL db.create.setNodeVectorProperty(source, $embeddingProperty, row.%4$s)
                            RETURN count(*)""";

//                        """
//                        UNWIND $rows AS row
//                        // MATCH (p:Document {<sanitizedIdProperty>: <sanitizedTextProperty>})
//                        CREATE (p)-[:<relType>]->(u:%1$s {%2$s: row.%2$s})
//                        SET u += row.%3$s
//                        WITH row, u
//                        CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
//                        RETURN count(*)""";
                    embeddingStore.setEntityCreationQuery(creationQuery);
                }
                
                final List<TextSegment> segments = graphDoc.nodes().stream().map(i -> {

                    final Map<String, String> properties = new HashMap<>(i.properties());
                    properties.put("type", i.type());
                    return TextSegment.from(i.id(), Metadata.from(properties));
                }).toList();

                final List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

                this.embeddingStore.addAll(embeddings, segments);

// data.stream().map(i -> embeddingModel.embed((String) i.get("id"))).toList();

            } else {
                String nodeImportQuery = getNodeImportQuery(includeSource);
                graph.executeWrite(nodeImportQuery, nodeParams);
            }
            
            // TODO - leverage the `entityCreationQuery` of embeddingStore for the `includeSource` stuff?
            /*
                protected static final String CUSTOM_CREATION_QUERY =
            """
                UNWIND $rows AS row
                MATCH (p:Document {<sanitizedIdProperty>: <sanitizedTextProperty>})
                CREATE (p)-[:<relType>]->(u:%1$s {%2$s: row.%2$s})
                SET u += row.%3$s
                WITH row, u
                CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
                RETURN count(*)""";
             */

            /*
            List<Map> data = (List<Map>) nodeParams.get("data");

            String s = (String) data.get(0).get("id");
            new AllMiniLmL6V2QuantizedEmbeddingModel().embed(s);
             */
            
            
            // Import relationships
            List<Map<String, String>> relData = graphDoc.relationships().stream()
                    .map(rel -> Map.of(
                            "source", rel.sourceNode().id(),
                            "source_label", rel.sourceNode().type(),
                            "target", rel.targetNode().id(),
                            "target_label", rel.targetNode().type(),
                            "type", rel.type().replace(" ", "_").toUpperCase()))
                    .toList();

            String relImportQuery = getRelImportQuery();
            graph.executeWrite(relImportQuery, Map.of("data", relData));
        }
    }

    private String getNodeImportQuery(boolean includeSource) {

        String includeDocsQuery = getIncludeDocsQuery(includeSource);
        final String withDocsRel = includeSource ? mergeDocsWithSource() : "";

        return includeDocsQuery + "UNWIND $data AS row "
                + String.format("MERGE (source:%1$s {%2$s: row.id}) ", sanitizedLabel, sanitizedIdProperty)
                + withDocsRel
                + "WITH source, row "
                + "SET source:$(row.type) "
                + "RETURN count(*) as total";
    }

    private String mergeDocsWithSource() {
        return String.format("MERGE (d)-[:%s]->(source) ", relType);
    }

    private String getIncludeDocsQuery(boolean includeSource) {
        if (!includeSource) {
            return "";
        }
        return String.format(
                """
                        MERGE (d:Document {%1$s: $document.metadata.%1$s})
                        SET d.%2$s = $document.text
                        SET d += $document.metadata
                        WITH d
                        """,
                sanitizedIdProperty, sanitizedTextProperty);
    }

    private String getRelImportQuery() {

        return String.format(
                """
                        UNWIND $data AS row
                        MERGE (source:%1$s {%2$s: row.source})
                        MERGE (target:%1$s {%2$s: row.target})
                        WITH source, target, row
                        MERGE (source)-[rel:$(toString(row.type) + '')]->(target)
                        RETURN distinct 'done'
                        """,
                sanitizedLabel, sanitizedIdProperty);
    }

    public static class Builder {

        private String label;
        private String idProperty;
        private String textProperty;
        private String relType;
        private String constraintName;
        private Neo4jGraph graph;
        private Neo4jEmbeddingStore embeddingStore;
        private EmbeddingModel embeddingModel;

        /**
         * @param graph the {@link Neo4jGraph} (required)
         */
        public Builder graph(Neo4jGraph graph) {
            this.graph = graph;
            return this;
        }

        /**
         * @param idProperty the entity id, to be used with {@link #addGraphDocuments(List, boolean)}
         */
        public Builder idProperty(String idProperty) {
            this.idProperty = idProperty;
            return this;
        }

        /**
         * @param textProperty the document text, to be used with {@link #addGraphDocuments(List, boolean)}
         *                     if the second parameter is true
         */
        public Builder textProperty(String textProperty) {
            this.textProperty = textProperty;
            return this;
        }

        /**
         * @param label the entity label, to be used with {@link #addGraphDocuments(List, boolean)}
         *              (default: "__Entity__")
         */
        public Builder label(String label) {
            this.label = label;
            return this;
        }

        /**
         * @param relType the entity relationship types, to be used with {@link #addGraphDocuments(List, boolean)}
         *                (default "HAS_ENTITY")
         */
        public Builder relType(String relType) {
            this.relType = relType;
            return this;
        }

        /**
         * @param constraintName the constraint name (default: "knowledge_cons")
         */
        public Builder constraintName(String constraintName) {
            this.constraintName = constraintName;
            return this;
        }

        /**
         * @param embeddingStore TODO
         */
        public Builder embeddingStore(Neo4jEmbeddingStore embeddingStore) {
            this.embeddingStore = embeddingStore;
            return this;
        }

        /**
         * @param embeddingModel TODO
         */
        public Builder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        public KnowledgeGraphWriter build() {
            return new KnowledgeGraphWriter(graph, idProperty, label, textProperty, relType, constraintName, embeddingStore, embeddingModel);
        }
    }
}
