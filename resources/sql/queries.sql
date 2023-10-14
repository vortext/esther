-- Users
-- :name create-user :! :1
-- :doc Creates a new user
insert into users (username, password_hash, data, iv)
values (:username, :password_hash, :data, :iv);

-- :name find-user-by-username :? :1
-- :doc retrieves a user by username
select * from users where username = :username limit 1;

-- Memory
-- :name push-memory :! :n
-- :doc Insert a single memory
insert into memory (gid, uid, data, iv, conversation)
values (:gid, :uid, :data, :iv, :conversation)

-- :name last-n-memories :? :*
-- :doc Get the last memories (from new to old)
select gid, data, iv from memory
where uid = :uid
order by created desc limit :n;

-- :name last-n-conversation-memories :? :*
-- :doc Get the last memories (from new to old) where conversation = 1
select gid, data, iv from memory
where uid = :uid
  and conversation = 1
order by created desc limit :n;

-- :name todays-non-archived-memories :? :*
-- :doc Get the memories for the current day (from new to old) not archived
select gid, data, iv from memory
where uid = :uid
  and created_date = date('now')
  and archived = 0
order by created desc;

-- :name archive-memories :! :n
-- :doc Archive memories by setting the archived flag to 1 for the given gids
update memory
set archived = 1
where gid in (:v*:gids);

-- :name archive-todays-memories :! :n
-- :doc Archive all memories for the given uid for the current date
update memory
set archived = 1
where uid = :uid
  and created_date = date('now');

-- :name see-keyword :! :1
-- :doc increments the seen counter of the keyword for uid
insert into memory_keyword (uid, fingerprint, data, iv)
values(:uid, :fingerprint, :data, :iv)
on conflict(uid, fingerprint) do update set seen=seen+1, last_seen=current_timestamp;

-- :name associate-keyword :! :n
-- :doc associates a memory gid with a keyword fingerprint
insert into memory_keyword_lookup (gid, fingerprint)
values (:gid, :fingerprint)

-- :name frecency-keywords :? :* :1
-- :doc returns the frecency of keywords for a uid with exponential decay
select uid,
       fingerprint,
       iv,
       data,
       seen as frequency,
       (julianday('now') - julianday(last_seen)) * 86400 as recency,
       seen / exp(:lambda * (julianday('now') - julianday(last_seen)) * 86400) as frecency
from memory_keyword
where uid = :uid
order by frecency desc limit :n;

-- :name forget-all-memory :! :1
-- :doc Forget all memories for a uid
delete from memory where uid = :uid;

-- :name forget-todays-memory :! :1
-- :doc Forget todays memories for a uid
delete from memory where uid = :uid and created_date = date('now');

-- :name forget-all-memory-keywords :! :1
-- :doc Forget all memory keywords for a uid
delete from memory_keyword where uid = :uid;
