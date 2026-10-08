import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { PagerComponent, PageState, PaginatePipe } from '../shared/pager/pager.component';
import { CardTableDirective } from '../shared/card-table.directive';

export interface SponsorUser {
  id: number; user_id: string; name: string; email: string | null; phone: string | null;
  chapter_name: string | null; status: string | null; role_name: string;
}

/** Port of templates/coordinator/manage_sponsors.html */
@Component({
  selector: 'app-coordinator-sponsors',
  standalone: true,
  imports: [CommonModule, RouterLink, AlertsComponent, PagerComponent, PaginatePipe, CardTableDirective],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Manage Sponsors</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>
    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Sponsors in rahbar</h4>
            <div class="mt-3 table-responsive">
              <table class="table table-striped">
                <thead><tr><th>Name</th><th>Email</th><th>Phone</th><th>Status</th><th>Actions</th></tr></thead>
                <tbody>
                  <tr *ngFor="let s of sponsors | paginate: pg.page : pg.size">
                    <td>{{ s.name }}</td>
                    <td>{{ s.email }}</td>
                    <td>{{ s.phone }}</td>
                    <td>
                      <span *ngIf="s.status === 'Active'" class="badge badge-success">Active</span>
                      <span *ngIf="s.status !== 'Active'" class="badge badge-danger">Inactive</span>
                    </td>
                    <td>
                      <button *ngIf="s.status === 'Active'" class="btn btn-sm btn-danger" (click)="setStatus(s, 'Inactive')">Deactivate</button>
                      <button *ngIf="s.status !== 'Active'" class="btn btn-sm btn-success" (click)="setStatus(s, 'Active')">Activate</button>
                      <a [routerLink]="['/coordinator/sponsors', s.id, 'map']" class="btn btn-sm btn-primary ml-1">Map Students</a>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <app-pager [state]="pg" [total]="sponsors.length"></app-pager>
          </div>
        </div>
      </div>
    </div>
  `
})
export class CoordinatorSponsorsComponent implements OnInit {
  readonly pg = new PageState();
  sponsors: SponsorUser[] = [];
  message = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<{ sponsorsConvenors: SponsorUser[] }>('/coordinator/manage-sponsors').subscribe({
      next: (r) => (this.sponsors = r.sponsorsConvenors),
      error: (e) => (this.error = errorText(e, 'Could not load sponsors.'))
    });
  }

  setStatus(s: SponsorUser, status: 'Active' | 'Inactive'): void {
    this.api.post<{ message: string }>(`/coordinator/users/${s.id}/status/${status}`, {}).subscribe({
      next: (r) => { this.message = r.message; this.load(); },
      error: (e) => (this.error = errorText(e, 'Could not update the status.'))
    });
  }
}
