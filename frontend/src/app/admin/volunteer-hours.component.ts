import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';
import { StatTileComponent } from '../shared/charts/stat-tile.component';
import { BarChartComponent, BarDatum } from '../shared/bar-chart/bar-chart.component';

type Row = Record<string, any>;

/**
 * Community > Volunteer Hours: hours given by volunteers, mentors and alumni. Log hours for anyone, approve the
 * hours people logged themselves, and print a certificate of someone's approved hours.
 */
@Component({
  selector: 'app-volunteer-hours',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, AppDatePipe, StatTileComponent, BarChartComponent],
  styles: [`.tiles { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 12px; margin-bottom: 20px; }`],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Volunteer Hours</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="d-flex flex-wrap align-items-end mb-3" style="gap: 8px">
      <div><label class="small mb-0" for="vf">From</label><input id="vf" type="date" class="form-control form-control-sm" [(ngModel)]="from" (ngModelChange)="load()"></div>
      <div><label class="small mb-0" for="vt">To</label><input id="vt" type="date" class="form-control form-control-sm" [(ngModel)]="to" (ngModelChange)="load()"></div>
      <select class="form-control form-control-sm w-auto" [(ngModel)]="status" (ngModelChange)="load()" aria-label="Status">
        <option value="">All</option><option value="APPROVED">Approved</option><option value="PENDING">To approve</option><option value="REJECTED">Rejected</option></select>
      <input class="form-control form-control-sm w-auto" placeholder="Search name" [(ngModel)]="q" (ngModelChange)="load()">
      <button *ngIf="canEdit" class="btn btn-sm btn-primary ml-auto" (click)="openLog()"><i class="mdi mdi-plus"></i> Log hours</button>
    </div>

    <ng-container *ngIf="d">
      <div class="tiles">
        <app-stat-tile label="Hours given" icon="mdi-clock-outline" [value]="n(d['total_hours'])" sub="approved, in this period"></app-stat-tile>
        <app-stat-tile label="Volunteers" icon="mdi-hand-heart" tone="success" [value]="d['volunteers']"></app-stat-tile>
        <app-stat-tile label="To approve" icon="mdi-check-circle-outline" [tone]="d['pending'] ? 'warning' : 'primary'" [value]="d['pending']" sub="logged by volunteers themselves"></app-stat-tile>
      </div>
      <div class="row">
        <div class="col-lg-5"><div class="card"><div class="card-body">
          <h4 class="header-title">Hours by activity</h4>
          <app-bar-chart [data]="activityBars" unit="h" emptyText="No hours yet."></app-bar-chart>
        </div></div></div>
        <div class="col-lg-7"><div class="card"><div class="card-body">
          <h4 class="header-title">Volunteers <small class="text-muted">· most hours first</small></h4>
          <p *ngIf="!d['people'].length" class="text-muted mb-0">No approved hours in this period.</p>
          <table class="table table-sm mb-0" *ngIf="d['people'].length"><tbody>
            <tr *ngFor="let p of d['people']">
              <td>{{ p['name'] }} <span class="small text-muted">{{ p['kind'] === 'VOLUNTEER' ? 'volunteer sign-up' : 'Rahbar user' }}</span></td>
              <td class="text-right"><strong>{{ n(p['hours']) }} h</strong> <span class="small text-muted">· {{ p['days'] }} day(s)</span></td>
              <td class="text-right"><button class="btn btn-xs btn-outline-primary" (click)="certificate(p)"><i class="mdi mdi-certificate"></i> Certificate</button></td>
            </tr>
          </tbody></table>
        </div></div></div>
      </div>

      <div class="card"><div class="card-body">
        <h4 class="header-title">Hours logged</h4>
        <p *ngIf="!d['rows'].length" class="text-muted mb-0">Nothing in this period.</p>
        <div class="table-responsive" *ngIf="d['rows'].length"><table class="table table-sm mb-0">
          <thead class="thead-light"><tr><th>Date</th><th>Who</th><th>Activity</th><th class="text-right">Hours</th><th>What</th><th>Status</th><th></th></tr></thead>
          <tbody><tr *ngFor="let h of d['rows']">
            <td>{{ h['worked_on'] | appDate }}</td><td>{{ h['person'] }}</td><td>{{ h['activity_label'] }}</td><td class="text-right">{{ n(h['hours']) }}</td>
            <td class="small">{{ h['description'] }}</td>
            <td><span class="badge" [ngClass]="h['status'] === 'APPROVED' ? 'badge-success' : h['status'] === 'PENDING' ? 'badge-warning' : 'badge-light border'">
              {{ h['status'] === 'APPROVED' ? 'Approved' : h['status'] === 'PENDING' ? 'To approve' : 'Rejected' }}</span>
              <span *ngIf="h['self_logged']" class="small text-muted d-block">self-logged</span></td>
            <td class="text-right text-nowrap" *ngIf="canEdit">
              <ng-container *ngIf="h['status'] === 'PENDING'">
                <button class="btn btn-xs btn-success" (click)="review(h, true)">Approve</button>
                <button class="btn btn-xs btn-outline-secondary ml-1" (click)="review(h, false)">Reject</button></ng-container>
              <button class="btn btn-xs btn-light ml-1" (click)="remove(h)" title="Delete"><i class="mdi mdi-delete"></i></button></td>
          </tr></tbody>
        </table></div>
      </div></div>
    </ng-container>

    <div *ngIf="form" class="modal fade show d-block" tabindex="-1" (click)="form = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()"><div class="modal-content">
        <div class="modal-header"><h5 class="modal-title">Log volunteer hours</h5>
          <button type="button" class="close" (click)="form = null" aria-label="Close">&times;</button></div>
        <div class="modal-body">
          <div *ngIf="formError" class="alert alert-danger">{{ formError }}</div>
          <div class="form-group"><label>Who *</label>
            <div *ngIf="form['personId']; else pickPerson" class="form-control-plaintext"><strong>{{ form['personName'] }}</strong>
              <a href="" class="small ml-2" (click)="$event.preventDefault(); form['personId'] = null">change</a></div>
            <ng-template #pickPerson>
              <input class="form-control" placeholder="Type a volunteer's or mentor's name" [(ngModel)]="who" (ngModelChange)="search()">
              <div class="list-group mt-1" *ngIf="hits.length">
                <a href="" class="list-group-item list-group-item-action py-1" *ngFor="let h of hits"
                   (click)="$event.preventDefault(); form['personKind'] = h['kind']; form['personId'] = h['id']; form['personName'] = h['name']; hits = []">
                  {{ h['name'] }} <span class="small text-muted">{{ h['who'] }}</span></a></div>
            </ng-template></div>
          <div class="row">
            <div class="col-6 form-group"><label>Activity</label><select class="form-control" [(ngModel)]="form['activity']">
              <option *ngFor="let a of activityList" [value]="a.key">{{ a.label }}</option></select></div>
            <div class="col-3 form-group"><label>Date</label><input type="date" class="form-control" [max]="today" [(ngModel)]="form['workedOn']"></div>
            <div class="col-3 form-group"><label>Hours *</label><input type="number" min="0.5" max="24" step="0.5" class="form-control" [(ngModel)]="form['hours']"></div>
          </div>
          <div class="form-group"><label>What they did</label><input class="form-control" maxlength="500" [(ngModel)]="form['description']"></div>
        </div>
        <div class="modal-footer"><button class="btn btn-light" (click)="form = null">Cancel</button>
          <button class="btn btn-primary" (click)="save()" [disabled]="busy || !form['personId'] || !form['hours']">Save</button></div>
      </div></div>
    </div>
  `
})
export class VolunteerHoursComponent implements OnInit {
  readonly today = new Date().toISOString().slice(0, 10);
  d: Row | null = null;
  from = new Date().getFullYear() + '-01-01';
  to = this.today;
  status = '';
  q = '';
  form: Row | null = null;
  formError = '';
  who = '';
  hits: Row[] = [];
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('VOLUNTEER_HOURS', 'EDIT'); }
  get activityList() { return Object.entries((this.d?.['activities'] ?? {}) as Record<string, string>).map(([key, label]) => ({ key, label })); }
  get activityBars(): BarDatum[] {
    return Object.entries((this.d?.['by_activity'] ?? {}) as Record<string, number>).filter(([, v]) => Number(v) > 0).map(([label, value]) => ({ label, value }));
  }

  n(v: unknown): string { return v == null ? '0' : String(Number(v)); }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<Row>('/admin/volunteer-hours', { status: this.status, from: this.from, to: this.to, q: this.q }).subscribe({
      next: (d) => (this.d = d), error: (e) => (this.error = errorText(e, 'Could not load.'))
    });
  }

  openLog(): void { this.formError = ''; this.who = ''; this.hits = []; this.form = { activity: 'MENTORING', workedOn: this.today, hours: 2 }; }

  search(): void {
    if (this.who.trim().length < 2) { this.hits = []; return; }
    this.api.get<Row[]>('/admin/volunteer-hours/people', { q: this.who }).subscribe({ next: (h) => (this.hits = h) });
  }

  save(): void {
    this.busy = true;
    this.api.post<{ message: string }>('/admin/volunteer-hours', this.form).subscribe({
      next: (m) => { this.busy = false; this.form = null; this.message = m.message; this.load(); },
      error: (e) => { this.busy = false; this.formError = errorText(e, 'Could not save.'); }
    });
  }

  review(h: Row, approve: boolean): void {
    this.api.put<{ message: string }>(`/admin/volunteer-hours/${h['id']}/review`, { approve }).subscribe({
      next: () => this.load(), error: (e) => (this.error = errorText(e, 'Could not save.'))
    });
  }

  remove(h: Row): void {
    if (!confirm('Delete these hours?')) return;
    this.api.delete(`/admin/volunteer-hours/${h['id']}`).subscribe({ next: () => this.load(), error: (e) => (this.error = errorText(e, 'Could not delete.')) });
  }

  certificate(p: Row): void {
    this.api.download('/admin/volunteer-hours/certificate', { kind: p['kind'], id: p['id'], from: this.from, to: this.to }, 'Volunteer-certificate.pdf')
      .subscribe({ error: (e) => (this.error = errorText(e, 'Could not make the certificate.')) });
  }
}
