-- The Bihar Anjuman website (biharanjuman.com) is managed here: Admin > Website. The website itself is only the
-- public pages; it reads this content through /api/public/website/** and sends its forms there.
-- Tables are prefixed web_. Starter content (projects, a news item, a blog post) is the website's old seed data.

CREATE TABLE IF NOT EXISTS web_projects (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(255) NOT NULL,
  slug VARCHAR(255) NOT NULL,
  category VARCHAR(255) NULL,
  summary VARCHAR(600) NOT NULL,
  description TEXT NULL,
  sponsorship_info VARCHAR(255) NULL,
  image_url VARCHAR(255) NULL,
  website_url VARCHAR(255) NULL,
  gallery_album VARCHAR(80) NULL,
  display_order INT NOT NULL DEFAULT 0,
  featured BIT NOT NULL DEFAULT 0,
  active BIT NOT NULL DEFAULT 1,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_web_projects_slug (slug),
  CONSTRAINT fk_web_projects_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_web_projects_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS web_news (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  slug VARCHAR(255) NOT NULL,
  kind VARCHAR(10) NOT NULL DEFAULT 'NEWS',
  summary VARCHAR(600) NULL,
  content TEXT NULL,
  event_date DATE NULL,
  location VARCHAR(255) NULL,
  chapter_id BIGINT NULL,
  image_url VARCHAR(255) NULL,
  published BIT NOT NULL DEFAULT 1,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_web_news_slug (slug),
  CONSTRAINT fk_web_news_chapter FOREIGN KEY (chapter_id) REFERENCES chapters(chapter_id) ON DELETE SET NULL,
  CONSTRAINT fk_web_news_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_web_news_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- source: STAFF (written in Admin) or PUBLIC ("Write for us" on the website, author_email set)
CREATE TABLE IF NOT EXISTS web_blog_posts (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL,
  slug VARCHAR(200) NOT NULL,
  topic VARCHAR(80) NOT NULL,
  author VARCHAR(120) NULL,
  author_email VARCHAR(160) NULL,
  source VARCHAR(10) NOT NULL DEFAULT 'STAFF',
  summary VARCHAR(600) NULL,
  content MEDIUMTEXT NOT NULL,
  cover_image_url VARCHAR(255) NULL,
  project_id BIGINT NULL,
  review_requested BIT NOT NULL DEFAULT 0,
  published BIT NOT NULL DEFAULT 0,
  published_at DATETIME(6) NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_web_blog_posts_slug (slug),
  KEY idx_web_blog_posts_published (published, published_at),
  CONSTRAINT fk_web_blog_posts_project FOREIGN KEY (project_id) REFERENCES web_projects(id) ON DELETE SET NULL,
  CONSTRAINT fk_web_blog_posts_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_web_blog_posts_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- The website shows only the first name and initial unless show_full_name; the photo only if show_photo.
CREATE TABLE IF NOT EXISTS web_stories (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL,
  slug VARCHAR(200) NOT NULL,
  person_name VARCHAR(120) NOT NULL,
  show_full_name BIT NOT NULL DEFAULT 0,
  show_photo BIT NOT NULL DEFAULT 1,
  consent_given BIT NOT NULL DEFAULT 0,
  photo_url VARCHAR(255) NULL,
  summary VARCHAR(600) NULL,
  story MEDIUMTEXT NOT NULL,
  now_where VARCHAR(200) NULL,
  story_year INT NULL,
  project_id BIGINT NULL,
  chapter_id BIGINT NULL,
  rcc_center_id BIGINT NULL,
  review_requested BIT NOT NULL DEFAULT 0,
  published BIT NOT NULL DEFAULT 0,
  published_at DATETIME(6) NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_web_stories_slug (slug),
  CONSTRAINT fk_web_stories_project FOREIGN KEY (project_id) REFERENCES web_projects(id) ON DELETE SET NULL,
  CONSTRAINT fk_web_stories_chapter FOREIGN KEY (chapter_id) REFERENCES chapters(chapter_id) ON DELETE SET NULL,
  CONSTRAINT fk_web_stories_center FOREIGN KEY (rcc_center_id) REFERENCES rcc_centers(rcc_center_id) ON DELETE SET NULL,
  CONSTRAINT fk_web_stories_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_web_stories_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Photos: the files are in <upload-dir>/website/; unpublished ones (blog / story photos) stay out of the gallery.
CREATE TABLE IF NOT EXISTS web_gallery_images (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL,
  caption VARCHAR(1000) NULL,
  album VARCHAR(80) NULL,
  taken_on DATE NULL,
  display_order INT NOT NULL DEFAULT 0,
  published BIT NOT NULL DEFAULT 1,
  file_name VARCHAR(64) NOT NULL,
  content_type VARCHAR(40) NULL,
  size_bytes BIGINT NOT NULL DEFAULT 0,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_web_gallery_published (published, album),
  CONSTRAINT fk_web_gallery_images_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_web_gallery_images_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Worldwide directory; visitors sign up (approved = 0) and staff approve them.
CREATE TABLE IF NOT EXISTS web_members (
  id BIGINT NOT NULL AUTO_INCREMENT,
  full_name VARCHAR(255) NOT NULL,
  home_state VARCHAR(20) NOT NULL,
  home_district VARCHAR(255) NULL,
  current_country VARCHAR(255) NOT NULL,
  current_city VARCHAR(255) NULL,
  profession VARCHAR(255) NULL,
  organization VARCHAR(255) NULL,
  email VARCHAR(255) NOT NULL,
  phone VARCHAR(255) NULL,
  chapter_id BIGINT NULL,
  show_contact BIT NOT NULL DEFAULT 0,
  approved BIT NOT NULL DEFAULT 0,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_web_members_email (email),
  CONSTRAINT fk_web_members_chapter FOREIGN KEY (chapter_id) REFERENCES chapters(chapter_id) ON DELETE SET NULL,
  CONSTRAINT fk_web_members_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_web_members_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Contact form messages.
CREATE TABLE IF NOT EXISTS web_messages (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  email VARCHAR(255) NOT NULL,
  phone VARCHAR(40) NULL,
  topic VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
  subject VARCHAR(200) NOT NULL,
  message TEXT NOT NULL,
  handled BIT NOT NULL DEFAULT 0,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_web_messages_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_web_messages_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Custom pages (menu items on the website).
CREATE TABLE IF NOT EXISTS web_pages (
  id BIGINT NOT NULL AUTO_INCREMENT,
  slug VARCHAR(120) NOT NULL,
  title VARCHAR(200) NOT NULL,
  body MEDIUMTEXT NULL,
  published BIT NOT NULL DEFAULT 0,
  show_in_menu BIT NOT NULL DEFAULT 0,
  menu_order INT NOT NULL DEFAULT 0,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_web_pages_slug (slug),
  CONSTRAINT fk_web_pages_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_web_pages_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Edited text of the website's fixed pages (keys such as "home.hero.title"), per language; missing = built-in text.
CREATE TABLE IF NOT EXISTS web_content (
  id BIGINT NOT NULL AUTO_INCREMENT,
  content_key VARCHAR(100) NOT NULL,
  lang VARCHAR(5) NOT NULL,
  text_value MEDIUMTEXT NOT NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_web_content_key_lang (content_key, lang),
  CONSTRAINT fk_web_content_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_web_content_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Urdu / Hindi versions of record fields (PROJECT, NEWS, PAGE, STORY).
CREATE TABLE IF NOT EXISTS web_translations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  entity_type VARCHAR(40) NOT NULL,
  entity_id BIGINT NOT NULL,
  lang VARCHAR(5) NOT NULL,
  field VARCHAR(40) NOT NULL,
  text_value MEDIUMTEXT NOT NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_web_translations (entity_type, entity_id, lang, field),
  CONSTRAINT fk_web_translations_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_web_translations_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Every saved version of editable text. target: site:<key>, rec:<TYPE>:<id>:<field> or tr:<TYPE>:<id>:<field>.
CREATE TABLE IF NOT EXISTS web_revisions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  target VARCHAR(200) NOT NULL,
  lang VARCHAR(5) NOT NULL,
  text_value MEDIUMTEXT NULL,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_web_revisions_target (target, lang, id),
  CONSTRAINT fk_web_revisions_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_web_revisions_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO web_projects (name, slug, category, summary, description, sponsorship_info, website_url, display_order, featured, active, created_at, updated_at) VALUES
('Rahbar Coaching Centres', 'rahbar-coaching-centres', 'Education',
 'Free coaching for Grade 8, 9 and 10 students of government schools so that they pass the 10th board exam and continue into higher education.',
 'Rahbar Coaching Centres (RCC) help economically deprived sections strengthen their place in the Indian mainstream educational system. The goal is a coaching centre in every district of Bihar and Jharkhand, reducing school drop-outs and raising the number of students who go on to higher and technical education.',
 'Sponsor a centre: Rs. 15,000 per month (reaches at least 60 families)', NULL, 1, TRUE, TRUE, NOW(6), NOW(6)),
('Rahbar Diploma & Paramedical Sponsorship', 'rahbar-diploma-paramedical-sponsorship', 'Scholarship',
 'Sponsorship for the poorest 10th-pass students from Rahbar Coaching Centres to pursue diploma engineering and paramedical studies.',
 'This campaign raises the percentage of minority students in technical education and adds to the nation''s technological empowerment. Sponsorship reaches the beneficiary directly (100%) through the Zero-Overhead Model.',
 'Sponsor a student: Rs. 3,500 per month', NULL, 2, TRUE, TRUE, NOW(6), NOW(6)),
('Rahbar-e-Banaat School-cum-Madarsa', 'rahbar-e-banaat', 'Girls Education',
 'A residential Madrasa-cum-School for girls teaching both the Madrasa curriculum and the Bihar Board syllabus.',
 'At Grade 10 the girls sit both examinations and then go on to specialise in a field of their interest. "Educate a man, you educate one man. Educate a woman, you educate a family."',
 'Educate a girl: Rs. 500 per month', NULL, 3, TRUE, TRUE, NOW(6), NOW(6)),
('Rahbar Career Academy', 'rahbar-career-academy', 'Career Guidance',
 'Career guidance, counselling and preparation support for students and young professionals from Bihar and Jharkhand.',
 'The academy runs the information and guidance centre and the data-bank of educational institutions for career enhancement.',
 NULL, 'https://rahbaracademy.org/', 4, FALSE, TRUE, NOW(6), NOW(6)),
('ITI in Every District', 'iti-in-every-district', 'Technical Education',
 'Helping set up Industrial Training Institutes offering job-oriented technical education to economically weaker students.',
 'Starting with RAHBAR ITI Baghauni, the Anjuman helps establish institutions that promote technical and job-oriented education across Bihar and Jharkhand.',
 NULL, NULL, 5, FALSE, TRUE, NOW(6), NOW(6)),
('BANEE Scholarship', 'banee-scholarship', 'Scholarship',
 'Scholarships for eligible, talented and economically challenged students of Bihar and Jharkhand pursuing technical education.',
 NULL, NULL, NULL, 6, FALSE, TRUE, NOW(6), NOW(6)),
('BAJEE: Women''s Empowerment', 'bajee', 'Women Empowerment',
 'Bihar Anjuman''s women empowerment projects, including computer training and skill programmes.',
 NULL, NULL, 'https://www.bajee.org/', 7, FALSE, TRUE, NOW(6), NOW(6)),
('BASIL Free Library', 'basil', 'Library',
 'Bihar Anjuman''s Sources of Infinite Learning: a free library serving the community and supporting educational empowerment.',
 'Books and learning material for the library, and for free distribution in Bihar, can be handed over to a representative of Bihar Anjuman.',
 NULL, NULL, 8, FALSE, TRUE, NOW(6), NOW(6)),
('Calamity Relief', 'calamity-relief', 'Relief',
 'Aid and assistance for people of Bihar and Jharkhand badly affected by floods and other natural calamities.',
 'Rahbar relief camps were organised during the 2008 Bihar floods. The Anjuman works with other organisations to support welfare work wherever calamities strike.',
 NULL, NULL, 9, FALSE, TRUE, NOW(6), NOW(6));

INSERT INTO web_news (title, slug, kind, summary, published, created_at, updated_at) VALUES
('19th RAHBAR Coaching Centre inaugurated in Hazaribagh', '19th-rahbar-coaching-centre-hazaribagh', 'NEWS',
 'The 19th Rahbar Coaching Centre has been inaugurated in Hazaribagh, Jharkhand, for Grade 8 to 10 students of government schools.',
 TRUE, NOW(6), NOW(6));

INSERT INTO web_blog_posts (title, slug, topic, author, summary, content, published, published_at, created_at, updated_at) VALUES
('Better light a candle than curse the darkness', 'better-light-a-candle', 'Community', 'Bihar Anjuman',
 'Why the Anjuman exists, and how every NRI from Bihar and Jharkhand can play a part.',
 'The saddest places on earth are graveyards, not because people are buried there, but because dreams, talents and purposes that never came to fruition are buried there.\n\n## Our purpose\n\nBihar Anjuman brings together expatriates from Bihar and Jharkhand to promote friendship, support one another abroad, and invest in education back home.\n\n## How you can help\n\n- Join the worldwide directory so fellow members can find you\n- Sponsor a student, a teacher or a Rahbar Coaching Centre\n- Volunteer with your local chapter\n\nYou have talents and gifts that no one else can offer. Don''t take them to your grave.',
 TRUE, NOW(6), NOW(6), NOW(6));

-- Who manages the website (change it on Roles & permissions). The Super Admin always has every key.
--   Application Administrator: everything, including publishing.
--   Office Coordinator: blog, gallery, news, projects, directory and messages (no publishing, no pages).
--   Chapter Lead: their chapter's news, directory members and success stories; writes blog posts.
--   RCC Coordinator: their center's success stories; writes blog posts.
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''),
    'WEBSITE_BLOG:VIEW,WEBSITE_BLOG:EDIT,WEBSITE_BLOG_PUBLISH:VIEW,WEBSITE_BLOG_PUBLISH:EDIT,WEBSITE_STORIES:VIEW,WEBSITE_STORIES:EDIT,WEBSITE_STORIES_PUBLISH:VIEW,WEBSITE_STORIES_PUBLISH:EDIT,WEBSITE_GALLERY:VIEW,WEBSITE_GALLERY:EDIT,WEBSITE_DIRECTORY:VIEW,WEBSITE_DIRECTORY:EDIT,WEBSITE_PROJECTS:VIEW,WEBSITE_PROJECTS:EDIT,WEBSITE_NEWS:VIEW,WEBSITE_NEWS:EDIT,WEBSITE_MESSAGES:VIEW,WEBSITE_MESSAGES:EDIT,WEBSITE_PAGES:VIEW,WEBSITE_PAGES:EDIT')
 WHERE role_id = 2 AND FIND_IN_SET('WEBSITE_BLOG:VIEW', COALESCE(permissions, '')) = 0;
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''),
    'WEBSITE_BLOG:VIEW,WEBSITE_BLOG:EDIT,WEBSITE_GALLERY:VIEW,WEBSITE_GALLERY:EDIT,WEBSITE_NEWS:VIEW,WEBSITE_NEWS:EDIT,WEBSITE_PROJECTS:VIEW,WEBSITE_PROJECTS:EDIT,WEBSITE_DIRECTORY:VIEW,WEBSITE_DIRECTORY:EDIT,WEBSITE_MESSAGES:VIEW,WEBSITE_MESSAGES:EDIT')
 WHERE role_id = 8 AND FIND_IN_SET('WEBSITE_BLOG:VIEW', COALESCE(permissions, '')) = 0;
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''),
    'WEBSITE_BLOG:VIEW,WEBSITE_NEWS:VIEW,WEBSITE_NEWS:EDIT,WEBSITE_DIRECTORY:VIEW,WEBSITE_DIRECTORY:EDIT,WEBSITE_STORIES:VIEW,WEBSITE_STORIES:EDIT')
 WHERE LOWER(role_name) = 'chapter lead' AND FIND_IN_SET('WEBSITE_BLOG:VIEW', COALESCE(permissions, '')) = 0;
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'WEBSITE_BLOG:VIEW,WEBSITE_STORIES:VIEW,WEBSITE_STORIES:EDIT')
 WHERE LOWER(role_name) = 'rcc coordinator' AND FIND_IN_SET('WEBSITE_BLOG:VIEW', COALESCE(permissions, '')) = 0;
