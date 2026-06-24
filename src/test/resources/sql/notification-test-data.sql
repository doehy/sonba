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

INSERT INTO notification_type (
    notification_type_id,
    type,
    template,
    created_at,
    modified_at
) VALUES (
    1,
    'INQUIRY',
    '%s님이 새 문의를 보냈습니다.',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
