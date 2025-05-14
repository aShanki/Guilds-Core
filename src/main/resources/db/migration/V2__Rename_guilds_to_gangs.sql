-- V2: Rename guilds to gangs and update all related columns and constraints

-- 1. Rename tables
ALTER TABLE guilds RENAME TO gangs;
ALTER TABLE guild_members RENAME TO gang_members;

-- 2. Rename columns in gang_members
ALTER TABLE gang_members RENAME COLUMN guild_id TO gang_id;

-- 3. Rename columns in invites
ALTER TABLE invites RENAME COLUMN guild_id TO gang_id;

-- 4. Rename columns in confirmations
ALTER TABLE confirmations RENAME COLUMN guild_id TO gang_id;

-- 5. Update foreign key in gang_members
-- Drop old foreign key (if exists)
ALTER TABLE gang_members DROP CONSTRAINT IF EXISTS guild_members_guild_id_fkey;
-- Add new foreign key
ALTER TABLE gang_members ADD CONSTRAINT gang_members_gang_id_fkey FOREIGN KEY (gang_id) REFERENCES gangs(id) ON DELETE CASCADE; 