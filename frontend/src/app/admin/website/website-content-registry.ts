import {
  CHAPTER_OBJECTIVES, CONTACT_EMAIL, FUND_OBJECTIVES, MAIN_OBJECTIVES, MOTTO, PURPOSE_PARAGRAPHS, REB_MISSION, REB_VISION,
} from './website-content';

/**
 * A copy of the website's own list (biharanjuman.com: frontend/src/app/core/content-registry.ts). Keep the two the
 * same: the website shows these defaults, and Admin > Website > Pages lists these slots for editing.
 *
 * Every piece of text an admin can change on the fixed pages (Admin → Pages → Site text).
 * The default is today's English wording; admins can override it per language. Untranslated
 * text falls back to the English version.
 *
 * Types: "inline" one line, *word* shows in the accent style; "text" plain paragraph;
 * "markdown" formatted text; "list" one item per line.
 */
export type SlotType = 'inline' | 'text' | 'markdown' | 'list';

export interface Slot {
  key: string;
  label: string;
  type: SlotType;
  default: string;
}

export interface SlotGroup {
  id: string;
  label: string;
  slots: Slot[];
}

const head = (page: string, eyebrow: string, title: string, lead: string): Slot[] => [
  { key: `${page}.eyebrow`, label: 'Label above the title', type: 'inline', default: eyebrow },
  { key: `${page}.title`, label: 'Page title', type: 'inline', default: title },
  { key: `${page}.lead`, label: 'Introduction', type: 'text', default: lead },
];

export const SLOT_GROUPS: SlotGroup[] = [
  {
    id: 'menu', label: 'Header, menu & footer', slots: [
      { key: 'site.tagline', label: 'Tagline under the name', type: 'inline', default: 'Connecting people to serve humanity' },
      { key: 'site.motto', label: 'Motto', type: 'inline', default: MOTTO },
      { key: 'nav.objectives', label: 'Menu: Objectives', type: 'inline', default: 'Objectives' },
      { key: 'nav.chapters', label: 'Menu: Chapters', type: 'inline', default: 'Chapters' },
      { key: 'nav.projects', label: 'Menu: Projects', type: 'inline', default: 'Projects' },
      { key: 'nav.news', label: 'Menu: News', type: 'inline', default: 'News' },
      { key: 'nav.hub', label: 'Menu: Knowledge Hub', type: 'inline', default: 'Knowledge Hub' },
      { key: 'nav.gallery', label: 'Menu: Gallery', type: 'inline', default: 'Gallery' },
      { key: 'nav.directory', label: 'Menu: Directory', type: 'inline', default: 'Directory' },
      { key: 'nav.stories', label: 'Menu: Success stories', type: 'inline', default: 'Stories' },
      { key: 'nav.volunteer', label: 'Menu: Volunteer', type: 'inline', default: 'Volunteer' },
      { key: 'nav.contact', label: 'Menu: Contact', type: 'inline', default: 'Contact' },
      { key: 'nav.sponsor', label: 'Menu button: Sponsor', type: 'inline', default: 'Sponsor' },
      { key: 'nav.centres', label: 'Footer link: Coaching centres', type: 'inline', default: 'Coaching centres' },
      { key: 'footer.volunteer', label: 'Footer link: Volunteer', type: 'inline', default: 'Volunteer' },
      { key: 'footer.explore', label: 'Footer heading: Explore', type: 'inline', default: 'Explore' },
      { key: 'footer.involved', label: 'Footer heading: Get involved', type: 'inline', default: 'Get involved' },
      { key: 'footer.contact', label: 'Footer heading: Contact', type: 'inline', default: 'Contact' },
      { key: 'footer.email', label: 'Contact email shown on the site', type: 'inline', default: CONTACT_EMAIL },
    ],
  },
  {
    id: 'home', label: 'Home page', slots: [
      { key: 'home.hero.eyebrow', label: 'Hero: label', type: 'inline', default: 'Serving NRIs from Bihar & Jharkhand worldwide' },
      { key: 'home.hero.title', label: 'Hero: headline', type: 'inline', default: 'Together for *education*, friendship & service' },
      {
        key: 'home.hero.lead', label: 'Hero: introduction', type: 'text',
        default: 'Bihar Anjuman connects expatriates from Bihar and Jharkhand to support one another abroad and to educate, motivate and uplift our communities back home.',
      },
      { key: 'home.hero.cta1', label: 'Hero: first button', type: 'inline', default: 'Join the directory' },
      { key: 'home.hero.cta2', label: 'Hero: second button', type: 'inline', default: 'Explore projects' },
      { key: 'home.hero.cta3', label: 'Hero: third button', type: 'inline', default: 'Sponsor a student' },
      { key: 'home.feature1.title', label: 'Highlight 1: title', type: 'inline', default: 'Rahbar Coaching Centres' },
      { key: 'home.feature1.text', label: 'Highlight 1: text', type: 'text', default: 'Free coaching for Grade 8–10 government-school students in every district.' },
      { key: 'home.feature2.title', label: 'Highlight 2: title', type: 'inline', default: 'Rahbar-e-Banaat' },
      { key: 'home.feature2.text', label: 'Highlight 2: text', type: 'text', default: 'A residential Madrasa-cum-School giving girls both faith and modern education.' },
      { key: 'home.projects.eyebrow', label: 'Projects section: label', type: 'inline', default: 'Our projects' },
      { key: 'home.projects.title', label: 'Projects section: title', type: 'inline', default: 'Education that *changes* families' },
      { key: 'home.projects.lead', label: 'Projects section: introduction', type: 'text', default: 'Zero-overhead programmes where every rupee reaches the student.' },
      { key: 'home.purpose.eyebrow', label: 'Purpose section: label', type: 'inline', default: 'Find your purpose' },
      { key: 'home.purpose.title', label: 'Purpose section: title', type: 'inline', default: 'Don’t take your gifts *to the grave*' },
      { key: 'home.purpose.body', label: 'Purpose section: text', type: 'markdown', default: PURPOSE_PARAGRAPHS.join('\n\n') },
      { key: 'home.blog.eyebrow', label: 'Knowledge Hub section: label', type: 'inline', default: 'From the Knowledge Hub' },
      { key: 'home.blog.title', label: 'Knowledge Hub section: title', type: 'inline', default: 'Stories & *reflections*' },
      { key: 'home.gallery.eyebrow', label: 'Gallery section: label', type: 'inline', default: 'Gallery' },
      { key: 'home.gallery.title', label: 'Gallery section: title', type: 'inline', default: 'Moments from the *field*' },
      { key: 'home.news.eyebrow', label: 'News section: label', type: 'inline', default: 'News & updates' },
      { key: 'home.news.title', label: 'News section: title', type: 'inline', default: 'Latest from the *Anjuman*' },
    ],
  },
  {
    id: 'about', label: 'Objectives page', slots: [
      { key: 'about.eyebrow', label: 'Label above the title', type: 'inline', default: 'About the Anjuman' },
      { key: 'about.title', label: 'Page title', type: 'inline', default: 'Aims & objectives' },
      { key: 'about.intro', label: 'Introduction (optional)', type: 'markdown', default: '' },
      { key: 'about.vm.title', label: 'Vision & mission: title', type: 'inline', default: 'Vision & mission' },
      { key: 'about.vm.note', label: 'Vision & mission: note', type: 'inline', default: 'Rahbar-e-Banaat, the Anjuman’s school for girls' },
      { key: 'about.vision', label: 'Vision', type: 'text', default: REB_VISION },
      { key: 'about.mission', label: 'Mission', type: 'text', default: REB_MISSION },
      { key: 'about.partA.title', label: 'Part A: title', type: 'inline', default: 'Part A · Main objectives' },
      { key: 'about.partA.items', label: 'Part A: objectives', type: 'list', default: MAIN_OBJECTIVES.join('\n') },
      { key: 'about.funds.title', label: 'Funds: title', type: 'inline', default: 'Funds and welfare' },
      { key: 'about.funds.lead', label: 'Funds: introduction', type: 'text', default: 'The Anjuman shall strive to establish funds to fulfil its objectives, including but not limited to:' },
      { key: 'about.funds.items', label: 'Funds: objectives', type: 'list', default: FUND_OBJECTIVES.join('\n') },
      { key: 'about.partB.title', label: 'Part B: title', type: 'inline', default: 'Part B · Objectives for local chapters' },
      { key: 'about.partB.lead', label: 'Part B: introduction', type: 'text', default: 'Chapters are formed at country or city level.' },
      { key: 'about.partB.items', label: 'Part B: objectives', type: 'list', default: CHAPTER_OBJECTIVES.join('\n') },
    ],
  },
  { id: 'projects', label: 'Projects page', slots: head('projects', 'Rahbar & more', 'Projects', 'Education, scholarships, women’s empowerment and relief for the people of Bihar and Jharkhand.') },
  { id: 'chapters', label: 'Chapters page', slots: head('chapters', 'Worldwide', 'Chapters', 'Local chapters meet regularly, support members in times of need and guide newcomers.') },
  { id: 'centres', label: 'Coaching centres page', slots: head('centres', 'Rahbar Coaching Centres', 'Coaching centres', 'Free coaching for Grades 8–10 government-school students. Our goal: a centre in every district.') },
  { id: 'news', label: 'News page', slots: head('news', 'Updates', 'News & events', '') },
  {
    id: 'hub', label: 'Knowledge Hub page', slots: [
      ...head('hub', 'Media & Knowledge Hub', 'Learn from our *work*',
        'Articles, research about education in Bihar, case studies, community reports and interviews from the Anjuman and its projects.'),
      { key: 'hub.all', label: 'Link under each section', type: 'inline', default: 'See all' },
      { key: 'hub.empty', label: 'Section without posts yet', type: 'inline', default: 'Coming soon.' },
      { key: 'hub.subject', label: 'Post page: heading of the interviewee / subject box', type: 'inline', default: 'About' },
      { key: 'hub.download', label: 'Post page: download button when it has no text', type: 'inline', default: 'Download (PDF)' },
    ],
  },
  {
    id: 'blog', label: 'Knowledge Hub: all posts and Write for us', slots: [
      ...head('blog', 'Knowledge Hub', 'Stories, ideas & *reflections*', 'Writing from members and volunteers on education, community and Islamic values.'),
      { key: 'blog.write.button', label: 'Button: write a post', type: 'inline', default: 'Write for us' },
      { key: 'blog.write.title', label: 'Write page: title', type: 'inline', default: 'Write for the *Knowledge Hub*' },
      {
        key: 'blog.write.lead', label: 'Write page: introduction', type: 'markdown',
        default: 'Share a story about a project, a student, an event or an idea. Every post is read by an admin before it appears on the site.',
      },
      { key: 'blog.write.thanks', label: 'Write page: thank-you message', type: 'text', default: 'Thank you! Your post has been sent. An admin will review it before it appears on the site.' },
      { key: 'blog.onProject.title', label: 'Project pages: Knowledge Hub section title', type: 'inline', default: 'From the *Knowledge Hub*' },
    ],
  },
  {
    id: 'stories', label: 'Success stories page', slots: [
      ...head('stories', 'Success stories', 'Lives *changed*', 'Students and members whose lives took a new direction through the Anjuman\u2019s projects.'),
      { key: 'stories.cta', label: 'Call to action under the stories', type: 'markdown',
        default: 'Every story here began with someone who sponsored a student, taught a class or volunteered. **Be the start of the next one.**' },
    ],
  },
  { id: 'gallery', label: 'Gallery page', slots: head('gallery', 'Gallery', 'Moments from our *work*', 'Coaching centres, Rahbar-e-Banaat, relief work and community gatherings.') },
  { id: 'directory', label: 'Directory page', slots: head('directory', 'Worldwide directory', 'Expatriates from Bihar & Jharkhand', 'Find fellow NRIs near you, or add yourself so others can find you.') },
  {
    id: 'volunteer', label: 'Volunteer page', slots: [
      ...head('volunteer', 'Volunteer with us', 'Lend your *time and skills*',
        'Mentor a student, teach at a coaching centre, sponsor a student’s education or join the chapter in your district. Tell us how you would like to help and our team will get in touch.'),
      { key: 'volunteer.extra', label: 'Extra information above the form', type: 'markdown', default: '' },
      { key: 'volunteer.thanks', label: 'After sending: next steps', type: 'text', default: 'Someone from the Rahbar team will call or write to you, usually within a week, to talk about how you can help.' },
    ],
  },
  {
    id: 'contact', label: 'Contact page', slots: [
      ...head('contact', 'Get in touch', 'Contact us', 'Want to sponsor, volunteer, or need help? Write to us, or email us.'),
      { key: 'contact.extra', label: 'Extra information (address, bank details …)', type: 'markdown', default: '' },
    ],
  },
];

export const SLOTS: Record<string, Slot> = Object.fromEntries(SLOT_GROUPS.flatMap((g) => g.slots).map((s) => [s.key, s]));
