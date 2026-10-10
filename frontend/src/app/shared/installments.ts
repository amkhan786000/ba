/** One installment between a sponsor and a student (see the backend's PaymentInstallmentService). */
export interface InstallmentRow {
  installment_id: number;
  installment_no: number;
  due_date: string;
  amount: number;
  /** Paid; Due (within the Payment Config's "show as due" days before its date); Overdue (date passed); Not Due. */
  status: 'Paid' | 'Due' | 'Overdue' | 'Not Due';
  student_id: number;
  sponsor_id: number;
  student_code?: string | null;
  student_name?: string | null;
  sponsor_code?: string | null;
  sponsor_name?: string | null;
  payment_id: number | null;
  paid_amount: number | null;
  paid_date: string | null;
  receipt_url: string | null;
  student_proof_url: string | null;
}

/** Paid green, Due red, Overdue dark red, Not Due orange. */
export function installmentBadge(status: string): string {
  switch (status) {
    case 'Paid': return 'badge-inst-paid';
    case 'Due': return 'badge-inst-due';
    case 'Overdue': return 'badge-inst-overdue';
    default: return 'badge-inst-not-due';
  }
}
