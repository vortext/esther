pragma foreign_keys = on;
--;;
create table memory (
    gid            text primary key,
    uid            text not null,
    data           text not null,
    iv             text not null,
    created        timestamp with time zone default current_timestamp,
    created_date   text default (date('now')),
    archived       boolean not null default 0,
    conversation   boolean not null default 0
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
    iv                      text not null,
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
--;;

create table memory_keyword_lookup (
    gid            text not null,
    fingerprint    text not null,
    primary key (gid, fingerprint),
    foreign key (gid) references memory(gid) on delete cascade,
    foreign key (fingerprint) references memory_keyword(fingerprint) on delete cascade
)
--;;
create index memory_keyword_lookup_gid on memory_keyword_lookup(gid);
--;;
create index memory_keyword_lookup_fingerpint on memory_keyword_lookup(fingerprint);
--;;
create trigger aftermemorydelete
after delete on memory
for each row
begin
delete from memory_keyword where fingerprint in (select fingerprint from memory_keyword_lookup where gid = old.gid);
delete from memory_keyword_lookup where gid = old.gid;
end;
