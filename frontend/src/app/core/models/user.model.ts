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
  MANAGEMENT: 7
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
