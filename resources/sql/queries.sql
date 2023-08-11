-- :name push-memory :! :n
-- :doc Insert a single memory
insert into memory (gid, emoji, prediction, question, summary, image_prompt)
values (:gid, :emoji, :prediction, :question, :summary, :image_prompt)

-- :name push-entry :! :n
-- :doc Insert a single entry
insert into entries (gid, uid, content, memory)
values (:gid, :uid, :content, :memory)

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

-- :name last-10-entries :? :*
-- :doc Get the last entries (from new to old)
select content from entries order by id desc limit 10;
