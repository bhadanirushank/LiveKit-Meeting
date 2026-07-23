-- Add publish_restricted and screen_share_allowed to participant_sessions
ALTER TABLE participant_sessions
ADD COLUMN publish_restricted BOOLEAN DEFAULT FALSE,
ADD COLUMN screen_share_allowed BOOLEAN DEFAULT FALSE;
