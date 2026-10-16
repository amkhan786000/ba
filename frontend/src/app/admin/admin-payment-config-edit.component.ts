import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { COLLEGE_TYPES, Schedule, collegeTypeLabel } from './admin-system-config.component';

/**
 * Add / edit the payment config of a session year and college type (Govt, Private or Any college). Editing updates
 * the unpaid installments of that year's students.
 */
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
                <select *ngIf="!isEdit" class="form-control" id="year" name="year" [(ngModel)]="form.year" (ngModelChange)="pickFreeType($event)" required>
                  <option [ngValue]="null">-- Select Year --</option>
                  <option *ngFor="let y of years" [ngValue]="y">{{ y }}{{ takenLabel(y) }}</option>
                </select>
                <small class="text-muted">Applies to students whose session year is this year.</small>
              </div>
              <div class="form-group">
                <label for="ctype">College Type</label>
                <input *ngIf="isEdit" type="text" class="form-control" id="ctype" [value]="typeLabel(form.collegeType)" disabled />
                <select *ngIf="!isEdit" class="form-control" id="ctype" name="ctype" [(ngModel)]="form.collegeType">
                  <option *ngFor="let t of types" [ngValue]="t.value" [disabled]="isTaken(form.year, t.value)">{{ t.label }}{{ isTaken(form.year, t.value) ? ' (already added)' : '' }}</option>
                </select>
                <small class="text-muted">
                  Students whose BA Details say <strong>Govt</strong> or <strong>Private</strong> get that college type's config;
                  <strong>Any college</strong> covers everyone of the year without one of their own (and students set to
                  "Doesn't matter"). A year with only one config uses it for everyone.
                </small>
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
                A student of {{ form.year || 'this year' }} ({{ typeLabel(form.collegeType).toLowerCase() }}) on an 8-semester (4-year) course gets {{ 4 * 12 / form.frequency }} installments of
                ₹{{ form.amount | number: '1.2-2' }}, one every {{ form.frequency }} months from their payment start date.
              </div>
              <div *ngIf="isEdit" class="alert alert-warning small">
                Saving updates the unpaid installments of every student whose session year is {{ form.year }}. Paid installments stay as they are.
              </div>
              <div class="d-flex justify-content-between">
                <a routerLink="/admin/system-configuration" class="btn btn-light btn-lg">Back</a>
                <button type="submit" class="btn btn-primary btn-lg waves-effect waves-light" [disabled]="f.invalid || saving || form.year === null || (!isEdit && isTaken(form.year, form.collegeType))">
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
  /** From ?collegeType= when editing (GOVT, PRIVATE or ANY; older links without it mean ANY). */
  @Input() collegeType?: string;

  readonly types = COLLEGE_TYPES;
  readonly typeLabel = collegeTypeLabel;
  form = { year: null as number | null, collegeType: 'ANY', amount: null as number | null, frequency: 3, dueNoticeDays: 30 };
  /** Years to choose for a new config: 5 years back to 5 years ahead. */
  years: number[] = [];
  /** "year:TYPE" of the configs that exist already. */
  private taken = new Set<string>();
  saving = false;
  error = '';

  constructor(private api: ApiService, private router: Router) {}

  get isEdit(): boolean { return !!this.year; }

  ngOnInit(): void {
    this.api.get<{ schedules: Schedule[] }>('/admin/system-configuration').subscribe({
      next: (r) => {
        this.taken = new Set(r.schedules.map((s) => s.year + ':' + (s.college_type || 'ANY')));
        const now = new Date().getFullYear();
        this.years = Array.from({ length: 11 }, (_, i) => now - 5 + i).filter((y) => this.types.some((t) => !this.isTaken(y, t.value)));
        if (this.isEdit) {
          const type = this.collegeType || 'ANY';
          const s = r.schedules.find((x) => String(x.year) === this.year && (x.college_type || 'ANY') === type);
          if (!s) { this.error = `There is no payment config for ${this.year} (${collegeTypeLabel(type).toLowerCase()}).`; return; }
          this.form = { year: s.year, collegeType: type, amount: s.amount, frequency: s.frequency_months || 3, dueNoticeDays: s.due_notice_days ?? 30 };
        }
      },
      error: (e) => (this.error = errorText(e, 'Could not load the payment config.'))
    });
  }

  /** A new config: keep the chosen college type if that year doesn't have it yet, else take the first free one. */
  pickFreeType(year: number | null): void {
    if (this.isTaken(year, this.form.collegeType)) this.form.collegeType = this.types.find((t) => !this.isTaken(year, t.value))?.value ?? 'ANY';
  }

  isTaken(year: number | null, type: string): boolean { return year !== null && this.taken.has(year + ':' + type); }

  /** " (Govt, Any added)" after a year in the list. */
  takenLabel(year: number): string {
    const have = this.types.filter((t) => this.isTaken(year, t.value)).map((t) => t.label.replace(' college', ''));
    return have.length ? ` (${have.join(', ')} added)` : '';
  }

  save(): void {
    if (this.form.year === null || this.form.amount === null) return;
    this.saving = true;
    this.error = '';
    this.api.post<{ message: string }>('/admin/system-configuration',
      { year: this.form.year, collegeType: this.form.collegeType, amount: this.form.amount, frequencyMonths: this.form.frequency, dueNoticeDays: this.form.dueNoticeDays }).subscribe({
      next: () => this.router.navigate(['/admin/system-configuration']),
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save the payment config.'); }
    });
  }
}
