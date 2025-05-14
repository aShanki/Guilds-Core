-- V1: Initial schema creation based on TODO.MD 3.6

-- Guilds table
CREATE TABLE IF NOT EXISTS guilds ( -- Added IF NOT EXISTS for safety, although Flyway manages versions
    id CHAR(36) PRIMARY KEY,
    name VARCHAR(32) UNIQUE NOT NULL,
    leader_uuid CHAR(36) NOT NULL,
    description VARCHAR(24) -- Adjusted length
);

-- Guild Members table
CREATE TABLE IF NOT EXISTS guild_members ( -- Added IF NOT EXISTS
    guild_id CHAR(36) NOT NULL,
    player_uuid CHAR(36) NOT NULL,
    PRIMARY KEY (guild_id, player_uuid),
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
);
-- Removed joined_at column and index

-- Invites table
CREATE TABLE IF NOT EXISTS invites ( -- Added IF NOT EXISTS
    invited_uuid CHAR(36) PRIMARY KEY,
    guild_id CHAR(36) NOT NULL,
    inviter_uuid CHAR(36) NOT NULL,
    timestamp BIGINT NOT NULL
    -- Removed FOREIGN KEY constraint temporarily as it might cause issues if guild is deleted before invite is handled.
    -- Consider adding a cleanup task or handling potential foreign key violations.
);
-- Removed timestamp index and foreign key

-- Confirmations table
CREATE TABLE IF NOT EXISTS confirmations ( -- Added IF NOT EXISTS
    player_uuid CHAR(36) PRIMARY KEY, -- Changed Primary Key
    type VARCHAR(32) NOT NULL, -- Adjusted length
    guild_id CHAR(36), -- Kept nullable as per V1
    timestamp BIGINT NOT NULL
);
-- Removed composite primary key and timestamp index 