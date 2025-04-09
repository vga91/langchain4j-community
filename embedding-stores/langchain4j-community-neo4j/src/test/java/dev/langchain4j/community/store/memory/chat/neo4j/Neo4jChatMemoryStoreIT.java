package dev.langchain4j.community.store.memory.chat.neo4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.community.store.memory.chat.neo4j.Neo4jChatMemoryStore.DEFAULT_WINDOW_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
public class Neo4jChatMemoryStoreIT {

    protected static final String USERNAME = "neo4j";
    protected static final String ADMIN_PASSWORD = "adminPass";
    protected static final String LABEL_TO_SANITIZE = "Label ` to \\ sanitize";
    protected static final String REL_TO_SANITIZE = "Rel ` to \\ sanitize";
    protected static final String NEO4J_VERSION = System.getProperty("neo4jVersion", "5.26");
    
//    protected static Session session;
    protected static Driver driver;
    private Neo4jChatMemoryStore memoryStore;
    // TODO
    private final String messageId = "someUserId";
    
    @Container
    protected static Neo4jContainer<?> neo4jContainer =
            new Neo4jContainer<>(DockerImageName.parse("neo4j:" + NEO4J_VERSION))
                    .withPlugins("apoc")
                    .withAdminPassword(ADMIN_PASSWORD);

    @BeforeAll
    static void beforeAll() {
        //neo4jContainer.start();
        driver = GraphDatabase.driver(neo4jContainer.getBoltUrl(), AuthTokens.basic(USERNAME, ADMIN_PASSWORD));
//        session = driver.session();
    }

    @BeforeEach
    void setUp() {
        memoryStore = Neo4jChatMemoryStore.builder()
                .driver(driver)
                //.host(neo4j.getHost())
                .build();
//        memoryStore.deleteMessages(userId);
//        List<ChatMessage> messages = memoryStore.getMessages(userId);
//        assertThat(messages).isEmpty();
    }
    
    @AfterEach
    void afterEach() {
        memoryStore.deleteMessages(messageId);
        List<ChatMessage> messages = memoryStore.getMessages(messageId);
        assertThat(messages).isEmpty();

        final List<Record> nodes = driver.session().run("MATCH (n) RETURN n").list();
        assertThat(nodes).isEmpty();
    }
    

    @AfterAll
    static void afterAll() {
//        session.close();
        driver.close();
        //neo4jContainer.stop();
    }


    @Test
    void should_set_messages_into_neo4j() {
        // given
        List<ChatMessage> messages = memoryStore.getMessages(messageId);
        assertThat(messages).isEmpty();

        
        List<ChatMessage> chatMessages = createChatMessages();
        memoryStore.updateMessages(messageId, chatMessages);
        
        List<Content> userMsgContents = List.of(new ImageContent("someCatImageUrl"));
        final List<ChatMessage> chatNewMessages = List.of(new UserMessage("What do you see in this image?", userMsgContents));
        memoryStore.updateMessages(messageId, chatNewMessages);

        // then
        messages = memoryStore.getMessages(messageId);
        assertThat(messages).hasSize(4);

        // TODO - check message contents
        
        // TODO - check entities
    }

    @Test
    void should_delete_messages_from_neo4j() {
        // given
        List<ChatMessage> chatMessages = createChatMessages();
        memoryStore.updateMessages(messageId, chatMessages);
        List<ChatMessage> messages = memoryStore.getMessages(messageId);
        assertThat(messages).hasSize(3);
        assertThat(messages).isEqualTo(chatMessages);
        // TODO - check message contents

        // when
        memoryStore.deleteMessages(messageId);

        // then
        messages = memoryStore.getMessages(messageId);
        assertThat(messages).isEmpty();
    }
    
    @Test
    void should_only_delete_messages_with_correct_memory_id() {
        final String anotherMessageId = "anotherId";
        final List<ChatMessage> chatMessages1 = createChatMessages();
        memoryStore.updateMessages(messageId, chatMessages1);

        final List<ChatMessage> chatMessages2 = createChatMessages();
        memoryStore.updateMessages(anotherMessageId, chatMessages2);

        List<ChatMessage> messagesBefore = memoryStore.getMessages(messageId);
        assertThat(messagesBefore).hasSize(3);
        assertThat(messagesBefore).isEqualTo(chatMessages1);


        List<ChatMessage> messages2Before = memoryStore.getMessages(anotherMessageId);
        assertThat(messages2Before).hasSize(3);
        assertThat(messages2Before).isEqualTo(chatMessages2);


        memoryStore.deleteMessages(messageId);

        List<ChatMessage> messagesAfterDelete = memoryStore.getMessages(messageId);
        assertThat(messagesAfterDelete).isEmpty();

        List<ChatMessage> messages2AfterDelete = memoryStore.getMessages(anotherMessageId);
        assertThat(messages2AfterDelete).hasSize(3);
        assertThat(messages2AfterDelete).isEqualTo(chatMessages2);
        
        memoryStore.deleteMessages(anotherMessageId);
        List<ChatMessage> messagesAfter2ndDelete = memoryStore.getMessages(anotherMessageId);
        assertThat(messagesAfter2ndDelete).isEmpty();
    }

    // TODO - custom label, relType
    @Test
    void should_only_delete_messages_with_custom_labels_and_rel_type() {
          // todo - change with LABEL_TO_SANITIZE and REL_TO_SANITIZE
        final String labelToSanitize = "LABEL_TO_SANITIZE";
        final String relToSanitize = "REL_TO_SANITIZE";
        Neo4jChatMemoryStore memoryStore = Neo4jChatMemoryStore.builder()
                .driver(driver)
                .label(labelToSanitize)
                .relType(relToSanitize)
                .build();
        final List<ChatMessage> chatMessages1 = createChatMessages();
        memoryStore.updateMessages(messageId, chatMessages1);

        List<ChatMessage> messages = memoryStore.getMessages(messageId);
        assertThat(messages).hasSize(3);
        assertThat(messages).isEqualTo(chatMessages1);
      // todo - check entities
      //    
        final String query = "MATCH p=(s:%s)-[lastRel:LAST_MESSAGE]->(lastNode)<-[:]-()<-[]-() RETURN p";
        final List<Record> list = driver.session().run(query)
                .list();
        assertThat(list).hasSize(1);
    }

    // TODO - window
    @Test
    void should_only_search_first_three_messages_besides_last_message() {
        final long window = 3L;
        Neo4jChatMemoryStore memoryStore = Neo4jChatMemoryStore.builder()
                .driver(driver)
                .window(window)
                .build();
        
        final List<ChatMessage> chatMessages1 = new ArrayList<>();
        chatMessages1.addAll(createChatMessages());
        chatMessages1.addAll(createChatMessages());
        chatMessages1.addAll(createChatMessages());
        chatMessages1.addAll(createChatMessages());
        memoryStore.updateMessages(messageId, chatMessages1);
        
        List<ChatMessage> messages = memoryStore.getMessages(messageId);
        assertThat(messages).hasSize((int) (window + 1));
        
        final List<ChatMessage> expectedChatMessages = new ArrayList<>();
        expectedChatMessages.addAll(createChatMessages());
        expectedChatMessages.add(new SystemMessage("foo"));
        assertThat(messages).isEqualTo(expectedChatMessages);
    }

    @Test
    void should_only_search_first_ten_messages_besides_last_message() {
        Neo4jChatMemoryStore memoryStore = Neo4jChatMemoryStore.builder()
                .driver(driver)
                .build();

        final List<ChatMessage> chatMessages1 = new ArrayList<>();
        chatMessages1.addAll(createChatMessages());
        chatMessages1.addAll(createChatMessages());
        chatMessages1.addAll(createChatMessages());
        chatMessages1.addAll(createChatMessages());
        memoryStore.updateMessages(messageId, chatMessages1);
        
        List<ChatMessage> messages = memoryStore.getMessages(messageId);
        assertThat(messages).hasSize((int) (DEFAULT_WINDOW_VALUE + 1));
        
        final List<ChatMessage> expectedChatMessages = new ArrayList<>();
        expectedChatMessages.addAll(createChatMessages());
        expectedChatMessages.addAll(createChatMessages());
        expectedChatMessages.addAll(createChatMessages());
        expectedChatMessages.add(new SystemMessage("foo"));
        assertThat(messages).isEqualTo(expectedChatMessages);
    }

    @Test
    void should_search_all_messages() {
        Neo4jChatMemoryStore memoryStore = Neo4jChatMemoryStore.builder()
                .driver(driver)
                .window(0L)
                .build();

        final List<ChatMessage> chatMessages1 = new ArrayList<>();
        chatMessages1.addAll(createChatMessages());
        chatMessages1.addAll(createChatMessages());
        chatMessages1.addAll(createChatMessages());
        chatMessages1.addAll(createChatMessages());
        memoryStore.updateMessages(messageId, chatMessages1);

        List<ChatMessage> messages = memoryStore.getMessages(messageId);
        assertThat(messages).hasSize(12);
        assertThat(messages).isEqualTo(chatMessages1);
    }
    
    @Test
    void getMessages_memoryId_null() {
        assertThatThrownBy(() -> memoryStore.getMessages(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

    @Test
    void getMessages_memoryId_empty() {
        assertThatThrownBy(() -> memoryStore.getMessages("   "))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

    @Test
    void updateMessages_messages_null() {
        assertThatThrownBy(() -> memoryStore.updateMessages(messageId, null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("messages cannot be null or empty");
    }

    @Test
    void updateMessages_messages_empty() {
        List<ChatMessage> chatMessages = new ArrayList<>();
        assertThatThrownBy(() -> memoryStore.updateMessages(messageId, chatMessages))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("messages cannot be null or empty");
    }

    @Test
    void updateMessages_memoryId_null() {
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));
        assertThatThrownBy(() -> memoryStore.updateMessages(null, chatMessages))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

    @Test
    void updateMessages_memoryId_empty() {
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage("You are a large language model working with Langchain4j"));
        assertThatThrownBy(() -> memoryStore.updateMessages("   ", chatMessages))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

    @Test
    void deleteMessages_memoryId_null() {
        assertThatThrownBy(() -> memoryStore.deleteMessages(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

    @Test
    void deleteMessages_memoryId_empty() {
        assertThatThrownBy(() -> memoryStore.deleteMessages("   "))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("memoryId cannot be null or empty");
    }

    @Test
    void constructor_driver_null() {
        assertThatThrownBy(() -> Neo4jChatMemoryStore.builder()
                .driver(null)
                .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("driver cannot be null");
    }

    private static List<ChatMessage> createChatMessages() {
        return new ArrayList<>(List.of(
                new SystemMessage("foo"), 
                new UserMessage("bar"), 
                new AiMessage("baz")
        ));
    }
}
