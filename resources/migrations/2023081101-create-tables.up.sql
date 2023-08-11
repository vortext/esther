CREATE TABLE memory (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    gid                     TEXT NOT NULL UNIQUE,
    emoji                   VARCHAR,
    prediction              TEXT,
    question                TEXT,
    summary                 TEXT,
    image_prompt            TEXT,
    image_uri               VARCHAR,
    created                 TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
--;;
CREATE TABLE entries (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    gid                     TEXT NOT NULL,
    uid                     TEXT NOT NULL,
    content                 TEXT,
    memory                  TEXT,
    created                 TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (memory) REFERENCES memory(gid)
);
