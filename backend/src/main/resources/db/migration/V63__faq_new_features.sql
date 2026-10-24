-- Help & FAQ brought up to date with everything added since V40. Re-runnable:
-- 1. Answers that are no longer right are corrected, but only while they still have the original wording (an
--    answer the office already rewrote is left alone).
-- 2. The question about Rahbar's income & expense book (removed) is switched off.
-- 3. New questions are added once (skipped when a question with the same wording exists).

-- ---------------------------------------------------------------------------------------- 1. corrections
UPDATE faqs SET answer = 'Open Payments. It lists the installments of all your students: "Due now" shows what to pay now (overdue and due), "Next payment" the next due date. Installments are shown as "Installment 1 paid", "Installment 2 due" and so on; the office keeps the amounts. You can filter by student, status and year.'
 WHERE question = 'How do I see what I need to pay?' AND answer LIKE '%next due date and amount%';

UPDATE faqs SET answer = 'On Payments, click "Pay now" on the installment, enter the date and the amount you transferred and upload the bank receipt. The student''s bank details are shown in the same window. Payment is only possible while the student is studying: when a student is on hold, has graduated or dropped out, the Pay button is switched off.'
 WHERE question = 'How do I record a payment I made?' AND answer NOT LIKE '%while the student is studying%';

UPDATE faqs SET answer = 'When a student is mapped to a sponsor, Rahbar builds the installments from the course''s number of semesters, the student''s payment start date and the Payment Config of the student''s session year and college type (Govt, Private or any college). Payment Records lists all installments; a student''s own list is in the Student Directory. Installments are shown by number ("Installment 2 due"); amounts stay in Payment Config.'
 WHERE question = 'How are a student''s installments created?' AND answer NOT LIKE '%college type%';

UPDATE faqs SET answer = 'Studying, On hold, Graduated or Dropped out. Payments can only be recorded while a student is Studying; reminders stop for the other statuses. Change it on the student''s Study tab in the Student Directory; the history is kept.'
 WHERE question = 'What is a student''s study status?' AND answer LIKE '%payment dues continue but no reminders%';

UPDATE faqs SET answer = 'Rahbar Coaching Centres teach students of classes 7 to 10. Each centre has its own students (from the admission form), teachers, monthly exams with 1st / 2nd / 3rd prizes, daily attendance and a dashboard, under Coaching Center in the menu. Class 10 students who do well are invited to apply for the Rahbar scholarship.'
 WHERE question = 'What are RCC coaching centres?' AND answer LIKE 'Rahbar Coaching Centres prepare students%';

-- ---------------------------------------------------------------------------------------- 2. removed
UPDATE faqs SET active = FALSE WHERE question = 'How do the income and expense figures reach the website?';

-- ---------------------------------------------------------------------------------------- 3. new questions
CREATE TEMPORARY TABLE rahbar_new_faqs (
  category VARCHAR(80) NOT NULL,
  audience VARCHAR(10) NOT NULL,
  question VARCHAR(300) NOT NULL,
  answer TEXT NOT NULL,
  display_order INT NOT NULL
) DEFAULT CHARSET=utf8mb4;

INSERT INTO rahbar_new_faqs (category, audience, question, answer, display_order) VALUES
  -- getting started
  ('Getting started', 'ALL', 'Where do I find Help & FAQ?',
   'Click "Help" at the top right, next to the notification bell, or choose "Help & FAQ" in the menu under your name. You only see the questions that apply to you.', 45),
  ('Getting started', 'ALL', 'Why do I see numbers instead of amounts for installments?',
   'Rahbar shows installments by number: "Installment 1 paid", "Installment 2 due". Nobody sees sponsorship amounts on the screens; the office keeps them in Payment Config, and a sponsor enters the amount only when recording a payment.', 85),

  -- students
  ('Students', 'STUDENTS', 'What is my reference number?',
   'Your User ID is your reference number, for example M001-2026 (Govt college) or PVT001-2026 (other colleges). It is shown on your dashboard. You still sign in with your email or phone number.', 145),
  ('Students', 'STUDENTS', 'How do I find jobs and internships?',
   'Open Careers in your student portal. It lists the openings the office has posted (jobs, internships, training) with the place, last date and who can apply. Click "I''m interested" so the office knows, and use "Apply" where there is a link. You can see later whether you were selected.', 150),
  ('Students', 'STUDENTS', 'I got a job, internship or admission. How do I tell Rahbar?',
   'In Careers, under My placements, click "+ Add" and fill in the organisation (or college), role or course, place and start date. The office confirms it. If you have graduated, a confirmed job or higher studies also updates your Alumni profile.', 155),
  ('Students', 'STUDENTS', 'Can I get career guidance?',
   'Yes. In Careers, choose what you need help with (choosing a course, finding a job, higher studies, entrance exams), write your question and click Ask. The office arranges a session; you get a notification with the date, and their advice appears on the same page.', 160),
  ('Students', 'STUDENTS', 'How do I sign up for an event or workshop?',
   'Open Events in your student portal. It shows upcoming events for you (and your parents): workshops, career sessions, parents'' meetings and more. Click Register; if your plans change, click "Cancel my place" so someone else can come.', 165),
  ('Students', 'STUDENTS', 'I have a problem. How do I tell the office?',
   'Open Helpline, click "Raise an issue", choose what it is about (fees, college, studies, health, harassment / safety, personal, account), and write what happened. The office replies on the same page and you get a notification. Click "It''s sorted, thank you" when it is solved, or write again if it is not.', 170),
  ('Students', 'STUDENTS', 'Who reads a confidential helpline issue?',
   'Tick "Confidential" when you raise the issue and only the head of the office (Super Admin) and the one person they give it to can read it. A report of harassment is always confidential and treated as urgent.', 175),

  -- sponsors
  ('Sponsors', 'SPONSORS', 'What is "My Impact"?',
   'My Impact in your Sponsor portal shows every student you have supported, now and before: their course, how they are doing, installments paid and, for graduates, what they do now. "Download PDF" gives you the same as a report to keep or share.', 225),
  ('Sponsors', 'SPONSORS', 'Rahbar asked whether I will continue next session. How do I answer?',
   'Before each new session the office asks every sponsor. You get an email and a yellow banner on your dashboard. Open Renewal and choose: continue with my current students, continue with a different number of students, or not this time. You can change your answer until the office closes the question.', 230),
  ('Sponsors', 'SPONSORS', 'How do I apply to sponsor students in a new session?',
   'While an application session is open, use "Apply for Sponsorship": say how many students you would like to sponsor and, if it matters to you, what kind (college type, father''s profession, family income). The office uses this when matching students to you. When no session is open, the page says so.', 235),
  ('Sponsors', 'SPONSORS', 'Why can''t I pay for my student right now?',
   'Payments are only possible while the student is studying. If the student is on hold, has graduated or dropped out, the Pay button is switched off; contact the office if you think the status is wrong.', 240),
  ('Sponsors', 'SPONSORS', 'Can I sponsor students on behalf of someone who wants to stay anonymous?',
   'Yes. The office can map students to you "on behalf of" an anonymous donor from a chapter, with a short label (never a name). You stay the sponsor of record and the contact; the student sees "on behalf of a donor from Chapter X".', 245),
  ('Sponsors', 'SPONSORS', 'How do I raise a question or problem with the office?',
   'Open Helpline in your Sponsor portal and click "Raise an issue" (a payment, your student, your account, or something else). The office replies on the same page and you get a notification.', 250),
  ('Sponsors', 'SPONSORS', 'Can I come to Rahbar events?',
   'Yes. Events in your Sponsor portal lists upcoming events for sponsors and for everyone, such as ceremonies and meetings. Click Register to book your place.', 255),

  -- applications
  ('Applications', 'STAFF', 'What does the online scholarship application ask for?',
   'It follows the 2026 paper form: who recommended the student (RCC or chapter), a Govt college seat, the course, personal details (date of birth, Aadhaar last 4 digits only, nationality), 10th / 12th / ITI / Diploma results, family and guardian, bank details, documents, and the terms and self-declaration in Hindi. Date of birth, 10th results, bank details and both declarations are required. Staff see everything in the "Application form details" card.', 445),
  ('Applications', 'STAFF', 'How are sponsorship applications handled?',
   'Applications > Apply for Sponsorship opens while an application period is active. Sponsors apply for themselves; staff with the "Apply for sponsorship: manage" permission can apply on behalf of a sponsor (chapter leads for their chapter) and see every application. Each application has "Suggested students": students without a sponsor who fit what the sponsor would like, ready to map in one click.', 450),
  ('Applications', 'STAFF', 'How does a student''s application move to the website tracker?',
   'Applicants can follow their application on biharanjuman.com with "Track your application". Every status change the office makes (shortlisted, interview, accepted...) shows there, and accepted applicants become student accounts with their bank details copied over.', 455),

  -- office & admin: students
  ('Office & admin', 'STAFF', 'How do I give students their reference numbers?',
   'Students > Reference Numbers (Super Admin and Application Administrator only). Choose the current session year and click Generate: every student of that year whose User ID isn''t a reference number yet gets one, oldest record first. Govt college: M001-2026, M002-2026...; Private, "Doesn''t matter" or not set: PVT001-2026... The new number replaces the User ID; the old one is shown next to it. Students still sign in with email or phone.', 460),
  ('Office & admin', 'STAFF', 'Why does the college type matter?',
   'BA Details > College type (Govt, Private, Doesn''t matter) picks which Payment Config gives the student''s installments, and the prefix of their reference number (M for Govt, PVT otherwise). Data Quality lists students without one.', 465),
  ('Office & admin', 'STAFF', 'What happens to a student''s chapter when I map them to a sponsor?',
   'The student takes the sponsor''s chapter, so they show on the right chapter dashboard.', 470),
  ('Office & admin', 'STAFF', 'Can I see who sponsored a student before?',
   'Yes. BA Details > Sponsor history lists every sponsor the student had, with the dates and why it ended. When you change or remove a sponsor, Rahbar asks for the reason (for example "Sponsor declined to continue").', 475),
  ('Office & admin', 'STAFF', 'What is the Early Warning list?',
   'Students > Early Warning shows students who may drop out and why. Scholarship students: latest marks under 50% or down 10+ points, progress report not sent, an installment unpaid 30+ days after its due date, or studies on hold. Coaching students: last exam under 40% or down 15+ points, missed the last two exams, or attendance under 75% in 30 days. Anyone with no follow-up for 60 days is also flagged.', 480),
  ('Office & admin', 'STAFF', 'How do I record a follow-up with a student at risk?',
   'On Early Warning click "Record follow-up": how (call, home visit, meeting), what happened, the outcome (keep watching, resolved, needs more help) and when to follow up again. The student leaves the "Needs a follow-up" list for 14 days, or until the next date you set. The history button shows every follow-up. Chapter leads see their chapter''s students, coaching coordinators their centre''s.', 485),
  ('Office & admin', 'STAFF', 'Can I click a number to see who is behind it?',
   'Yes. Many counts open a list when clicked, for example the number of students of a sponsor on Sponsorships, overdue installments, or reports pending. Every report also has a year filter.', 490),
  ('Office & admin', 'STAFF', 'How do I reset many passwords at once?',
   'Administration > Bulk Password Reset (Super Admin). Choose a role (students: also their session years) and who: everyone, only those still on a temporary password, or only those who set their own. Check who, then reset: each person gets a new temporary password by email. The same page shows who has set their own password since.', 495),
  ('Office & admin', 'STAFF', 'Can I delete a user?',
   'The Super Admin can delete a user from Edit User, but only when nothing is linked to them (no students, sponsorships, chapter, payments and so on). Otherwise make the account inactive.', 500),

  -- sponsors & payments (staff)
  ('Payments', 'STAFF', 'How does matching students to a sponsor work?',
   'A sponsor''s preferences (Edit Sponsor > Student preferences, or their sponsorship application) say what kind of student they would like: college type, father''s profession, family income range. On Map Students, students who fit come first with a "Fits" badge and why; tick "Show only students who fit" to hide the rest. A preference left empty is no condition; a student whose profession or income isn''t known doesn''t fit that preference.', 505),
  ('Payments', 'STAFF', 'How do I ask sponsors whether they will continue next session?',
   'Sponsors & Payments > Sponsor Renewal. Start a campaign for the session year (with an answer-by date and your message): every active sponsor who has students now is emailed and sees the question in their portal. The page shows who answered what, how many students are covered next session, and who hasn''t answered: use Remind to email them again, or Record answer after a phone call. Close the campaign when you are done.', 510),
  ('Payments', 'STAFF', 'What is an on-behalf sponsorship?',
   'A sponsor can sponsor students on behalf of an anonymous donor from a chapter. Tick "on behalf of an anonymous donor" when mapping, choose the donor''s chapter and a short label (never a name). The sponsor stays the contact; the donor''s chapter lead sees these students and the sponsor''s contact.', 515),
  ('Payments', 'STAFF', 'Where is the impact report for donors and the board?',
   'Administration > Impact Report: choose a session year or all years and download the PDF. It shows counts only, never amounts: students, girls, sponsors, courses, chapters, installments paid, marks, graduates and what they do now, and coaching results. Chapter leads get their chapter''s. A sponsor''s own impact PDF is in their portal (My Impact).', 520),

  -- coaching centres
  ('Coaching centres', 'STAFF', 'Who can do what in the coaching centres?',
   'The office sees every centre. The RCC Coordinator (coaching centre coordinator) manages their own centre: students, teachers, exams, marks and attendance. A Coaching Teacher sees their centre''s students, exams and marks and marks attendance, but can''t change students. Give a teacher a login with the Coaching Teacher role and their centre in Manage Users, then enter that email on the teacher''s record.', 600),
  ('Coaching centres', 'STAFF', 'How do I admit a coaching student?',
   'Coaching Center > Students > New admission: centre, class (7 to 10), admission number and date, the student''s details, school and previous marks, parents, mobiles, family income and address (Aadhaar: last 4 digits only), and an email if they have one. A student who leaves is set to Left, not deleted.', 605),
  ('Coaching centres', 'STAFF', 'How do I mark attendance?',
   'Coaching Center > Attendance. Choose the class and the day (it opens on a class that has students), tap P (present), A (absent) or L (leave) for each student, or "All present" first, and Save. Leave doesn''t lower a student''s %. Below, "Attendance by student" shows everyone''s attendance for any period, lowest first.', 610),
  ('Coaching centres', 'STAFF', 'How do exams and prizes work?',
   'Coaching Center > Exams & Prizes > New exam: centre, class, date and the subjects with their maximum marks. "Enter marks" opens the marks sheet (leave a box empty when a student was absent). "Generate prizes" gives 1st, 2nd and 3rd by total marks; equal totals share a place. Changing marks clears the prizes, so generate them again. The office manages the subject list.', 615),
  ('Coaching centres', 'STAFF', 'Which Class 10 students should we invite for the scholarship?',
   'Coaching Center > Ready for Scholarship lists Class 10 students, best exam average first, with attendance and phone numbers. Call them and click "Called", or click "Email" to send the invitation with the apply link. When they apply on the website, their application is found by name and mobile, and the funnel shows invited, applied and accepted.', 620),
  ('Coaching centres', 'STAFF', 'What does the coaching dashboard show?',
   'Coaching Center > Coaching Dashboard: students (girls / boys), admissions, teachers, exams, average marks and attendance, with charts by class, by month and by marks band, the Class 10 to scholarship funnel and recent prize winners. The office sees all centres together (a row per centre) or one centre; coordinators and teachers see their own.', 625),

  -- careers, events, helpline (staff)
  ('Careers & events', 'STAFF', 'How do we post jobs and record placements?',
   'Students > Careers. Openings: post jobs, internships and training; see who is interested and mark applied / selected, then "Record placement". Placements: record one for any student or graduate, or confirm one a student added (a confirmed job or higher studies of a graduate updates their Alumni profile). Employers: the companies and hospitals that hire our students. Guidance requests: assign someone, set a session, and write the advice the student sees.', 700),
  ('Careers & events', 'STAFF', 'How do I run an event or workshop?',
   'Community > Events & Workshops > New event: title, kind, date and time, venue or online link, audience (scholarship students, coaching students, sponsors, parents, everyone), places and whether people sign up in their portal; "Tell them now" sends a notification. "People" lets you add students, sponsors, coaching students or guests such as parents, and mark who came. Chapter leads run their chapter''s events, coaching coordinators their centre''s.', 705),
  ('Careers & events', 'STAFF', 'How do we handle helpline issues?',
   'Communication > Helpline lists issues from students and sponsors, those needing attention first. Open one to read the conversation, assign it, set the priority and status, and reply. Tick "Staff-only note" for something the student or sponsor must not see. Confidential issues and every harassment report are seen only by the Super Admin and the person assigned. Students and sponsors see replies as from "Rahbar office".', 710);

INSERT INTO faqs (category, audience, question, answer, on_website, active, display_order)
SELECT n.category, n.audience, n.question, n.answer, FALSE, TRUE, n.display_order
  FROM rahbar_new_faqs n
 WHERE NOT EXISTS (SELECT 1 FROM faqs f WHERE BINARY f.question = BINARY n.question); -- binary: the tables' collations may differ

DROP TEMPORARY TABLE rahbar_new_faqs;
