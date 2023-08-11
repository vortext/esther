CREATE TABLE memory (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    gid                     TEXT NOT NULL UNIQUE,
    content                 TEXT,
    emoji                   VARCHAR,
    prediction              TEXT,
    keywords                TEXT,
    question                TEXT,
    image_prompt            TEXT,
    image_uri               VARCHAR,
    created                 TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
