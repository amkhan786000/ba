export interface AuthUser {
  /** users.id: identifies the user in API calls and links. */
  id: number;
  /** The user's code (users.user_id), for display only. */
  userId: string;
  name: string;
  roleId: number;
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
}

/** Admin screens permissions are granted on (same names as the backend's Section enum). */
export type Section = 'DASHBOARD' | 'USERS' | 'ROLES' | 'CHAPTERS' | 'RCC_CENTERS' | 'COURSES' | 'PAYMENT_CONFIG'
  | 'PAYMENT_DUES' | 'REPORTS' | 'APPLICATION_PERIOD' | 'APPLICATIONS' | 'SPONSORSHIPS' | 'STUDENTS' | 'ACTIVITY' | 'MESSAGES'
  | 'SPONSOR_DETAILS' | 'PROGRESS_DUE_DATES' | 'CHAPTER_DASHBOARD' | 'DATA_QUALITY' | 'PAYMENT_RECORDS' | 'EMAIL_LOG' | 'EMAIL_TEMPLATES' | 'ALUMNI';

/** Roles that use their own portal (coordinator, convenor, sponsor, student) instead of the admin screens. */
export const PORTAL_ROLES = [3, 4, 5, 6];

/** Admin menu, in order; each entry needs VIEW on its section. */
export const ADMIN_LINKS: { path: string; label: string; icon: string; section: Section }[] = [
  { path: '/admin/dashboard', label: 'Dashboard', icon: 'mdi-view-dashboard', section: 'DASHBOARD' },
  { path: '/admin/chapter-dashboard', label: 'Chapter Dashboard', icon: 'mdi-view-quilt', section: 'CHAPTER_DASHBOARD' },
  { path: '/admin/users', label: 'Manage Users', icon: 'mdi-account-multiple', section: 'USERS' },
  { path: '/admin/roles', label: 'Roles & Permissions', icon: 'mdi-shield-account', section: 'ROLES' },
  { path: '/admin/system-configuration', label: 'Payment Config', icon: 'mdi-settings', section: 'PAYMENT_CONFIG' },
  { path: '/admin/payment-dues', label: 'Payment Dues', icon: 'mdi-alarm', section: 'PAYMENT_DUES' },
  { path: '/admin/payment-records', label: 'Payment Records', icon: 'mdi-receipt', section: 'PAYMENT_RECORDS' },
  { path: '/admin/reports', label: 'Reports', icon: 'mdi-chart-bar', section: 'REPORTS' },
  { path: '/admin/application-period', label: 'App Period', icon: 'mdi-calendar', section: 'APPLICATION_PERIOD' },
  { path: '/admin/applications', label: 'Applications', icon: 'mdi-file-document', section: 'APPLICATIONS' },
  { path: '/admin/rcc-centers', label: 'RCC Centers', icon: 'mdi-bank', section: 'RCC_CENTERS' },
  { path: '/admin/chapters', label: 'Chapters', icon: 'mdi-map-marker-multiple', section: 'CHAPTERS' },
  { path: '/admin/courses', label: 'Courses', icon: 'mdi-book-open', section: 'COURSES' },
  { path: '/admin/sponsorships', label: 'Sponsorships', icon: 'mdi-account-switch', section: 'SPONSORSHIPS' },
  { path: '/admin/students', label: 'Student Directory', icon: 'mdi-account-details', section: 'STUDENTS' },
  { path: '/admin/alumni', label: 'Alumni', icon: 'mdi-school', section: 'ALUMNI' },
  { path: '/admin/progress-due-dates', label: 'Progress Due Dates', icon: 'mdi-calendar-clock', section: 'PROGRESS_DUE_DATES' },
  { path: '/admin/data-quality', label: 'Data Quality', icon: 'mdi-clipboard-check-outline', section: 'DATA_QUALITY' },
  { path: '/admin/broadcasts', label: 'Broadcast Messages', icon: 'mdi-bullhorn', section: 'MESSAGES' },
  { path: '/admin/email-templates', label: 'Email Templates', icon: 'mdi-email-edit-outline', section: 'EMAIL_TEMPLATES' },
  { path: '/admin/email-log', label: 'Email Log', icon: 'mdi-email-search-outline', section: 'EMAIL_LOG' },
  { path: '/admin/activity', label: 'Activity Log', icon: 'mdi-history', section: 'ACTIVITY' }
];

/** True when the user may use the section: EDIT implies VIEW; the Super Admin may do everything. */
export function canAccess(user: AuthUser | null, section: Section, level: 'VIEW' | 'EDIT' = 'VIEW'): boolean {
  if (!user) return false;
  if (user.roleId === ROLE.SUPER_ADMIN) return true;
  const p = user.permissions ?? [];
  return p.includes(section + ':EDIT') || (level === 'VIEW' && p.includes(section + ':VIEW'));
}

/** Where a user lands after signing in: their portal, or the first admin screen they may open. */
export function landingPath(user: AuthUser | null): string {
  if (!user) return '/login';
  if (PORTAL_ROLES.includes(user.roleId)) return dashboardPathForRole(user.roleId);
  return ADMIN_LINKS.find((l) => canAccess(user, l.section))?.path ?? '/admin/profile';
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
