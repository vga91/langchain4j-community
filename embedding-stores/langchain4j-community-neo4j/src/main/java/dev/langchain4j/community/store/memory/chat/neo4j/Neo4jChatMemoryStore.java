package dev.langchain4j.community.store.memory.chat.neo4j;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;

import java.util.List;
import java.util.Map;
import java.util.Objects;

// TODO - dico che lo metto in embedding-stores come RedisChatMemoryStoreIT
// TODO - builder

//import static dev.langchain4j.community.store.embedding.neo4j.Neo4jEmbeddingUtils.sanitizeOrThrows;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class Neo4jChatMemoryStore implements ChatMemoryStore {
    public static final String DEFAULT_MEMORY_LABEL = "Memory";
    public static final String DEFAULT_LABEL_MESSAGE = "Message";
    public static final String DEFAULT_REL_TYPE = "LAST_MESSAGE";
    public static final String DEFAULT_REL_TYPE_NEXT = "NEXT";
    public static final String DEFAULT_ID_PROP = "id";
    public static final String DEFAULT_MESSAGE_PROP = "message";
    public static final String DEFAULT_DATABASE_NAME = "neo4j";
    public static final long DEFAULT_WINDOW_VALUE = 10L;

    private final Driver driver;
    private final SessionConfig config;
    private final String memoryLabel;
    private final String messageLabel;
    private final String lastMessageRelType;
    private final String nextMessageRelType;
    private final String idProperty;
    private final String messageProperty;
    private final long window;
    
    // TODO - withBasicAuth like embeddingStore

    /**
     * TODO
     * 
     * @param driver
     * @param config
     * @param memoryLabel
     */
    
    // TODO - EXAMPLE WITH window(null)
    public Neo4jChatMemoryStore(final Driver driver, final SessionConfig config, final String memoryLabel, final String messageLabel, final String lastMessageRelType, String nextMessageRelType, String idProperty, String messageProperty, String databaseName, Long window) {
        /* required configs */
        this.driver = ensureNotNull(driver, "driver");

        /* optional configs */
        String dbName = getOrDefault(databaseName, DEFAULT_DATABASE_NAME);
        this.config = getOrDefault(config, SessionConfig.forDatabase(dbName));
        // TODO - sanitized label??
        this.memoryLabel = getOrDefault(memoryLabel, DEFAULT_MEMORY_LABEL);
        this.messageLabel = getOrDefault(messageLabel, DEFAULT_LABEL_MESSAGE);
        this.lastMessageRelType = getOrDefault(lastMessageRelType, DEFAULT_REL_TYPE);
        this.nextMessageRelType = getOrDefault(nextMessageRelType, DEFAULT_REL_TYPE_NEXT);
        this.idProperty = getOrDefault(idProperty, DEFAULT_ID_PROP);
        this.messageProperty = getOrDefault(messageProperty, DEFAULT_MESSAGE_PROP);
        // TODO - to string..
        this.window = getOrDefault(window, DEFAULT_WINDOW_VALUE);

        /* sanitize labels and property names, to prevent from Cypher Injections */
//        this.sanitizedLabel = sanitizeOrThrows(this.label, "label");
//        this.sanitizedRelType = sanitizeOrThrows(this.relType, "relType");
    }

    public static Builder builder() {
        return new Builder();
    }

    private void createSessionNode(final Session session, final Object memoryId) {
//        try (var session = session()) {
            final Map<String, Object> params = Map.of("label", memoryLabel, "window", window, "memoryId", memoryId);
            final String query = String.format("MERGE (s:%s {%s: $memoryId})", memoryLabel, idProperty);
            session.run(query, params);
//        }
    }

    @Override
    public List<ChatMessage> getMessages(final Object memoryIdObj) {
        final String memoryId = toMemoryIdString(memoryIdObj);
        try (var session = session()) {
            
            
            // TODO

//            ChatMessageDeserializer.messageFromJson(jsonString)

            String windowPar = this.window < 1 ? "" : Long.toString(this.window);
            final Map<String, Object> params = Map.of("label", memoryLabel, "window", windowPar, "memoryId", memoryId);

            final String query = String.format("""
                    MATCH (s:%1$s)-[:%2$s]->(lastNode)
                    WHERE s.%5$s = $memoryId MATCH p=(lastNode)<-[:%3$s*0..%4$s]-()
                    WITH p, length(p) AS length
                    ORDER BY length DESC LIMIT 1
                    UNWIND reverse(nodes(p)) AS node
                    RETURN node.%6$s AS msg""",
                    memoryLabel, lastMessageRelType, nextMessageRelType, windowPar, idProperty, messageProperty);

//            final String query = String.format("""
//                    MATCH (s:%s)-[:LAST_MESSAGE]->(lastNode)
//                    WHERE s.id = $memoryId MATCH p=(lastNode)<-[:NEXT*0..%s]-()
//                    WITH p, length(p) AS length
//                    ORDER BY length DESC LIMIT 1
//                    UNWIND reverse(nodes(p)) AS node
//                    RETURN node.message AS message""",
//                    memoryLabel, windowPar);
            
            final List<ChatMessage> messages = session.run(query, params)
                    .stream()
//                    .map(i -> i.get("message").asString(null))
                    .map(i -> i.get("msg").asString(null))
                    .filter(Objects::nonNull)
                    .map(ChatMessageDeserializer::messageFromJson)
                    .toList();
            return messages;
        }
        //return List.of();
    }

    @Override
    public void updateMessages(final Object memoryIdObj, final List<ChatMessage> messages) {
        final String memoryId = toMemoryIdString(memoryIdObj);
        /*
                try {
            refreshSchema();
        } catch (ClientException e) {
            if ("Neo.ClientError.Procedure.ProcedureNotFound".equals(e.code())) {
                throw new Neo4jException("Please ensure the APOC plugin is installed in Neo4j", e);
            }
            throw e;
        }
         */
        
        // todo - unwind???
        // final ChatMessage chatMessage = list.get(0);
        // chatMessage.type();

        ensureNotEmpty(messages, "messages");
        final List<Map<String, String >> messagesValues = messages.stream()
                .map(ChatMessageSerializer::messageToJson)
//                .map(i -> Map.of("message", i))
                .map(i -> Map.of(messageProperty, i))
                .toList();
        
//        if (messagesValues.isEmpty()) {
//            return;
//        }

        try (var session = session()) {
            createSessionNode(session, memoryId);
        }

        try (var session = session()) {
            // TODO - session id ??? is the memoryId <---

//            final String query = String.format("""
//                    MATCH (s:%s) WHERE s.id = $memoryId
//                    OPTIONAL MATCH (s)-[lastRel:LAST_MESSAGE]->(lastNode)
//                    CALL apoc.create.nodes([$label], $messages)
//                    YIELD node
//                    WITH collect(node) AS nodes, s, lastNode, lastRel
//                    CALL apoc.nodes.link(nodes, 'NEXT', {avoidDuplicates: true})
//                    WITH nodes[-1] AS new, s, lastNode, lastRel
//                    CREATE (s)-[:LAST_MESSAGE]->(new)
//                    //SET new += {type:$type, content:$content}
//                    WITH new, lastRel, lastNode WHERE lastNode IS NOT NULL
//                    
//                    // TODO TODO - change NEXT
//                    CREATE (lastNode)-[:NEXT]->(new)
//                    DELETE lastRel
//                    
//                    """, memoryLabel);
            final String query = String.format("""
                    MATCH (s:%1$s) WHERE s.%4$s = $memoryId
                    OPTIONAL MATCH (s)-[lastRel:%2$s]->(lastNode)
                    CALL apoc.create.nodes([$label], $messages)
                    YIELD node
                    WITH collect(node) AS nodes, s, lastNode, lastRel
                    CALL apoc.nodes.link(nodes, $relType, {avoidDuplicates: true})
                    WITH nodes[-1] AS new, s, lastNode, lastRel
                    CREATE (s)-[:%2$s]->(new)
                    //SET new += {type:$type, content:$content}
                    WITH new, lastRel, lastNode WHERE lastNode IS NOT NULL

                    // TODO TODO - change NEXT
                    CREATE (lastNode)-[:%3$s]->(new)
                    DELETE lastRel

                    """, memoryLabel, lastMessageRelType, nextMessageRelType, idProperty);     
            
//            final String query = String.format("""
//                    MATCH (s:%s) // WHERE s.id = $session_id
//                    OPTIONAL MATCH (s)-[lm:LAST_MESSAGE]->(lastNode)
//                    CREATE (s)-[:LAST_MESSAGE]->(new:Message)
//                    SET new += {type:$type, content:$content}
//                    WITH new, lm, lastNode WHERE lastNode IS NOT NULL
//                    CREATE (lastNode)-[:NEXT]->(new)
//                    DELETE lm""", label);

            final Map<String, Object> params = Map.of("memoryId", memoryId,
                    "relType", nextMessageRelType, 
                    // TODO - create MessageLabel and SessionLabel
                    "label", messageLabel, 
                    "messages", messagesValues);

            session.run(query, params);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteMessages(final Object memoryIdObj) {
        final String memoryId = toMemoryIdString(memoryIdObj);
        try (var session = session()) {
//            final String query = """
//                    MATCH (s:%s) WHERE s.id = $memoryId
//                    CALL apoc.path.spanningTree(start, {
//                      relationshipFilter: "NEXT>",
//                      uniqueness: "NODE_GLOBAL"
//                    })
//                    YIELD path
//                    RETURN path
//                    ORDER BY length(path) DESC
//                    LIMIT 1
//                    """;


//            final String query = String.format("""
//                    MATCH (s:%s)-[:LAST_MESSAGE]->(lastNode)
//                    WHERE s.id = $memoryId
//                    MATCH p=(lastNode)<-[:NEXT]-()
//                    WITH p, length(p) AS length ORDER BY length DESC LIMIT 1
//                    UNWIND nodes(p) as node DETACH DELETE node""", label);

//            final String query = String.format("""
//                    MATCH (s:%s)
//                    WHERE s.id = $memoryId
//                    OPTIONAL MATCH p=(s)-[lastRel:LAST_MESSAGE]->(lastNode)<-[:NEXT*0..]-()
//                    WITH s, p, length(p) AS length ORDER BY length DESC LIMIT 1
//                    //MATCH p=(lastNode)<-[:NEXT*0..]-()
//                    DETACH DELETE s, p""", memoryLabel);
//
//            final Map<String, Object> params = Map.of("memoryId", memoryId,
//                    "relType", nextMessageRelType,
//                    "label", memoryLabel);
            final String query = String.format("""
                    MATCH (s:%1$s)
                    WHERE s.%4$s = $memoryId
                    OPTIONAL MATCH p=(s)-[lastRel:%2$s]->(lastNode)<-[:%3$s*0..]-()
                    WITH s, p, length(p) AS length ORDER BY length DESC LIMIT 1
                    //MATCH p=(lastNode)<-[:%3$s*0..]-()
                    DETACH DELETE s, p""", memoryLabel, lastMessageRelType, nextMessageRelType, idProperty);

            final Map<String, Object> params = Map.of("memoryId", memoryId,
                    "relType", lastMessageRelType,
                    "label", memoryLabel);


            session.run(query, params);

            System.out.println("params = " + params);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private static String toMemoryIdString(Object memoryId) {
        boolean isNullOrEmpty = memoryId == null || memoryId.toString().trim().isEmpty();
        if (isNullOrEmpty) {
            throw new IllegalArgumentException("memoryId cannot be null or empty");
        }
        return memoryId.toString();
    }

    private Session session() {
        return this.driver.session(this.config);
    }

    public static class Builder {
        private Driver driver;
        private SessionConfig config;
        private String memoryLabel;
        private String messageLabel;
        private String lastMessageRelType;
        private String nextMessageRelType;
        private String idProperty;
        private String messageProperty;
        private String databaseName;
        private Long window;

        /**
         * TODO: various descriptions
         * @param driver
         * @return
         */
        public Builder driver(Driver driver) {
            this.driver = driver;
            return this;
        }

        public Builder config(SessionConfig config) {
            this.config = config;
            return this;
        }

        public Builder memoryLabel(String memoryLabel) {
            this.memoryLabel = memoryLabel;
            return this;
        }

        public Builder messageLabel(String messageLabel) {
            this.messageLabel = messageLabel;
            return this;
        }

        public Builder idProperty(String idProperty) {
            this.idProperty = idProperty;
            return this;
        }

        public Builder messageProperty(String messageProperty) {
            this.messageProperty = messageProperty;
            return this;
        }

        public Builder lastMessageRelType(String lastMessageRelType) {
            this.lastMessageRelType = lastMessageRelType;
            return this;
        }

        public Builder nextMessageRelType(String nextMessageRelType) {
            this.nextMessageRelType = nextMessageRelType;
            return this;
        }

        public Builder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        public Builder window(Long window) {
            this.window = window;
            return this;
        }

        public Neo4jChatMemoryStore build() {
            return new Neo4jChatMemoryStore(driver, config, memoryLabel, messageLabel, lastMessageRelType, nextMessageRelType, idProperty, messageProperty, databaseName, window);
        }
    }
    
}
