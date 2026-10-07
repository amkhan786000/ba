-- Longer chapter lead contact details (several phone numbers, long email addresses).
ALTER TABLE chapters MODIFY lead_phone VARCHAR(100) NULL;
ALTER TABLE chapters MODIFY lead_email VARCHAR(255) NULL;
