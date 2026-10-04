export interface AuthUser {
  userId: string;
  name: string;
  roleId: number;
  status: string;
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
    case ROLE.OFFICE_COORDINATOR: return '/office/dashboard';
    default: return '/';
  }
}
