-- :name push-memory :! :n
-- :doc Insert a single memory
insert into memory (gid, uid, sid, content, emoji, energy, keywords, image_prompt)
values (:gid, :uid, :sid, :content, :emoji, :energy, :keywords, :image_prompt)

-- :name last-10-memories :? :*
-- :doc Get the last entries (from new to old)
select gid, content, keywords, image_prompt from memory order by created desc limit 10;

-- :name inspect-memory
-- :doc Inspects the memory at this point
select created, emoji, energy, keywords from memory order by created desc limit 5;

-- :name see-keyword :! :1
-- :doc increments the seen counter of the keyword for uid
insert into memory_keyword (uid, keyword)
values(:uid, :keyword)
on conflict(uid, keyword) do update set seen=seen+1, last_seen=current_timestamp;
