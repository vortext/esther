-- :name push-memory :! :n
-- :doc Insert a single memory
insert into memory (gid, uid, content, emoji, keywords, image_prompt)
values (:gid, :uid, :content, :emoji, :keywords, :image_prompt)

-- A ":result" value of ":*" specifies a vector of records
-- (as hashmaps) will be returned
-- :name last-memories :? :*
-- :doc Get the last memories
select * from memory
order by id desc limit 5

-- A ":result" value of ":*" specifies a vector of records
-- (as hashmaps) will be returned
-- :name first-memories :? :*
-- :doc Get the last memories
select * from memory
order by id limit 5

-- A ":result" value of ":*" specifies a vector of records
-- (as hashmaps) will be returned
-- :name a-few-random-memories :? :*
-- :doc Get a few random memories
select * from memory order by random() limit 5;

-- :name last-10-memories :? :*
-- :doc Get the last entries (from new to old)
select content, keywords, image_prompt from memory order by id desc limit 10;
