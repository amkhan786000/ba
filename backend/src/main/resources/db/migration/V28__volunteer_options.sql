-- The ways to help on the website's "Volunteer with us" form, managed in Admin > Volunteers > Form options instead
-- of being fixed in the website. Four kinds keep their extra questions (MENTOR: fields, TUTOR: subjects, grades,
-- online / in person and coaching centre, SPONSOR: how many students, CHAPTER: which chapter); OTHER options are
-- plain ways to help with up to two optional lists to pick from. Lists are one item per line.
CREATE TABLE IF NOT EXISTS web_volunteer_options (
  id BIGINT NOT NULL AUTO_INCREMENT,
  option_key VARCHAR(60) NOT NULL,
  kind VARCHAR(20) NOT NULL,
  title VARCHAR(120) NOT NULL,
  description VARCHAR(400) NULL,
  choices_label VARCHAR(120) NULL,
  choices TEXT NULL,
  extra_label VARCHAR(120) NULL,
  extra_choices TEXT NULL,
  display_order INT NOT NULL DEFAULT 0,
  active BIT NOT NULL DEFAULT 1,
  created_by BIGINT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_web_volunteer_options_key (option_key),
  CONSTRAINT fk_web_volunteer_options_created_by FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT fk_web_volunteer_options_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- The four options the website had built in, with the same wording and lists.
INSERT INTO web_volunteer_options (option_key, kind, title, description, choices_label, choices, extra_label, extra_choices,
                                   display_order, active, created_at, updated_at)
SELECT * FROM (
  SELECT 'mentor' AS k, 'MENTOR' AS kind, 'Register as a mentor' AS t,
         'Guide students and newcomers on careers, studies and jobs.' AS d, 'Fields you can guide in' AS cl,
         'Career guidance\nEngineering & IT\nMedicine & health\nBusiness & finance\nGovernment jobs & civil services\nLaw\nTeaching & research\nHigher studies abroad\nEntrepreneurship' AS c,
         NULL AS el, NULL AS ec, 1 AS o, 1 AS a, NOW(6) AS ca, NOW(6) AS ua UNION ALL
  SELECT 'tutor', 'TUTOR', 'Become a tutor', 'Teach Grade 8–12 students online or at a Rahbar coaching centre.', 'Subjects',
         'Maths\nScience\nPhysics\nChemistry\nBiology\nEnglish\nHindi\nUrdu\nSocial science\nComputer',
         'Grades', '8\n9\n10\n11\n12', 2, 1, NOW(6), NOW(6) UNION ALL
  SELECT 'sponsor', 'SPONSOR', 'Sponsor students', 'Support a student’s education through Rahbar scholarships.',
         NULL, NULL, NULL, NULL, 3, 1, NOW(6), NOW(6) UNION ALL
  SELECT 'chapter', 'CHAPTER', 'Join a district chapter', 'Meet members near you, or help start a chapter in your district.',
         NULL, NULL, NULL, NULL, 4, 1, NOW(6), NOW(6)
) d WHERE NOT EXISTS (SELECT 1 FROM web_volunteer_options);

-- Answers to options added later (kind OTHER), kept as readable text with the option's title at the time of
-- sign-up, e.g. "Organise events: Patna, Online".
ALTER TABLE volunteer_applications ADD COLUMN other_offers VARCHAR(2000) NULL AFTER chapter_id;

-- Who adds and changes the options: its own section on Roles & permissions ("Volunteer form options"). By default
-- the roles that already manage volunteers (the Super Admin always has every key).
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'VOLUNTEER_OPTIONS:VIEW', 'VOLUNTEER_OPTIONS:EDIT')
 WHERE FIND_IN_SET('VOLUNTEERS:EDIT', COALESCE(permissions, '')) > 0
   AND FIND_IN_SET('VOLUNTEER_OPTIONS:VIEW', COALESCE(permissions, '')) = 0;
