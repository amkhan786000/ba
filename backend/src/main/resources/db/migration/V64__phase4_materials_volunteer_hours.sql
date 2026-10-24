-- Phase 4:
-- 1. Books & material distribution: items, stock received (from which donor) and hand-outs to scholarship students,
--    coaching students and Rahbar-e-Banaat girls. Stock left = received - handed out.
-- 2. Volunteer hours: hours given by volunteers, mentors and alumni, logged by staff or by the person (then approved),
--    with a certificate.
-- Re-runnable.

CREATE TABLE IF NOT EXISTS material_items (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(150) NOT NULL,
  category VARCHAR(12) NOT NULL,
  unit VARCHAR(20) NULL,
  notes VARCHAR(500) NULL,
  active BIT NOT NULL DEFAULT 1,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS material_receipts (
  id BIGINT NOT NULL AUTO_INCREMENT,
  item_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  received_on DATE NOT NULL,
  donor_name VARCHAR(150) NULL,
  donor_user_id BIGINT NULL,
  note VARCHAR(500) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_material_receipts_item (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS material_distributions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  item_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  given_on DATE NOT NULL,
  recipient_kind VARCHAR(10) NOT NULL,
  recipient_id BIGINT NOT NULL,
  note VARCHAR(500) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_material_distributions_item (item_id),
  KEY idx_material_distributions_recipient (recipient_kind, recipient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One row per volunteer per day / activity. The person is a website volunteer (volunteer_id) or a Rahbar user
-- (user_id: a mentor, an alumnus, a sponsor...). Self-logged rows wait for the office's approval.
CREATE TABLE IF NOT EXISTS volunteer_hours (
  id BIGINT NOT NULL AUTO_INCREMENT,
  volunteer_id BIGINT NULL,
  user_id BIGINT NULL,
  activity VARCHAR(12) NOT NULL,
  worked_on DATE NOT NULL,
  hours DECIMAL(5,2) NOT NULL,
  description VARCHAR(500) NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'APPROVED',
  self_logged BIT NOT NULL DEFAULT 0,
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME(6) NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_volunteer_hours_volunteer (volunteer_id),
  KEY idx_volunteer_hours_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Permissions (changeable in Roles; the Super Admin always has them): the office, chapter leads (their chapter's
-- students) and coaching coordinators (their centre's students) hand out materials and log volunteer hours.
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'MATERIALS:VIEW', 'MATERIALS:EDIT')
 WHERE (role_id IN (2, 8) OR LOWER(role_name) IN ('chapter lead', 'rcc coordinator'))
   AND FIND_IN_SET('MATERIALS:VIEW', COALESCE(permissions, '')) = 0;
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'VOLUNTEER_HOURS:VIEW', 'VOLUNTEER_HOURS:EDIT')
 WHERE (role_id IN (2, 8) OR LOWER(role_name) IN ('chapter lead', 'rcc coordinator'))
   AND FIND_IN_SET('VOLUNTEER_HOURS:VIEW', COALESCE(permissions, '')) = 0;
