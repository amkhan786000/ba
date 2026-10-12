import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { AlertsComponent, errorText } from '../../shared/alerts/alerts.component';
import { CardTableDirective } from '../../shared/card-table.directive';
import { FEE_PLANS } from './reb.models';
import { AppDatePipe } from '../../shared/app-date';

interface Dashboard {
  session: { name: string; start_date: string; end_date: string } | null;
  active: number; residential: number; fee_plans: Record<string, number>;
  admitted_this_session?: number; left_this_session?: number; not_enrolled?: number; sections_without_teacher?: number;
  without_guardian_phone: number; without_birth_certificate?: number;
  sections?: { id: number; label: string; girls: number; capacity: number; class_teacher: string }[];
  fees?: { label: string; billed: number; bills_made: boolean; collected: number; overdue: number; girls_overdue: number } | null;
}

/** Rahbar-e-Banaat > REB Dashboard: the current session at a glance, and what needs attention. */
@Component({
  selector: 'app-reb-dashboard',
  standalone: true,
  imports: [AppDatePipe, CommonModule, RouterLink, AlertsComponent, CardTableDirective],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Rahbar-e-Banaat</h4></div></div></div>
    <app-alerts [(error)]="error"></app-alerts>

    <ng-container *ngIf="d">
      <div *ngIf="!d.session" class="alert alert-info">
        No session is set up yet. <a *ngIf="canSetup" routerLink="/admin/reb/setup">Open REB Setup</a> to add the session, classes, sections and subjects.
      </div>
      <p *ngIf="d.session" class="text-muted">Session <strong>{{ d.session.name }}</strong>: {{ d.session.start_date | appDate }} to {{ d.session.end_date | appDate }}</p>

      <div class="row">
        <div class="col-6 col-xl-3"><div class="card-box"><h4 class="header-title mt-0">Girls studying</h4><h2>{{ d.active }}</h2>
          <small class="text-muted">{{ d.residential }} in the hostel, {{ d.active - d.residential }} day scholars</small></div></div>
        <div class="col-6 col-xl-3" *ngFor="let k of planKeys"><div class="card-box"><h4 class="header-title mt-0">{{ plans[k] }}</h4><h2>{{ d.fee_plans[k] || 0 }}</h2>
          <small class="text-muted">fee plan</small></div></div>
      </div>

      <div class="row" *ngIf="d.fees as f">
        <div class="col-6 col-xl-3"><div class="card-box"><h4 class="header-title mt-0">Billed for {{ f.label }}</h4>
          <h2>₹{{ f.billed | number: '1.0-0' }}</h2><small class="text-muted"><a routerLink="/admin/reb/fees" [queryParams]="{ tab: 'bills' }">{{ f.bills_made ? 'See the bills' : 'Bills not made yet' }}</a></small></div></div>
        <div class="col-6 col-xl-3"><div class="card-box"><h4 class="header-title mt-0">Collected this month</h4>
          <h2>₹{{ f.collected | number: '1.0-0' }}</h2><small class="text-muted"><a routerLink="/admin/reb/fees" [queryParams]="{ tab: 'daybook' }">Day book</a></small></div></div>
        <div class="col-6 col-xl-3"><div class="card-box"><h4 class="header-title mt-0">Overdue</h4>
          <h2 [class.text-danger]="f.overdue > 0">₹{{ f.overdue | number: '1.0-0' }}</h2><small class="text-muted"><a routerLink="/admin/reb/fees" [queryParams]="{ tab: 'dues' }">{{ f.girls_overdue }} girls</a></small></div></div>
      </div>

      <div class="row">
        <div class="col-lg-7">
          <div class="card"><div class="card-body">
            <h4 class="header-title">Classes</h4>
            <div class="table-responsive"><table class="table table-sm table-centered mb-0">
              <thead><tr><th>Class</th><th>Girls</th><th>Class teacher</th></tr></thead>
              <tbody>
                <tr *ngIf="!d.sections?.length"><td colspan="3" class="text-center text-muted">No sections yet.</td></tr>
                <tr *ngFor="let s of d.sections">
                  <td><a *ngIf="canStudents; else plain" routerLink="/admin/reb/students">{{ s.label }}</a><ng-template #plain>{{ s.label }}</ng-template></td>
                  <td>{{ s.girls }}<span *ngIf="s.capacity" class="text-muted"> / {{ s.capacity }}</span></td>
                  <td>{{ s.class_teacher || '--' }}</td>
                </tr>
              </tbody>
            </table></div>
          </div></div>
        </div>
        <div class="col-lg-5">
          <div class="card"><div class="card-body">
            <h4 class="header-title">This session</h4>
            <ul class="list-unstyled mb-0">
              <li class="d-flex justify-content-between py-1 border-bottom"><span>Admitted</span><strong>{{ d.admitted_this_session ?? '--' }}</strong></li>
              <li class="d-flex justify-content-between py-1"><span>Left</span><strong>{{ d.left_this_session ?? '--' }}</strong></li>
            </ul>
          </div></div>
          <div class="card"><div class="card-body">
            <h4 class="header-title">Needs attention</h4>
            <ul class="list-unstyled mb-0">
              <li class="d-flex justify-content-between py-1 border-bottom" *ngFor="let a of attention">
                <span>{{ a.label }}</span><span class="badge" [ngClass]="a.count ? 'badge-warning' : 'badge-success'">{{ a.count }}</span>
              </li>
            </ul>
          </div></div>
        </div>
      </div>
    </ng-container>
  `
})
export class RebDashboardComponent implements OnInit {
  readonly plans = FEE_PLANS;
  readonly planKeys = Object.keys(FEE_PLANS);
  d: Dashboard | null = null;
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canSetup(): boolean { return this.auth.can('REB_SETUP'); }
  get canStudents(): boolean { return this.auth.can('REB_STUDENTS'); }

  get attention(): { label: string; count: number }[] {
    const d = this.d!;
    const out = [
      { label: 'Girls not in a class this session', count: d.not_enrolled ?? 0 },
      { label: 'Girls without a guardian phone number', count: d.without_guardian_phone },
      { label: 'Classes without a class teacher', count: d.sections_without_teacher ?? 0 }
    ];
    if (d.without_birth_certificate != null) out.push({ label: 'Girls without a birth certificate uploaded', count: d.without_birth_certificate });
    return out;
  }

  ngOnInit(): void {
    this.api.get<Dashboard>('/admin/reb/dashboard').subscribe({
      next: (r) => (this.d = r),
      error: (e) => (this.error = errorText(e, 'Could not load the dashboard.'))
    });
  }
}
