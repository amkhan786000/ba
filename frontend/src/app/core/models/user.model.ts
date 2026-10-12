export interface AuthUser {
  /** users.id: identifies the user in API calls and links. */
  id: number;
  /** The user's code (users.user_id), for display only. */
  userId: string;
  name: string;
  /** The main role (home screen). */
  roleId: number;
  /** The main role and any additional ones (a person can be e.g. Super Admin and Sponsor). */
  roleIds?: number[];
  status: string;
  /** True until the user replaces the password an admin / bulk upload gave them. */
  mustChangePassword?: boolean;
  /** True when the user has no real email address (none, or a placeholder ...@rahbar.com). */
  emailMissing?: boolean;
  /** Name of the user's role (custom roles have no built-in label). */
  roleName?: string;
  /** Admin-screen permission keys of the user's role, e.g. "USERS:EDIT" (EDIT implies VIEW). */
  permissions?: string[];
  /** ALL, CHAPTER (own chapter only) or RCC (own RCC center only). */
  scope?: string;
  chapterId?: number | null;
  rccCenterId?: number | null;
  /** All the user's roles with their names (main first); portal roles have their own area. */
  roles?: { id: number; name: string; main: boolean; portal: boolean }[];
  /** How dates are shown (Administration > Application Settings), e.g. DD/MM/YYYY. */
  dateFormat?: string;
  /** "View as": the one role the user is acting as right now (null: all their roles). */
  actingRoleId?: number | null;
}

/** Admin screens permissions are granted on (same names as the backend's Section enum). */
export type Section = 'DASHBOARD' | 'USERS' | 'ROLES' | 'CHAPTERS' | 'RCC_CENTERS' | 'COURSES' | 'PAYMENT_CONFIG' | 'ONLINE_PAYMENTS'
  | 'PAYMENT_DUES' | 'REPORTS' | 'APPLICATION_PERIOD' | 'APPLICATIONS' | 'SPONSORSHIPS' | 'STUDENTS' | 'ACTIVITY' | 'MESSAGES'
  | 'SPONSOR_DETAILS' | 'PROGRESS_DUE_DATES' | 'CHAPTER_DASHBOARD' | 'DATA_QUALITY' | 'PAYMENT_RECORDS' | 'EMAIL_LOG' | 'EMAIL_TEMPLATES' | 'ALUMNI' | 'STUDENT_NOTES' | 'RCC_STUDENTS' | 'VOLUNTEERS' | 'VOLUNTEER_OPTIONS' | 'MENTORING'
  | 'WEBSITE_BLOG' | 'WEBSITE_BLOG_PUBLISH' | 'WEBSITE_BLOG_CATEGORIES' | 'WEBSITE_STORIES' | 'WEBSITE_STORIES_PUBLISH' | 'WEBSITE_GALLERY' | 'WEBSITE_DIRECTORY'
  | 'WEBSITE_PROJECTS' | 'WEBSITE_NEWS' | 'WEBSITE_MESSAGES' | 'WEBSITE_PAGES'
  | 'DOCUMENTS' | 'DOCUMENT_CATEGORIES' | 'FAQS' | 'APP_SETTINGS' | 'FINANCE'
  | 'REB_DASHBOARD' | 'REB_SETUP' | 'REB_STAFF' | 'REB_STUDENTS' | 'REB_STUDENT_PRIVATE' | 'REB_REPORTS'
  | 'REB_FEES' | 'REB_FEE_APPROVALS' | 'REB_SPONSORSHIPS' | 'REB_ATTENDANCE' | 'REB_EXAMS' | 'REB_ENQUIRIES' | 'REB_QURAN' | 'REB_HOSTEL';

/** Roles that use their own portal (coordinator, convenor, sponsor, student) instead of the admin screens. */
export const PORTAL_ROLES = [3, 4, 5, 6];

/** Groups of the admin menu (a group shows when the user may open at least one of its entries). */
export const NAV_GROUPS: Record<string, { label: string; icon: string }> = {
  students: { label: 'Students', icon: 'mdi-school' },
  sponsors: { label: 'Sponsors & Payments', icon: 'mdi-cash-multiple' },
  community: { label: 'Community', icon: 'mdi-account-group' },
  reb: { label: 'Rahbar-e-Banaat', icon: 'mdi-human-female-girl' },
  website: { label: 'Website', icon: 'mdi-web' },
  communication: { label: 'Communication', icon: 'mdi-bullhorn' },
  administration: { label: 'Administration', icon: 'mdi-shield-account' }
};

/** Admin menu, in order; each entry needs VIEW on its section. Entries of a group stay together. */
/** Entries without a section are shown to everyone in the admin area (e.g. Documents, which filters per document). */
export const ADMIN_LINKS: { path: string; label: string; icon: string; section?: Section; group?: string }[] = [
  { path: '/admin/dashboard', label: 'Dashboard', icon: 'mdi-view-dashboard', section: 'DASHBOARD' },

  { path: '/admin/applications', label: 'Applications', icon: 'mdi-file-document', section: 'APPLICATIONS', group: 'students' },
  { path: '/admin/application-period', label: 'Application Period', icon: 'mdi-calendar', section: 'APPLICATION_PERIOD', group: 'students' },
  { path: '/admin/students', label: 'Student Directory', icon: 'mdi-account-details', section: 'STUDENTS', group: 'students' },
  { path: '/admin/rcc-students', label: 'Coaching Center Students', icon: 'mdi-account-group', section: 'RCC_STUDENTS', group: 'students' },
  { path: '/admin/alumni', label: 'Alumni', icon: 'mdi-school', section: 'ALUMNI', group: 'students' },
  { path: '/admin/student-notes', label: 'Notes to Sponsors', icon: 'mdi-message-text-outline', section: 'STUDENT_NOTES', group: 'students' },
  { path: '/admin/mentoring', label: 'Mentoring', icon: 'mdi-account-tie-voice', section: 'MENTORING', group: 'students' },
  { path: '/admin/courses', label: 'Courses', icon: 'mdi-book-open', section: 'COURSES', group: 'students' },
  { path: '/admin/progress-due-dates', label: 'Progress Due Dates', icon: 'mdi-calendar-clock', section: 'PROGRESS_DUE_DATES', group: 'students' },

  { path: '/admin/sponsorships', label: 'Sponsorships', icon: 'mdi-account-switch', section: 'SPONSORSHIPS', group: 'sponsors' },
  { path: '/admin/sponsor-followup', label: 'Sponsor Follow-up', icon: 'mdi-account-heart', section: 'SPONSORSHIPS', group: 'sponsors' },
  { path: '/admin/payment-records', label: 'Payment Records', icon: 'mdi-receipt', section: 'PAYMENT_RECORDS', group: 'sponsors' },
  { path: '/admin/online-payments', label: 'Online Payments', icon: 'mdi-credit-card-outline', section: 'ONLINE_PAYMENTS', group: 'sponsors' },
  { path: '/admin/payment-dues', label: 'Payment Dues', icon: 'mdi-alarm', section: 'PAYMENT_DUES', group: 'sponsors' },
  { path: '/admin/system-configuration', label: 'Payment Config', icon: 'mdi-settings', section: 'PAYMENT_CONFIG', group: 'sponsors' },

  { path: '/admin/chapter-dashboard', label: 'Chapter Dashboard', icon: 'mdi-view-quilt', section: 'CHAPTER_DASHBOARD', group: 'community' },
  { path: '/admin/chapters', label: 'Chapters', icon: 'mdi-map-marker-multiple', section: 'CHAPTERS', group: 'community' },
  { path: '/admin/rcc-centers', label: 'RCC Centers', icon: 'mdi-bank', section: 'RCC_CENTERS', group: 'community' },
  { path: '/admin/volunteers', label: 'Volunteers', icon: 'mdi-hand-heart', section: 'VOLUNTEERS', group: 'community' },
  { path: '/admin/volunteer-options', label: 'Volunteer Form Options', icon: 'mdi-format-list-checks', section: 'VOLUNTEER_OPTIONS', group: 'community' },

  { path: '/admin/reb/dashboard', label: 'REB Dashboard', icon: 'mdi-view-dashboard-outline', section: 'REB_DASHBOARD', group: 'reb' },
  { path: '/admin/reb/students', label: 'Girls', icon: 'mdi-human-female', section: 'REB_STUDENTS', group: 'reb' },
  { path: '/admin/reb/enquiries', label: 'Admission Enquiries', icon: 'mdi-account-question', section: 'REB_ENQUIRIES', group: 'reb' },
  { path: '/admin/reb/quran', label: 'Quran / Hifz', icon: 'mdi-book-open-variant', section: 'REB_QURAN', group: 'reb' },
  { path: '/admin/reb/hostel', label: 'Hostel Leave', icon: 'mdi-exit-run', section: 'REB_HOSTEL', group: 'reb' },
  { path: '/admin/reb/attendance', label: 'Attendance', icon: 'mdi-calendar-check', section: 'REB_ATTENDANCE', group: 'reb' },
  { path: '/admin/reb/exams', label: 'Exams & Results', icon: 'mdi-clipboard-text', section: 'REB_EXAMS', group: 'reb' },
  { path: '/admin/reb/fees', label: 'REB Fees', icon: 'mdi-cash-multiple', section: 'REB_FEES', group: 'reb' },
  { path: '/admin/reb/sponsorships', label: 'REB Sponsorships', icon: 'mdi-hand-heart', section: 'REB_SPONSORSHIPS', group: 'reb' },
  { path: '/admin/reb/staff', label: 'Teachers & Staff', icon: 'mdi-teach', section: 'REB_STAFF', group: 'reb' },
  { path: '/admin/reb/reports', label: 'REB Reports', icon: 'mdi-chart-bar', section: 'REB_REPORTS', group: 'reb' },
  { path: '/admin/reb/setup', label: 'REB Setup', icon: 'mdi-settings', section: 'REB_SETUP', group: 'reb' },

  { path: '/admin/website/blog', label: 'Knowledge Hub', icon: 'mdi-post-outline', section: 'WEBSITE_BLOG', group: 'website' },
  { path: '/admin/website/stories', label: 'Success Stories', icon: 'mdi-star-outline', section: 'WEBSITE_STORIES', group: 'website' },
  { path: '/admin/website/gallery', label: 'Gallery', icon: 'mdi-image-multiple', section: 'WEBSITE_GALLERY', group: 'website' },
  { path: '/admin/website/directory', label: 'Directory', icon: 'mdi-earth', section: 'WEBSITE_DIRECTORY', group: 'website' },
  { path: '/admin/website/projects', label: 'Projects', icon: 'mdi-briefcase-outline', section: 'WEBSITE_PROJECTS', group: 'website' },
  { path: '/admin/website/news', label: 'News & Events', icon: 'mdi-newspaper', section: 'WEBSITE_NEWS', group: 'website' },
  { path: '/admin/website/messages', label: 'Messages', icon: 'mdi-email-outline', section: 'WEBSITE_MESSAGES', group: 'website' },
  { path: '/admin/website/pages', label: 'Pages', icon: 'mdi-file-document-edit-outline', section: 'WEBSITE_PAGES', group: 'website' },

  { path: '/admin/broadcasts', label: 'Broadcast Messages', icon: 'mdi-bullhorn', section: 'MESSAGES', group: 'communication' },
  { path: '/admin/email-templates', label: 'Email Templates', icon: 'mdi-email-edit-outline', section: 'EMAIL_TEMPLATES', group: 'communication' },
  { path: '/admin/email-log', label: 'Email Log', icon: 'mdi-email-search-outline', section: 'EMAIL_LOG', group: 'communication' },

  { path: '/admin/users', label: 'Manage Users', icon: 'mdi-account-multiple', section: 'USERS', group: 'administration' },
  { path: '/admin/roles', label: 'Roles & Permissions', icon: 'mdi-shield-account', section: 'ROLES', group: 'administration' },
  { path: '/admin/reports', label: 'Reports', icon: 'mdi-chart-bar', section: 'REPORTS', group: 'administration' },
  { path: '/admin/finance', label: 'Income & Expenses', icon: 'mdi-scale-balance', section: 'FINANCE', group: 'administration' },
  { path: '/admin/documents', label: 'Documents', icon: 'mdi-file-document-box-multiple-outline', group: 'administration' },
  { path: '/admin/faq', label: 'Help & FAQ', icon: 'mdi-help-circle-outline', group: 'administration' },
  { path: '/admin/app-settings', label: 'Application Settings', icon: 'mdi-tune-variant', section: 'APP_SETTINGS', group: 'administration' },
  { path: '/admin/data-quality', label: 'Data Quality', icon: 'mdi-clipboard-check-outline', section: 'DATA_QUALITY', group: 'administration' },
  { path: '/admin/activity', label: 'Activity Log', icon: 'mdi-history', section: 'ACTIVITY', group: 'administration' }
];

/** True when the user has the role, as their main role or an additional one. */
export function hasRole(user: AuthUser | null, roleId: number): boolean {
  return !!user && (user.roleId === roleId || (user.roleIds ?? []).includes(roleId));
}

/** True when the user may use the section: EDIT implies VIEW; the Super Admin may do everything. */
export function canAccess(user: AuthUser | null, section: Section, level: 'VIEW' | 'EDIT' = 'VIEW'): boolean {
  if (!user) return false;
  // Super Admin may do everything, unless viewing as another of their roles.
  if (user.actingRoleId != null ? user.actingRoleId === ROLE.SUPER_ADMIN : hasRole(user, ROLE.SUPER_ADMIN)) return true;
  const p = user.permissions ?? [];
  return p.includes(section + ':EDIT') || (level === 'VIEW' && p.includes(section + ':VIEW'));
}

/** Every role of the user: the main one and any additional ones. */
export function rolesOf(user: AuthUser | null): number[] {
  if (!user) return [];
  return [...new Set([user.roleId, ...(user.roleIds ?? [])])];
}

/** May the user use the admin area? Only with a staff role (any role other than the portal ones). */
export function hasAdminArea(user: AuthUser | null): boolean {
  return rolesOf(user).some((r) => !PORTAL_ROLES.includes(r));
}

const PORTAL_LABELS: Record<number, string> = {
  [3]: 'Coordinator portal', [4]: 'Convenor portal', [5]: 'Sponsor portal', [6]: 'Student portal'
};

/** The areas the user can switch between (admin area and each portal they have a role for), main role's first. */
export function areasOf(user: AuthUser | null): { label: string; path: string; root: string }[] {
  const out: { label: string; path: string; root: string }[] = [];
  const main = user?.roleId ?? 0;
  const admin = hasAdminArea(user)
    ? { label: 'Admin area', path: ADMIN_LINKS.find((l) => l.section && canAccess(user, l.section))?.path ?? '/admin/profile', root: '/admin' } : null;
  if (admin && !PORTAL_ROLES.includes(main)) out.push(admin);
  for (const r of [main, ...rolesOf(user).filter((x) => x !== main)]) {
    if (PORTAL_ROLES.includes(r)) {
      const path = dashboardPathForRole(r);
      out.push({ label: PORTAL_LABELS[r], path, root: '/' + path.split('/')[1] });
    }
  }
  if (admin && PORTAL_ROLES.includes(main)) out.push(admin);
  return out;
}

/** Where a user lands after signing in: their portal, or the first admin screen they may open. */
export function landingPath(user: AuthUser | null): string {
  if (!user) return '/login';
  if (PORTAL_ROLES.includes(user.roleId)) return dashboardPathForRole(user.roleId);
  return ADMIN_LINKS.find((l) => l.section && canAccess(user, l.section))?.path ?? '/admin/profile';
}

export const ROLE = {
  SUPER_ADMIN: 1,
  APP_ADMIN: 2,
  COORDINATOR: 3,
  CONVENOR: 4,
  SPONSOR: 5,
  STUDENT: 6,
  MANAGEMENT: 7,
  OFFICE_COORDINATOR: 8
};

/** Display names of the built-in roles (shown under the user's name in the top bar). */
export const ROLE_LABELS: Record<number, string> = {
  1: 'Super Admin',
  2: 'Application Admin',
  3: 'Coordinator',
  4: 'Convenor',
  5: 'Sponsor',
  6: 'Student',
  7: 'Management',
  8: 'Office Coordinator'
};

export function dashboardPathForRole(roleId: number): string {
  switch (roleId) {
    case ROLE.SUPER_ADMIN:
    case ROLE.APP_ADMIN: return '/admin/dashboard';
    case ROLE.COORDINATOR: return '/coordinator/dashboard';
    case ROLE.CONVENOR: return '/convenor/dashboard';
    case ROLE.SPONSOR: return '/sponsor/dashboard';
    case ROLE.STUDENT: return '/student/dashboard';
    default: return '/';
  }
}
