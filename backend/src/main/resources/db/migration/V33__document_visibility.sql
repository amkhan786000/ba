-- Admin > Documents: who can read is set on each document instead of by permission. visibility: PUBLIC (every
-- signed-in user, all roles and portals) or ROLES (only the roles in visible_role_ids, comma-separated role ids).
-- People with "Documents" edit always see every document. "Documents" on Roles & permissions now means adding and
-- editing documents. Existing documents stay visible only to their editors until someone chooses who may read them.
ALTER TABLE documents
  ADD COLUMN visibility VARCHAR(10) NOT NULL DEFAULT 'ROLES',
  ADD COLUMN visible_role_ids VARCHAR(500) NULL;
