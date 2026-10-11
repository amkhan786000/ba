/** Rahbar-e-Banaat (REB): shapes of the API rows and the labels shown for codes. */

export interface RebSession { id: number; name: string; start_date: string; end_date: string; is_current: boolean; status: string }
export interface RebClass { id: number; name: string; track: string; level_order: number; next_class_id: number | null; board_exam: boolean; active: boolean }
export interface RebSectionRow {
  id: number; session_id: number; class_id: number; name: string; class_teacher_staff_id: number | null; room: string | null;
  capacity: number | null; class_name: string; track: string; label: string; class_teacher_name: string | null; girls: number;
}
export interface RebSubject { id: number; name: string; code: string | null; track: string; assessment: string; active: boolean }
export interface RebStaff {
  id: number; user_id: number | null; name: string; designation: string; phone: string | null; email: string | null;
  qualification: string | null; joined_on: string | null; left_on: string | null; status: string; notes: string | null;
  login: string | null; subjects_this_session?: number;
}
export interface RebStudentRow {
  id: number; admission_no: string; name: string; date_of_birth: string | null; section_id: number | null; class_id: number | null;
  section: string | null; roll_no: string | null; residential: boolean; fee_plan: string; status: string;
  guardian_name: string | null; guardian_relation: string | null; guardian_phone: string | null;
}
export interface RebGuardian {
  id?: number; relation: string; name: string; phone: string | null; whatsapp: string | null; email: string | null;
  occupation: string | null; monthly_income?: number | null; is_primary: boolean; may_pick_up: boolean;
}

export const TRACKS: Record<string, string> = { MADRASA: 'Madrasa', SCHOOL: 'School', BOTH: 'Madrasa & School' };
export const FEE_PLANS: Record<string, string> = { SELF: 'Self-paying', SPONSORED: 'Sponsored', FREE: 'Free' };
export const STATUSES: Record<string, string> = { ACTIVE: 'Studying', LEFT: 'Left', PASSED_OUT: 'Passed out' };
export const RELATIONS: Record<string, string> = {
  FATHER: 'Father', MOTHER: 'Mother', BROTHER: 'Brother', SISTER: 'Sister', UNCLE: 'Uncle', AUNT: 'Aunt',
  GRANDPARENT: 'Grandparent', GUARDIAN: 'Guardian'
};
export const CATEGORIES = ['GEN', 'EWS', 'BC', 'EBC', 'SC', 'ST'];
export const DESIGNATIONS: Record<string, string> = { TEACHER: 'Teacher', PRINCIPAL: 'Principal / Nazima', WARDEN: 'Warden', OFFICE: 'Office', OTHER: 'Other' };
export const ASSESSMENTS: Record<string, string> = { WRITTEN: 'Written', ORAL: 'Oral', PRACTICAL: 'Practical' };
export const DOCUMENT_KINDS: Record<string, string> = {
  BIRTH_CERTIFICATE: 'Birth certificate', AADHAAR: 'Aadhaar', PHOTO: 'Photo', TC: 'Transfer certificate (previous school)',
  MARKSHEET: 'Marksheet', OTHER: 'Other'
};
export const ENROLLMENT_STATUSES: Record<string, string> = {
  ACTIVE: 'Studying', PROMOTED: 'Promoted', DETAINED: 'Kept in the same class', LEFT: 'Left', COMPLETED: 'Completed'
};

export function statusBadge(status: string): string {
  return status === 'ACTIVE' ? 'badge-success' : status === 'PASSED_OUT' ? 'badge-info' : 'badge-secondary';
}

export function planBadge(plan: string): string {
  return plan === 'SPONSORED' ? 'badge-primary' : plan === 'FREE' ? 'badge-warning' : 'badge-light border';
}

/** A blank guardian for the admission form. */
export function newGuardian(primary = false): RebGuardian {
  return { relation: 'FATHER', name: '', phone: '', whatsapp: '', email: '', occupation: '', monthly_income: null, is_primary: primary, may_pick_up: true };
}

/** API body of a guardian (camelCase). */
export function guardianBody(g: RebGuardian): Record<string, unknown> {
  return {
    relation: g.relation, name: g.name, phone: g.phone, whatsapp: g.whatsapp, email: g.email, occupation: g.occupation,
    monthlyIncome: g.monthly_income, primary: g.is_primary, mayPickUp: g.may_pick_up
  };
}
