-- Users
-- :name create-user :! :n
-- :doc Creates a new user
insert into users (uid, username, password_hash, email) values (:uid, :username, :password_hash, :email);

-- :name find-user-by-username :? :1
-- :doc retrieves a user by username
select * from users where username = :username limit 1;

-- :name update-user! :! :n
-- :doc updates a user's details
update users set password_hash = :password_hash, email = :email, updated_at = current_timestamp where username = :username;

-- :name delete-user! :! :n
-- :doc deletes a user by uid
delete from users where uid = :uid;

-- Core
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
