import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { AlertsComponent, errorText } from '../../shared/alerts/alerts.component';
import { CardTableDirective } from '../../shared/card-table.directive';
import { isoDate } from '../../shared/format';
import { RebStudentRow } from './reb.models';

export interface RebSponsorshipRow {
  id: number; student_id: number; student_name: string; admission_no: string; sponsor_user_id: number; sponsor_name: string;
  sponsor_code: string; monthly_amount: number; start_period: string; end_period: string | null; status: string; note: string | null;
}

/**
 * Rahbar-e-Banaat > Sponsorships: which Rahbar sponsor supports which girl, how much a month and since when.
 * Bills made for those months put that amount on the sponsor's share. Sponsors show by name and ID only.
 */
@Component({
  selector: 'app-reb-sponsorships',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, CardTableDirective],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Sponsorships</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="card"><div class="card-body">
      <div class="d-flex flex-wrap justify-content-between align-items-end mb-3">
        <div class="d-flex flex-wrap align-items-end">
          <div class="form-group mb-2 mr-2"><label for="sp-status">Show</label>
            <select id="sp-status" class="form-control" [(ngModel)]="status" (ngModelChange)="load()">
              <option value="ACTIVE">Current</option><option value="ENDED">Ended</option><option value="">All</option></select></div>
          <input class="form-control mb-2" style="max-width: 260px" placeholder="Girl or sponsor" [(ngModel)]="q" aria-label="Search sponsorships">
        </div>
        <button *ngIf="canEdit" class="btn btn-primary mb-2" (click)="openStart()"><i class="mdi mdi-plus mr-1"></i>Start a sponsorship</button>
      </div>
      <p class="small text-muted" *ngIf="status === 'ACTIVE'">{{ visible.length }} girls sponsored, ₹{{ monthlyTotal | number: '1.0-2' }} a month.</p>
      <div class="table-responsive"><table class="table table-sm table-centered mb-0">
        <thead><tr><th>Girl</th><th>Sponsor</th><th class="text-right">Per month</th><th>From</th><th>To</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!visible.length"><td colspan="6" class="text-center text-muted">No sponsorships.</td></tr>
          <tr *ngFor="let s of visible">
            <td><a [routerLink]="['/admin/reb/students', s.student_id]"><strong>{{ s.student_name }}</strong></a><div class="small text-muted">{{ s.admission_no }}</div></td>
            <td>{{ s.sponsor_name }}<div class="small text-muted">{{ s.sponsor_code }}</div></td>
            <td class="text-right">₹{{ s.monthly_amount | number: '1.0-2' }}</td>
            <td>{{ s.start_period }}</td><td>{{ s.end_period || 'ongoing' }}</td>
            <td class="text-right text-nowrap">
              <button *ngIf="canEdit && s.status === 'ACTIVE'" class="btn btn-xs btn-outline-primary mr-1" (click)="openEdit(s)">Edit</button>
              <button *ngIf="canEdit && s.status === 'ACTIVE'" class="btn btn-xs btn-outline-secondary" (click)="openEnd(s)">End</button>
            </td>
          </tr>
        </tbody>
      </table></div>
    </div></div>

    <div *ngIf="form" class="modal fade show d-block" tabindex="-1" (click)="form = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">{{ form.mode === 'start' ? 'Start a sponsorship' : form.mode === 'edit' ? 'Edit sponsorship' : 'End sponsorship' }}</h5>
            <button type="button" class="close" (click)="form = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <ng-container *ngIf="form.mode === 'start'">
              <div class="form-group"><label for="f-girl">Girl *</label>
                <input id="f-girl" class="form-control" [(ngModel)]="girlQ" (ngModelChange)="findGirls()" placeholder="Name or admission number" [disabled]="!!form.studentId">
                <div class="list-group mt-1" *ngIf="!form.studentId && girlOptions.length">
                  <a *ngFor="let g of girlOptions" class="list-group-item list-group-item-action py-1" tabindex="0" (click)="form.studentId = g.id; girlQ = g.name" (keydown.enter)="form.studentId = g.id; girlQ = g.name">
                    {{ g.name }} <small class="text-muted">{{ g.admission_no }} · {{ g.section }}</small></a></div>
                <button *ngIf="form.studentId" class="btn btn-link btn-sm p-0" (click)="form.studentId = null; girlQ = ''">Change</button>
              </div>
              <div class="form-group"><label for="f-sponsor">Rahbar sponsor *</label>
                <input id="f-sponsor" class="form-control" [(ngModel)]="sponsorQ" (ngModelChange)="findSponsors()" placeholder="Sponsor name or ID" [disabled]="!!form.sponsorUserId">
                <div class="list-group mt-1" *ngIf="!form.sponsorUserId && sponsorOptions.length">
                  <a *ngFor="let s of sponsorOptions" class="list-group-item list-group-item-action py-1" tabindex="0" (click)="form.sponsorUserId = s.id; sponsorQ = s.name + ' (' + s.user_id + ')'"
                     (keydown.enter)="form.sponsorUserId = s.id; sponsorQ = s.name + ' (' + s.user_id + ')'">{{ s.name }} <small class="text-muted">{{ s.user_id }}</small></a></div>
                <button *ngIf="form.sponsorUserId" class="btn btn-link btn-sm p-0" (click)="form.sponsorUserId = null; sponsorQ = ''">Change</button>
                <small class="d-block text-muted">Sponsors are added in Manage Users (role Sponsor).</small>
              </div>
            </ng-container>
            <ng-container *ngIf="form.mode !== 'end'">
              <div class="row">
                <div class="col-6 form-group"><label for="f-amount">Per month (₹)</label><input id="f-amount" type="number" min="1" class="form-control" [(ngModel)]="form.monthlyAmount" placeholder="500"></div>
                <div class="col-6 form-group"><label for="f-start">From month</label><input id="f-start" type="month" class="form-control" [(ngModel)]="form.startPeriod"></div>
              </div>
              <div class="form-group mb-0"><label for="f-note">Note</label><input id="f-note" class="form-control" [(ngModel)]="form.note"></div>
            </ng-container>
            <ng-container *ngIf="form.mode === 'end'">
              <div class="form-group"><label for="f-end">Last month the sponsor pays for</label><input id="f-end" type="month" class="form-control" [(ngModel)]="form.endPeriod"></div>
              <p class="small text-muted mb-0">Bills made for later months go fully to the family (unless another sponsor starts).</p>
            </ng-container>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="form = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="save()" [disabled]="busy || (form.mode === 'start' && (!form.studentId || !form.sponsorUserId))">Save</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class RebSponsorshipsComponent implements OnInit {
  rows: RebSponsorshipRow[] = [];
  status = 'ACTIVE';
  q = '';
  form: { mode: 'start' | 'edit' | 'end'; id: number | null; studentId: number | null; sponsorUserId: number | null; monthlyAmount: number | null;
    startPeriod: string; endPeriod: string; note: string } | null = null;
  girlQ = '';
  sponsorQ = '';
  girlOptions: RebStudentRow[] = [];
  sponsorOptions: { id: number; name: string; user_id: string }[] = [];
  private girls: RebStudentRow[] | null = null;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('REB_SPONSORSHIPS', 'EDIT'); }
  get visible(): RebSponsorshipRow[] {
    const q = this.q.trim().toLowerCase();
    return this.rows.filter((s) => !q || [s.student_name, s.admission_no, s.sponsor_name, s.sponsor_code].some((v) => (v ?? '').toLowerCase().includes(q)));
  }
  get monthlyTotal(): number { return this.visible.reduce((t, s) => t + Number(s.monthly_amount), 0); }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<RebSponsorshipRow[]>('/admin/reb/sponsorships', { status: this.status }).subscribe({
      next: (r) => (this.rows = r), error: (e) => (this.error = errorText(e, 'Could not load the sponsorships.'))
    });
  }

  private month(): string { return isoDate(new Date()).slice(0, 7); }

  openStart(): void {
    this.girlQ = '';
    this.sponsorQ = '';
    this.girlOptions = [];
    this.sponsorOptions = [];
    this.form = { mode: 'start', id: null, studentId: null, sponsorUserId: null, monthlyAmount: null, startPeriod: this.month(), endPeriod: '', note: '' };
  }

  openEdit(s: RebSponsorshipRow): void {
    this.form = { mode: 'edit', id: s.id, studentId: s.student_id, sponsorUserId: s.sponsor_user_id, monthlyAmount: s.monthly_amount,
      startPeriod: s.start_period, endPeriod: '', note: s.note ?? '' };
  }

  openEnd(s: RebSponsorshipRow): void {
    this.form = { mode: 'end', id: s.id, studentId: s.student_id, sponsorUserId: s.sponsor_user_id, monthlyAmount: null, startPeriod: s.start_period, endPeriod: this.month(), note: '' };
  }

  findGirls(): void {
    const q = this.girlQ.trim().toLowerCase();
    if (q.length < 2) { this.girlOptions = []; return; }
    const filter = () => (this.girlOptions = (this.girls ?? []).filter((g) => g.name.toLowerCase().includes(q) || g.admission_no.toLowerCase().includes(q)).slice(0, 10));
    if (this.girls) { filter(); return; }
    this.api.get<{ students: RebStudentRow[] }>('/admin/reb/students', { status: 'ACTIVE' }).subscribe({ next: (r) => { this.girls = r.students; filter(); } });
  }

  findSponsors(): void {
    const q = this.sponsorQ.trim();
    if (q.length < 2) { this.sponsorOptions = []; return; }
    this.api.get<{ id: number; name: string; user_id: string }[]>('/admin/reb/sponsor-options', { q }).subscribe({ next: (r) => (this.sponsorOptions = r) });
  }

  save(): void {
    const f = this.form;
    if (!f) return;
    this.busy = true;
    const req = f.mode === 'start' ? this.api.post<{ message: string }>('/admin/reb/sponsorships', f)
      : f.mode === 'edit' ? this.api.put<{ message: string }>(`/admin/reb/sponsorships/${f.id}`, { monthlyAmount: f.monthlyAmount, startPeriod: f.startPeriod, note: f.note })
      : this.api.post<{ message: string }>(`/admin/reb/sponsorships/${f.id}/end`, { endPeriod: f.endPeriod });
    req.subscribe({
      next: (r) => { this.busy = false; this.form = null; this.message = r.message; this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save the sponsorship.'); }
    });
  }
}
