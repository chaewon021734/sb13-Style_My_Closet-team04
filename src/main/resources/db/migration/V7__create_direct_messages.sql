
CREATE TABLE direct_messages (
                                 id UUID PRIMARY KEY,
                                 sender_id UUID NOT NULL,
                                 receiver_id UUID NOT NULL,
                                 content TEXT NOT NULL,
                                 created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                                 updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,

                                 CONSTRAINT fk_direct_messages_sender
                                     FOREIGN KEY (sender_id)
                                         REFERENCES users(id),

                                 CONSTRAINT fk_direct_messages_receiver
                                     FOREIGN KEY (receiver_id)
                                         REFERENCES users(id)
);

CREATE INDEX idx_direct_messages_sender_id
    ON direct_messages(sender_id);

CREATE INDEX idx_direct_messages_receiver_id
    ON direct_messages(receiver_id);

CREATE INDEX idx_direct_messages_conversation
    ON direct_messages(sender_id, receiver_id, created_at);
