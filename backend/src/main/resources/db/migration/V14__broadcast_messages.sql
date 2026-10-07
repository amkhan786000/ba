-- Broadcast messages (Admin > Broadcast Messages) and the permission to use them.
-- Super Admin, Application Administrator and Office Coordinator get MESSAGES:VIEW / MESSAGES:EDIT;
-- other roles can be given it in Admin > Roles & Permissions.

CREATE TABLE IF NOT EXISTS broadcast_messages (
  broadcast_id BIGINT NOT NULL AUTO_INCREMENT,
  subject VARCHAR(200) NOT NULL,
  body TEXT NOT NULL,
  audience VARCHAR(500) NOT NULL,
  recipients INT NOT NULL DEFAULT 0,
  sent INT NOT NULL DEFAULT 0,
  skipped INT NOT NULL DEFAULT 0,
  failed INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'SENDING',
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (broadcast_id),
  CONSTRAINT fk_broadcast_messages_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_broadcast_messages_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

UPDATE roles
   SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'MESSAGES:VIEW', 'MESSAGES:EDIT')
 WHERE role_id IN (1, 2, 8)
   AND (permissions IS NULL OR FIND_IN_SET('MESSAGES:EDIT', permissions) = 0);
