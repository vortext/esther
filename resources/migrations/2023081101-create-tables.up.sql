create table memory (
    id                      integer primary key autoincrement,
    gid                     text not null unique,
    uid                     text not null,
    sid                     text not null,
    content                 text,
    emoji                   varchar,
    energy                  float,
    keywords                text,
    image_prompt            text,
    image_uri               varchar,
    image_description       text,
    created                 timestamp with time zone default current_timestamp
);
--;;
create index memory_created on memory(uid, created);
--;;
create index memory_access on memory(uid, sid);
--;;
create index memory_uid on memory(uid);
--;;

create table memory_keyword (
    id                      integer primary key autoincrement,
    uid                     text not null,
    keyword                 varchar,
    seen                    integer default 1,
    last_seen               timestamp with time zone default current_timestamp,
    unique(uid,keyword)
)
--;;
create index memory_keyword_access on memory_keyword(uid);
--;;
create index memory_keyword_last_seen on memory_keyword(uid, last_seen);
--;;
create index memory_keyword_uid_seen on memory_keyword(uid, seen);
