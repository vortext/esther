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
