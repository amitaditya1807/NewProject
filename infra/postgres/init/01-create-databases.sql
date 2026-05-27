CREATE DATABASE auth_db;
CREATE DATABASE user_db;

\connect auth_db
CREATE EXTENSION IF NOT EXISTS pgcrypto;

\connect user_db
CREATE EXTENSION IF NOT EXISTS pgcrypto;
