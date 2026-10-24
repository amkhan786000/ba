import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';
import { StatTileComponent } from '../shared/charts/stat-tile.component';

interface Student {
  id: number; name: string; user_id: string | null; current: boolean; course: string | null; institution: string | null;
  study_status: string; from: string | null; until: string | null; installments_paid: number; latest_marks: string | null; now_doing: string | null;
}
interface Impact {
  sponsor: { name: string; user_id: string };
  totals: { students_supported: number; studying_now: number; graduated: number; installments_paid: number; sponsoring_since: string | null };
  students: Student[]; generated_on: string;
}

/** Sponsor portal > My Impact: every student this sponsor has supported, and how they are doing (no amounts). */
@Component({
  selector: 'app-sponsor-impact',
  standalone: true,
  imports: [CommonModule, AlertsComponent, AppDatePipe, StatTileComponent],
  styles: [`.tiles { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; margin-bottom: 20px; }`],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box d-flex flex-wrap justify-content-between align-items-center" style="gap: 8px">
      <h4 class="page-title">My Impact</h4>
      <button class="btn btn-primary" (click)="pdf()" [disabled]="busy || !d"><i class="mdi mdi-file-pdf-box mr-1"></i>Download PDF</button>
    </div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>
    <ng-container *ngIf="d">
      <p class="text-muted">Thank you{{ d.totals.sponsoring_since ? ' for sponsoring since ' : '' }}{{ d.totals.sponsoring_since ? (d.totals.sponsoring_since | appDate) : '' }}.
        Here is what your support has made possible.</p>
      <div class="tiles">
        <app-stat-tile label="Students supported" icon="mdi-hand-heart" [value]="d.totals.students_supported"></app-stat-tile>
        <app-stat-tile label="Studying now" icon="mdi-school" tone="info" [value]="d.totals.studying_now"></app-stat-tile>
        <app-stat-tile label="Graduated" icon="mdi-account-school" tone="success" [value]="d.totals.graduated"></app-stat-tile>
        <app-stat-tile label="Installments paid" icon="mdi-check-decagram" tone="purple" [value]="d.totals.installments_paid"></app-stat-tile>
      </div>
      <div class="card"><div class="card-body">
        <h4 class="header-title">Your students</h4>
        <p *ngIf="!d.students.length" class="text-muted mb-0">No students yet.</p>
        <div class="table-responsive" *ngIf="d.students.length"><table class="table table-sm mb-0">
          <thead class="thead-light"><tr><th>Student</th><th>Course</th><th>Status</th><th>Sponsored</th><th class="text-right">Installments</th><th class="text-right">Latest marks</th><th>Now</th></tr></thead>
          <tbody><tr *ngFor="let s of d.students" [class.text-muted]="!s.current">
            <td><strong>{{ s.name }}</strong> <span *ngIf="!s.current" class="badge badge-light border">earlier</span></td>
            <td>{{ s.course || '-' }}<div class="small text-muted">{{ s.institution }}</div></td>
            <td>{{ s.study_status }}</td>
            <td class="small">{{ s.from ? (s.from | appDate) : '' }}{{ s.until ? ' – ' + (s.until | appDate) : (s.current ? ' – now' : '') }}</td>
            <td class="text-right">{{ s.installments_paid }}</td>
            <td class="text-right">{{ s.latest_marks ? s.latest_marks + '%' : '-' }}</td>
            <td class="small">{{ s.now_doing || '' }}</td>
          </tr></tbody>
        </table></div>
      </div></div>
    </ng-container>
  `
})
export class SponsorImpactComponent implements OnInit {
  d: Impact | null = null;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<Impact>('/sponsor/impact').subscribe({ next: (d) => (this.d = d), error: (e) => (this.error = errorText(e, 'Could not load your impact.')) });
  }

  pdf(): void {
    this.busy = true;
    this.api.download('/sponsor/impact/pdf', {}, 'My-Rahbar-impact.pdf').subscribe({
      next: () => (this.busy = false),
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not download the PDF.'); }
    });
  }
}
