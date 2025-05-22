package dev.langchain4j.community.rag.content.retriever.neo4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;

<<<<<<<< HEAD:content-retrievers/langchain4j-community-neo4j-retriever/src/test/java/dev/langchain4j/community/rag/content/retriever/neo4j/Neo4jRetrieverBaseTest.java
public class Neo4jRetrieverBaseTest {
========
public class Neo4jContainerBaseTest {
>>>>>>>> upstream/main:content-retrievers/langchain4j-community-neo4j-retriever/src/test/java/dev/langchain4j/community/rag/content/retriever/neo4j/Neo4jContainerBaseTest.java

    protected static final String NEO4J_VERSION = System.getProperty("neo4jVersion", "5.26");

    protected static Driver driver;
<<<<<<<< HEAD:content-retrievers/langchain4j-community-neo4j-retriever/src/test/java/dev/langchain4j/community/rag/content/retriever/neo4j/Neo4jRetrieverBaseTest.java
    protected static Neo4jGraph graph;
========
>>>>>>>> upstream/main:content-retrievers/langchain4j-community-neo4j-retriever/src/test/java/dev/langchain4j/community/rag/content/retriever/neo4j/Neo4jContainerBaseTest.java

    @Container
    protected static final Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:" + NEO4J_VERSION)
            .withoutAuthentication()
            .withPlugins("apoc");

    @BeforeAll
    static void beforeAll() {
        neo4jContainer.start();
<<<<<<<< HEAD:content-retrievers/langchain4j-community-neo4j-retriever/src/test/java/dev/langchain4j/community/rag/content/retriever/neo4j/Neo4jRetrieverBaseTest.java

        driver = GraphDatabase.driver(neo4jContainer.getBoltUrl(), AuthTokens.none());
        graph = Neo4jGraph.builder().driver(driver).build();
========
        driver = GraphDatabase.driver(neo4jContainer.getBoltUrl(), AuthTokens.none());
>>>>>>>> upstream/main:content-retrievers/langchain4j-community-neo4j-retriever/src/test/java/dev/langchain4j/community/rag/content/retriever/neo4j/Neo4jContainerBaseTest.java
    }

    @AfterAll
    static void afterAll() {
<<<<<<<< HEAD:content-retrievers/langchain4j-community-neo4j-retriever/src/test/java/dev/langchain4j/community/rag/content/retriever/neo4j/Neo4jRetrieverBaseTest.java
        graph.close();
        driver.close();
        
        neo4jContainer.stop();
    }

    @BeforeEach
    void beforeEach() {
        initDb();
    }

    public void initDb() {}

========
        driver.close();
        neo4jContainer.stop();
    }

>>>>>>>> upstream/main:content-retrievers/langchain4j-community-neo4j-retriever/src/test/java/dev/langchain4j/community/rag/content/retriever/neo4j/Neo4jContainerBaseTest.java
    @AfterEach
    void afterEach() {
        try (Session session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n");
        }
<<<<<<<< HEAD:content-retrievers/langchain4j-community-neo4j-retriever/src/test/java/dev/langchain4j/community/rag/content/retriever/neo4j/Neo4jRetrieverBaseTest.java

========
>>>>>>>> upstream/main:content-retrievers/langchain4j-community-neo4j-retriever/src/test/java/dev/langchain4j/community/rag/content/retriever/neo4j/Neo4jContainerBaseTest.java
    }
}
