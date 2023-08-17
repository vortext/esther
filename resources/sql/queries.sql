-- Users
-- :name create-user :! :1
-- :doc Creates a new user
insert into users (username, password_hash, vault) values (:username, :password_hash, :vault);

-- :name find-user-by-username :? :1
-- :doc retrieves a user by username
select * from users where username = :username limit 1;

-- Memory
-- :name push-memory :! :n
-- :doc Insert a single memory
insert into memory (gid, uid, sid, data, iv)
values (:gid, :uid, :sid, :data, :iv)

-- :name last-n-memories :? :*
-- :doc Get the last entries (from new to old)
select gid, data, iv from memory
where uid = :uid
order by created desc limit :n;

-- :name todays-memories :? :*
-- :doc Get the memories for the current day (from new to old)
select gid, data, iv from memory
where uid = :uid
  and created_date = date('now')
order by created desc;


-- :name see-keyword :! :1
-- :doc increments the seen counter of the keyword for uid
insert into memory_keyword (uid, fingerprint, data, iv)
values(:uid, :fingerprint, :data, :iv)
on conflict(uid, fingerprint) do update set seen=seen+1, last_seen=current_timestamp;

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

-- :name clear-memory :! :1
-- :doc clears all memories for a uid
delete from memory where uid = :uid;

-- :name clear-memory-keywords :! :1
-- :doc clears all memory keywords for a uid
delete from memory_keyword where uid = :uid;
