-- Custom wording for the emails the application sends (Admin > Email Templates). A row replaces the built-in
-- subject and text of one kind of email (service.EmailType); without a row the built-in wording is used.

CREATE TABLE IF NOT EXISTS email_templates (
  template_key VARCHAR(60) NOT NULL,
  subject VARCHAR(300) NOT NULL,
  body TEXT NOT NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (template_key),
  CONSTRAINT fk_email_templates_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_email_templates_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Super Admin, Application Administrator and Office Coordinator may view, add and edit templates
-- (deleting one is for the Super Admin only, checked in the application).
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'EMAIL_TEMPLATES:VIEW', 'EMAIL_TEMPLATES:EDIT')
 WHERE role_id IN (1, 2, 8) AND FIND_IN_SET('EMAIL_TEMPLATES:VIEW', COALESCE(permissions, '')) = 0;
