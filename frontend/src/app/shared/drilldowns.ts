import { ApiService } from '../core/services/api.service';
import { PopupList } from './list-popup.component';
import { InstallmentRow } from './installments';

/** Opens a popup (loading), then fills it from an API call; a failed call closes it and reports the error. */
function open<T>(set: (l: PopupList | null) => void, list: Omit<PopupList, 'rows' | 'loading'>,
                 call: { subscribe: (o: { next: (v: T) => void; error: (e: unknown) => void }) => unknown },
                 rows: (v: T) => Record<string, unknown>[], onError: (e: unknown) => void): void {
  const shown: PopupList = { ...list, rows: [], loading: true };
  set(shown);
  call.subscribe({
    next: (v) => { shown.rows = rows(v); shown.loading = false; set({ ...shown }); },
    error: (e) => { set(null); onError(e); }
  });
}

/** A sponsor's students now (needs Sponsorships: view). */
export function sponsorStudents(api: ApiService, sponsor: { id: number; name: string; user_id?: string | null },
                                set: (l: PopupList | null) => void, onError: (e: unknown) => void): void {
  open<{ students: Record<string, unknown>[] }>(set, {
    title: 'Students of ' + sponsor.name, subtitle: sponsor.user_id ?? undefined, empty: 'No students mapped to this sponsor.',
    columns: [{ key: 'user_id', label: 'Student ID' }, { key: 'name', label: 'Name' }, { key: 'year', label: 'Session year' },
      { key: 'chapter_name', label: 'Chapter' }, { key: 'on_behalf_text', label: 'On behalf of' }, { key: 'status', label: 'Account' }]
  }, api.get(`/admin/sponsors/${sponsor.id}`), (r) => r.students ?? [], onError);
}

/** A sponsor's overdue installments, oldest first (needs Payment Records: view). */
export function sponsorOverdue(api: ApiService, sponsor: { name: string; user_id: string },
                               set: (l: PopupList | null) => void, onError: (e: unknown) => void): void {
  open<{ rows: InstallmentRow[] }>(set, {
    title: 'Overdue installments of ' + sponsor.name, subtitle: sponsor.user_id, empty: 'Nothing overdue.',
    columns: [{ key: 'student_code', label: 'Student ID' }, { key: 'student_name', label: 'Student' },
      { key: 'installment', label: 'Installment' }, { key: 'due_date', label: 'Due date', type: 'date' }]
  }, api.get('/admin/payment-records', { q: sponsor.user_id, status: 'Overdue', page: 0, size: 200 }),
  (r) => (r.rows ?? []).filter((i) => i.sponsor_code === sponsor.user_id)
    .sort((a, b) => a.due_date.localeCompare(b.due_date))
    .map((i) => ({ ...i, installment: 'Installment ' + i.installment_no })), onError);
}

/** One student's installments (needs Payment Records: view). */
export function studentInstallments(api: ApiService, student: { id: number; name: string; code?: string | null },
                                    set: (l: PopupList | null) => void, onError: (e: unknown) => void): void {
  open<{ installments: InstallmentRow[] }>(set, {
    title: 'Installments of ' + student.name, subtitle: student.code ?? undefined, empty: 'No installments yet.',
    columns: [{ key: 'installment', label: 'Installment' }, { key: 'due_date', label: 'Due date', type: 'date' },
      { key: 'status', label: 'Status', type: 'badge' }, { key: 'paid_date', label: 'Paid on', type: 'date' }]
  }, api.get(`/admin/students/${student.id}/installments`),
  (r) => (r.installments ?? []).map((i) => ({ ...i, installment: 'Installment ' + i.installment_no })), onError);
}
