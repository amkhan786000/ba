-- Students > Reference Numbers can now be generated chapter by chapter. The last number given per session year and
-- prefix (M = Govt, PVT = the rest) is stored, so the next chapter carries on from there and a number is never given
-- twice, and each batch (which chapter, which numbers) is kept. Re-runnable.
CREATE TABLE IF NOT EXISTS reference_counters (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_year INT NOT NULL,
  prefix VARCHAR(5) NOT NULL,
  last_number INT NOT NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_reference_counters_year_prefix (session_year, prefix)
);

CREATE TABLE IF NOT EXISTS reference_batches (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_year INT NOT NULL,
  chapter_id BIGINT NULL,                 -- with all_chapters = 0: NULL = students without a chapter
  chapter_name VARCHAR(255) NULL,
  all_chapters BIT NOT NULL DEFAULT 0,
  prefix VARCHAR(5) NOT NULL,
  first_number INT NOT NULL,
  last_number INT NOT NULL,
  how_many INT NOT NULL,
  created_by BIGINT NULL, updated_by BIGINT NULL, created_at DATETIME(6) NULL, updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_reference_batches_year (session_year)
);
