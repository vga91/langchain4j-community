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
    public static final String DEFAULT_LABEL_MESSAGE = "Memory";
    public static final String DEFAULT_REL_TYPE = "LAST_MESSAGE";
    public static final String DEFAULT_REL_TYPE_NEXT = "NEXT";
    public static final String DEFAULT_DATABASE_NAME = "neo4j";
    public static final long DEFAULT_WINDOW_VALUE = 10L;

    private final Driver driver;
    private final SessionConfig config;
    private final String memoryLabel;
    private final String messageLabel;
    private final String relType;
    private final String nextMessageRelType;
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
    public Neo4jChatMemoryStore(final Driver driver, final SessionConfig config, final String memoryLabel, final String relType, String databaseName, Long window) {
        /* required configs */
        this.driver = ensureNotNull(driver, "driver");

        /* optional configs */
        String dbName = getOrDefault(databaseName, DEFAULT_DATABASE_NAME);
        this.config = getOrDefault(config, SessionConfig.forDatabase(dbName));
        // TODO - sanitized label??
        this.memoryLabel = getOrDefault(memoryLabel, DEFAULT_MEMORY_LABEL);
        this.messageLabel = getOrDefault(memoryLabel, DEFAULT_LABEL_MESSAGE);
        this.relType = getOrDefault(relType, DEFAULT_REL_TYPE);
        this.nextMessageRelType = getOrDefault(relType, DEFAULT_REL_TYPE_NEXT);
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
            final String query = String.format("MERGE (s:%s {id: $memoryId})", memoryLabel);
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
                    MATCH (s:%s)-[:LAST_MESSAGE]->(lastNode)
                    WHERE s.id = $memoryId MATCH p=(lastNode)<-[:NEXT*0..%s]-()
                    WITH p, length(p) AS length
                    ORDER BY length DESC LIMIT 1
                    UNWIND reverse(nodes(p)) AS node
                    RETURN node.message AS message""",
                    memoryLabel, windowPar);

            final List<ChatMessage> messages = session.run(query, params)
                    .stream()
                    .map(i -> i.get("message").asString(null))
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
                .map(i -> Map.of("message", i))
                .toList();
        
//        if (messagesValues.isEmpty()) {
//            return;
//        }

        try (var session = session()) {
            createSessionNode(session, memoryId);
        }

        try (var session = session()) {
            // TODO - session id ??? is the memoryId <---

            final String query = String.format("""
                    MATCH (s:%s) WHERE s.id = $memoryId
                    OPTIONAL MATCH (s)-[lastRel:LAST_MESSAGE]->(lastNode)
                    CALL apoc.create.nodes([$label], $messages)
                    YIELD node
                    WITH collect(node) AS nodes, s, lastNode, lastRel
                    CALL apoc.nodes.link(nodes, $relType, {avoidDuplicates: true})
                    WITH nodes[-1] AS new, s, lastNode, lastRel
                    CREATE (s)-[:LAST_MESSAGE]->(new)
                    //SET new += {type:$type, content:$content}
                    WITH new, lastRel, lastNode WHERE lastNode IS NOT NULL
                    
                    // TODO TODO - change NEXT
                    CREATE (lastNode)-[:NEXT]->(new)
                    DELETE lastRel
                    
                    """, messageLabel);     
            
//            final String query = String.format("""
//                    MATCH (s:%s) // WHERE s.id = $session_id
//                    OPTIONAL MATCH (s)-[lm:LAST_MESSAGE]->(lastNode)
//                    CREATE (s)-[:LAST_MESSAGE]->(new:Message)
//                    SET new += {type:$type, content:$content}
//                    WITH new, lm, lastNode WHERE lastNode IS NOT NULL
//                    CREATE (lastNode)-[:NEXT]->(new)
//                    DELETE lm""", label);

            final Map<String, Object> params = Map.of("memoryId", memoryId,
                    "relType", relType, 
                    // TODO - create MessageLabel and SessionLabel
                    "label", messageLabel, 
                    "messages", messagesValues);

            session.run(query, params);
        } catch (Exception e) {
            System.out.println("e = " + e);
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

            final String query = String.format("""
                    MATCH (s:%s)
                    WHERE s.id = $memoryId
                    OPTIONAL MATCH p=(s)-[lastRel:LAST_MESSAGE]->(lastNode)<-[:NEXT*0..]-()
                    WITH s, p, length(p) AS length ORDER BY length DESC LIMIT 1
                    //MATCH p=(lastNode)<-[:NEXT*0..]-()
                    DETACH DELETE s, p""", memoryLabel);

            final Map<String, Object> params = Map.of("memoryId", memoryId,
                    "relType", relType,
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
        private String label;
        private String relType;
        private String databaseName;
        private Long window;

        public Builder driver(Driver driver) {
            this.driver = driver;
            return this;
        }

        public Builder config(SessionConfig config) {
            this.config = config;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder relType(String relType) {
            this.relType = relType;
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
            return new Neo4jChatMemoryStore(driver, config, label, relType, databaseName, window);
        }
    }
    
}


/*


    def __init__(
        self,
        session_id: Union[str, int],
        url: Optional[str] = None,
        username: Optional[str] = None,
        password: Optional[str] = None,
        database: str = "neo4j",
        node_label: str = "Session",
        window: int = 3,
        *,
        graph: Optional[Neo4jGraph] = None,
    ):
        try:
            import neo4j
        except ImportError:
            raise ImportError(
                "Could not import neo4j python package. "
                "Please install it with `pip install neo4j`."
            )

        # Make sure session id is not null
        if not session_id:
            raise ValueError("Please ensure that the session_id parameter is provided")

        # Graph object takes precedent over env or input params
        if graph:
            self._driver = graph._driver
            self._database = graph._database
        else:
            # Handle if the credentials are environment variables
            url = get_from_dict_or_env({"url": url}, "url", "NEO4J_URI")
            username = get_from_dict_or_env(
                {"username": username}, "username", "NEO4J_USERNAME"
            )
            password = get_from_dict_or_env(
                {"password": password}, "password", "NEO4J_PASSWORD"
            )
            database = get_from_dict_or_env(
                {"database": database}, "database", "NEO4J_DATABASE", "neo4j"
            )

            self._driver = neo4j.GraphDatabase.driver(url, auth=(username, password))
            self._database = database
            # Verify connection
            try:
                self._driver.verify_connectivity()
            except neo4j.exceptions.ServiceUnavailable:
                raise ValueError(
                    "Could not connect to Neo4j database. "
                    "Please ensure that the url is correct"
                )
            except neo4j.exceptions.AuthError:
                raise ValueError(
                    "Could not connect to Neo4j database. "
                    "Please ensure that the username and password are correct"
                )
        self._session_id = session_id
        self._node_label = node_label
        self._window = window
        # Create session node
        self._driver.execute_query(
            f"MERGE (s:`{self._node_label}` {{id:$session_id}})",
            {"session_id": self._session_id},
        ).summary

    @property
    def messages(self) -> List[BaseMessage]:
        """Retrieve the messages from Neo4j"""
        query = (
            f"MATCH (s:`{self._node_label}`)-[:LAST_MESSAGE]->(lastNode) "
            "WHERE s.id = $session_id MATCH p=(lastNode)<-[:NEXT*0.."
            f"{self._window*2}]-() WITH p, length(p) AS length "
            "ORDER BY length DESC LIMIT 1 UNWIND reverse(nodes(p)) AS node "
            "RETURN {data:{content: node.content}, type:node.type} AS result"
        )
        records, _, _ = self._driver.execute_query(
            query, {"session_id": self._session_id}
        )

        messages = messages_from_dict([el["result"] for el in records])
        return messages

    @messages.setter
    def messages(self, messages: List[BaseMessage]) -> None:
        raise NotImplementedError(
            "Direct assignment to 'messages' is not allowed."
            " Use the 'add_messages' instead."
        )

    def add_message(self, message: BaseMessage) -> None:
        """Append the message to the record in Neo4j"""
        query = (
            f"MATCH (s:`{self._node_label}`) WHERE s.id = $session_id "
            "OPTIONAL MATCH (s)-[lm:LAST_MESSAGE]->(lastNode) "
            "CREATE (s)-[:LAST_MESSAGE]->(new:Message) "
            "SET new += {type:$type, content:$content} "
            "WITH new, lm, lastNode WHERE lastNode IS NOT NULL "
            "CREATE (lastNode)-[:NEXT]->(new) "
            "DELETE lm"
        )
        self._driver.execute_query(
            query,
            {
                "type": message.type,
                "content": message.content,
                "session_id": self._session_id,
            },
        ).summary

    def clear(self) -> None:
        """Clear session memory from Neo4j"""
        query = (
            f"MATCH (s:`{self._node_label}`)-[:LAST_MESSAGE]->(lastNode) "
            "WHERE s.id = $session_id MATCH p=(lastNode)<-[:NEXT]-() "
            "WITH p, length(p) AS length ORDER BY length DESC LIMIT 1 "
            "UNWIND nodes(p) as node DETACH DELETE node;"
        )
        self._driver.execute_query(query, {"session_id": self._session_id}).summary

    def __del__(self) -> None:
        if self._driver:
            self._driver.close()

 */
