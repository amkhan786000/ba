-- A student mapped to a sponsor belongs to the sponsor's chapter (from now on Rahbar sets it on every mapping; see
-- service.SponsorMappingService). This brings the students already mapped in line: each takes their current sponsor's
-- chapter, also when the student had another chapter. Sponsors without a chapter and the "unassigned" grantor
-- (user_id 12) change nothing; sponsors' chapters are never changed. Compares ids only (no text): re-runnable.

UPDATE users s
  JOIN grantor_grantees gg ON gg.grantee_id = s.id
  JOIN users sp ON sp.id = gg.grantor_id
   SET s.chapter_id = sp.chapter_id
 WHERE sp.chapter_id IS NOT NULL
   AND (sp.user_id IS NULL OR sp.user_id <> '12')
   AND (s.chapter_id IS NULL OR s.chapter_id <> sp.chapter_id);
