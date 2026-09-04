CREATE TABLE spring_ai_chat_memory (
    conversation_id VARCHAR(36) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(10) NOT NULL,
    "timestamp" TIMESTAMP NOT NULL,
    sequence_id BIGINT NOT NULL,
    PRIMARY KEY (conversation_id, sequence_id)
);

CREATE TABLE vector_store (
    id UUID DEFAULT uuid_generate_v4() NOT NULL PRIMARY KEY,
    content TEXT,
    metadata JSON,
    embedding VECTOR
);

CREATE INDEX spring_ai_vector_index
    ON vector_store USING hnsw (embedding vector_cosine_ops);
