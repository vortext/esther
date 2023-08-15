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

-- :name see-keyword :! :1
-- :doc increments the seen counter of the keyword for uid
insert into memory_keyword (uid, fingerprint, data, iv)
values(:uid, :fingerprint, :data, :iv)
on conflict(uid, fingerprint) do update set seen=seen+1, last_seen=current_timestamp;

-- :name frecency-keywords :? :* :1
-- :doc returns the frecency of keywords for a uid with exponential decay
SELECT uid,
       fingerprint,
       iv,
       data,
       seen AS frequency,
       (julianday('now') - julianday(last_seen)) * 86400 AS recency,
       seen * exp(:lambda * (julianday('now') - julianday(last_seen)) * 86400) AS frecency
FROM memory_keyword
WHERE uid = :uid
ORDER BY frecency ASC LIMIT :n;
