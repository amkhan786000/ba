import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { AlertsComponent, errorText } from '../../shared/alerts/alerts.component';
import { PagerComponent } from '../../shared/pager/pager.component';
import { PagedResult, ServerTable } from '../../shared/pager/server-table';
import { CardTableDirective } from '../../shared/card-table.directive';
import { isoDate } from '../../shared/format';
import {
  CATEGORIES, FEE_PLANS, RELATIONS, RebGuardian, RebSectionRow, RebSession, RebStudentRow, STATUSES, guardianBody, newGuardian,
  planBadge, statusBadge
} from './reb.models';

interface ImportResult { added: number; updated: number; skipped: number; message: string; problems: { row: number; name: string; reason: string }[] }

/**
 * Rahbar-e-Banaat > Girls: every girl with her class in the chosen session and her main guardian. Admit a girl,
 * bring existing girls in from a spreadsheet, and open a girl's page.
 */
@Component({
  selector: 'app-reb-students',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, PagerComponent, CardTableDirective],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Girls</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div *ngIf="noSession" class="alert alert-info">There is no current session yet. Add one in <a routerLink="/admin/reb/setup">REB Setup</a> first.</div>

    <div class="d-flex flex-wrap mb-3" *ngIf="canEdit">
      <button class="btn btn-primary mr-2 mb-2" (click)="openAdmit()" [disabled]="!sections.length"><i class="mdi mdi-plus mr-1"></i>Admit a girl</button>
      <button class="btn btn-outline-primary mr-2 mb-2" (click)="importing = { file: null, result: null }"><i class="mdi mdi-upload mr-1"></i>Import from Excel / CSV</button>
      <a class="btn btn-outline-secondary mb-2" routerLink="/admin/reb/promotion"><i class="mdi mdi-arrow-up-bold-circle-outline mr-1"></i>Year-end promotion</a>
    </div>

    <div class="card">
      <div class="card-body">
        <div class="row">
          <div class="col-6 col-md-2 form-group">
            <label for="f-session">Session</label>
            <select id="f-session" class="form-control" [(ngModel)]="sessionId" (ngModelChange)="sectionId = null; loadSections(); load()">
              <option *ngFor="let s of sessions" [ngValue]="s.id">{{ s.name }}</option>
            </select>
          </div>
          <div class="col-6 col-md-3 form-group">
            <label for="f-section">Class</label>
            <select id="f-section" class="form-control" [(ngModel)]="sectionId" (ngModelChange)="table.filter()">
              <option [ngValue]="null">All classes</option>
              <option *ngFor="let s of sections" [ngValue]="s.id">{{ s.label }} ({{ s.girls }})</option>
            </select>
          </div>
          <div class="col-6 col-md-2 form-group">
            <label for="f-status">Status</label>
            <select id="f-status" class="form-control" [(ngModel)]="status" (ngModelChange)="load()">
              <option *ngFor="let s of statusKeys" [value]="s">{{ statuses[s] }}</option>
              <option value="ALL">Everyone</option>
            </select>
          </div>
          <div class="col-6 col-md-2 form-group">
            <label for="f-plan">Fee plan</label>
            <select id="f-plan" class="form-control" [(ngModel)]="feePlan" (ngModelChange)="table.filter()">
              <option value="">Any</option>
              <option *ngFor="let p of planKeys" [value]="p">{{ plans[p] }}</option>
            </select>
          </div>
          <div class="col-12 col-md-3 form-group">
            <label for="f-q">Search</label>
            <input id="f-q" class="form-control" placeholder="Name, admission no., guardian, phone" [(ngModel)]="q" (ngModelChange)="table.search()">
          </div>
        </div>
        <p class="small text-muted mb-2">{{ table.total }} girl{{ table.total === 1 ? '' : 's' }}<span *ngIf="table.last"> · {{ table.last.residential }} in the hostel</span></p>
        <div class="table-responsive">
          <table class="table table-sm table-centered table-hover mb-0">
            <thead><tr><th>Girl</th><th>Class</th><th>Roll</th><th>Guardian</th><th>Fee plan</th><th>Status</th></tr></thead>
            <tbody>
              <tr *ngIf="table.loading && !table.rows.length"><td colspan="6" class="text-center"><span class="spinner-border spinner-border-sm"></span></td></tr>
              <tr *ngIf="!table.loading && !table.rows.length"><td colspan="6" class="text-center text-muted">No girls found.</td></tr>
              <tr *ngFor="let r of table.rows" class="clickable" (click)="open(r)">
                <td><a [routerLink]="['/admin/reb/students', r.id]" (click)="$event.stopPropagation()"><strong>{{ r.name }}</strong></a>
                  <div class="small text-muted">{{ r.admission_no }}<span *ngIf="r.residential"> · Hostel</span></div></td>
                <td>{{ r.section || '--' }}</td>
                <td>{{ r.roll_no || '' }}</td>
                <td>{{ r.guardian_name || '--' }}<div class="small text-muted">{{ r.guardian_phone }}</div></td>
                <td><span class="badge" [ngClass]="planBadge(r.fee_plan)">{{ plans[r.fee_plan] }}</span></td>
                <td><span class="badge" [ngClass]="statusBadge(r.status)">{{ statuses[r.status] }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
        <app-pager [state]="table.pg" [total]="table.total" (pageChange)="table.load()"></app-pager>
      </div>
    </div>

    <!-- admit -->
    <div *ngIf="admit" class="modal fade show d-block" tabindex="-1" (click)="admit = null">
      <div class="modal-dialog modal-lg modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">Admit a girl</h5><button type="button" class="close" (click)="admit = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <h5 class="mt-0">Class</h5>
            <div class="row">
              <div class="col-md-6 form-group"><label for="a-section">Class and section *</label>
                <select id="a-section" class="form-control" [(ngModel)]="admit.d['sectionId']">
                  <option [ngValue]="null">-- Choose --</option>
                  <option *ngFor="let s of sections" [ngValue]="s.id">{{ s.label }}</option>
                </select></div>
              <div class="col-6 col-md-3 form-group"><label for="a-roll">Roll number</label><input id="a-roll" class="form-control" [(ngModel)]="admit.d['rollNo']"></div>
              <div class="col-6 col-md-3 form-group"><label for="a-date">Admitted on</label><input id="a-date" type="date" class="form-control" [(ngModel)]="admit.d['admittedOn']"></div>
            </div>
            <h5>The girl</h5>
            <div class="row">
              <div class="col-md-6 form-group"><label for="a-name">Full name *</label><input id="a-name" class="form-control" [(ngModel)]="admit.d['name']"></div>
              <div class="col-6 col-md-3 form-group"><label for="a-dob">Date of birth</label><input id="a-dob" type="date" class="form-control" [(ngModel)]="admit.d['dateOfBirth']"></div>
              <div class="col-6 col-md-3 form-group"><label for="a-blood">Blood group</label><input id="a-blood" class="form-control" [(ngModel)]="admit.d['bloodGroup']" placeholder="e.g. B+"></div>
              <div class="col-6 col-md-3 form-group"><label for="a-plan">Fee plan</label>
                <select id="a-plan" class="form-control" [(ngModel)]="admit.d['feePlan']"><option *ngFor="let p of planKeys" [value]="p">{{ plans[p] }}</option></select></div>
              <div class="col-6 col-md-3 form-group"><label for="a-cat">Category</label>
                <select id="a-cat" class="form-control" [(ngModel)]="admit.d['socialCategory']"><option [ngValue]="null">--</option><option *ngFor="let c of categories" [value]="c">{{ c }}</option></select></div>
              <div class="col-md-6 form-group d-flex align-items-end">
                <div class="custom-control custom-checkbox mb-2"><input type="checkbox" class="custom-control-input" id="a-res" [(ngModel)]="admit.d['residential']"><label class="custom-control-label" for="a-res">Lives in the hostel</label></div>
              </div>
              <div class="col-12 form-group"><label for="a-address">Address</label><input id="a-address" class="form-control" [(ngModel)]="admit.d['address']"></div>
              <div class="col-6 col-md-4 form-group"><label for="a-district">District</label><input id="a-district" class="form-control" [(ngModel)]="admit.d['district']"></div>
              <div class="col-6 col-md-4 form-group"><label for="a-state">State</label><input id="a-state" class="form-control" [(ngModel)]="admit.d['state']"></div>
              <div class="col-md-4 form-group"><label for="a-prev">Previous school</label><input id="a-prev" class="form-control" [(ngModel)]="admit.d['previousSchool']"></div>
              <div class="col-md-6 form-group" *ngIf="canPrivate"><label for="a-aadhaar">Aadhaar (last 4 digits)</label>
                <input id="a-aadhaar" class="form-control" maxlength="14" [(ngModel)]="admit.d['aadhaarLast4']" placeholder="Only the last 4 digits are kept"></div>
            </div>
            <h5>Parents and guardians</h5>
            <div *ngFor="let g of admit.guardians; let i = index" class="border rounded p-2 mb-2">
              <div class="row">
                <div class="col-6 col-md-3 form-group mb-2"><label [for]="'g-rel' + i">Relation</label>
                  <select [id]="'g-rel' + i" class="form-control" [(ngModel)]="g.relation"><option *ngFor="let r of relationKeys" [value]="r">{{ relations[r] }}</option></select></div>
                <div class="col-6 col-md-5 form-group mb-2"><label [for]="'g-name' + i">Name *</label><input [id]="'g-name' + i" class="form-control" [(ngModel)]="g.name"></div>
                <div class="col-6 col-md-4 form-group mb-2"><label [for]="'g-phone' + i">Phone</label><input [id]="'g-phone' + i" type="tel" class="form-control" [(ngModel)]="g.phone"></div>
                <div class="col-6 col-md-4 form-group mb-2"><label [for]="'g-occ' + i">Occupation</label><input [id]="'g-occ' + i" class="form-control" [(ngModel)]="g.occupation"></div>
                <div class="col-6 col-md-4 form-group mb-2" *ngIf="canPrivate"><label [for]="'g-inc' + i">Monthly income (₹)</label><input [id]="'g-inc' + i" type="number" min="0" class="form-control" [(ngModel)]="g.monthly_income"></div>
                <div class="col-12 d-flex flex-wrap align-items-center">
                  <div class="custom-control custom-radio mr-3"><input type="radio" class="custom-control-input" name="primary" [id]="'g-pri' + i" [checked]="g.is_primary" (change)="setPrimary(i)"><label class="custom-control-label" [for]="'g-pri' + i">Main contact</label></div>
                  <div class="custom-control custom-checkbox mr-auto"><input type="checkbox" class="custom-control-input" [id]="'g-pick' + i" [(ngModel)]="g.may_pick_up"><label class="custom-control-label" [for]="'g-pick' + i">May take her home</label></div>
                  <button *ngIf="admit.guardians.length > 1" class="btn btn-xs btn-outline-danger" (click)="admit.guardians.splice(i, 1)">Remove</button>
                </div>
              </div>
            </div>
            <button class="btn btn-sm btn-outline-primary" (click)="admit.guardians.push(newGuardian())"><i class="mdi mdi-plus mr-1"></i>Add another guardian</button>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="admit = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="saveAdmit()" [disabled]="busy || !admit.d['name'] || !admit.d['sectionId'] || !admit.guardians[0].name">{{ busy ? 'Saving…' : 'Admit' }}</button>
          </div>
        </div>
      </div>
    </div>

    <!-- import -->
    <div *ngIf="importing" class="modal fade show d-block" tabindex="-1" (click)="importing = null">
      <div class="modal-dialog modal-lg modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">Import girls from Excel / CSV</h5><button type="button" class="close" (click)="importing = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <ol class="pl-3">
              <li>Download the <a href="" (click)="$event.preventDefault(); template()">template</a> and fill one row per girl (in Excel, save it as <strong>CSV UTF-8</strong>).</li>
              <li>The class must match a class in REB Setup; a missing section of the current session is added.</li>
              <li>Girls with an admission number that is already known are updated, not added again.</li>
            </ol>
            <input type="file" accept=".csv,text/csv" class="form-control-file mb-2" (change)="importing.file = fileOf($event)" aria-label="CSV file">
            <div *ngIf="importing.result as r" class="mt-3">
              <div class="alert" [ngClass]="r.problems.length ? 'alert-warning' : 'alert-success'">{{ r.message }}</div>
              <div class="table-responsive" *ngIf="r.problems.length"><table class="table table-sm mb-0">
                <thead><tr><th>Row</th><th>Name</th><th>Problem</th></tr></thead>
                <tbody><tr *ngFor="let p of r.problems"><td>{{ p.row }}</td><td>{{ p.name }}</td><td>{{ p.reason }}</td></tr></tbody>
              </table></div>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="importing = null">Close</button>
            <button type="button" class="btn btn-primary" (click)="runImport()" [disabled]="busy || !importing.file">{{ busy ? 'Importing…' : 'Import' }}</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class RebStudentsComponent implements OnInit, OnDestroy {
  readonly plans = FEE_PLANS;
  readonly planKeys = Object.keys(FEE_PLANS);
  readonly statuses = STATUSES;
  readonly statusKeys = Object.keys(STATUSES);
  readonly relations = RELATIONS;
  readonly relationKeys = Object.keys(RELATIONS);
  readonly categories = CATEGORIES;
  readonly newGuardian = newGuardian;
  readonly planBadge = planBadge;
  readonly statusBadge = statusBadge;

  sessions: RebSession[] = [];
  sections: RebSectionRow[] = [];
  sessionId: number | null = null;
  sectionId: number | null = null;
  status = 'ACTIVE';
  feePlan = '';
  q = '';
  readonly table = new ServerTable<RebStudentRow, PagedResult<RebStudentRow> & { residential: number }>(
    (p) => this.api.get('/admin/reb/students', { sessionId: this.sessionId, sectionId: this.sectionId, status: this.status,
      feePlan: this.feePlan, q: this.q, ...p }),
    (e) => (this.error = errorText(e, 'Could not load the girls.')));
  noSession = false;
  busy = false;
  message = '';
  error = '';
  admit: { d: Record<string, unknown>; guardians: RebGuardian[] } | null = null;
  importing: { file: File | null; result: ImportResult | null } | null = null;

  constructor(private api: ApiService, private auth: AuthService, private router: Router) {}

  get canEdit(): boolean { return this.auth.can('REB_STUDENTS', 'EDIT'); }
  get canPrivate(): boolean { return this.auth.can('REB_STUDENT_PRIVATE', 'EDIT'); }

  ngOnInit(): void {
    this.api.get<RebSession[]>('/admin/reb/sessions').subscribe({
      next: (r) => {
        this.sessions = r;
        const current = r.find((s) => s.is_current);
        this.noSession = !current;
        this.sessionId = current?.id ?? r[0]?.id ?? null;
        this.loadSections();
        this.load();
      },
      error: (e) => (this.error = errorText(e, 'Could not load the sessions.'))
    });
  }

  loadSections(): void {
    if (!this.sessionId) { this.sections = []; return; }
    this.api.get<RebSectionRow[]>('/admin/reb/sections', { sessionId: this.sessionId }).subscribe({
      next: (r) => (this.sections = r), error: () => (this.sections = [])
    });
  }

  load(): void { this.table.filter(); }

  ngOnDestroy(): void { this.table.destroy(); }

  open(r: RebStudentRow): void { this.router.navigate(['/admin/reb/students', r.id]); }

  openAdmit(): void {
    this.admit = {
      d: { sectionId: this.sectionId, rollNo: '', admittedOn: isoDate(new Date()), name: '', dateOfBirth: '', bloodGroup: '',
        feePlan: 'SELF', socialCategory: null, residential: true, address: '', district: '', state: 'Bihar', previousSchool: '', aadhaarLast4: '' },
      guardians: [newGuardian(true)]
    };
  }

  setPrimary(i: number): void { this.admit?.guardians.forEach((g, j) => (g.is_primary = i === j)); }

  saveAdmit(): void {
    if (!this.admit) return;
    this.busy = true;
    const body = { ...this.admit.d, guardians: this.admit.guardians.filter((g) => g.name.trim()).map(guardianBody) };
    this.api.post<{ message: string; id: number }>('/admin/reb/students', body).subscribe({
      next: (r) => { this.busy = false; this.admit = null; this.router.navigate(['/admin/reb/students', r.id]); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not admit her.'); }
    });
  }

  fileOf(e: Event): File | null { return (e.target as HTMLInputElement).files?.[0] ?? null; }

  template(): void {
    this.api.download('/admin/reb/students/import/template', {}, 'reb_students_template.csv').subscribe({
      error: (e) => (this.error = errorText(e, 'Could not download the template.'))
    });
  }

  runImport(): void {
    if (!this.importing?.file) return;
    const f = new FormData();
    f.append('file', this.importing.file);
    this.busy = true;
    this.api.post<ImportResult>('/admin/reb/students/import', f).subscribe({
      next: (r) => { this.busy = false; if (this.importing) this.importing.result = r; this.loadSections(); this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not import the file.'); }
    });
  }
}
