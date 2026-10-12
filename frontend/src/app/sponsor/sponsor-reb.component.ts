import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';

interface Girl {
  student_id: number; name: string; status: string; class: string | null; monthly_amount: number; since: string; until: string | null;
  current: boolean; attendance_percent: number | null; results: { exam: string; percent: number | string; grade: string; result: string }[];
  payments: { id: number; receipt_no: string; paid_on: string; amount: number }[]; total_paid: number;
}

/**
 * Sponsor portal > Rahbar-e-Banaat: the girls of the girls' Madrasa-cum-School this sponsor supports (or
 * supported): her class, attendance this session, published results, and the payments made for her with receipts.
 */
@Component({
  selector: 'app-sponsor-reb',
  standalone: true,
  imports: [AppDatePipe, CommonModule, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Rahbar-e-Banaat</h4></div></div></div>
    <app-alerts [(error)]="error"></app-alerts>
    <p class="text-muted">The girls you sponsor at Rahbar-e-Banaat, the residential Madrasa-cum-School for girls.</p>
    <div *ngIf="loaded && !girls.length" class="card"><div class="card-body text-muted">You don't sponsor a girl at Rahbar-e-Banaat yet. To sponsor one, contact the office.</div></div>
    <div class="row">
      <div class="col-lg-6" *ngFor="let g of girls">
        <div class="card"><div class="card-body">
          <div class="d-flex justify-content-between align-items-start">
            <div><h4 class="mt-0 mb-1">{{ g.name }}</h4><div class="text-muted">{{ g.class || (g.status === 'PASSED_OUT' ? 'Passed out' : '') }}</div></div>
            <span class="badge" [ngClass]="g.current ? 'badge-success' : 'badge-light border'">{{ g.current ? 'Sponsoring' : 'Sponsored' }}</span>
          </div>
          <p class="small mt-2 mb-2">₹{{ g.monthly_amount | number: '1.0-2' }} a month since {{ g.since }}{{ g.until ? ' until ' + g.until : '' }} · you have given ₹{{ g.total_paid | number: '1.0-2' }}</p>
          <p class="mb-2" *ngIf="g.attendance_percent !== null">Attendance this session: <strong>{{ g.attendance_percent }}%</strong></p>
          <h6 *ngIf="g.results.length">Results</h6>
          <table class="table table-sm mb-2" *ngIf="g.results.length"><tbody>
            <tr *ngFor="let r of g.results"><td>{{ r.exam }}</td><td class="text-right">{{ r.percent }}%</td><td>{{ r.grade }}</td></tr>
          </tbody></table>
          <h6 *ngIf="g.payments.length">Your payments</h6>
          <ul class="list-unstyled small mb-0">
            <li *ngFor="let p of g.payments" class="d-flex justify-content-between border-bottom py-1">
              <span>{{ p.paid_on | appDate }} · ₹{{ p.amount | number: '1.0-2' }}</span>
              <a href="" (click)="$event.preventDefault(); receipt(p.id)">Receipt</a></li>
          </ul>
        </div></div>
      </div>
    </div>
  `
})
export class SponsorRebComponent implements OnInit {
  girls: Girl[] = [];
  loaded = false;
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<Girl[]>('/sponsor/reb-girls').subscribe({
      next: (r) => { this.girls = r; this.loaded = true; }, error: (e) => (this.error = errorText(e, 'Could not load the girls.'))
    });
  }

  receipt(id: number): void {
    this.api.download(`/sponsor/reb-receipts/${id}`, {}, 'receipt.pdf').subscribe({ error: (e) => (this.error = errorText(e, 'Could not create the receipt.')) });
  }
}
