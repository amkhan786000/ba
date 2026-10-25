import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';

interface Numbered { id: number; user_id: string; name: string; chapter: string | null; college_type: string; previous_user_id?: string | null }
interface YearRow { year: number; students: number; without: number }
interface ChapterRow { chapter_id: number; chapter: string; students: number; without_govt: number; without_private: number }
interface Batch { id: number; chapter: string; prefix: string; first: string; last: string; how_many: number; created_at: string | null }
interface Overview {
  year: number; years: YearRow[]; students: number; without_govt: number; without_private: number;
  chapters: ChapterRow[]; next_govt: string; next_private: string; batches: Batch[];
  numbered: Numbered[]; generated?: Numbered[];
}

/**
 * Students > Reference Numbers (Super Admin and Application Admin): a student's reference number is their user ID.
 * Gives a session year's students theirs, all at once or chapter by chapter (each chapter carries on from the last
 * number given). Govt college: M001-2026, ...; Private, Doesn't matter or not set: PVT001-2026, ... A year or a
 * chapter with nothing left is greyed out.
 */
@Component({
  selector: 'app-admin-student-references',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, AppDatePipe],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Reference Numbers</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>
    <p *ngIf="!admin" class="text-muted">Only the Super Admin and the Application Admin can generate reference numbers.</p>

    <ng-container *ngIf="admin && data">
      <div class="card"><div class="card-body">
        <h4 class="header-title">Generate reference numbers</h4>
        <p class="text-muted small">A student's reference number is their <strong>User ID</strong>. Every student of the chosen
          session year whose User ID isn't a reference number yet (for example the number they got when their application was
          accepted) gets one, oldest record first; it replaces their User ID. They keep signing in with their email or phone. The college type in BA Details decides the prefix: <strong>Govt</strong> gets M001-{{ data.year }},
          M002-{{ data.year }}, ...; <strong>Private</strong>, <strong>Doesn't matter</strong> or not set gets PVT001-{{ data.year }}, ...
          Numbers already given never change; new ones continue from the last one given. You can number one chapter at a
          time: the next chapter carries on from where the last one stopped.</p>
        <div class="row align-items-end">
          <div class="col-md-3 form-group">
            <label for="year">Session year</label>
            <select id="year" class="form-control" [(ngModel)]="year" (ngModelChange)="load()">
              <option *ngFor="let y of data.years" [ngValue]="y.year" [disabled]="!y.without && y.year !== year">
                {{ y.year }}{{ y.without ? ' (' + y.without + ' left)' : y.students ? ' (all generated)' : ' (no students yet)' }}</option>
            </select>
          </div>
          <div class="col-md-4 form-group">
            <label for="chapter">Chapter <span class="text-muted small">(optional)</span></label>
            <select id="chapter" class="form-control" [(ngModel)]="chapterId">
              <option [ngValue]="null">All chapters ({{ waiting }} left)</option>
              <option *ngFor="let c of data.chapters" [ngValue]="c.chapter_id" [disabled]="!left(c)">
                {{ c.chapter }} ({{ left(c) ? left(c) + ' left' : 'all generated' }})</option>
            </select>
          </div>
          <div class="col-md-5 form-group">
            <button class="btn btn-primary" (click)="generate()" [disabled]="busy || !toGenerate">
              <i class="mdi mdi-numeric mr-1"></i>Generate {{ toGenerate || '' }} reference number{{ toGenerate === 1 ? '' : 's' }}</button>
          </div>
        </div>
        <div *ngIf="!waiting && data.students" class="alert alert-success mb-2"><i class="mdi mdi-check-all mr-1"></i>
          All reference numbers of {{ data.year }} have been generated.</div>
        <div *ngIf="!data.students" class="alert alert-light border mb-2">There are no students in {{ data.year }} yet.</div>
        <p class="mb-0 small">{{ data.students }} student{{ data.students === 1 ? '' : 's' }} in {{ data.year }};
          <strong>{{ waiting }}</strong> without a reference number <span class="text-muted">({{ data.without_govt }} Govt, {{ data.without_private }} Private / other)</span>.
          Next numbers: <strong>{{ data.next_govt }}</strong> (Govt) and <strong>{{ data.next_private }}</strong> (Private / other).</p>
      </div></div>

      <div class="card" *ngIf="data.chapters.length"><div class="card-body">
        <h4 class="header-title">By chapter, {{ data.year }}</h4>
        <div class="table-responsive"><table class="table table-sm mb-0">
          <thead class="thead-light"><tr><th>Chapter</th><th class="text-right">Students</th><th class="text-right">Left: Govt</th><th class="text-right">Left: Private / other</th><th></th></tr></thead>
          <tbody><tr *ngFor="let c of data.chapters">
            <td>{{ c.chapter }}</td><td class="text-right">{{ c.students }}</td>
            <td class="text-right">{{ c.without_govt }}</td><td class="text-right">{{ c.without_private }}</td>
            <td class="text-right"><span *ngIf="!left(c)" class="badge badge-success">All generated</span>
              <button *ngIf="left(c)" class="btn btn-xs btn-outline-primary" (click)="chapterId = c.chapter_id; generate()" [disabled]="busy">Generate {{ left(c) }}</button></td>
          </tr></tbody>
        </table></div>
      </div></div>

      <div class="card" *ngIf="data.batches.length"><div class="card-body">
        <h4 class="header-title">Generated so far, {{ data.year }}</h4>
        <div class="table-responsive"><table class="table table-sm mb-0">
          <thead class="thead-light"><tr><th>When</th><th>Chapter</th><th>Numbers</th><th class="text-right">How many</th></tr></thead>
          <tbody><tr *ngFor="let b of data.batches">
            <td>{{ b.created_at | appDate: 'datetime' }}</td><td>{{ b.chapter }}</td>
            <td><strong>{{ b.first }}</strong>{{ b.first !== b.last ? ' to ' : '' }}<strong *ngIf="b.first !== b.last">{{ b.last }}</strong></td>
            <td class="text-right">{{ b.how_many }}</td>
          </tr></tbody>
        </table></div>
      </div></div>

      <div class="card" *ngIf="data.generated?.length"><div class="card-body">
        <h4 class="header-title">Just generated ({{ data.generated!.length }})</h4>
        <ng-container *ngTemplateOutlet="table; context: { rows: data.generated, previous: true }"></ng-container>
      </div></div>

      <div class="card"><div class="card-body">
        <h4 class="header-title">Reference numbers for {{ data.year }} ({{ data.numbered.length }})</h4>
        <p *ngIf="!data.numbered.length" class="text-muted mb-0">None yet.</p>
        <ng-container *ngIf="data.numbered.length">
          <ng-container *ngTemplateOutlet="table; context: { rows: data.numbered }"></ng-container>
        </ng-container>
      </div></div>
    </ng-container>

    <ng-template #table let-rows="rows" let-previous="previous">
      <div class="table-responsive"><table class="table table-sm mb-0">
        <thead class="thead-light"><tr><th>User ID (reference no.)</th><th *ngIf="previous">Was</th><th>Name</th><th>Chapter</th><th>College type</th></tr></thead>
        <tbody><tr *ngFor="let r of rows">
          <td><strong>{{ r.user_id }}</strong></td><td *ngIf="previous">{{ r.previous_user_id || '-' }}</td><td>{{ r.name }}</td>
          <td>{{ r.chapter || '-' }}</td><td>{{ r.college_type }}</td>
        </tr></tbody>
      </table></div>
    </ng-template>
  `
})
export class AdminStudentReferencesComponent implements OnInit {
  data: Overview | null = null;
  year: number | null = null;
  /** null: every chapter; 0: students without a chapter. */
  chapterId: number | null = null;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get admin(): boolean { return this.auth.isAdmin(); }
  get waiting(): number { return this.data ? this.data.without_govt + this.data.without_private : 0; }

  left(c: ChapterRow): number { return c.without_govt + c.without_private; }

  /** How many the button numbers: the chosen chapter's, or everyone's. */
  get toGenerate(): number {
    if (this.chapterId === null) return this.waiting;
    const c = this.data?.chapters.find((x) => x.chapter_id === this.chapterId);
    return c ? this.left(c) : 0;
  }

  ngOnInit(): void {
    if (!this.admin) return;
    this.load();
  }

  load(): void {
    this.api.get<Overview>('/admin/student-references', { year: this.year }).subscribe({
      next: (d) => { this.data = d; this.year = d.year; if (this.chapterId !== null && !d.chapters.some((c) => c.chapter_id === this.chapterId)) this.chapterId = null; },
      error: (e) => (this.error = errorText(e, 'Could not load the students.'))
    });
  }

  generate(): void {
    const chapter = this.chapterId === null ? 'every chapter' : this.data?.chapters.find((c) => c.chapter_id === this.chapterId)?.chapter;
    if (!this.year || !confirm(`Give ${this.toGenerate} student(s) of ${this.year} (${chapter}) a reference number? It replaces their current User ID and can't be undone here.`)) return;
    this.busy = true;
    this.error = '';
    this.api.post<Overview>('/admin/student-references/generate', { year: this.year, chapterId: this.chapterId }).subscribe({
      next: (d) => {
        this.busy = false;
        this.data = d;
        this.chapterId = null;
        this.message = `${d.generated?.length ?? 0} reference number(s) generated.`;
      },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not generate the reference numbers.'); }
    });
  }
}
