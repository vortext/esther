create table users (
username text    primary key,
email text       default "NA",
password_hash    text not null,
vault_data       blob not null,
vault_iv         blob not null,
created_at       timestamp default current_timestamp,
updated_at       timestamp default current_timestamp);
--;;
create index users_username_idx on users(username);
