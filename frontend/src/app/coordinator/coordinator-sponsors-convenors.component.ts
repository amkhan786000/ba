import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { SponsorUser } from './coordinator-sponsors.component';
import { PagerComponent, PageState, PaginatePipe } from '../shared/pager/pager.component';
import { CardTableDirective } from '../shared/card-table.directive';

/** Port of templates/coordinator/view_sponsors_convenors.html (not in the Flask menu; reachable by URL). */
@Component({
  selector: 'app-coordinator-sponsors-convenors',
  standalone: true,
  imports: [CommonModule, AlertsComponent, PagerComponent, PaginatePipe, CardTableDirective],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">View Sponsors and Convenors</h4></div></div></div>
    <app-alerts [(error)]="error"></app-alerts>
    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Sponsors and Convenors List</h4>
            <div class="table-responsive">
              <table class="table table-centered mb-0">
                <thead><tr><th>User ID</th><th>Name</th><th>Email</th><th>Role</th><th>Chapter</th><th>Status</th></tr></thead>
                <tbody>
                  <tr *ngFor="let u of users | paginate: pg.page : pg.size">
                    <td>{{ u.user_id }}</td><td>{{ u.name }}</td><td>{{ u.email }}</td><td>{{ u.role_name }}</td><td>{{ u.chapter_name }}</td>
                    <td><span class="badge" [ngClass]="u.status === 'Active' ? 'badge-success' : 'badge-danger'">{{ u.status }}</span></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <app-pager [state]="pg" [total]="users.length"></app-pager>
          </div>
        </div>
      </div>
    </div>
  `
})
export class CoordinatorSponsorsConvenorsComponent implements OnInit {
  readonly pg = new PageState();
  users: SponsorUser[] = [];
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<SponsorUser[]>('/coordinator/sponsors-convenors').subscribe({
      next: (u) => (this.users = u),
      error: (e) => (this.error = errorText(e, 'Could not load sponsors and convenors.'))
    });
  }
}
