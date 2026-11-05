-- 의상 속성 정의: 선택 가능한 값은 순서를 보존하는 JSONB 배열로 저장한다.
CREATE TABLE clothes_attribute_defs (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    selectable_values JSONB NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,

    CONSTRAINT pk_clothes_attribute_defs PRIMARY KEY (id),
    -- 프로토타입과 동일하게 속성명은 대소문자를 구분해 중복을 판정한다.
    CONSTRAINT uk_clothes_attribute_defs_name UNIQUE (name),
    CONSTRAINT chk_clothes_attribute_defs_selectable_values
        CHECK (CASE
            WHEN jsonb_typeof(selectable_values) = 'array'
                THEN jsonb_array_length(selectable_values) > 0
            ELSE FALSE
        END)
);

-- 사용자 의상: 이미지가 없는 등록을 허용하므로 image_url은 NULL을 허용한다.
CREATE TABLE clothes (
    id UUID NOT NULL,
    owner_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    image_url VARCHAR(500),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,

    CONSTRAINT pk_clothes PRIMARY KEY (id),
    CONSTRAINT fk_clothes_owner_id
        FOREIGN KEY (owner_id) REFERENCES users (id)
);

-- 의상별 선택 속성: 한 의상에는 같은 속성 정의를 한 번만 연결한다.
CREATE TABLE clothes_attributes (
    id UUID NOT NULL,
    clothes_id UUID NOT NULL,
    definition_id UUID NOT NULL,
    value VARCHAR(100) NOT NULL,

    CONSTRAINT pk_clothes_attributes PRIMARY KEY (id),
    CONSTRAINT fk_clothes_attributes_clothes_id
        FOREIGN KEY (clothes_id) REFERENCES clothes (id),
    CONSTRAINT fk_clothes_attributes_definition_id
        FOREIGN KEY (definition_id) REFERENCES clothes_attribute_defs (id),
    CONSTRAINT uk_clothes_attributes_clothes_definition
        UNIQUE (clothes_id, definition_id)
);

CREATE INDEX idx_clothes_owner_id
    ON clothes (owner_id);

CREATE INDEX idx_clothes_attributes_definition_id
    ON clothes_attributes (definition_id);
