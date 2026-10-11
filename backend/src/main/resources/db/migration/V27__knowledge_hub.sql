-- Media & Knowledge Hub on biharanjuman.com: blog posts get a category (managed by blog publishers in
-- Admin > Website > Blog > Categories) and optional extras: a PDF to download, a video / audio link and the
-- person interviewed / subject of a case study.

CREATE TABLE IF NOT EXISTS web_blog_categories (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  slug VARCHAR(100) NOT NULL,
  description VARCHAR(600) NULL,
  display_order INT NOT NULL DEFAULT 0,
  active BIT NOT NULL DEFAULT 1,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_web_blog_categories_slug (slug),
  CONSTRAINT fk_web_blog_categories_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_web_blog_categories_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO web_blog_categories (name, slug, description, display_order, active, created_at, updated_at)
SELECT * FROM (
  SELECT 'Articles' AS name, 'articles' AS slug,
         'Stories, opinions and news from the community.' AS description, 1 AS display_order, 1 AS active, NOW(6) AS c, NOW(6) AS u UNION ALL
  SELECT 'Research', 'research', 'Research about education in Bihar and Jharkhand.', 2, 1, NOW(6), NOW(6) UNION ALL
  SELECT 'Case Studies', 'case-studies', 'How our projects changed things, one place or person at a time.', 3, 1, NOW(6), NOW(6) UNION ALL
  SELECT 'Community Reports', 'community-reports', 'Reports on the community and on our programmes.', 4, 1, NOW(6), NOW(6) UNION ALL
  SELECT 'Interview Series', 'interviews', 'Conversations with students, teachers, sponsors and leaders.', 5, 1, NOW(6), NOW(6)
) d WHERE NOT EXISTS (SELECT 1 FROM web_blog_categories);

ALTER TABLE web_blog_posts
  ADD COLUMN category_id BIGINT NULL AFTER topic,
  ADD COLUMN attachment_url VARCHAR(255) NULL,
  ADD COLUMN attachment_name VARCHAR(200) NULL,
  ADD COLUMN media_url VARCHAR(500) NULL,
  ADD COLUMN subject_name VARCHAR(200) NULL,
  ADD COLUMN subject_details VARCHAR(600) NULL,
  ADD CONSTRAINT fk_web_blog_posts_category FOREIGN KEY (category_id) REFERENCES web_blog_categories(id) ON DELETE SET NULL;

-- Posts written so far are articles.
UPDATE web_blog_posts SET category_id = (SELECT id FROM web_blog_categories WHERE slug = 'articles') WHERE category_id IS NULL;

-- Who adds and changes categories: its own section on Roles & permissions. Application Administrator by default
-- (the Super Admin always has every key).
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'WEBSITE_BLOG_CATEGORIES:VIEW', 'WEBSITE_BLOG_CATEGORIES:EDIT')
 WHERE role_id = 2 AND FIND_IN_SET('WEBSITE_BLOG_CATEGORIES:VIEW', COALESCE(permissions, '')) = 0;
