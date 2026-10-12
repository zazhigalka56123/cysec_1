CREATE TABLE users (
    username VARCHAR(64) PRIMARY KEY,
    password VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL
);

CREATE TABLE authorities (
    username VARCHAR(64) NOT NULL REFERENCES users(username),
    authority VARCHAR(50) NOT NULL,
    UNIQUE (username, authority)
);

CREATE TABLE notes (
    id UUID PRIMARY KEY,
    username VARCHAR(64) NOT NULL REFERENCES users(username),
    title VARCHAR(120) NOT NULL,
    content VARCHAR(2000) NOT NULL
);

CREATE INDEX notes_username_idx ON notes(username);
