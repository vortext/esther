create table memory (
    gid                     text primary key,
    uid                     text not null,
    sid                     text not null,
    data                    text not null,
    iv                      iv not null,
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
    uid                     text not null,
    data                    text not null,
    iv                      iv not null,
    seen                    integer default 1,
    last_seen               timestamp with time zone default current_timestamp,
    unique(uid,data,iv)
)
--;;
create index memory_keyword_access on memory_keyword(uid);
--;;
create index memory_keyword_last_seen on memory_keyword(uid, last_seen);
--;;
create index memory_keyword_uid_seen on memory_keyword(uid, seen);
