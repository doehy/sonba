INSERT INTO region (
    city,
    district,
    created_at,
    modified_at
) VALUES (
    '서울특별시',
    '강남구',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO category (
    category_id,
    category_name,
    created_at,
    modified_at
) VALUES
    (1, '주방', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, '타일', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
