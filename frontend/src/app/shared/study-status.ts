/** A student's study status (separate from the login account's Active / Inactive). No status counts as Studying. */
export const STUDY_STATUS_LABELS: Record<string, string> = {
  STUDYING: 'Studying',
  ON_HOLD: 'On hold',
  GRADUATED: 'Graduated',
  DROPPED_OUT: 'Dropped out'
};

export const STUDY_STATUSES = Object.keys(STUDY_STATUS_LABELS);

export function studyStatusLabel(status: string | null | undefined): string {
  return STUDY_STATUS_LABELS[status || 'STUDYING'] ?? status ?? '';
}

export function studyStatusBadge(status: string | null | undefined): string {
  switch (status || 'STUDYING') {
    case 'STUDYING': return 'badge-success';
    case 'ON_HOLD': return 'badge-warning';
    case 'GRADUATED': return 'badge-primary';
    default: return 'badge-secondary';
  }
}
