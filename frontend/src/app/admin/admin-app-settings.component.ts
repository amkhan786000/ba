import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { setDateFormat } from '../shared/app-date';

/** Administration > Application Settings: how the app shows things to everyone (for now, the date format). */
@Component({
  selector: 'app-admin-app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Application Settings</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="card" *ngIf="formats"><div class="card-body">
      <h5 class="mt-0">Date format</h5>
      <p class="text-muted small">How dates are shown on every screen of Rahbar (admin area and the sponsor, student, coordinator and convenor
        portals), in lists, reports and exports on screen. Dates written with the month's name, such as "Oct 2026", stay as they are.</p>
      <div class="custom-control custom-radio mb-2" *ngFor="let f of formatKeys">
        <input type="radio" class="custom-control-input" [id]="'df-' + f" name="dateFormat" [value]="f" [(ngModel)]="dateFormat" [disabled]="!canEdit">
        <label class="custom-control-label" [for]="'df-' + f"><strong>{{ f }}</strong> <span class="text-muted ml-2">e.g. {{ formats[f] }}</span></label>
      </div>
      <p class="small text-muted mt-3 mb-3">The boxes where you pick a date (calendar fields) are drawn by the browser and follow your computer's
        region settings; on Windows or Mac set the region to India (or United Kingdom) to see them as DD/MM/YYYY too.</p>
      <button *ngIf="canEdit" class="btn btn-primary" (click)="save()" [disabled]="busy">Save</button>
    </div></div>
  `
})
export class AdminAppSettingsComponent implements OnInit {
  formats: Record<string, string> | null = null;
  dateFormat = 'DD/MM/YYYY';
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('APP_SETTINGS', 'EDIT'); }
  get formatKeys(): string[] { return Object.keys(this.formats ?? {}); }

  ngOnInit(): void {
    this.api.get<{ date_format: string; date_formats: Record<string, string> }>('/admin/app-settings').subscribe({
      next: (r) => { this.formats = r.date_formats; this.dateFormat = r.date_format; },
      error: (e) => (this.error = errorText(e, 'Could not load the settings.'))
    });
  }

  save(): void {
    this.busy = true;
    this.api.put<{ message: string }>('/admin/app-settings', { dateFormat: this.dateFormat }).subscribe({
      next: (r) => {
        this.busy = false;
        this.message = r.message;
        setDateFormat(this.dateFormat); // this browser at once
        this.auth.refreshAccess().subscribe({ error: () => {} });
      },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save the settings.'); }
    });
  }
}
