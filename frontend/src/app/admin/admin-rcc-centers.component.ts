import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';

export interface RccCenter {
  rccCenterId?: number;
  centerName: string;
  inchargeName: string;
  contactNumber: string;
  location: string;
  createdAt?: string | null;
  updatedAt?: string | null;
}

/** Port of templates/admin/manage_rcc_centers.html */
@Component({
  selector: 'app-admin-rcc-centers',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Manage RCC Centers</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row mb-3">
      <div class="col-12">
        <a routerLink="/admin/rcc-centers/new" class="btn btn-primary btn-responsive"><i class="mdi mdi-plus mr-1"></i>Add New RCC Center</a>
      </div>
    </div>

    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title mb-3">RCC Center List</h4>
            <div class="row mb-3">
              <div class="col-12 col-md-4">
                <input type="text" class="form-control" placeholder="Search Center, Incharge, or Location..." [(ngModel)]="search" />
              </div>
            </div>
            <div class="table-responsive">
              <table class="table table-striped table-centered mb-0">
                <thead><tr><th>Center Name</th><th>Incharge Name</th><th>Contact Number</th><th>Location</th><th>Actions</th></tr></thead>
                <tbody>
                  <tr *ngFor="let c of visible">
                    <td>{{ c.centerName }}</td>
                    <td>{{ c.inchargeName }}</td>
                    <td>{{ c.contactNumber }}</td>
                    <td>{{ c.location }}</td>
                    <td>
                      <a [routerLink]="['/admin/rcc-centers', c.rccCenterId, 'edit']" class="btn btn-sm btn-primary waves-effect">Edit</a>
                      <button type="button" class="btn btn-sm btn-danger waves-effect ml-1" (click)="remove(c)">Delete</button>
                    </td>
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
export class AdminRccCentersComponent implements OnInit {
  centers: RccCenter[] = [];
  search = '';
  message = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<RccCenter[]>('/admin/rcc-centers').subscribe({
      next: (c) => (this.centers = c),
      error: (e) => (this.error = errorText(e, 'Could not load RCC centers.'))
    });
  }

  get visible(): RccCenter[] {
    const f = this.search.toLowerCase();
    return this.centers.filter((c) =>
      [c.centerName, c.inchargeName, c.location].some((v) => (v ?? '').toLowerCase().includes(f)));
  }

  remove(c: RccCenter): void {
    if (!confirm('Are you sure you want to delete this RCC center?')) return;
    this.api.delete(`/admin/rcc-centers/${c.rccCenterId}`).subscribe({
      next: () => { this.message = 'RCC Center deleted successfully!'; this.load(); },
      error: (e) => (this.error = errorText(e, 'Could not delete the RCC center.'))
    });
  }
}
