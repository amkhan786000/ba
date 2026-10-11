import { Section } from '../../core/models/user.model';

/**
 * Admin > Website: what each kind of website record looks like, so one screen (WebsiteRecordsComponent) can list and
 * edit them all. Keys are the JSON fields of the records (/api/admin/website/<path>).
 */

export type FieldType = 'text' | 'textarea' | 'markdown' | 'number' | 'date' | 'datetime' | 'bool' | 'select' | 'ref' | 'image' | 'file' | 'info';
export type RefList = 'projects' | 'chapters' | 'centers' | 'categories';

export interface WebsiteField {
  key: string;
  label: string;
  type: FieldType;
  required?: boolean;
  options?: string[];
  ref?: RefList;
  /** Shown as a column in the list. */
  column?: boolean;
  hint?: string;
  /** Suggest values already used in this column (e.g. blog topics). */
  suggest?: boolean;
  /** Keep a version history of this text (shown under the editor). */
  revisions?: boolean;
  /** Text of the empty choice in a dropdown (default "—"). */
  emptyLabel?: string;
  /** Only shown to (and sent by) users with EDIT on this section, e.g. "Published" for publishers. */
  requires?: Section;
  /** File field: the field that gets the uploaded file's name (shown on the website's download button). */
  nameField?: string;
}

export interface WebsiteResource {
  path: string;
  label: string;
  /** Section the screen needs VIEW on; changing needs EDIT (the blog: VIEW lets people write their own posts). */
  section: Section;
  /** One sentence under the title. */
  intro: string;
  fields: WebsiteField[];
  defaults: Record<string, unknown>;
  /** Messages come from the website; staff only read and handle them. */
  canCreate: boolean;
  /** A yes / no field with a one-click button in the list (publish, approve, handled ...). */
  toggle?: { key: string; on: string; off: string; requires?: Section };
  /** Urdu / Hindi versions of these fields (record type for /api/admin/website/translations). */
  translate?: { type: 'PROJECT' | 'NEWS' | 'STORY' | 'PAGE' | 'CATEGORY' | 'VOLUNTEER_OPTION'; fields: string[] };
}

const STATES = ['BIHAR', 'JHARKHAND', 'OTHER'];
const MARKDOWN = 'Markdown: ## Heading, **bold**, *italic*, - list, [link](https://...), > quote. Photos: Gallery → Copy link.';

export const WEBSITE_RESOURCES: WebsiteResource[] = [
  {
    path: 'blog', section: 'WEBSITE_BLOG', label: 'Posts', canCreate: true,
    intro: 'Posts of the website’s Media & Knowledge Hub (articles, research, case studies, reports, interviews). Everyone with Knowledge Hub access can write; publishers check and publish them.',
    defaults: { published: false, reviewRequested: false, topic: 'Community', projectId: null, categoryId: null },
    toggle: { key: 'published', on: 'Unpublish', off: 'Publish', requires: 'WEBSITE_BLOG_PUBLISH' },
    fields: [
      { key: 'title', label: 'Title', type: 'text', required: true, column: true },
      { key: 'status', label: 'Status', type: 'info', column: true },
      { key: 'categoryId', label: 'Category', type: 'ref', ref: 'categories', column: true, required: true,
        hint: 'The Knowledge Hub section the post appears in. Categories are on the Categories tab.' },
      { key: 'slug', label: 'Slug (address)', type: 'text', required: true, hint: 'Filled in from the title. The post opens at /knowledge-hub/<category>/<slug>.' },
      { key: 'projectId', label: 'Project', type: 'ref', ref: 'projects', column: true, emptyLabel: 'Others (not about a specific project)',
        hint: 'The post is also shown on this project’s page. “Others” = only in the Knowledge Hub.' },
      { key: 'topic', label: 'Topic', type: 'text', required: true, suggest: true },
      { key: 'author', label: 'Author', type: 'text', column: true },
      { key: 'authorEmail', label: 'Sent by a website visitor', type: 'info' },
      { key: 'reviewRequested', label: 'Ready for review (publishers are notified)', type: 'bool' },
      { key: 'published', label: 'Published (visible on the website)', type: 'bool', requires: 'WEBSITE_BLOG_PUBLISH' },
      { key: 'publishedAt', label: 'Publish date', type: 'datetime', column: true, requires: 'WEBSITE_BLOG_PUBLISH',
        hint: 'Leave empty to use the moment it is first published.' },
      { key: 'coverImageUrl', label: 'Cover image', type: 'image' },
      { key: 'summary', label: 'Summary', type: 'textarea', hint: 'One or two sentences shown on the blog list.' },
      { key: 'content', label: 'Content', type: 'markdown', required: true, hint: MARKDOWN },
      { key: 'subjectName', label: 'Interviewee / subject (optional)', type: 'text',
        hint: 'For interviews and case studies: the person or place the post is about.' },
      { key: 'subjectDetails', label: 'About them (optional)', type: 'textarea', hint: 'One line, e.g. “Class of 2019, now a civil engineer in Patna”.' },
      { key: 'mediaUrl', label: 'Video / audio link (optional)', type: 'text',
        hint: 'A YouTube, Vimeo or SoundCloud link (https://…); YouTube and Vimeo play on the page.' },
      { key: 'attachmentUrl', label: 'PDF to download (optional)', type: 'file', nameField: 'attachmentName',
        hint: 'A research paper or report, up to 20 MB, shown as a Download button.' },
      { key: 'attachmentName', label: 'Download button text', type: 'text', hint: 'e.g. “Full report (PDF)”. Filled in from the file name.' }
    ]
  },
  {
    path: 'volunteer-options', section: 'VOLUNTEER_OPTIONS', label: 'Ways to help', canCreate: true,
    intro: 'The ways to help that visitors tick on the website’s “Volunteer with us” form, in this order. Hidden ones are not shown; sign-ups already received keep their answers.',
    translate: { type: 'VOLUNTEER_OPTION', fields: ['title', 'description', 'choicesLabel', 'choices', 'extraLabel', 'extraChoices'] },
    defaults: { active: true, displayOrder: 10, kind: 'OTHER' },
    toggle: { key: 'active', on: 'Hide', off: 'Show' },
    fields: [
      { key: 'title', label: 'Title', type: 'text', required: true, column: true, hint: 'e.g. “Organise events”.' },
      { key: 'description', label: 'Description', type: 'textarea', hint: 'One sentence shown under the title.' },
      { key: 'kind', label: 'Kind', type: 'select', required: true, column: true, options: ['OTHER', 'MENTOR', 'TUTOR', 'SPONSOR', 'CHAPTER'],
        hint: 'OTHER for new ways to help. MENTOR, TUTOR, SPONSOR and CHAPTER (one of each) keep their own questions on the website: tutors are asked online / in person and the coaching centre, sponsors how many students, chapter members which chapter.' },
      { key: 'optionKey', label: 'Key', type: 'text', column: true,
        hint: 'Short name for links, e.g. /volunteer?role=tutor. Lower-case letters, numbers and dashes; filled in from the title when empty.' },
      { key: 'choicesLabel', label: 'First list: heading', type: 'text', hint: 'e.g. “Fields you can guide in” (mentor), “Subjects” (tutor). Leave empty for no list.' },
      { key: 'choices', label: 'First list: items (one per line)', type: 'textarea', hint: 'Visitors tick the ones that apply. No commas inside an item.' },
      { key: 'extraLabel', label: 'Second list: heading (optional)', type: 'text', hint: 'e.g. “Grades” (tutor).' },
      { key: 'extraChoices', label: 'Second list: items (one per line)', type: 'textarea', hint: 'For tutors the website shows each item as “Grade …”.' },
      { key: 'displayOrder', label: 'Order', type: 'number', column: true, hint: 'Lower numbers show first.' },
      { key: 'active', label: 'Shown on the website', type: 'bool', column: true }
    ]
  },
  {
    path: 'categories', section: 'WEBSITE_BLOG_CATEGORIES', label: 'Categories', canCreate: true,
    intro: 'The sections of the website’s Media & Knowledge Hub. Every post has one; hidden categories (and their posts) are not shown on the website.',
    translate: { type: 'CATEGORY', fields: ['name', 'description'] },
    defaults: { active: true, displayOrder: 0 },
    toggle: { key: 'active', on: 'Hide', off: 'Show' },
    fields: [
      { key: 'name', label: 'Name', type: 'text', required: true, column: true },
      { key: 'slug', label: 'Slug (address)', type: 'text', required: true, column: true, hint: 'The section opens at /knowledge-hub/<slug> (not “all” or “write”: the website uses those).' },
      { key: 'description', label: 'Description', type: 'textarea', hint: 'One sentence shown under the name.' },
      { key: 'displayOrder', label: 'Order', type: 'number', column: true, hint: 'Lower numbers show first.' },
      { key: 'active', label: 'Shown on the website', type: 'bool', column: true }
    ]
  },
  {
    path: 'stories', section: 'WEBSITE_STORIES', label: 'Success Stories', canCreate: true,
    intro: 'Stories of students and members. Chapter and RCC leads add their own; publishers check and publish them.',
    defaults: { consentGiven: false, showFullName: false, showPhoto: true, reviewRequested: false, projectId: null },
    toggle: { key: 'published', on: 'Unpublish', off: 'Publish', requires: 'WEBSITE_STORIES_PUBLISH' },
    translate: { type: 'STORY', fields: ['title', 'summary', 'story', 'nowWhere'] },
    fields: [
      { key: 'title', label: 'Title', type: 'text', required: true, column: true },
      { key: 'status', label: 'Status', type: 'info', column: true },
      { key: 'slug', label: 'Slug (address)', type: 'text', required: true, hint: 'Filled in from the title. The story opens at /success-stories/<slug>.' },
      { key: 'personName', label: 'Name of the person', type: 'text', required: true, column: true },
      { key: 'consentGiven', label: 'The person (or a parent / guardian if under 18) agreed to share this story and photo', type: 'bool', required: true },
      { key: 'showFullName', label: 'Show the full name (otherwise only first name and initial, e.g. “Sana K.”)', type: 'bool' },
      { key: 'showPhoto', label: 'Show the photo on the website', type: 'bool' },
      { key: 'photoUrl', label: 'Photo', type: 'image' },
      { key: 'nowWhere', label: 'Where are they now?', type: 'text', hint: 'e.g. “Diploma engineer at Tata Steel, Jamshedpur”' },
      { key: 'year', label: 'Year', type: 'number', column: true },
      { key: 'projectId', label: 'Project', type: 'ref', ref: 'projects', column: true, emptyLabel: 'Others (not about a specific project)' },
      { key: 'chapterId', label: 'Chapter', type: 'ref', ref: 'chapters', hint: 'Set automatically for chapter leads.' },
      { key: 'rccCenterId', label: 'RCC center', type: 'ref', ref: 'centers', hint: 'Set automatically for RCC coordinators.' },
      { key: 'summary', label: 'Summary', type: 'textarea', hint: 'One or two sentences shown on the stories list.' },
      { key: 'story', label: 'The story', type: 'markdown', required: true,
        hint: 'Where they started, the help they got, where they are now. ' + MARKDOWN },
      { key: 'reviewRequested', label: 'Ready for review (publishers are notified)', type: 'bool' },
      { key: 'published', label: 'Published (visible on the website)', type: 'bool', requires: 'WEBSITE_STORIES_PUBLISH' },
      { key: 'publishedAt', label: 'Publish date', type: 'datetime', requires: 'WEBSITE_STORIES_PUBLISH' }
    ]
  },
  {
    path: 'directory', section: 'WEBSITE_DIRECTORY', label: 'Directory', canCreate: true,
    intro: 'The worldwide directory on the website. Visitors sign up; approve them to show them. Contact details only show when allowed.',
    toggle: { key: 'approved', on: 'Hide', off: 'Approve' },
    defaults: { homeState: 'BIHAR', approved: true, showContact: false },
    fields: [
      { key: 'fullName', label: 'Full name', type: 'text', required: true, column: true },
      { key: 'approved', label: 'Approved (shown in the directory)', type: 'bool', column: true },
      { key: 'homeState', label: 'Home state', type: 'select', options: STATES, required: true },
      { key: 'homeDistrict', label: 'Home district', type: 'text' },
      { key: 'currentCountry', label: 'Country', type: 'text', required: true, column: true },
      { key: 'currentCity', label: 'City', type: 'text', column: true },
      { key: 'profession', label: 'Profession', type: 'text' },
      { key: 'organization', label: 'Organisation', type: 'text' },
      { key: 'email', label: 'Email', type: 'text', required: true, column: true },
      { key: 'phone', label: 'Phone', type: 'text' },
      { key: 'chapterId', label: 'Chapter', type: 'ref', ref: 'chapters' },
      { key: 'showContact', label: 'Show email and phone publicly', type: 'bool' }
    ]
  },
  {
    path: 'projects', section: 'WEBSITE_PROJECTS', label: 'Projects', canCreate: true,
    intro: 'The projects on the website, each with its own page.',
    translate: { type: 'PROJECT', fields: ['name', 'category', 'summary', 'description', 'sponsorshipInfo'] },
    defaults: { active: true, featured: false, displayOrder: 0 },
    fields: [
      { key: 'name', label: 'Name', type: 'text', required: true, column: true },
      { key: 'slug', label: 'Slug (address)', type: 'text', required: true, hint: 'The page opens at /projects/<slug>.' },
      { key: 'category', label: 'Category', type: 'text', column: true, suggest: true },
      { key: 'summary', label: 'Summary', type: 'textarea', required: true },
      { key: 'description', label: 'Page content', type: 'markdown', revisions: true, hint: 'Everything on the project page under the summary. ' + MARKDOWN },
      { key: 'sponsorshipInfo', label: 'Sponsorship info', type: 'text', hint: 'e.g. “Sponsor a student: Rs. 3,500 per month”' },
      { key: 'imageUrl', label: 'Image', type: 'image' },
      { key: 'websiteUrl', label: 'Website', type: 'text' },
      { key: 'galleryAlbum', label: 'Photo album', type: 'text', hint: 'Name of a Gallery album; its photos are shown on the project page.' },
      { key: 'displayOrder', label: 'Display order', type: 'number', column: true },
      { key: 'featured', label: 'Featured on the home page', type: 'bool', column: true },
      { key: 'active', label: 'Active (shown on the website)', type: 'bool', column: true }
    ]
  },
  {
    path: 'news', section: 'WEBSITE_NEWS', label: 'News & Events', canCreate: true,
    intro: 'News and upcoming events. Chapter leads post their chapter’s.',
    translate: { type: 'NEWS', fields: ['title', 'summary', 'content', 'location'] },
    defaults: { kind: 'NEWS', published: true },
    toggle: { key: 'published', on: 'Unpublish', off: 'Publish' },
    fields: [
      { key: 'title', label: 'Title', type: 'text', required: true, column: true },
      { key: 'slug', label: 'Slug (address)', type: 'text', required: true },
      { key: 'kind', label: 'Type', type: 'select', options: ['NEWS', 'EVENT'], required: true, column: true },
      { key: 'eventDate', label: 'Date', type: 'date', column: true },
      { key: 'location', label: 'Location', type: 'text' },
      { key: 'chapterId', label: 'Chapter', type: 'ref', ref: 'chapters', hint: 'Set automatically for chapter leads.' },
      { key: 'summary', label: 'Summary', type: 'textarea' },
      { key: 'content', label: 'Content', type: 'markdown', revisions: true, hint: MARKDOWN },
      { key: 'imageUrl', label: 'Image', type: 'image' },
      { key: 'published', label: 'Published', type: 'bool', column: true }
    ]
  },
  {
    path: 'messages', section: 'WEBSITE_MESSAGES', label: 'Messages', canCreate: false,
    intro: 'Messages sent through the website’s contact form. Reply by email, then mark them handled.',
    defaults: {},
    toggle: { key: 'handled', on: 'Reopen', off: 'Mark handled' },
    fields: [
      { key: 'createdAt', label: 'Received', type: 'info', column: true },
      { key: 'name', label: 'Name', type: 'text', required: true, column: true },
      { key: 'email', label: 'Email', type: 'text', required: true, column: true },
      { key: 'phone', label: 'Phone', type: 'text' },
      { key: 'topic', label: 'Topic', type: 'select', options: ['GENERAL', 'SPONSOR', 'VOLUNTEER', 'HELP_NEEDED', 'JOB_GUIDANCE', 'CHAPTER'], column: true },
      { key: 'subject', label: 'Subject', type: 'text', required: true, column: true },
      { key: 'message', label: 'Message', type: 'textarea', required: true },
      { key: 'handled', label: 'Handled', type: 'bool', column: true }
    ]
  },
  {
    path: 'pages', section: 'WEBSITE_PAGES', label: 'Custom pages', canCreate: true,
    intro: 'Pages you add to the website. They open at /p/<slug> and can be shown in its menu.',
    translate: { type: 'PAGE', fields: ['title', 'body'] },
    defaults: { published: false, showInMenu: false, menuOrder: 0 },
    toggle: { key: 'published', on: 'Unpublish', off: 'Publish' },
    fields: [
      { key: 'title', label: 'Title', type: 'text', required: true, column: true },
      { key: 'slug', label: 'Slug (address)', type: 'text', required: true, column: true, hint: 'The page opens at /p/<slug>.' },
      { key: 'published', label: 'Published (visible on the website)', type: 'bool', column: true },
      { key: 'showInMenu', label: 'Show in the menu', type: 'bool', column: true },
      { key: 'menuOrder', label: 'Menu position', type: 'number' },
      { key: 'body', label: 'Content', type: 'markdown', revisions: true, hint: MARKDOWN }
    ]
  }
];

export function websiteResource(path: string): WebsiteResource {
  return WEBSITE_RESOURCES.find((r) => r.path === path) ?? WEBSITE_RESOURCES[0];
}

export function slugify(text: string): string {
  return text.toLowerCase().normalize('NFKD').replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 150);
}

/** "2026-10-10T20:44:48" -> value for <input type="datetime-local">. */
export function toLocalInput(v: unknown): string | null {
  return v ? String(v).slice(0, 16) : null;
}
