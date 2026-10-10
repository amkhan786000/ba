import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { Schedule } from './admin-system-config.component';

/** Add / edit the payment config of a session year. Editing updates the unpaid installments of that year's students. */
@Component({
  selector: 'app-admin-payment-config-edit',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">{{ isEdit ? 'Edit' : 'Add' }} Payment Config</h4></div></div></div>
    <div class="row">
      <div class="col-12 col-md-8 offset-md-2">
        <app-alerts [(error)]="error"></app-alerts>
        <div class="card">
          <div class="card-body">
            <form #f="ngForm" (ngSubmit)="save()">
              <div class="form-group">
                <label for="year">Session Year</label>
                <input *ngIf="isEdit" type="text" class="form-control" id="year" [value]="form.year" disabled />
                <select *ngIf="!isEdit" class="form-control" id="year" name="year" [(ngModel)]="form.year" required>
                  <option [ngValue]="null">-- Select Year --</option>
                  <option *ngFor="let y of freeYears" [ngValue]="y">{{ y }}</option>
                </select>
                <small class="text-muted">Applies to students whose session year is this year.</small>
              </div>
              <div class="row">
                <div class="col-md-6 form-group">
                  <label for="amount">Amount per Installment (₹)</label>
                  <input type="number" class="form-control" id="amount" name="amount" step="0.01" min="0" [(ngModel)]="form.amount" required />
                </div>
                <div class="col-md-6 form-group">
                  <label for="frequency">Frequency</label>
                  <select class="form-control" id="frequency" name="frequency" [(ngModel)]="form.frequency">
                    <option [ngValue]="3">Every 3 months (4 installments a year)</option>
                    <option [ngValue]="4">Every 4 months (3 installments a year)</option>
                  </select>
                </div>
              </div>
              <div class="form-group">
                <label for="notice">Show as "Due"</label>
                <div class="input-group" style="max-width: 260px">
                  <input type="number" class="form-control" id="notice" name="notice" min="0" max="120" [(ngModel)]="form.dueNoticeDays" required />
                  <div class="input-group-append"><span class="input-group-text">days before</span></div>
                </div>
                <small class="text-muted">
                  An installment shows as <strong>Due</strong> (red) this many days before its due date, and the sponsor gets a "due soon"
                  reminder then. After the due date it shows as <strong>Overdue</strong>.
                </small>
              </div>
              <div class="alert alert-light border small" *ngIf="form.amount">
                A student of {{ form.year || 'this year' }} on an 8-semester (4-year) course gets {{ 4 * 12 / form.frequency }} installments of
                ₹{{ form.amount | number: '1.2-2' }}, one every {{ form.frequency }} months from their payment start date.
              </div>
              <div *ngIf="isEdit" class="alert alert-warning small">
                Saving updates the unpaid installments of every student whose session year is {{ form.year }}. Paid installments stay as they are.
              </div>
              <div class="d-flex justify-content-between">
                <a routerLink="/admin/system-configuration" class="btn btn-light btn-lg">Back</a>
                <button type="submit" class="btn btn-primary btn-lg waves-effect waves-light" [disabled]="f.invalid || saving || form.year === null">
                  {{ isEdit ? 'Update Payment Config' : 'Save Payment Config' }}
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminPaymentConfigEditComponent implements OnInit {
  /** From the route parameter :year (absent on /admin/system-configuration/new). */
  @Input() year?: string;

  form = { year: null as number | null, amount: null as number | null, frequency: 3, dueNoticeDays: 30 };
  /** Years that have no config yet (for a new one): 5 years back to 5 years ahead. */
  freeYears: number[] = [];
  saving = false;
  error = '';

  constructor(private api: ApiService, private router: Router) {}

  get isEdit(): boolean { return !!this.year; }

  ngOnInit(): void {
    this.api.get<{ schedules: Schedule[] }>('/admin/system-configuration').subscribe({
      next: (r) => {
        const taken = new Set(r.schedules.map((s) => s.year));
        const now = new Date().getFullYear();
        this.freeYears = Array.from({ length: 11 }, (_, i) => now - 5 + i).filter((y) => !taken.has(y));
        if (this.isEdit) {
          const s = r.schedules.find((x) => String(x.year) === this.year);
          if (!s) { this.error = `There is no payment config for ${this.year}.`; return; }
          this.form = { year: s.year, amount: s.amount, frequency: s.frequency_months || 3, dueNoticeDays: s.due_notice_days ?? 30 };
        }
      },
      error: (e) => (this.error = errorText(e, 'Could not load the payment config.'))
    });
  }

  save(): void {
    if (this.form.year === null || this.form.amount === null) return;
    this.saving = true;
    this.error = '';
    this.api.post<{ message: string }>('/admin/system-configuration',
      { year: this.form.year, amount: this.form.amount, frequencyMonths: this.form.frequency, dueNoticeDays: this.form.dueNoticeDays }).subscribe({
      next: () => this.router.navigate(['/admin/system-configuration']),
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save the payment config.'); }
    });
  }
}
