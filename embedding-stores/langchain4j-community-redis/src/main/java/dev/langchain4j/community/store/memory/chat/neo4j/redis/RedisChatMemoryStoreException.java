package dev.langchain4j.community.store.memory.chat.neo4j.redis;

public class RedisChatMemoryStoreException extends RuntimeException {

    public RedisChatMemoryStoreException(String message) {
        super(message);
    }
}
