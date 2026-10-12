/**
 * Badge colour of a student's study status (separate from the login account's Active / Inactive). No status counts
 * as Studying. The statuses and their labels come from the server: StudyStatusService.
 */
export function studyStatusBadge(status: string | null | undefined): string {
  switch (status || 'STUDYING') {
    case 'STUDYING': return 'badge-success';
    case 'ON_HOLD': return 'badge-warning';
    case 'GRADUATED': return 'badge-primary';
    default: return 'badge-secondary';
  }
}
