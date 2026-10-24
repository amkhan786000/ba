-- Phase 3:
-- 1. Careers: job / internship openings and who is interested, placements of students and graduates, employers,
--    and career guidance requests.
-- 2. Events & workshops: events for an audience, registrations (portal sign-up or added by staff) and attendance.
-- 3. Helpline: issues raised by students and sponsors, handled by the office (confidential ones: Super Admin and
--    whoever they assign).
-- Re-runnable.

CREATE TABLE IF NOT EXISTS employers (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(200) NOT NULL,
  sector VARCHAR(100) NULL,
  city VARCHAR(100) NULL,
  contact_person VARCHAR(150) NULL,
  contact_phone VARCHAR(30) NULL,
  contact_email VARCHAR(150) NULL,
  website VARCHAR(300) NULL,
  notes VARCHAR(1000) NULL,
  active BIT NOT NULL DEFAULT 1,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS career_opportunities (
  id BIGINT NOT NULL AUTO_INCREMENT,
  kind VARCHAR(12) NOT NULL,
  title VARCHAR(200) NOT NULL,
  organisation VARCHAR(200) NOT NULL,
  employer_id BIGINT NULL,
  location VARCHAR(150) NULL,
  description VARCHAR(3000) NULL,
  eligibility VARCHAR(500) NULL,
  apply_link VARCHAR(500) NULL,
  last_date DATE NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'OPEN',
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_career_opportunities_status (status, last_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS career_interests (
  id BIGINT NOT NULL AUTO_INCREMENT,
  opportunity_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  status VARCHAR(15) NOT NULL DEFAULT 'INTERESTED',
  note VARCHAR(500) NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_career_interest (opportunity_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS placements (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  kind VARCHAR(15) NOT NULL,
  organisation VARCHAR(200) NOT NULL,
  employer_id BIGINT NULL,
  role_title VARCHAR(150) NULL,
  location VARCHAR(150) NULL,
  start_date DATE NULL,
  salary_band VARCHAR(30) NULL,
  opportunity_id BIGINT NULL,
  added_by_student BIT NOT NULL DEFAULT 0,
  verified_at DATETIME(6) NULL,
  verified_by BIGINT NULL,
  note VARCHAR(500) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_placements_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS guidance_requests (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  topic VARCHAR(20) NOT NULL,
  message VARCHAR(2000) NOT NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'OPEN',
  assigned_to BIGINT NULL,
  session_at DATETIME(6) NULL,
  outcome VARCHAR(2000) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_guidance_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL,
  kind VARCHAR(15) NOT NULL,
  description VARCHAR(3000) NULL,
  starts_at DATETIME(6) NOT NULL,
  ends_at DATETIME(6) NULL,
  venue VARCHAR(300) NULL,
  online_link VARCHAR(500) NULL,
  audience VARCHAR(10) NOT NULL,
  chapter_id BIGINT NULL,
  rcc_center_id BIGINT NULL,
  capacity INT NULL,
  registration_open BIT NOT NULL DEFAULT 1,
  status VARCHAR(10) NOT NULL DEFAULT 'SCHEDULED',
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_events_starts (starts_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Who comes: a user (student, sponsor...), a coaching student, or a guest (e.g. a parent) by name and phone.
CREATE TABLE IF NOT EXISTS event_registrations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_id BIGINT NOT NULL,
  user_id BIGINT NULL,
  coaching_student_id BIGINT NULL,
  guest_name VARCHAR(150) NULL,
  guest_phone VARCHAR(20) NULL,
  self_registered BIT NOT NULL DEFAULT 0,
  attended BIT NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_event_registrations_event (event_id),
  KEY idx_event_registrations_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS helpline_tickets (
  id BIGINT NOT NULL AUTO_INCREMENT,
  raised_by BIGINT NOT NULL,
  raiser_kind VARCHAR(10) NOT NULL,
  category VARCHAR(15) NOT NULL,
  subject VARCHAR(200) NOT NULL,
  confidential BIT NOT NULL DEFAULT 0,
  status VARCHAR(12) NOT NULL DEFAULT 'OPEN',
  priority VARCHAR(10) NOT NULL DEFAULT 'NORMAL',
  assigned_to BIGINT NULL,
  resolved_at DATETIME(6) NULL,
  last_activity_at DATETIME(6) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_helpline_raised_by (raised_by),
  KEY idx_helpline_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- The conversation; internal notes are for staff only.
CREATE TABLE IF NOT EXISTS helpline_messages (
  id BIGINT NOT NULL AUTO_INCREMENT,
  ticket_id BIGINT NOT NULL,
  author_id BIGINT NOT NULL,
  body VARCHAR(4000) NOT NULL,
  internal BIT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_helpline_messages_ticket (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Permissions (changeable in Roles; the Super Admin always has them):
-- Careers: office manages; chapter leads see (their chapter's students' placements).
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'CAREERS:VIEW', 'CAREERS:EDIT')
 WHERE role_id IN (2, 8) AND FIND_IN_SET('CAREERS:VIEW', COALESCE(permissions, '')) = 0;
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'CAREERS:VIEW')
 WHERE LOWER(role_name) = 'chapter lead' AND FIND_IN_SET('CAREERS:VIEW', COALESCE(permissions, '')) = 0;
-- Events: office, chapter leads (their chapter) and coaching coordinators (their center) run events; coaching teachers see them.
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'EVENTS:VIEW', 'EVENTS:EDIT')
 WHERE (role_id IN (2, 8) OR LOWER(role_name) IN ('chapter lead', 'rcc coordinator'))
   AND FIND_IN_SET('EVENTS:VIEW', COALESCE(permissions, '')) = 0;
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'EVENTS:VIEW')
 WHERE LOWER(role_name) = 'coaching teacher' AND FIND_IN_SET('EVENTS:VIEW', COALESCE(permissions, '')) = 0;
-- Helpline: the office handles issues.
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'HELPLINE:VIEW', 'HELPLINE:EDIT')
 WHERE role_id IN (2, 8) AND FIND_IN_SET('HELPLINE:VIEW', COALESCE(permissions, '')) = 0;
