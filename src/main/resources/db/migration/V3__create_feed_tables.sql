
-- 1. 피드 테이블
CREATE TABLE feeds (
                       id UUID NOT NULL,
                       user_id UUID NOT NULL,
                       weather_id UUID,
                       content TEXT,
                       created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                       updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,

                       CONSTRAINT pk_feeds PRIMARY KEY (id)
);

-- 2. 댓글 테이블
CREATE TABLE comments (
                          id UUID NOT NULL,
                          feed_id UUID NOT NULL,
                          user_id UUID NOT NULL,
                          content TEXT NOT NULL,
                          created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                          updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,

                          CONSTRAINT pk_comments PRIMARY KEY (id),
                          CONSTRAINT fk_comments_feed
                              FOREIGN KEY (feed_id) REFERENCES feeds(id)
);

-- 3. 피드 좋아요 테이블
CREATE TABLE feed_likes (
                            id UUID NOT NULL,
                            feed_id UUID NOT NULL,
                            user_id UUID NOT NULL,
                            created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                            updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,

                            CONSTRAINT pk_feed_likes PRIMARY KEY (id),
                            CONSTRAINT fk_feed_likes_feed
                                FOREIGN KEY (feed_id) REFERENCES feeds(id),
                            CONSTRAINT uk_feed_likes_feed_user
                                UNIQUE (feed_id, user_id)
);

-- 4. 피드 의상 연결 테이블
CREATE TABLE feed_clothes (
                              id UUID NOT NULL,
                              feed_id UUID NOT NULL,
                              clothes_id UUID NOT NULL,
                              created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                              updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,

                              CONSTRAINT pk_feed_clothes PRIMARY KEY (id),
                              CONSTRAINT fk_feed_clothes_feed
                                  FOREIGN KEY (feed_id) REFERENCES feeds(id),
                              CONSTRAINT uk_feed_clothes_feed_clothes
                                  UNIQUE (feed_id, clothes_id)
);

-- 5. 조회 성능을 위한 인덱스
CREATE INDEX idx_feeds_created_at
    ON feeds(created_at);

CREATE INDEX idx_feeds_user_id
    ON feeds(user_id);

CREATE INDEX idx_comments_feed_id
    ON comments(feed_id);

CREATE INDEX idx_feed_likes_feed_id
    ON feed_likes(feed_id);

CREATE INDEX idx_feed_clothes_feed_id
    ON feed_clothes(feed_id);
