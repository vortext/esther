create table users (
    username text primary_key,
    email text default "NA",
    password_hash text not null,
    vault text not null default "",
    created_at timestamp default current_timestamp,
    updated_at timestamp default current_timestamp
);
--;;
create index users_username_idx on users(username);
