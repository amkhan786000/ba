-- The admin menu moved "Notes to Sponsors" from Students to Sponsors & Payments: the Help & FAQ answer that says
-- where to find it follows. Only that text is replaced (an answer the office rewrote is left as it is). Re-runnable.
UPDATE faqs
   SET answer = REPLACE(answer, 'Students > Notes to Sponsors', 'Sponsors & Payments > Notes to Sponsors')
 WHERE answer LIKE '%Students > Notes to Sponsors%';
