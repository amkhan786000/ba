import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';

interface Schedule {
  schedule_id: number;
  year: number;
  amount: number | null;
  updated_at: string | null;
  updated_by_name: string | null;
}

/** Port of templates/admin/system_configuration.html (Payment Config). */
@Component({
  selector: 'app-admin-system-config',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box mt-3"><h4 class="page-title">Yearly Payment Configuration</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row">
      <div class="col-lg-8 col-md-10 mx-auto">
        <div class="card form-section">
          <div class="card-body">
            <h5 class="card-title mb-4">Set or Update Payment Amount for a Year</h5>
            <form (ngSubmit)="save()">
              <div class="form-group row">
                <label for="selected_year_input" class="col-sm-3 col-form-label">Select Year</label>
                <div class="col-sm-9">
                  <select class="form-control" id="selected_year_input" name="selected_year" [(ngModel)]="year" (ngModelChange)="onYearChange()" required>
                    <option [ngValue]="null">-- Select Year --</option>
                    <option *ngFor="let y of years" [ngValue]="y">{{ y }}</option>
                  </select>
                </div>
              </div>
              <div class="form-group row">
                <label for="amount_input" class="col-sm-3 col-form-label">Fee Amount (₹)</label>
                <div class="col-sm-9">
                  <input type="number" class="form-control" id="amount_input" name="amount" step="0.01" min="0"
                         [placeholder]="placeholder" [(ngModel)]="amount" [disabled]="locked" required>
                </div>
              </div>
              <div class="form-group row mt-4">
                <div class="col-sm-9 offset-sm-3">
                  <button type="submit" class="btn btn-primary waves-effect waves-light" [disabled]="locked || saving || amount === null"
                          [title]="locked ? lockReason : ''">
                    <i class="mdi mdi-content-save mr-1"></i> Save Amount for Year
                  </button>
                </div>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>

    <div class="row mt-4">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title mb-3">Existing Payment Schedules</h4>
            <div class="table-responsive" *ngIf="schedules.length; else none">
              <table class="table table-bordered table-hover mb-0">
                <thead class="thead-light">
                  <tr><th>ID</th><th>Year</th><th>Amount (₹)</th><th>Last Updated At</th><th>Updated By</th></tr>
                </thead>
                <tbody>
                  <tr *ngFor="let s of schedules">
                    <td>{{ s.schedule_id }}</td>
                    <td>{{ s.year }}</td>
                    <td>{{ s.amount !== null ? (s.amount | number: '1.2-2') : 'N/A' }}</td>
                    <td>{{ s.updated_at ? (s.updated_at | date: 'yyyy-MM-dd HH:mm:ss') : 'N/A' }}</td>
                    <td>{{ s.updated_by_name || 'N/A' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <ng-template #none><p class="text-muted">No payment schedules have been configured yet.</p></ng-template>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminSystemConfigComponent implements OnInit {
  /** Same range as the Flask page: 5 years back to 5 years ahead. */
  years: number[] = Array.from({ length: 11 }, (_, i) => new Date().getFullYear() - 5 + i);
  schedules: Schedule[] = [];
  year: number | null = null;
  amount: number | null = null;
  saving = false;
  message = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<{ schedules: Schedule[] }>('/admin/system-configuration').subscribe({
      next: (r) => { this.schedules = r.schedules; this.onYearChange(); },
      error: (e) => (this.error = errorText(e, 'Could not load payment schedules.'))
    });
  }

  /** The Flask page locks the form once a year already has an amount above 0. */
  get existing(): Schedule | undefined {
    return this.schedules.find((s) => s.year === this.year && (s.amount ?? 0) > 0);
  }
  get locked(): boolean { return this.year === null || !!this.existing; }
  get lockReason(): string {
    return this.year === null ? 'Please select a year.' : 'Cannot save, amount already set for the selected year.';
  }
  get placeholder(): string {
    if (this.year === null) return 'Select a year first';
    return this.existing ? 'Amount already set for this year' : 'Enter amount for selected year';
  }

  onYearChange(): void {
    this.amount = this.existing?.amount ?? null;
  }

  save(): void {
    if (this.locked || this.amount === null) return;
    this.saving = true;
    this.api.post<{ message: string }>('/admin/system-configuration', { year: this.year, amount: this.amount }).subscribe({
      next: (r) => { this.saving = false; this.message = r.message; this.error = ''; this.load(); },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save the amount.'); }
    });
  }
}
