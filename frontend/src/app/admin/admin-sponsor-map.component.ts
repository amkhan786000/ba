import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { PagerComponent, PageState, PaginatePipe, pageOf } from '../shared/pager/pager.component';
import { CardTableDirective } from '../shared/card-table.directive';
import { ChapterService } from '../core/services/chapter.service';
import { Chapter } from './admin-chapters.component';

interface MapScreen {
  sponsor: { id: number; user_id: string; name: string; email: string | null; chapter_name: string | null };
  mappedStudents: { id: number; user_id: string; name: string; email: string | null; on_behalf_text?: string | null }[];
  availableStudents: {
    id: number; user_id: string; name: string; email: string | null;
    current_sponsor_name: string | null; current_sponsor_id: number | null; current_sponsor_code: string | null;
    /** Only when the sponsor has preferences: does the student fit them, and why (not). */
    match?: { fits: boolean; why: string[]; missing: string[] };
  }[];
  /** The sponsor's preferences in words (null: none given). */
  preferences?: string | null;
}

/** Port of templates/admin/map_students_to_sponsor.html */
@Component({
  selector: 'app-admin-sponsor-map',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, PagerComponent, PaginatePipe, CardTableDirective],
  template: `
    <div class="row">
      <div class="col-12">
        <div class="page-title-box d-flex flex-column flex-md-row justify-content-between align-items-center">
          <h4 class="page-title mb-2 mb-md-0">Sponsor: <span class="text-primary">{{ data?.sponsor?.name }}</span></h4>
          <div class="page-title-right">
            <a [routerLink]="['/', section, 'sponsorships']" class="btn btn-secondary waves-effect waves-light"><i class="mdi mdi-arrow-left"></i> Back</a>
          </div>
        </div>
      </div>
    </div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row" *ngIf="data">
      <div class="col-12 col-xl-7">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title mb-3">1. Select Students to Assign</h4>
            <div *ngIf="data.preferences" class="alert alert-light border small mb-2">
              <i class="mdi mdi-heart-outline text-success"></i> <strong>This sponsor would like:</strong> {{ data.preferences }}.
              Students who fit come first, marked <span class="badge badge-success">Fits</span>.
              <div class="custom-control custom-checkbox mt-1">
                <input type="checkbox" class="custom-control-input" id="onlyFits" [(ngModel)]="onlyFits" (ngModelChange)="pg.page = 1">
                <label class="custom-control-label" for="onlyFits">Show only students who fit ({{ fitCount }})</label>
              </div>
            </div>
            <div class="form-group">
              <input type="text" class="form-control" placeholder="Search students..." [(ngModel)]="search">
            </div>
            <form (ngSubmit)="submit()">
              <p class="text-muted small">Students ticked below will be assigned to <strong>{{ data.sponsor.name }}</strong>. Students who already have another sponsor will be moved.</p>
              <div class="table-responsive table-fixed-head">
                <table class="table table-hover mb-0">
                  <thead>
                    <tr>
                      <th style="width: 20px;">
                        <div class="custom-control custom-checkbox">
                          <input type="checkbox" class="custom-control-input" id="selectAll" [checked]="allChecked" (change)="toggleAll($event)">
                          <label class="custom-control-label" for="selectAll">&nbsp;</label>
                        </div>
                      </th>
                      <th>Student</th>
                      <th>Current Mapping</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr *ngIf="!data.availableStudents.length"><td colspan="3" class="text-center">No students available.</td></tr>
                    <tr *ngFor="let s of visible | paginate: pg.page : pg.size">
                      <td>
                        <div class="custom-control custom-checkbox">
                          <input type="checkbox" class="custom-control-input" [id]="'check' + s.id" [checked]="selected.has(s.id)" (change)="toggle(s.id)">
                          <label class="custom-control-label" [for]="'check' + s.id">&nbsp;</label>
                        </div>
                      </td>
                      <td><strong>{{ s.name }}</strong>
                        <span *ngIf="s.match?.fits" class="badge badge-success ml-1" [title]="s.match!.why.join(', ')">Fits</span><br>
                        <small class="text-muted">ID: {{ s.user_id }}</small>
                        <div *ngIf="s.match" class="small" [ngClass]="s.match.fits ? 'text-success' : 'text-muted'">
                          {{ s.match.fits ? s.match.why.join(' · ') : 'Not a fit: ' + s.match.missing.join(', ') }}</div></td>
                      <td>
                        <span *ngIf="s.current_sponsor_id; else unassigned" class="badge badge-warning" [title]="'Assigned to sponsor ' + s.current_sponsor_code">
                          <i class="mdi mdi-account-check"></i> {{ s.current_sponsor_name || s.current_sponsor_code }}
                        </span>
                        <ng-template #unassigned><span class="badge badge-success">Unassigned</span></ng-template>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <app-pager [state]="pg" [total]="visible.length"></app-pager>
              <div class="border rounded p-2 mt-3">
                <div class="custom-control custom-checkbox">
                  <input type="checkbox" class="custom-control-input" id="onBehalf" name="onBehalf" [(ngModel)]="onBehalf.on">
                  <label class="custom-control-label" for="onBehalf">On behalf of an anonymous donor</label>
                </div>
                <small class="text-muted d-block">{{ data.sponsor.name }} stays the sponsor and the contact; the donor gets no account. The donor's
                  chapter sees these students on its Chapter Dashboard, with {{ data.sponsor.name }} to contact. Students keep their own chapter.</small>
                <div class="row mt-2" *ngIf="onBehalf.on">
                  <div class="col-md-6 form-group mb-2"><label for="donorChapter">Donor's chapter</label>
                    <select id="donorChapter" name="donorChapter" class="form-control" [(ngModel)]="onBehalf.chapterId">
                      <option [ngValue]="null">-- Choose --</option>
                      <option *ngFor="let c of chapterOptions()" [ngValue]="c.chapterId">{{ c.chapterName }}</option>
                    </select></div>
                  <div class="col-md-6 form-group mb-2"><label for="donorLabel">Label <span class="text-muted small">(optional, never a name)</span></label>
                    <input id="donorLabel" name="donorLabel" class="form-control" maxlength="80" [(ngModel)]="onBehalf.label" placeholder="e.g. Donor A"></div>
                </div>
              </div>
              <div class="mt-3">
                <button type="submit" class="btn btn-primary btn-block waves-effect waves-light" [disabled]="saving || (onBehalf.on && onBehalf.chapterId === null)">
                  <i class="mdi mdi-check-all mr-1"></i> Map Selected Students to Sponsor <span *ngIf="selected.size">({{ selected.size }})</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>

      <div class="col-12 col-xl-5 mt-4 mt-xl-0">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title mb-3">Students Mapped to this Sponsor</h4>
            <p class="text-muted small">{{ data.mappedStudents.length }} student(s) supported by this sponsor.</p>
            <div class="table-responsive table-fixed-head">
              <table class="table table-striped mb-0">
                <thead><tr><th>Student Name</th><th>Student ID</th><th>Email</th></tr></thead>
                <tbody>
                  <tr *ngIf="!data.mappedStudents.length"><td colspan="3" class="text-center text-muted">No students assigned yet.</td></tr>
                  <tr *ngFor="let m of data.mappedStudents | paginate: pgMapped.page : pgMapped.size">
                    <td><strong>{{ m.name }}</strong><div *ngIf="m.on_behalf_text" class="small text-info">{{ m.on_behalf_text }}</div></td>
                    <td><code class="text-primary">{{ m.user_id }}</code></td>
                    <td>{{ m.email || '-' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <app-pager [state]="pgMapped" [total]="data.mappedStudents.length"></app-pager>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminSponsorMapComponent implements OnInit {
  readonly pg = new PageState();
  readonly pgMapped = new PageState();
  /** Area this page is shown in ('admin' or 'office'); set from route data, defaults to admin. */
  @Input() set section(v: string | undefined) { this._section = v || 'admin'; }
  get section(): string { return this._section; }
  private _section = 'admin';
  /** The sponsor's users.id (route parameter :id). */
  @Input() id = '';

  data: MapScreen | null = null;
  search = '';
  /** users.id of the ticked students. */
  selected = new Set<number>();
  saving = false;
  message = '';
  error = '';

  /** The students ticked are sponsored on behalf of an anonymous donor of this chapter (with a short label). */
  onBehalf: { on: boolean; chapterId: number | null; label: string } = { on: false, chapterId: null, label: '' };
  chapters: Chapter[] = [];

  constructor(private api: ApiService, private chapterList: ChapterService) {
    this.chapterList.list().subscribe({ next: (c) => (this.chapters = c) });
  }

  chapterOptions(): Chapter[] { return ChapterService.options(this.chapters, this.onBehalf.chapterId); }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<MapScreen>(`/admin/sponsorships/${encodeURIComponent(this.id)}/map`).subscribe({
      next: (d) => { this.data = d; this.selected.clear(); },
      error: (e) => (this.error = errorText(e, 'Could not load sponsor.'))
    });
  }

  /** Show only the students who fit the sponsor's preferences. */
  onlyFits = false;

  get fitCount(): number { return (this.data?.availableStudents ?? []).filter((s) => s.match?.fits).length; }

  get visible() {
    const f = this.search.toLowerCase();
    return (this.data?.availableStudents ?? []).filter((s) => !this.onlyFits || s.match?.fits).filter((s) =>
      [s.name, s.user_id, s.current_sponsor_name, s.current_sponsor_code].some((v) => (v ?? '').toLowerCase().includes(f)));
  }

  /** Students on the page being shown (select-all only ticks these). */
  get pageRows() {
    return pageOf(this.visible, Math.min(this.pg.page, Math.max(1, Math.ceil(this.visible.length / this.pg.size))), this.pg.size);
  }

  get allChecked(): boolean {
    const v = this.pageRows;
    return v.length > 0 && v.every((s) => this.selected.has(s.id));
  }

  toggle(id: number): void {
    if (this.selected.has(id)) this.selected.delete(id); else this.selected.add(id);
  }

  toggleAll(event: Event): void {
    const on = (event.target as HTMLInputElement).checked;
    for (const s of this.pageRows) { if (on) this.selected.add(s.id); else this.selected.delete(s.id); }
  }

  submit(): void {
    this.error = '';
    if (!this.selected.size) { this.error = 'Please select at least one student.'; return; }
    this.saving = true;
    this.api.post<{ message: string }>(`/admin/sponsorships/${encodeURIComponent(this.id)}/map`, {
      studentIds: [...this.selected],
      onBehalf: this.onBehalf.on, donorChapterId: this.onBehalf.on ? this.onBehalf.chapterId : null,
      donorLabel: this.onBehalf.on ? this.onBehalf.label : null
    }).subscribe({
      next: (r) => { this.saving = false; this.message = r.message; this.load(); },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not map students.'); }
    });
  }
}
