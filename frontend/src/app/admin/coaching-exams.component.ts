import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';
import { CoachingCenter, CoachingScope } from './coaching-students.component';

interface Subject { id: number; name: string; sort_order: number; active: boolean }
interface ExamSubject { subject_id: number; name: string; max_marks: number }
interface Prize { position: number; name: string; total: number }
interface Exam {
  id: number; rcc_center_id: number; center_name: string | null; class_level: number; name: string; exam_date: string;
  prizes_generated_at: string | null; subjects: ExamSubject[]; prizes: Prize[];
}
type Answer = CoachingScope & { rows: Exam[]; subjects: Subject[] };
interface SheetRow {
  student_id: number; name: string; admission_no: string | null; status: string; marks: Record<string, number | undefined>;
  total: number | null; percent: number | null; prize: number | null;
}
interface Sheet { exam: Exam; subjects: ExamSubject[]; max_total: number; rows: SheetRow[] }

interface ExamForm { rccCenterId: number | null; classLevel: number; name: string; examDate: string; subjects: { subjectId: number | null; maxMarks: number | null }[] }

/**
 * Coaching Center > Exams: monthly exams per class, marks per subject, and the 1st / 2nd / 3rd prizes from the totals
 * (equal totals share a position). Teachers see everything; managing needs Coaching exams: manage.
 */
@Component({
  selector: 'app-coaching-exams',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, AppDatePipe],
  styles: [`
    .marks input { width: 72px; }
    .prize-1 { background: #fff4cc; } .prize-2 { background: #eef1f4; } .prize-3 { background: #f6e7dc; }
  `],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Coaching Center Exams</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="d-flex flex-wrap mb-3" style="gap: 8px">
      <button *ngIf="canEdit" class="btn btn-primary" (click)="openNew()" [disabled]="!centers.length"><i class="mdi mdi-plus mr-1"></i>New exam</button>
      <select *ngIf="!scoped" class="form-control w-auto" [(ngModel)]="centerId" (ngModelChange)="load()" aria-label="Center">
        <option [ngValue]="null">All centers</option>
        <option *ngFor="let c of centers" [ngValue]="c.rcc_center_id">{{ c.center_name }}</option>
      </select>
      <select class="form-control w-auto" [(ngModel)]="classLevel" (ngModelChange)="load()" aria-label="Class">
        <option [ngValue]="null">All classes</option>
        <option *ngFor="let c of classes" [ngValue]="c">Class {{ c }}</option>
      </select>
    </div>

    <div class="card"><div class="card-body">
      <div class="table-responsive"><table class="table table-sm table-centered mb-0">
        <thead><tr><th>Exam</th><th>Date</th><th>Class</th><th *ngIf="!scoped">Center</th><th>Subjects</th><th>Prizes</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!data"><td colspan="7" class="text-center"><span class="spinner-border spinner-border-sm"></span></td></tr>
          <tr *ngIf="data && !data.rows.length"><td colspan="7" class="text-center text-muted">No exams yet.</td></tr>
          <tr *ngFor="let e of data?.rows">
            <td><strong>{{ e.name }}</strong></td>
            <td>{{ e.exam_date | appDate }}</td>
            <td>{{ e.class_level }}</td>
            <td *ngIf="!scoped">{{ e.center_name }}</td>
            <td class="small">{{ subjectsText(e) }}</td>
            <td class="small">
              <div *ngFor="let p of e.prizes">{{ ordinal(p.position) }}: {{ p.name }} <span class="text-muted">({{ p.total }})</span></div>
              <span *ngIf="!e.prizes.length" class="text-muted">Not generated</span>
            </td>
            <td class="text-right text-nowrap">
              <button class="btn btn-xs btn-outline-primary" (click)="openSheet(e)">{{ canEdit ? 'Enter marks' : 'Marks' }}</button>
              <button *ngIf="canEdit" class="btn btn-xs btn-warning ml-1" (click)="generate(e)" [disabled]="busy"
                      title="Work out the 1st, 2nd and 3rd prizes from the totals"><i class="mdi mdi-trophy"></i> {{ e.prizes.length ? 'Regenerate' : 'Generate' }} prizes</button>
              <button *ngIf="canEdit" class="btn btn-xs btn-outline-secondary ml-1" (click)="openEdit(e)" title="Edit exam"><i class="mdi mdi-pencil"></i></button>
              <button *ngIf="canEdit" class="btn btn-xs btn-outline-danger ml-1" (click)="remove(e)" title="Delete exam"><i class="mdi mdi-delete"></i></button>
            </td>
          </tr>
        </tbody>
      </table></div>
    </div></div>

    <div class="card" *ngIf="canEdit && !scoped && subjectList"><div class="card-body">
      <h4 class="header-title">Subjects</h4>
      <p class="text-muted small">The subjects exams can have (shared by every center).</p>
      <div class="d-flex flex-wrap mb-2" style="gap: 6px">
        <span *ngFor="let s of subjectList" class="badge border p-2" [ngClass]="s.active ? 'badge-light' : 'badge-secondary'">{{ s.name }}
          <a href="" class="ml-1" (click)="$event.preventDefault(); toggleSubject(s)" [title]="s.active ? 'Hide from new exams' : 'Use again'">
            <i class="mdi" [ngClass]="s.active ? 'mdi-eye-off' : 'mdi-eye'"></i></a>
          <a href="" class="ml-1 text-danger" (click)="$event.preventDefault(); deleteSubject(s)" title="Delete"><i class="mdi mdi-close"></i></a></span>
      </div>
      <div class="d-flex" style="gap: 6px; max-width: 420px">
        <input class="form-control form-control-sm" maxlength="80" [(ngModel)]="newSubject" placeholder="New subject, e.g. Computer">
        <button class="btn btn-sm btn-outline-primary" (click)="addSubject()" [disabled]="!newSubject.trim()">Add</button>
      </div>
    </div></div>

    <!-- exam form -->
    <div *ngIf="form" class="modal fade show d-block" tabindex="-1" (click)="form = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">{{ editId ? 'Edit exam' : 'New exam' }}</h5>
            <button type="button" class="close" (click)="form = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <div *ngIf="formError" class="alert alert-danger">{{ formError }}</div>
            <div class="row">
              <div class="col-12 form-group"><label>Exam name *</label><input class="form-control" maxlength="100" [(ngModel)]="form.name" placeholder="e.g. Monthly test, July"></div>
              <div class="col-6 form-group"><label>Date *</label><input type="date" class="form-control" [(ngModel)]="form.examDate"></div>
              <div class="col-6 form-group"><label>Class *</label>
                <select class="form-control" [(ngModel)]="form.classLevel"><option *ngFor="let c of classes" [ngValue]="c">{{ c }}</option></select></div>
              <div class="col-12 form-group" *ngIf="!scoped"><label>Center *</label>
                <select class="form-control" [(ngModel)]="form.rccCenterId">
                  <option [ngValue]="null">-- Choose --</option>
                  <option *ngFor="let c of centers" [ngValue]="c.rcc_center_id">{{ c.center_name }}</option>
                </select></div>
            </div>
            <label>Subjects and maximum marks *</label>
            <div class="d-flex mb-2" style="gap: 6px" *ngFor="let s of form.subjects; let i = index">
              <select class="form-control form-control-sm" [(ngModel)]="s.subjectId" aria-label="Subject">
                <option [ngValue]="null">-- Subject --</option>
                <option *ngFor="let o of activeSubjects" [ngValue]="o.id">{{ o.name }}</option>
              </select>
              <input type="number" min="1" class="form-control form-control-sm" style="max-width: 110px" [(ngModel)]="s.maxMarks" placeholder="Max marks" aria-label="Maximum marks">
              <button class="btn btn-sm btn-light" (click)="form.subjects.splice(i, 1)" aria-label="Remove"><i class="mdi mdi-close"></i></button>
            </div>
            <button class="btn btn-sm btn-outline-secondary" (click)="addRow()">
              <i class="mdi mdi-plus"></i> Subject</button>
            <p *ngIf="editId" class="small text-muted mt-2 mb-0">Taking a subject off deletes its marks.</p>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="form = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="save()" [disabled]="saving || !form.name.trim() || !form.examDate">{{ saving ? 'Saving…' : 'Save' }}</button>
          </div>
        </div>
      </div>
    </div>

    <!-- marks sheet -->
    <div *ngIf="sheet" class="modal fade show d-block" tabindex="-1" (click)="closeSheet()">
      <div class="modal-dialog modal-xl modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">{{ sheet.exam.name }} <small class="text-muted">Class {{ sheet.exam.class_level }} · {{ sheet.exam.exam_date | appDate }}
              <span *ngIf="!scoped"> · {{ sheet.exam.center_name }}</span></small></h5>
            <button type="button" class="close" (click)="closeSheet()" aria-label="Close">&times;</button>
          </div>
          <div class="modal-body">
            <div *ngIf="sheetError" class="alert alert-danger">{{ sheetError }}</div>
            <div *ngIf="sheetMessage" class="alert alert-success">{{ sheetMessage }}</div>
            <p *ngIf="!sheet.rows.length" class="text-muted">No studying students in class {{ sheet.exam.class_level }} at this center.</p>
            <div class="table-responsive marks" *ngIf="sheet.rows.length"><table class="table table-sm mb-0">
              <thead class="thead-light"><tr><th>Student</th>
                <th *ngFor="let s of sheet.subjects" class="text-center">{{ s.name }}<div class="small text-muted">/ {{ s.max_marks }}</div></th>
                <th class="text-right">Total<div class="small text-muted">/ {{ sheet.max_total }}</div></th><th class="text-right">%</th><th>Prize</th></tr></thead>
              <tbody><tr *ngFor="let r of sheet.rows" [ngClass]="r.prize ? 'prize-' + r.prize : ''">
                <td>{{ r.name }} <span class="small text-muted">{{ r.admission_no }}</span>
                  <span *ngIf="r.status === 'LEFT'" class="badge badge-light border">Left</span></td>
                <td *ngFor="let s of sheet.subjects" class="text-center">
                  <input *ngIf="canEdit; else shown" type="number" min="0" [max]="s.max_marks" step="0.5" class="form-control form-control-sm d-inline-block"
                         [(ngModel)]="edits[r.student_id + ':' + s.subject_id]" [attr.aria-label]="r.name + ' ' + s.name">
                  <ng-template #shown>{{ r.marks[s.subject_id] ?? '-' }}</ng-template>
                </td>
                <td class="text-right"><strong>{{ r.total ?? '-' }}</strong></td>
                <td class="text-right">{{ r.percent ?? '-' }}</td>
                <td><span *ngIf="r.prize" class="badge badge-warning"><i class="mdi mdi-trophy"></i> {{ ordinal(r.prize) }}</span></td>
              </tr></tbody>
            </table></div>
            <p class="small text-muted mt-2 mb-0" *ngIf="canEdit">Leave a box empty when the student was absent. Changing marks clears the prizes; generate them again after.</p>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="closeSheet()">Close</button>
            <button *ngIf="canEdit" type="button" class="btn btn-warning" (click)="generate(sheet.exam)" [disabled]="busy || dirty"
                    [title]="dirty ? 'Save the marks first' : ''"><i class="mdi mdi-trophy"></i> Generate prizes</button>
            <button *ngIf="canEdit" type="button" class="btn btn-primary" (click)="saveMarks()" [disabled]="busy || !dirty">Save marks</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class CoachingExamsComponent implements OnInit {
  data: Answer | null = null;
  subjectList: Subject[] | null = null;
  centerId: number | null = null;
  classLevel: number | null = null;
  form: ExamForm | null = null;
  editId: number | null = null;
  formError = '';
  sheet: Sheet | null = null;
  /** The marks being typed, by "studentId:subjectId". */
  edits: Record<string, number | string | null> = {};
  private original: Record<string, number | null> = {};
  sheetError = '';
  sheetMessage = '';
  newSubject = '';
  saving = false;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('COACHING_EXAMS', 'EDIT'); }
  get centers(): CoachingCenter[] { return this.data?.centers ?? []; }
  get scoped(): boolean { return this.data?.scoped ?? false; }
  get classes(): number[] { return this.data?.classes ?? [7, 8, 9, 10]; }
  get activeSubjects(): Subject[] { return this.data?.subjects ?? []; }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<Answer>('/admin/coaching/exams', { centerId: this.centerId, classLevel: this.classLevel }).subscribe({
      next: (d) => { this.data = d; if (this.canEdit && !d.scoped) this.loadSubjects(); },
      error: (e) => (this.error = errorText(e, 'Could not load the exams.'))
    });
  }

  subjectsText(e: Exam): string { return e.subjects.map((s) => `${s.name} ${s.max_marks}`).join(', '); }

  ordinal(n: number): string { return n === 1 ? '1st' : n === 2 ? '2nd' : n === 3 ? '3rd' : n + 'th'; }

  // ---------------- exams

  openNew(): void {
    this.editId = null;
    this.formError = '';
    const first = this.activeSubjects.slice(0, 2).map((s) => ({ subjectId: s.id as number | null, maxMarks: 50 as number | null }));
    this.form = { rccCenterId: this.scoped ? this.centers[0]?.rcc_center_id ?? null : this.centerId, classLevel: this.classLevel ?? 7,
      name: '', examDate: new Date().toISOString().slice(0, 10), subjects: first.length ? first : [{ subjectId: null, maxMarks: 50 }] };
  }

  openEdit(e: Exam): void {
    this.editId = e.id;
    this.formError = '';
    this.form = { rccCenterId: e.rcc_center_id, classLevel: e.class_level, name: e.name, examDate: e.exam_date,
      subjects: e.subjects.map((s) => ({ subjectId: s.subject_id, maxMarks: s.max_marks })) };
  }

  /** Another subject row, with the last row's maximum marks. */
  addRow(): void {
    if (!this.form) return;
    const last = this.form.subjects.length ? this.form.subjects[this.form.subjects.length - 1].maxMarks : 50;
    this.form.subjects.push({ subjectId: null, maxMarks: last });
  }

  save(): void {
    if (!this.form) return;
    this.saving = true;
    const body = { ...this.form, subjects: this.form.subjects.filter((s) => s.subjectId !== null) };
    const req = this.editId ? this.api.put(`/admin/coaching/exams/${this.editId}`, body) : this.api.post('/admin/coaching/exams', body);
    req.subscribe({
      next: () => { this.saving = false; this.form = null; this.message = 'Exam saved.'; this.load(); },
      error: (e) => { this.saving = false; this.formError = errorText(e, 'Could not save the exam.'); }
    });
  }

  remove(e: Exam): void {
    if (!confirm(`Delete ${e.name} (class ${e.class_level}) with all its marks and prizes?`)) return;
    this.api.delete<{ message: string }>(`/admin/coaching/exams/${e.id}`).subscribe({
      next: (m) => { this.message = m.message; this.load(); },
      error: (err) => (this.error = errorText(err, 'Could not delete the exam.'))
    });
  }

  // ---------------- marks and prizes

  openSheet(e: Exam): void {
    this.sheetError = '';
    this.sheetMessage = '';
    this.api.get<Sheet>(`/admin/coaching/exams/${e.id}/marks`).subscribe({
      next: (s) => this.showSheet(s),
      error: (err) => (this.error = errorText(err, 'Could not load the marks.'))
    });
  }

  private showSheet(s: Sheet): void {
    this.sheet = s;
    this.edits = {};
    this.original = {};
    for (const r of s.rows) for (const sub of s.subjects) {
      const v = r.marks[sub.subject_id] ?? null;
      this.edits[r.student_id + ':' + sub.subject_id] = v;
      this.original[r.student_id + ':' + sub.subject_id] = v;
    }
  }

  private norm(v: unknown): number | null { return v === null || v === undefined || v === '' ? null : Number(v); }

  get dirty(): boolean { return Object.keys(this.edits).some((k) => this.norm(this.edits[k]) !== this.norm(this.original[k])); }

  closeSheet(): void {
    if (this.dirty && !confirm('Close without saving the marks?')) return;
    this.sheet = null;
    this.load();
  }

  saveMarks(): void {
    if (!this.sheet) return;
    const changed = Object.keys(this.edits).filter((k) => this.norm(this.edits[k]) !== this.norm(this.original[k]))
      .map((k) => { const [studentId, subjectId] = k.split(':').map(Number); return { studentId, subjectId, marks: this.norm(this.edits[k]) }; });
    const examId = this.sheet.exam.id;
    this.busy = true;
    this.sheetError = '';
    this.api.put<{ message: string }>(`/admin/coaching/exams/${examId}/marks`, { marks: changed }).subscribe({
      next: (m) => {
        this.busy = false;
        this.api.get<Sheet>(`/admin/coaching/exams/${examId}/marks`).subscribe({ next: (s) => { this.showSheet(s); this.sheetMessage = m.message; } });
      },
      error: (e) => { this.busy = false; this.sheetError = errorText(e, 'Could not save the marks.'); }
    });
  }

  generate(e: Exam): void {
    if (e.prizes?.length && !this.sheet && !confirm('Work out the prizes again from the current marks?')) return;
    this.busy = true;
    this.api.post<Sheet>(`/admin/coaching/exams/${e.id}/prizes`, {}).subscribe({
      next: (s) => {
        this.busy = false;
        const winners = s.rows.filter((r) => r.prize).map((r) => `${this.ordinal(r.prize!)}: ${r.name}`).join(', ');
        if (this.sheet) { this.showSheet(s); this.sheetMessage = 'Prizes: ' + winners; } else { this.message = `${e.name} prizes: ${winners}`; }
        this.load();
      },
      error: (err) => { this.busy = false; const t = errorText(err, 'Could not generate the prizes.'); if (this.sheet) this.sheetError = t; else this.error = t; }
    });
  }

  // ---------------- subjects (office)

  loadSubjects(): void {
    this.api.get<Subject[]>('/admin/coaching/subjects').subscribe({ next: (s) => (this.subjectList = s) });
  }

  addSubject(): void {
    this.api.post('/admin/coaching/subjects', { name: this.newSubject, sortOrder: (this.subjectList?.length ?? 0) + 1 }).subscribe({
      next: () => { this.newSubject = ''; this.load(); },
      error: (e) => (this.error = errorText(e, 'Could not add the subject.'))
    });
  }

  toggleSubject(s: Subject): void {
    this.api.put(`/admin/coaching/subjects/${s.id}`, { name: s.name, active: !s.active }).subscribe({
      next: () => this.load(),
      error: (e) => (this.error = errorText(e, 'Could not change the subject.'))
    });
  }

  deleteSubject(s: Subject): void {
    if (!confirm(`Delete the subject ${s.name}?`)) return;
    this.api.delete(`/admin/coaching/subjects/${s.id}`).subscribe({
      next: () => this.load(),
      error: (e) => (this.error = errorText(e, 'Could not delete the subject.'))
    });
  }
}
