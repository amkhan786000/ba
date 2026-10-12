-- One person, several roles: users.role_id stays the person's main role (their home screen); this table holds any
-- further roles, e.g. a Super Admin who is also a Sponsor, or a graduate who became a chapter lead. Email and phone
-- stay unique on users: a person is one record, never one record per role.
CREATE TABLE IF NOT EXISTS user_additional_roles (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  role_id INT NOT NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_user_additional_roles (user_id, role_id),
  KEY idx_user_additional_roles_role (role_id),
  CONSTRAINT fk_user_additional_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_user_additional_roles_role FOREIGN KEY (role_id) REFERENCES roles(role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
