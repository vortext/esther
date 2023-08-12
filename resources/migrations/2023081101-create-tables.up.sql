CREATE TABLE memory (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    gid                     TEXT NOT NULL UNIQUE,
    uid                     TEXT NOT NULL,
    content                 TEXT,
    emoji                   VARCHAR,
    keywords                TEXT,
    image_prompt            TEXT,
    image_uri               VARCHAR,
    created                 TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
