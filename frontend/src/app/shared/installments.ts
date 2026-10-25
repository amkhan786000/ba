/**
 * One installment between a sponsor and a student (see the backend's PaymentInstallmentService). Amounts stay in
 * Rahbar but are never sent to a screen: everyone sees installments ("Installment 2 · Paid").
 */
export interface InstallmentRow {
  installment_id: number;
  installment_no: number;
  due_date: string;
  /**
   * Paid; Due (within the Payment Config's "show as due" days before its date); Overdue (date passed); Not Due;
   * Not needed (the student graduated before it was paid: never due).
   */
  status: 'Paid' | 'Due' | 'Overdue' | 'Not Due' | 'Not needed';
  student_id: number;
  sponsor_id: number;
  student_code?: string | null;
  student_name?: string | null;
  sponsor_code?: string | null;
  sponsor_name?: string | null;
  /** Payment Records: the student's study status (payments only while STUDYING). */
  student_study_status?: string | null;
  payment_id: number | null;
  paid_date: string | null;
  receipt_url: string | null;
  student_proof_url: string | null;
}

/** Paid green, Due red, Overdue dark red, Not Due orange, Not needed grey. */
export function installmentBadge(status: string): string {
  switch (status) {
    case 'Paid': return 'badge-inst-paid';
    case 'Due': return 'badge-inst-due';
    case 'Overdue': return 'badge-inst-overdue';
    case 'Not needed': return 'badge-inst-not-needed';
    default: return 'badge-inst-not-due';
  }
}

/** "3 paid · 1 due · 8 to come" for a list of installments (amounts are never shown). */
export function installmentSummary(rows: { status: string }[]): string {
  const n = (s: string) => rows.filter((r) => r.status === s).length;
  const parts = [`${n('Paid')} paid`];
  if (n('Overdue')) parts.push(`${n('Overdue')} overdue`);
  if (n('Due')) parts.push(`${n('Due')} due`);
  if (n('Not Due')) parts.push(`${n('Not Due')} to come`);
  if (n('Not needed')) parts.push(`${n('Not needed')} not needed`);
  return parts.join(' · ');
}

/** New payments only while the student is studying (on hold, graduated and dropped out: closed). No status = studying. */
export function takesPayments(studyStatus: unknown): boolean {
  return !studyStatus || String(studyStatus).toUpperCase() === 'STUDYING';
}
