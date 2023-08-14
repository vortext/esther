create table users (
    uid varchar primary key,
    username text not null unique,
    password_hash text not null,
    email text not null unique,
    created_at timestamp default current_timestamp,
    updated_at timestamp default current_timestamp
);
--;;
create index users_username_idx on users(username);