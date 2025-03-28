package dev.langchain4j.rag.content.retriever.neo4j;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.rag.transformer.Neo4jUtils.sanitizeOrThrows;

import dev.langchain4j.internal.ValidationUtils;
import dev.langchain4j.rag.transformer.GraphDocument;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.summary.ResultSummary;

public class Neo4jGraph implements AutoCloseable {
    public record GraphSchema(List<String> nodesProperties, List<String> relationshipsProperties, List<String> patterns) {}

    public static class Builder {
        private String label;
        private String idProperty;
        private String textProperty;
        private Driver driver;

        /**
         * @param driver the {@link Driver} (required)
         */
        Builder driver(Driver driver) {
            this.driver = driver;
            return this;
        }

        /**
         * @param idProperty the entity id, to be used with {@link Neo4jGraph#addGraphDocuments(List, boolean, boolean)}
         */
        public Builder idProperty(String idProperty) {
            this.idProperty = idProperty;
            return this;
        }

        /**
         * @param textProperty the document text, to be used with {@link Neo4jGraph#addGraphDocuments(List, boolean, boolean)}
         *                     if the second parameter is true
         */
        public Builder textProperty(String textProperty) {
            this.textProperty = textProperty;
            return this;
        }

        /**
         * @param label the entity label, to be used with {@link Neo4jGraph#addGraphDocuments(List, boolean, boolean)}
         *              if the third parameter is true,
         *              otherwise it will create nodes with label `Document`
         */
        public Builder label(String label) {
            this.label = label;
            return this;
        }

        /**
         * Creates an instance a {@link Driver}, starting from uri, user and password
         *
         * @param uri      the Bolt URI to a Neo4j instance
         * @param user     the Neo4j instance's username
         * @param password the Neo4j instance's password
         */
        public Builder withBasicAuth(String uri, String user, String password) {
            this.driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
            return this;
        }

        Neo4jGraph build() {
            return new Neo4jGraph(driver, idProperty, label, textProperty);
        }
    }
    /* default configs */
    public static final String DEFAULT_ID_PROP = "id";
    public static final String DEFAULT_TEXT_PROP = "text";
    public static final String DEFAULT_LABEL = "__Entity__";
    private static final String SCHEMA_FROM_META_DATA =
            """
            CALL apoc.meta.data({maxRels: $maxRels, sample: $sample})
            YIELD label, other, elementType, type, property
            WITH label, elementType,
                 apoc.text.join(collect(case when NOT type = "RELATIONSHIP" then property+": "+type else null end),", ") AS properties,
                 collect(case when type = "RELATIONSHIP" AND elementType = "node" then "(:" + label + ")-[:" + property + "]->(:" + toString(other[0]) + ")" else null end) AS patterns
            WITH elementType AS type,
                collect(":"+label+" {"+properties+"}") AS entities,
                apoc.coll.flatten(collect(coalesce(patterns,[]))) AS patterns
            RETURN collect(case type when "relationship" then entities end)[0] AS relationships,
                collect(case type when "node" then entities end)[0] AS nodes,
                collect(case type when "node" then patterns end)[0] as patterns
            """;

    private final Driver driver;
    final String label;
    final String sanitizedLabel;
    final String idProperty;
    final String sanitizedIdProperty;
    final String textProperty;
    final String sanitizedTextProperty;
    private String schema;
    private GraphSchema structuredSchema;

    public Neo4jGraph(final Driver driver, String idProperty, String label, String textProperty) {
        this.label = getOrDefault(label, DEFAULT_LABEL);
        this.idProperty = getOrDefault(idProperty, DEFAULT_ID_PROP);
        this.textProperty = getOrDefault(textProperty, DEFAULT_TEXT_PROP);

        /* sanitize labels and property names, to prevent from Cypher Injections */
        this.sanitizedLabel = sanitizeOrThrows(this.label, "label");
        this.sanitizedIdProperty = sanitizeOrThrows(this.idProperty, "idProperty");
        this.sanitizedTextProperty = sanitizeOrThrows(this.textProperty, "textProperty");

        this.driver = ValidationUtils.ensureNotNull(driver, "driver");
        this.driver.verifyConnectivity();
        try {
            refreshSchema();
        } catch (ClientException e) {
            if ("Neo.ClientError.Procedure.ProcedureNotFound".equals(e.code())) {
                throw new Neo4jException("Please ensure the APOC plugin is installed in Neo4j", e);
            }
            throw e;
        }
    }

    public GraphSchema getStructuredSchema() {
        return structuredSchema;
    }

    public String getSchema() {
        return schema;
    }

    static Builder builder() {
        return new Builder();
    }

    public ResultSummary executeWrite(String queryString) {
        return executeWrite(queryString, Map.of());
    }

    public ResultSummary executeWrite(String queryString, Map<String, Object> params) {

        try (Session session = this.driver.session()) {
            return session.executeWrite(tx -> tx.run(queryString, params).consume());
        } catch (ClientException e) {
            throw new Neo4jException("Error executing query: " + queryString, e);
        }
    }

    public List<Record> executeRead(String queryString) {

        return this.driver.executableQuery(queryString).execute().records();
    }

    public void refreshSchema() {
        final Record record = executeRead(SCHEMA_FROM_META_DATA, Map.of("sample", sample, "maxRels", maxRels))
                .get(0);
        final List<String> nodes = record.get("nodes").asList(Value::asString);
        final List<String> relationships = record.get("relationships").asList(Value::asString);
        final List<String> patterns = record.get("patterns").asList(Value::asString);
        this.structuredSchema = new GraphSchema(nodes, relationships, patterns);


        final String nodesString = String.join(", ", nodes);
        final String relationshipsString = String.join(", ", relationships);
        final String patternsString = String.join(", ", patterns);
        this.schema = "Node properties are the following:\n" + nodesString
                + "\n\n" + "Relationship properties are the following:\n"
                + relationshipsString
                + "\n\n" + "The relationships are the following:\n"
                + patternsString;
    }

    private List<String> formatNodeProperties(List<Record> records) {

        return records.stream()
                .map(this::getOutput)
                .map(r -> String.format(
                        "%s %s",
                        r.asMap().get("labels"), formatMap(r.get("properties").asList(Value::asMap))))
                .toList();
    }

    private List<String> formatRelationshipProperties(List<Record> records) {

        return records.stream()
                .map(this::getOutput)
                .map(r -> String.format(
                        "%s %s", r.get("type"), formatMap(r.get("properties").asList(Value::asMap))))
                .toList();
    }

    private List<String> formatRelationships(List<Record> records) {

        return records.stream()
                .map(r -> getOutput(r).asMap())
                .map(r -> String.format("(:%s)-[:%s]->(:%s)", r.get("start"), r.get("type"), r.get("end")))
                .toList();
    }

    private Value getOutput(Record record) {

        return record.get("output");
    }

    private String formatMap(List<Map<String, Object>> properties) {

        return properties.stream()
                .map(prop -> prop.get("property") + ":" + prop.get("type"))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    public void addGraphDocuments(List<GraphDocument> graphDocuments, boolean includeSource, boolean baseEntityLabel) {
        Neo4jGraphUtils.addGraphDocuments(graphDocuments, includeSource, baseEntityLabel, this);
    }

    @Override
    public void close() {

        this.driver.close();
    }
}
