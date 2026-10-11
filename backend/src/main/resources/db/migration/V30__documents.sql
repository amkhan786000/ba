-- Admin > Documents: the organisation's by-laws and other documents, kept as text (pasted in, not uploaded), in
-- categories the admin manages. Each change keeps the previous version in document_revisions.

CREATE TABLE IF NOT EXISTS document_categories (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(500) NULL,
  display_order INT NOT NULL DEFAULT 0,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_document_categories_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- content: the text as pasted (line breaks kept; Markdown such as ## headings and **bold** is shown formatted).
-- archived: kept but hidden from the normal list.
CREATE TABLE IF NOT EXISTS documents (
  id BIGINT NOT NULL AUTO_INCREMENT,
  category_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  content MEDIUMTEXT NOT NULL,
  effective_date DATE NULL,
  archived BIT NOT NULL DEFAULT 0,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_documents_category (category_id),
  CONSTRAINT fk_documents_category FOREIGN KEY (category_id) REFERENCES document_categories(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- The version before each change (title and content as they were), newest last.
CREATE TABLE IF NOT EXISTS document_revisions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  document_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  content MEDIUMTEXT NOT NULL,
  effective_date DATE NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_document_revisions_document (document_id),
  CONSTRAINT fk_document_revisions_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Who may read / add / edit documents and manage their categories: sections on Roles & permissions.
-- Application Administrator (2): everything; Office Coordinator (8): read, add and edit documents.
-- The Super Admin always has every key (and is the only one who can delete a document).
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'DOCUMENTS:VIEW', 'DOCUMENTS:EDIT', 'DOCUMENT_CATEGORIES:VIEW', 'DOCUMENT_CATEGORIES:EDIT')
 WHERE role_id = 2 AND FIND_IN_SET('DOCUMENTS:VIEW', COALESCE(permissions, '')) = 0;
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'DOCUMENTS:VIEW', 'DOCUMENTS:EDIT')
 WHERE role_id = 8 AND FIND_IN_SET('DOCUMENTS:VIEW', COALESCE(permissions, '')) = 0;
