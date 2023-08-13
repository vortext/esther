-- :name push-memory :! :n
-- :doc Insert a single memory
insert into memory (gid, uid, sid, content)
values (:gid, :uid, :sid, :content)

-- :name last-n-memories :? :*
-- :doc Get the last entries (from new to old)
select gid, content from memory
where uid = :uid
order by created desc limit :n;

-- :name see-keyword :! :1
-- :doc increments the seen counter of the keyword for uid
insert into memory_keyword (uid, keyword)
values(:uid, :keyword)
on conflict(uid, keyword) do update set seen=seen+1, last_seen=current_timestamp;
