import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { AlertsComponent, errorText } from '../../shared/alerts/alerts.component';
import { RebSectionRow, RebSession, TRACKS } from './reb.models';
import { AppDatePipe } from '../../shared/app-date';

interface Exam { id: number; name: string; kind: string; weight_percent: number; start_date: string | null; end_date: string | null; status: string; papers: number }
interface SheetSubject { subject_id: number; subject_name: string; track: string; included: boolean; exam_date: string | null; max_marks: number; pass_marks: number }
interface SheetClass { class_id: number; class_name: string; subjects: SheetSubject[] }
interface MyPaper { paper_id: number; class_name: string; subject_name: string; exam_date: string | null; max_marks: number; girls: number; entered: number }
interface MarkRow { student_id: number; name: string; roll_no: string | null; section: string; marks: number | null; absent: boolean; remark: string | null }
interface ResultCell { paper_id: number; subject_name: string; track: string; marks: number | null; absent?: boolean; passed?: boolean; max_marks: number }
interface ResultRow {
  student_id: number; name: string; roll_no: string | null; papers: ResultCell[]; total: number; max_total: number; percent: number | null;
  grade: string | null; rank: number | null; result: string | null; missing: number; attendance_percent: number | null; teacher_remark: string | null;
}
interface Band { min: number; grade: string; remark: string | null; pass: boolean }

const KINDS: Record<string, string> = { UNIT_TEST: 'Unit test', TERM: 'Term exam', ANNUAL: 'Annual exam', ORAL: 'Oral exam' };
const STATUS: Record<string, string> = { DRAFT: 'Draft', MARKS_ENTRY: 'Marks entry open', PUBLISHED: 'Published' };

/**
 * Rahbar-e-Banaat > Exams & Results: the office adds an exam and its date sheet, opens marks entry, then publishes
 * the results; teachers enter marks for their own subjects. Results show the Madrasa and School blocks, grade,
 * rank and result; report cards print as PDF. The annual result combines the exams by weight.
 */
@Component({
  selector: 'app-reb-exams',
  standalone: true,
  imports: [AppDatePipe, CommonModule, FormsModule, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Exams &amp; Results</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row">
      <div class="col-lg-3">
        <div class="card"><div class="card-body">
          <label for="e-session">Session</label>
          <select id="e-session" class="form-control mb-3" [(ngModel)]="sessionId" (ngModelChange)="loadExams(); loadSections()">
            <option *ngFor="let s of sessions" [ngValue]="s.id">{{ s.name }}</option></select>
          <button *ngIf="office" class="btn btn-primary btn-block btn-sm mb-2" (click)="editExam()"><i class="mdi mdi-plus mr-1"></i>Add exam</button>
          <div class="list-group">
            <a *ngFor="let e of exams" class="list-group-item list-group-item-action py-2" [class.active]="e.id === exam?.id" tabindex="0" (click)="open(e)" (keydown.enter)="open(e)">
              <div>{{ e.name }}</div><small [class.text-muted]="e.id !== exam?.id">{{ status[e.status] }}<span *ngIf="e.weight_percent"> · {{ e.weight_percent }}%</span></small></a>
            <a class="list-group-item list-group-item-action py-2" [class.active]="view === 'annual'" tabindex="0" (click)="openAnnual()" (keydown.enter)="openAnnual()">Annual result</a>
            <a *ngIf="office" class="list-group-item list-group-item-action py-2" [class.active]="view === 'grades'" tabindex="0" (click)="openGrades()" (keydown.enter)="openGrades()">Grading scale</a>
          </div>
          <p *ngIf="!exams.length" class="small text-muted mt-2 mb-0">No exams in this session yet.</p>
        </div></div>
      </div>

      <div class="col-lg-9">
        <!-- an exam -->
        <ng-container *ngIf="view === 'exam' && exam">
          <div class="card"><div class="card-body">
            <div class="d-flex flex-wrap justify-content-between align-items-start">
              <div class="mb-2"><h4 class="mt-0 mb-1">{{ exam.name }}</h4>
                <span class="badge" [ngClass]="exam.status === 'PUBLISHED' ? 'badge-success' : exam.status === 'MARKS_ENTRY' ? 'badge-info' : 'badge-light border'">{{ status[exam.status] }}</span>
                <span class="small text-muted ml-1">{{ kinds[exam.kind] }}<span *ngIf="exam.start_date"> · {{ exam.start_date | date: 'd MMM' }}<span *ngIf="exam.end_date"> – {{ exam.end_date | appDate }}</span></span>
                  <span *ngIf="exam.weight_percent"> · counts {{ exam.weight_percent }}% in the annual result</span></span></div>
              <div *ngIf="office" class="d-flex flex-wrap">
                <button class="btn btn-sm btn-outline-primary mr-1 mb-1" (click)="editExam(exam)">Edit</button>
                <button *ngIf="exam.status === 'DRAFT'" class="btn btn-sm btn-info mr-1 mb-1" (click)="setStatus('MARKS_ENTRY')" [disabled]="!exam.papers">Open marks entry</button>
                <button *ngIf="exam.status === 'MARKS_ENTRY'" class="btn btn-sm btn-success mr-1 mb-1" (click)="setStatus('PUBLISHED')">Publish results</button>
                <button *ngIf="exam.status === 'PUBLISHED'" class="btn btn-sm btn-outline-warning mr-1 mb-1" (click)="setStatus('MARKS_ENTRY')">Unpublish to correct</button>
                <button *ngIf="exam.status === 'DRAFT'" class="btn btn-sm btn-outline-danger mb-1" (click)="deleteExam()">Delete</button>
              </div>
            </div>
            <ul class="nav nav-tabs mt-2">
              <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'sheet'" (click)="$event.preventDefault(); goTab('sheet')">Date sheet</a></li>
              <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'marks'" (click)="$event.preventDefault(); goTab('marks')">Marks</a></li>
              <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'results'" (click)="$event.preventDefault(); goTab('results')">Results</a></li>
            </ul>
          </div></div>

          <!-- date sheet -->
          <div class="card" *ngIf="tab === 'sheet'"><div class="card-body">
            <p class="text-muted small">Tick the subjects each class sits; the marks come from the class subjects in REB Setup.</p>
            <p *ngIf="!sheet.length" class="text-muted">No classes with sections in this session.</p>
            <div *ngFor="let c of sheet" class="mb-3">
              <h5>{{ c.class_name }}</h5>
              <p *ngIf="!c.subjects.length" class="small text-muted">No subjects for this class (REB Setup &gt; Class subjects).</p>
              <div class="table-responsive" *ngIf="c.subjects.length"><table class="table table-sm mb-0">
                <thead><tr><th></th><th>Subject</th><th>Date</th><th style="width: 110px">Max</th><th style="width: 110px">Pass</th></tr></thead>
                <tbody><tr *ngFor="let s of c.subjects">
                  <td><input type="checkbox" [(ngModel)]="s.included" [disabled]="!sheetEditable" [attr.aria-label]="'Include ' + s.subject_name"></td>
                  <td>{{ s.subject_name }} <span class="small text-muted">{{ tracks[s.track] }}</span></td>
                  <td><input type="date" class="form-control form-control-sm" [(ngModel)]="s.exam_date" [disabled]="!sheetEditable || !s.included" [attr.aria-label]="'Date of ' + s.subject_name"></td>
                  <td><input type="number" min="1" class="form-control form-control-sm" [(ngModel)]="s.max_marks" [disabled]="!sheetEditable || !s.included" aria-label="Maximum marks"></td>
                  <td><input type="number" min="0" class="form-control form-control-sm" [(ngModel)]="s.pass_marks" [disabled]="!sheetEditable || !s.included" aria-label="Pass marks"></td>
                </tr></tbody>
              </table></div>
            </div>
            <button *ngIf="sheetEditable && sheet.length" class="btn btn-primary" (click)="saveSheet()" [disabled]="busy">Save date sheet</button>
          </div></div>

          <!-- marks -->
          <div class="card" *ngIf="tab === 'marks'"><div class="card-body">
            <ng-container *ngIf="!sheetMarks">
              <p *ngIf="exam.status === 'DRAFT'" class="text-muted">Marks can be entered once the office opens marks entry.</p>
              <p *ngIf="!myPapers.length" class="text-muted">No papers{{ office ? '' : ' of your subjects' }} in this exam.</p>
              <div class="list-group">
                <a *ngFor="let p of myPapers" class="list-group-item list-group-item-action d-flex justify-content-between align-items-center" tabindex="0"
                   (click)="openPaper(p.paper_id)" (keydown.enter)="openPaper(p.paper_id)">
                  <span>{{ p.class_name }} · <strong>{{ p.subject_name }}</strong><span *ngIf="p.exam_date" class="small text-muted"> · {{ p.exam_date | date: 'd MMM' }}</span></span>
                  <span class="badge" [ngClass]="p.entered >= p.girls ? 'badge-success' : 'badge-light border'">{{ p.entered }} / {{ p.girls }}</span></a>
              </div>
            </ng-container>
            <ng-container *ngIf="sheetMarks as s">
              <div class="d-flex justify-content-between align-items-center mb-2">
                <div><button class="btn btn-sm btn-light mr-2" (click)="sheetMarks = null; loadMyPapers()"><i class="mdi mdi-arrow-left"></i></button>
                  <strong>{{ s.class_name }} · {{ s.subject_name }}</strong> <span class="text-muted">out of {{ s.paper.max_marks }} (pass {{ s.paper.pass_marks }})</span></div>
              </div>
              <div class="table-responsive"><table class="table table-sm table-centered mb-2">
                <thead><tr><th>Girl</th><th style="width: 120px">Marks</th><th>Absent</th></tr></thead>
                <tbody><tr *ngFor="let r of s.rows">
                  <td><span class="text-muted">{{ r.roll_no }}</span> {{ r.name }} <span class="small text-muted">{{ r.section }}</span></td>
                  <td><input type="number" min="0" [max]="s.paper.max_marks" step="0.5" class="form-control form-control-sm" [(ngModel)]="r.marks" [disabled]="!s.editable || r.absent"
                             [class.is-invalid]="r.marks !== null && r.marks > s.paper.max_marks" [attr.aria-label]="'Marks of ' + r.name"></td>
                  <td><input type="checkbox" [(ngModel)]="r.absent" [disabled]="!s.editable" [attr.aria-label]="r.name + ' absent'"></td>
                </tr></tbody>
              </table></div>
              <p *ngIf="!s.editable" class="small text-muted">{{ exam.status === 'PUBLISHED' ? 'Published: marks are locked.' : 'Marks entry is not open.' }}</p>
              <button *ngIf="s.editable && canEdit" class="btn btn-primary" (click)="saveMarks()" [disabled]="busy">Save marks</button>
            </ng-container>
          </div></div>

          <!-- results -->
          <div class="card" *ngIf="tab === 'results'"><div class="card-body">
            <div class="d-flex flex-wrap align-items-end mb-2">
              <div class="form-group mr-2 mb-2"><label for="r-section">Class</label>
                <select id="r-section" class="form-control" [(ngModel)]="sectionId" (ngModelChange)="loadResults()"><option [ngValue]="null">-- Choose --</option>
                  <option *ngFor="let s of sections" [ngValue]="s.id">{{ s.label }}</option></select></div>
              <button *ngIf="exam.status === 'PUBLISHED' && sectionId" class="btn btn-outline-secondary mb-2" (click)="reportCards()"><i class="mdi mdi-printer mr-1"></i>Report cards (PDF)</button>
            </div>
            <div class="table-responsive" *ngIf="results.length"><table class="table table-sm table-centered mb-2">
              <thead><tr><th>Girl</th><th *ngFor="let p of results[0].papers" class="text-center small">{{ p.subject_name }}</th>
                <th class="text-right">%</th><th>Grade</th><th>Rank</th><th>Result</th><th>Remark</th></tr></thead>
              <tbody><tr *ngFor="let r of results">
                <td class="text-nowrap"><span class="text-muted">{{ r.roll_no }}</span> {{ r.name }}</td>
                <td *ngFor="let p of r.papers" class="text-center" [class.text-danger]="p.passed === false || p.absent">{{ p.absent ? 'Ab' : p.marks ?? '–' }}</td>
                <td class="text-right">{{ r.percent ?? '' }}</td><td>{{ r.grade }}</td><td>{{ r.rank ?? '' }}</td>
                <td><span *ngIf="r.result" class="badge" [ngClass]="r.result === 'PASS' ? 'badge-success' : 'badge-danger'">{{ r.result === 'PASS' ? 'Pass' : r.result === 'FAIL' ? 'Fail' : 'Absent' }}</span>
                  <span *ngIf="r.missing" class="small text-warning">{{ r.missing }} missing</span></td>
                <td style="min-width: 180px"><input class="form-control form-control-sm" [(ngModel)]="r.teacher_remark" [disabled]="!canEdit" [attr.aria-label]="'Remark for ' + r.name"></td>
              </tr></tbody>
            </table></div>
            <p *ngIf="sectionId && !results.length" class="text-muted">No girls or no papers for this class.</p>
            <button *ngIf="canEdit && results.length" class="btn btn-outline-primary btn-sm" (click)="saveRemarks()" [disabled]="busy">Save remarks</button>
          </div></div>
        </ng-container>

        <!-- annual result -->
        <div class="card" *ngIf="view === 'annual'"><div class="card-body">
          <h4 class="header-title">Annual result</h4>
          <p class="text-muted small">Published exams with a weight count by their weight (over the exams the girl sat). Use it at year end with Girls &gt; Year-end promotion.</p>
          <div class="form-group" style="max-width: 320px"><label for="f-section">Class</label>
            <select id="f-section" class="form-control" [(ngModel)]="sectionId" (ngModelChange)="loadAnnual()"><option [ngValue]="null">-- Choose --</option>
              <option *ngFor="let s of sections" [ngValue]="s.id">{{ s.label }}</option></select></div>
          <div class="table-responsive" *ngIf="annual?.rows?.length"><table class="table table-sm mb-0">
            <thead><tr><th>Girl</th><th *ngFor="let e of annual!.exams" class="text-right small">{{ e.name }} ({{ e.weight }}%)</th><th class="text-right">Final %</th><th>Grade</th><th>Suggestion</th></tr></thead>
            <tbody><tr *ngFor="let r of annual!.rows">
              <td class="text-nowrap"><span class="text-muted">{{ r.roll_no }}</span> {{ r.name }}</td>
              <td *ngFor="let x of r.exams" class="text-right">{{ x.percent ?? '–' }}</td>
              <td class="text-right"><strong>{{ r.percent ?? '' }}</strong></td><td>{{ r.grade }}</td><td>{{ r.suggestion }}</td>
            </tr></tbody>
          </table></div>
          <p *ngIf="annual && !annual.exams.length" class="text-muted mb-0">No published exams with a weight yet.</p>
        </div></div>

        <!-- grading scale -->
        <div class="card" *ngIf="view === 'grades'"><div class="card-body">
          <h4 class="header-title">Grading scale</h4>
          <p class="text-muted small">{{ customScale ? 'This session\\'s scale.' : 'The standard scale is used until you save one for this session.' }} A girl gets the highest grade whose starting percentage she reaches.</p>
          <table class="table table-sm mb-2" style="max-width: 560px">
            <thead><tr><th>From %</th><th>Grade</th><th>Remark</th><th>Pass</th><th></th></tr></thead>
            <tbody><tr *ngFor="let b of scale; let i = index">
              <td><input type="number" min="0" max="100" class="form-control form-control-sm" [(ngModel)]="b.min" aria-label="From percent"></td>
              <td><input class="form-control form-control-sm" maxlength="5" [(ngModel)]="b.grade" aria-label="Grade"></td>
              <td><input class="form-control form-control-sm" [(ngModel)]="b.remark" aria-label="Remark"></td>
              <td><input type="checkbox" [(ngModel)]="b.pass" aria-label="Pass"></td>
              <td><button class="btn btn-xs btn-link text-danger" (click)="scale.splice(i, 1)" aria-label="Remove">&times;</button></td>
            </tr></tbody>
          </table>
          <button class="btn btn-sm btn-light mr-2" (click)="scale.push({ min: 0, grade: '', remark: '', pass: true })">Add a grade</button>
          <button class="btn btn-primary btn-sm" (click)="saveScale()" [disabled]="busy">Save scale</button>
        </div></div>

        <div class="card" *ngIf="view === 'none'"><div class="card-body text-muted text-center py-5">Choose an exam.</div></div>
      </div>
    </div>

    <div *ngIf="form" class="modal fade show d-block" tabindex="-1" (click)="form = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">{{ form.id ? 'Edit exam' : 'Add exam' }}</h5><button type="button" class="close" (click)="form = null" aria-label="Close">&times;</button></div>
          <div class="modal-body"><div class="row">
            <div class="col-12 form-group"><label for="x-name">Name *</label><input id="x-name" class="form-control" [(ngModel)]="form.name" placeholder="e.g. Half-yearly exam"></div>
            <div class="col-6 form-group"><label for="x-kind">Kind</label>
              <select id="x-kind" class="form-control" [(ngModel)]="form.kind"><option *ngFor="let k of kindKeys" [value]="k">{{ kinds[k] }}</option></select></div>
            <div class="col-6 form-group"><label for="x-weight">Weight in annual result (%)</label><input id="x-weight" type="number" min="0" max="100" class="form-control" [(ngModel)]="form.weightPercent"></div>
            <div class="col-6 form-group"><label for="x-start">Starts</label><input id="x-start" type="date" class="form-control" [(ngModel)]="form.startDate"></div>
            <div class="col-6 form-group"><label for="x-end">Ends</label><input id="x-end" type="date" class="form-control" [(ngModel)]="form.endDate"></div>
          </div></div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="form = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="saveExam()" [disabled]="busy || !form.name.trim()">Save</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class RebExamsComponent implements OnInit {
  readonly kinds = KINDS;
  readonly kindKeys = Object.keys(KINDS);
  readonly status = STATUS;
  readonly tracks = TRACKS;

  sessions: RebSession[] = [];
  sessionId: number | null = null;
  sections: RebSectionRow[] = [];
  sectionId: number | null = null;
  exams: Exam[] = [];
  exam: Exam | null = null;
  view: 'none' | 'exam' | 'annual' | 'grades' = 'none';
  tab: 'sheet' | 'marks' | 'results' = 'marks';
  sheet: SheetClass[] = [];
  myPapers: MyPaper[] = [];
  sheetMarks: { paper: { id: number; max_marks: number; pass_marks: number }; class_name: string; subject_name: string; editable: boolean; rows: MarkRow[] } | null = null;
  results: ResultRow[] = [];
  annual: { exams: { name: string; weight: number }[]; rows: { name: string; roll_no: string | null; exams: { percent: number | null }[]; percent: number | null; grade: string | null; suggestion: string }[] } | null = null;
  scale: Band[] = [];
  customScale = false;
  form: { id: number | null; name: string; kind: string; weightPercent: number | null; startDate: string; endDate: string } | null = null;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  /** The office sets up and publishes exams; teachers (limited to their classes) only enter marks and remarks. */
  get office(): boolean { return this.auth.can('REB_EXAMS', 'EDIT') && this.auth.can('REB_SETUP'); }
  get canEdit(): boolean { return this.auth.can('REB_EXAMS', 'EDIT'); }
  get sheetEditable(): boolean { return this.office && this.exam?.status !== 'PUBLISHED'; }

  ngOnInit(): void {
    this.api.get<RebSession[]>('/admin/reb/sessions').subscribe({
      next: (r) => { this.sessions = r; this.sessionId = (r.find((s) => s.is_current) ?? r[0])?.id ?? null; this.loadExams(); this.loadSections(); },
      error: (e) => (this.error = errorText(e, 'Could not load the sessions.'))
    });
  }

  loadExams(): void {
    if (!this.sessionId) return;
    this.api.get<Exam[]>('/admin/reb/exams', { sessionId: this.sessionId }).subscribe({
      next: (r) => { this.exams = r; if (this.exam) this.exam = r.find((e) => e.id === this.exam!.id) ?? null; },
      error: (e) => (this.error = errorText(e, 'Could not load the exams.'))
    });
  }

  loadSections(): void {
    if (!this.sessionId) return;
    this.api.get<RebSectionRow[]>('/admin/reb/sections', { sessionId: this.sessionId }).subscribe({ next: (r) => (this.sections = r) });
  }

  open(e: Exam): void {
    this.exam = e;
    this.view = 'exam';
    this.sheetMarks = null;
    this.goTab(e.status === 'DRAFT' && this.office ? 'sheet' : e.status === 'PUBLISHED' ? 'results' : 'marks');
  }

  goTab(t: 'sheet' | 'marks' | 'results'): void {
    this.tab = t;
    if (t === 'sheet') this.loadSheet();
    if (t === 'marks') { this.sheetMarks = null; this.loadMyPapers(); }
    if (t === 'results') this.loadResults();
  }

  editExam(e?: Exam): void {
    this.form = { id: e?.id ?? null, name: e?.name ?? '', kind: e?.kind ?? 'TERM', weightPercent: e?.weight_percent ?? null, startDate: e?.start_date ?? '', endDate: e?.end_date ?? '' };
  }

  saveExam(): void {
    const f = this.form;
    if (!f) return;
    this.busy = true;
    const body = { ...f, sessionId: this.sessionId };
    const req = f.id ? this.api.put<{ message: string; data: Exam }>(`/admin/reb/exams/${f.id}`, body) : this.api.post<{ message: string; data: Exam }>('/admin/reb/exams', body);
    req.subscribe({
      next: (r) => { this.busy = false; this.form = null; this.message = r.message; this.loadExams(); if (!f.id) { this.exam = { ...r.data, papers: 0 }; this.view = 'exam'; this.goTab('sheet'); } },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save the exam.'); }
    });
  }

  deleteExam(): void {
    if (!this.exam || !confirm(`Delete ${this.exam.name}?`)) return;
    this.api.delete<{ message: string }>(`/admin/reb/exams/${this.exam.id}`).subscribe({
      next: (r) => { this.message = r.message; this.exam = null; this.view = 'none'; this.loadExams(); },
      error: (e) => (this.error = errorText(e, 'Could not delete the exam.'))
    });
  }

  setStatus(status: string): void {
    if (!this.exam) return;
    if (status === 'PUBLISHED' && !confirm('Publish the results? Marks will be locked and report cards can be printed.')) return;
    this.busy = true;
    this.api.post<{ message: string }>(`/admin/reb/exams/${this.exam.id}/status`, { status }).subscribe({
      next: (r) => { this.busy = false; this.message = r.message; this.exam = { ...this.exam!, status }; this.loadExams(); this.goTab(this.tab); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not change the exam.'); }
    });
  }

  loadSheet(): void {
    this.api.get<{ classes: SheetClass[] }>(`/admin/reb/exams/${this.exam!.id}/papers`).subscribe({
      next: (r) => (this.sheet = r.classes), error: (e) => (this.error = errorText(e, 'Could not load the date sheet.'))
    });
  }

  saveSheet(): void {
    const papers = this.sheet.flatMap((c) => c.subjects.map((s) => ({ classId: c.class_id, subjectId: s.subject_id, included: s.included,
      examDate: s.exam_date, maxMarks: s.max_marks, passMarks: s.pass_marks })));
    this.busy = true;
    this.api.put<{ message: string }>(`/admin/reb/exams/${this.exam!.id}/papers`, { papers }).subscribe({
      next: (r) => { this.busy = false; this.message = r.message; this.loadExams(); this.loadSheet(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save the date sheet.'); }
    });
  }

  loadMyPapers(): void {
    this.api.get<MyPaper[]>(`/admin/reb/exams/${this.exam!.id}/my-papers`).subscribe({
      next: (r) => (this.myPapers = r), error: (e) => (this.error = errorText(e, 'Could not load the papers.'))
    });
  }

  openPaper(id: number): void {
    this.api.get<NonNullable<typeof this.sheetMarks>>(`/admin/reb/papers/${id}/marks`).subscribe({
      next: (r) => (this.sheetMarks = r), error: (e) => (this.error = errorText(e, 'Could not open the marks sheet.'))
    });
  }

  saveMarks(): void {
    const s = this.sheetMarks;
    if (!s) return;
    this.busy = true;
    this.api.put<{ message: string }>(`/admin/reb/papers/${s.paper.id}/marks`, {
      marks: s.rows.map((r) => ({ studentId: r.student_id, marks: r.marks, absent: r.absent, remark: r.remark }))
    }).subscribe({
      next: (r) => { this.busy = false; this.message = r.message; this.openPaper(s.paper.id); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save the marks.'); }
    });
  }

  loadResults(): void {
    this.results = [];
    if (!this.sectionId || !this.exam) return;
    this.api.get<{ rows: ResultRow[] }>(`/admin/reb/exams/${this.exam.id}/results`, { sectionId: this.sectionId }).subscribe({
      next: (r) => (this.results = r.rows), error: (e) => (this.error = errorText(e, 'Could not load the results.'))
    });
  }

  saveRemarks(): void {
    this.busy = true;
    this.api.put<{ message: string }>(`/admin/reb/exams/${this.exam!.id}/remarks`, {
      remarks: this.results.map((r) => ({ studentId: r.student_id, remark: r.teacher_remark }))
    }).subscribe({
      next: (r) => { this.busy = false; this.message = r.message; },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save the remarks.'); }
    });
  }

  reportCards(): void {
    this.api.download(`/admin/reb/exams/${this.exam!.id}/report-cards`, { sectionId: this.sectionId }, 'report_cards.pdf').subscribe({
      error: (e) => (this.error = errorText(e, 'Could not create the report cards.'))
    });
  }

  openAnnual(): void {
    this.view = 'annual';
    this.exam = null;
    this.loadAnnual();
  }

  loadAnnual(): void {
    this.annual = null;
    if (!this.sectionId) return;
    this.api.get<NonNullable<typeof this.annual>>('/admin/reb/final-results', { sessionId: this.sessionId, sectionId: this.sectionId }).subscribe({
      next: (r) => (this.annual = r), error: (e) => (this.error = errorText(e, 'Could not load the annual result.'))
    });
  }

  openGrades(): void {
    this.view = 'grades';
    this.exam = null;
    this.api.get<{ custom: boolean; bands: Band[] }>('/admin/reb/grade-scale', { sessionId: this.sessionId }).subscribe({
      next: (r) => { this.customScale = r.custom; this.scale = r.bands.map((b) => ({ ...b })); },
      error: (e) => (this.error = errorText(e, 'Could not load the grading scale.'))
    });
  }

  saveScale(): void {
    this.busy = true;
    this.api.put<{ message: string }>('/admin/reb/grade-scale', { sessionId: this.sessionId, bands: this.scale }).subscribe({
      next: (r) => { this.busy = false; this.message = r.message; this.openGrades(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save the scale.'); }
    });
  }
}
