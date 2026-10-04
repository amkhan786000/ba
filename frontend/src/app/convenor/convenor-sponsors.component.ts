import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { REGION_NOT_SET } from './convenor-dashboard.component';

interface Sponsor { user_id: string; name: string; email: string | null; phone: string | null; status: string | null; region: string | null }

/**
 * Port of templates/convenor/manage_sponsors.html. In the Flask template the Activate/Deactivate and
 * Map Students buttons are commented out, so this list is read-only here as well.
 */
@Component({
  selector: 'app-convenor-sponsors',
  standalone: true,
  imports: [CommonModule, RouterLink, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Manage Sponsors</h4></div></div></div>
    <app-alerts [(error)]="error"></app-alerts>
    <div *ngIf="needsRegion" class="alert alert-warning">Your region is not set. <a routerLink="/convenor/dashboard">Set it on the dashboard.</a></div>
    <div class="row" *ngIf="!needsRegion">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Sponsors in {{ region }}</h4>
            <div class="mt-3 table-responsive">
              <table class="table table-striped">
                <thead><tr><th>Name</th><th>Email</th><th>Phone</th><th>Status</th></tr></thead>
                <tbody>
                  <tr *ngIf="!sponsors.length"><td colspan="4" class="text-center text-muted">No sponsors in your region.</td></tr>
                  <tr *ngFor="let s of sponsors">
                    <td>{{ s.name }}</td><td>{{ s.email }}</td><td>{{ s.phone }}</td>
                    <td><span class="badge" [ngClass]="s.status === 'Active' ? 'badge-success' : 'badge-danger'">{{ s.status === 'Active' ? 'Active' : 'Inactive' }}</span></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class ConvenorSponsorsComponent implements OnInit {
  sponsors: Sponsor[] = [];
  region = '';
  needsRegion = false;
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<{ sponsors: Sponsor[] }>('/convenor/manage-sponsors').subscribe({
      next: (r) => { this.sponsors = r.sponsors; this.region = r.sponsors[0]?.region ?? ''; },
      error: (e) => {
        const text = errorText(e, 'Could not load sponsors.');
        if (text.startsWith(REGION_NOT_SET)) this.needsRegion = true; else this.error = text;
      }
    });
    this.api.get<{ convenor: { region: string | null } }>('/convenor/dashboard').subscribe({
      next: (d) => (this.region = d.convenor.region ?? this.region),
      error: () => { /* handled above */ }
    });
  }
}
