import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { AlertsComponent, errorText } from '../../shared/alerts/alerts.component';
import { isoDate } from '../../shared/format';
import { RELATIONS, RebGuardian, RebStudentRow } from './reb.models';

interface Leave {
  id: number; pass_no: string; student_id: number; student_name: string; admission_no: string; guardian_name: string | null;
  guardian_relation: string | null; guardian_phone: string | null; leave_from: string; leave_to: string; reason: string;
  status: string; returned_at: string | null; returned_with: string | null; note: string | null; overdue: boolean;
}

/**
 * Rahbar-e-Banaat > Hostel Leave: girls out of the hostel now (overdue ones first), gate passes given only to a
 * guardian allowed to take her home, and marking her back.
 */
@Component({
  selector: 'app-reb-hostel',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Hostel Leave</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="card"><div class="card-body">
      <div class="d-flex flex-wrap justify-content-between align-items-end mb-2">
        <div class="btn-group btn-group-sm mb-2" role="group" aria-label="Show">
          <button type="button" class="btn" [ngClass]="show === 'OUT' ? 'btn-secondary' : 'btn-light'" (click)="show = 'OUT'; load()">Out now</button>
          <button type="button" class="btn" [ngClass]="show === 'OVERDUE' ? 'btn-secondary' : 'btn-light'" (click)="show = 'OVERDUE'; load()">Overdue</button>
          <button type="button" class="btn" [ngClass]="show === '' ? 'btn-secondary' : 'btn-light'" (click)="show = ''; load()">All</button>
        </div>
        <button *ngIf="canEdit" class="btn btn-primary btn-sm mb-2" (click)="openPass()"><i class="mdi mdi-plus mr-1"></i>Give a gate pass</button>
      </div>
      <div class="table-responsive"><table class="table table-sm table-centered mb-0">
        <thead><tr><th>Pass</th><th>Girl</th><th>Taken by</th><th>Out since</th><th>Due back</th><th>Reason</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!rows.length"><td colspan="7" class="text-center text-muted">{{ show === 'OUT' ? 'No girl is out of the hostel.' : 'Nothing here.' }}</td></tr>
          <tr *ngFor="let l of rows" [class.table-danger]="l.overdue">
            <td class="small">{{ l.pass_no }}</td>
            <td><a [routerLink]="['/admin/reb/students', l.student_id]"><strong>{{ l.student_name }}</strong></a></td>
            <td>{{ l.guardian_name }} <span class="small text-muted">{{ relation(l.guardian_relation) }}</span><div class="small text-muted">{{ l.guardian_phone }}</div></td>
            <td>{{ l.leave_from | date: 'd MMM, h:mm a' }}</td>
            <td>{{ l.leave_to | date: 'd MMM' }}<span *ngIf="l.overdue" class="badge badge-danger ml-1">Overdue</span></td>
            <td>{{ l.reason }}<div class="small text-muted" *ngIf="l.status === 'RETURNED'">Back {{ l.returned_at | date: 'd MMM, h:mm a' }}{{ l.returned_with ? ' with ' + l.returned_with : '' }}</div></td>
            <td class="text-right"><button *ngIf="canEdit && l.status === 'OUT'" class="btn btn-xs btn-success" (click)="back(l)">She's back</button></td>
          </tr>
        </tbody>
      </table></div>
    </div></div>

    <div *ngIf="pass" class="modal fade show d-block" tabindex="-1" (click)="pass = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">Give a gate pass</h5><button type="button" class="close" (click)="pass = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <div class="form-group"><label for="p-girl">Girl *</label>
              <input id="p-girl" class="form-control" [(ngModel)]="girlQ" (ngModelChange)="findGirls()" placeholder="Name or admission number" [disabled]="!!pass.studentId">
              <div class="list-group mt-1" *ngIf="!pass.studentId && girlOptions.length">
                <a *ngFor="let g of girlOptions" class="list-group-item list-group-item-action py-1" tabindex="0" (click)="pickGirl(g)" (keydown.enter)="pickGirl(g)">
                  {{ g.name }} <small class="text-muted">{{ g.section }}</small></a></div>
              <button *ngIf="pass.studentId" class="btn btn-link btn-sm p-0" (click)="pass.studentId = null; girlQ = ''; pickers = []">Change</button>
            </div>
            <div class="form-group" *ngIf="pass.studentId"><label for="p-guardian">Taken by *</label>
              <select id="p-guardian" class="form-control" [(ngModel)]="pass.guardianId"><option [ngValue]="null">-- Choose --</option>
                <option *ngFor="let g of pickers" [ngValue]="g.id">{{ g.name }} ({{ relation(g.relation) }}){{ g.phone ? ' · ' + g.phone : '' }}</option></select>
              <small *ngIf="!pickers.length" class="text-danger">None of her guardians may take her home. Change that on her page first.</small></div>
            <div class="form-group"><label for="p-back">Due back on *</label><input id="p-back" type="date" class="form-control" [min]="today" [(ngModel)]="pass.leaveTo"></div>
            <div class="form-group"><label for="p-reason">Reason *</label><input id="p-reason" class="form-control" [(ngModel)]="pass.reason" placeholder="e.g. Eid holidays, family wedding"></div>
            <div class="form-group mb-0"><label for="p-note">Note</label><input id="p-note" class="form-control" [(ngModel)]="pass.note"></div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="pass = null">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="give()" [disabled]="busy || !pass.studentId || !pass.guardianId || !pass.leaveTo || !pass.reason.trim()">Give pass</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class RebHostelComponent implements OnInit {
  readonly today = isoDate(new Date());
  rows: Leave[] = [];
  show = 'OUT';
  pass: { studentId: number | null; guardianId: number | null; leaveTo: string; reason: string; note: string } | null = null;
  girlQ = '';
  girlOptions: RebStudentRow[] = [];
  pickers: RebGuardian[] = [];
  private girls: RebStudentRow[] | null = null;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('REB_HOSTEL', 'EDIT'); }
  relation(r: string | null): string { return r ? RELATIONS[r] ?? r : ''; }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<Leave[]>('/admin/reb/hostel-leaves', { status: this.show }).subscribe({
      next: (r) => (this.rows = [...r].sort((a, b) => Number(b.overdue) - Number(a.overdue))),
      error: (e) => (this.error = errorText(e, 'Could not load the hostel leave.'))
    });
  }

  openPass(): void {
    this.girlQ = '';
    this.girlOptions = [];
    this.pickers = [];
    this.pass = { studentId: null, guardianId: null, leaveTo: '', reason: '', note: '' };
  }

  findGirls(): void {
    const q = this.girlQ.trim().toLowerCase();
    if (q.length < 2) { this.girlOptions = []; return; }
    const filter = () => (this.girlOptions = (this.girls ?? []).filter((g) => g.residential && (g.name.toLowerCase().includes(q) || g.admission_no.toLowerCase().includes(q))).slice(0, 10));
    if (this.girls) { filter(); return; }
    this.api.get<{ students: RebStudentRow[] }>('/admin/reb/students', { status: 'ACTIVE' }).subscribe({ next: (r) => { this.girls = r.students; filter(); } });
  }

  pickGirl(g: RebStudentRow): void {
    if (!this.pass) return;
    this.pass.studentId = g.id;
    this.girlQ = g.name;
    this.api.get<{ guardians: RebGuardian[] }>(`/admin/reb/students/${g.id}`).subscribe({
      next: (r) => {
        this.pickers = r.guardians.filter((x) => x.may_pick_up);
        if (this.pass && this.pickers.length === 1) this.pass.guardianId = this.pickers[0].id ?? null;
      }
    });
  }

  give(): void {
    if (!this.pass) return;
    this.busy = true;
    this.api.post<{ message: string }>('/admin/reb/hostel-leaves', this.pass).subscribe({
      next: (r) => { this.busy = false; this.pass = null; this.message = r.message; this.show = 'OUT'; this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not give the pass.'); }
    });
  }

  back(l: Leave): void {
    const who = prompt(`${l.student_name} is back. Who brought her? (optional)`, l.guardian_name ?? '');
    if (who === null) return;
    this.api.post<{ message: string }>(`/admin/reb/hostel-leaves/${l.id}/return`, { returnedWith: who }).subscribe({
      next: (r) => { this.message = r.message; this.load(); }, error: (e) => (this.error = errorText(e, 'Could not mark her back.'))
    });
  }
}
