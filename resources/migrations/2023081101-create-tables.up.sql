create table memory (
    gid            text primary key,
    uid            text not null,
    sid            text not null,
    data           text not null,
    iv             text not null,
    created        timestamp with time zone default current_timestamp,
    created_date   text default (strftime('%y-%m-%d', current_timestamp)),
    archived       boolean not null default 0
);
--;;
create index memory_created on memory(uid, created);
--;;
create index memory_created_date on memory(uid, created_date);
--;;
create index memory_uid on memory(uid);
--;;

create table memory_keyword (
    uid                     text not null,
    fingerprint             text primary key,
    data                    text not null,
    iv                      iv not null,
    seen                    integer default 1,
    last_seen               timestamp with time zone default current_timestamp,
    unique(uid,fingerprint)
)
--;;
create index memory_keyword_access on memory_keyword(uid);
--;;
create index memory_keyword_last_seen on memory_keyword(uid, last_seen);
--;;
create index memory_keyword_uid_seen on memory_keyword(uid, seen);
