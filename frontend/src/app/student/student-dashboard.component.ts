import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { BarChartComponent, BarDatum } from '../shared/bar-chart/bar-chart.component';

interface Person { name?: string; email?: string | null; phone?: string | null }
interface Progress { marks: string | number | null; session: string | null; year: string | number | null }

/** Port of templates/student/dashboard.html (charts use the student's own marks instead of sample numbers). */
@Component({
  selector: 'app-student-dashboard',
  standalone: true,
  imports: [CommonModule, AlertsComponent, BarChartComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box mt-2"><h4 class="page-title">Student Dashboard</h4></div></div></div>
    <app-alerts [(error)]="error"></app-alerts>

    <div class="row">
      <div class="col-12 col-md-6">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Student Details</h4>
            <div class="mt-3">
              <p class="mb-1"><strong>Name:</strong> {{ student?.name }}</p>
              <p class="mb-1"><strong>Email:</strong> {{ student?.email }}</p>
              <p class="mb-0"><strong>Phone:</strong> {{ student?.phone }}</p>
            </div>
          </div>
        </div>
      </div>
      <div class="col-12 col-md-6">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Sponsor Details</h4>
            <div class="mt-3">
              <ng-container *ngIf="sponsor?.name; else noSponsor">
                <p class="mb-1"><strong>Name:</strong> {{ sponsor?.name }}</p>
                <p class="mb-1"><strong>Email:</strong> {{ sponsor?.email }}</p>
                <p class="mb-0"><strong>Phone:</strong> {{ sponsor?.phone }}</p>
              </ng-container>
              <ng-template #noSponsor><p class="text-muted">No sponsor assigned yet.</p></ng-template>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="row">
      <div class="col-12 col-xl-6">
        <div class="card-box">
          <h4 class="header-title mb-4">Marks per year</h4>
          <app-bar-chart [data]="byYear"></app-bar-chart>
        </div>
      </div>
      <div class="col-12 col-xl-6">
        <div class="card-box">
          <h4 class="header-title mb-4">Marks Distribution</h4>
          <app-bar-chart [data]="bySession"></app-bar-chart>
          <p class="text-muted mb-0 mt-3 text-truncate">Your marks for each academic session</p>
        </div>
      </div>
    </div>
  `
})
export class StudentDashboardComponent implements OnInit {
  student: Person | null = null;
  sponsor: Person | null = null;
  byYear: BarDatum[] = [];
  bySession: BarDatum[] = [];
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<{ student: Person; sponsor: Person }>('/student/dashboard').subscribe({
      next: (d) => { this.student = d.student; this.sponsor = d.sponsor; },
      error: (e) => (this.error = errorText(e, 'Could not load the dashboard.'))
    });
    this.api.get<Progress[]>('/student/progress').subscribe({
      next: (rows) => {
        this.byYear = this.average(rows, (p) => (p.year != null ? `Year ${p.year}` : null));
        this.bySession = this.average(rows, (p) => p.session);
      }
    });
  }

  /** Average numeric marks per group, in first-seen (chronological) order. */
  private average(rows: Progress[], key: (p: Progress) => string | null): BarDatum[] {
    const groups = new Map<string, number[]>();
    for (const p of [...rows].reverse()) {
      const k = key(p);
      const m = parseFloat(String(p.marks ?? ''));
      if (!k || isNaN(m)) continue;
      groups.set(k, [...(groups.get(k) ?? []), m]);
    }
    return [...groups.entries()].map(([label, ms]) => ({ label, value: Math.round((ms.reduce((a, b) => a + b, 0) / ms.length) * 10) / 10 }));
  }
}
