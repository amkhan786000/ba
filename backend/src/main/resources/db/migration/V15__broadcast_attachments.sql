-- Files attached to broadcast messages (Admin > Broadcast Messages), stored in the uploads folder under file_path.
-- Separate from V14 because V14 was already applied on some databases before attachments were added.

CREATE TABLE IF NOT EXISTS broadcast_attachments (
  attachment_id BIGINT NOT NULL AUTO_INCREMENT,
  broadcast_id BIGINT NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  file_path VARCHAR(255) NOT NULL,
  size_bytes BIGINT NOT NULL DEFAULT 0,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (attachment_id),
  CONSTRAINT fk_broadcast_attachments_broadcast FOREIGN KEY (broadcast_id) REFERENCES broadcast_messages(broadcast_id),
  CONSTRAINT fk_broadcast_attachments_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_broadcast_attachments_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
