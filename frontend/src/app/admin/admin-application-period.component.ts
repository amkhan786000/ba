import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';

interface Period { id: number; startDate: string | null; endDate: string | null; isActive: boolean }

/** Port of templates/admin/manage_application_period.html */
@Component({
  selector: 'app-admin-application-period',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent],
  template: `
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>
    <div class="row"><div class="col-12"><div class="page-title-box mt-3"><h4 class="page-title">Application Period Management</h4></div></div></div>

    <div class="row">
      <div class="col-12 col-md-8 col-lg-6">
        <div class="card">
          <div class="card-body">
            <h5 class="card-title">Current Status</h5>
            <ng-container *ngIf="period?.isActive; else inactive">
              <p><span class="badge badge-success font-14">ACTIVE</span></p>
              <p class="mb-1"><strong>Start Date:</strong> {{ period?.startDate ? (period?.startDate | date: 'MMMM dd, y') : 'N/A' }}</p>
              <p class="mb-0"><strong>End Date:</strong> {{ period?.endDate ? (period?.endDate | date: 'MMMM dd, y') : 'N/A' }}</p>
            </ng-container>
            <ng-template #inactive>
              <p><span class="badge badge-secondary font-14">INACTIVE</span></p>
              <p class="mb-0">No application period is currently active or has been set up yet.</p>
            </ng-template>
          </div>
        </div>
      </div>
    </div>

    <div class="row">
      <div class="col-12 col-md-8 col-lg-6">
        <div class="card">
          <div class="card-body">
            <h5 class="card-title">Manage Application Period</h5>
            <form (ngSubmit)="submit()">
              <div class="form-group">
                <label for="start_date">Set New Start Date</label>
                <input type="date" class="form-control" id="start_date" name="start_date" [(ngModel)]="startDate" [disabled]="!!period?.isActive" />
                <small class="form-text text-muted">Required to start a new period.</small>
              </div>
              <div class="form-group">
                <label for="end_date">Set New End Date</label>
                <input type="date" class="form-control" id="end_date" name="end_date" [(ngModel)]="endDate" [disabled]="!!period?.isActive" />
                <small class="form-text text-muted">Required to start a new period.</small>
              </div>
              <button *ngIf="period?.isActive; else startBtn" type="submit" class="btn btn-danger btn-block waves-effect waves-light" [disabled]="busy">
                <i class="mdi mdi-stop-circle-outline mr-1"></i> End Current Active Period
              </button>
              <ng-template #startBtn>
                <button type="submit" class="btn btn-success btn-block waves-effect waves-light" [disabled]="busy">
                  <i class="mdi mdi-play-circle-outline mr-1"></i> Start New Application Period
                </button>
              </ng-template>
            </form>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminApplicationPeriodComponent implements OnInit {
  period: Period | null = null;
  startDate = '';
  endDate = '';
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<Period | null>('/admin/application-period').subscribe({
      next: (p) => (this.period = p),
      error: (e) => (this.error = errorText(e, 'Error fetching application period.'))
    });
  }

  submit(): void {
    this.error = '';
    if (this.period?.isActive) {
      this.run(this.api.post<{ message: string }>('/admin/application-period/end', {}));
      return;
    }
    if (!this.startDate || !this.endDate) {
      this.error = 'Start date and end date are required to start a new period.';
      return;
    }
    if (this.endDate < this.startDate) {
      this.error = 'End date cannot be before the start date.';
      return;
    }
    this.run(this.api.post<{ message: string }>('/admin/application-period/start', { startDate: this.startDate, endDate: this.endDate }));
  }

  private run(req: ReturnType<ApiService['post']>): void {
    this.busy = true;
    req.subscribe({
      next: (r) => { this.busy = false; this.message = (r as { message: string }).message; this.startDate = ''; this.endDate = ''; this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'An error occurred.'); }
    });
  }
}
