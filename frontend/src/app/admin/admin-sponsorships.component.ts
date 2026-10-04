import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { PagerComponent, pageOf } from '../shared/pager/pager.component';

interface SponsorRow {
  user_id: string;
  name: string;
  email: string | null;
  phone: string | null;
  region: string | null;
  role_name: string;
  all_references: string | null;
}

interface SponsorReference {
  reference_id: string;
  sponsor_year: string | null;
  referral: string | null;
  installment_date: string | null;
  payment_months: number | string | null;
  confirm_credit_date: string | null;
}

/** Port of templates/admin/manage_sponsorships.html (DataTable, ref lookup, bulk upload, edit-profile modal). */
@Component({
  selector: 'app-admin-sponsorships',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, PagerComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Manage Sponsorships</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <div class="d-flex flex-column flex-md-row justify-content-between align-items-center mb-4">
              <h4 class="header-title mb-2 mb-md-0">Sponsors &amp; Direct ID Lookup</h4>
              <div class="d-flex flex-column flex-sm-row align-items-center">
                <div class="input-group mr-sm-3 lookup-container" style="width: 320px;">
                  <input type="text" class="form-control" placeholder="Lookup Ref ID (e.g. 2024-001)" [(ngModel)]="lookup" (keyup.enter)="applyLookup()">
                  <div class="input-group-append"><button class="btn btn-dark" type="button" (click)="applyLookup()">Go</button></div>
                </div>
                <button type="button" class="btn btn-success waves-effect waves-light" (click)="showUpload = true">
                  <i class="mdi mdi-file-upload"></i> Bulk Upload
                </button>
              </div>
            </div>

            <div class="row dt-toolbar mb-2">
              <div class="col-sm-6">
                <label>Show
                  <select class="form-control form-control-sm" [(ngModel)]="pageSize" (ngModelChange)="page = 1">
                    <option [ngValue]="10">10</option><option [ngValue]="25">25</option><option [ngValue]="50">50</option><option [ngValue]="100000">All</option>
                  </select> entries</label>
              </div>
              <div class="col-sm-6 text-sm-right">
                <label>Search:<input type="search" class="form-control form-control-sm" [(ngModel)]="search" (ngModelChange)="page = 1"></label>
              </div>
            </div>

            <div class="table-responsive">
              <table class="table table-bordered table-striped nowrap" style="width:100%">
                <thead><tr><th>Sponsor Name</th><th>Commitment References</th><th>Contact Info</th><th>Chapter</th><th>Actions</th></tr></thead>
                <tbody>
                  <tr *ngIf="!filtered.length"><td colspan="5" class="text-center text-muted">No matching records found</td></tr>
                  <tr *ngFor="let s of rows">
                    <td><strong>{{ s.name }}</strong><br><small class="text-muted">ID: {{ s.user_id }}</small></td>
                    <td>
                      <ng-container *ngIf="s.all_references; else noRefs">
                        <span class="ref-tag" *ngFor="let ref of s.all_references.split(', ')">{{ ref }}</span>
                      </ng-container>
                      <ng-template #noRefs><span class="text-muted small">None</span></ng-template>
                    </td>
                    <td>
                      <span><i class="mdi mdi-email-outline"></i> {{ s.email || '-' }}</span><br>
                      <span><i class="mdi mdi-phone"></i> {{ s.phone || '-' }}</span>
                    </td>
                    <td>{{ s.region }}</td>
                    <td>
                      <div class="btn-group">
                        <button class="btn btn-sm btn-info" (click)="openEdit(s.user_id)"><i class="mdi mdi-pencil"></i> Edit Profile</button>
                        <a [routerLink]="['/admin/sponsorships', s.user_id, 'map']" class="btn btn-sm btn-primary waves-effect waves-light">
                          <i class="mdi mdi-account-arrow-right"></i> Map Students
                        </a>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <app-pager [total]="filtered.length" [page]="page" [pageSize]="pageSize" (pageChange)="page = $event"></app-pager>
          </div>
        </div>
      </div>
    </div>

    <!-- Bulk upload modal -->
    <div *ngIf="showUpload" class="modal fade show d-block" tabindex="-1" (click)="showUpload = false">
      <div class="modal-dialog modal-dialog-centered" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header bg-success text-white">
            <h5 class="modal-title text-white"><i class="mdi mdi-file-upload"></i> Bulk Import Sponsor Data</h5>
            <button type="button" class="close text-white" (click)="showUpload = false">&times;</button>
          </div>
          <form (ngSubmit)="upload()">
            <div class="modal-body">
              <div class="alert alert-info">
                <strong>Format Required:</strong><br>
                <small>Sponsor Year, Sponsor Reference, Sponsor Chapter, Sponsor Name, Sponsor Mobile1, Sponsor Email, Student Assigned, etc.</small>
              </div>
              <div class="form-group">
                <label>Select CSV File</label>
                <input type="file" class="form-control-file" accept=".csv" (change)="onFile($event)" required>
              </div>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-secondary" (click)="showUpload = false">Cancel</button>
              <button type="submit" class="btn btn-success" [disabled]="!file">Upload &amp; Process</button>
            </div>
          </form>
        </div>
      </div>
    </div>

    <!-- Edit sponsor modal -->
    <div *ngIf="edit" class="modal fade show d-block" tabindex="-1" (click)="edit = null">
      <div class="modal-dialog modal-xl" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header bg-info text-white">
            <h4 class="modal-title text-white">Edit Sponsor Details: {{ editName }}</h4>
            <button type="button" class="close text-white" (click)="edit = null">&times;</button>
          </div>
          <div class="modal-body">
            <h5 class="text-info border-bottom pb-2"><i class="mdi mdi-account-circle"></i> Master Account Details</h5>
            <div class="row">
              <div class="col-md-6 form-group"><label>Full Name</label><input type="text" class="form-control" [(ngModel)]="edit.name"></div>
              <div class="col-md-6 form-group"><label>Email Address</label><input type="email" class="form-control" [(ngModel)]="edit.email"></div>
              <div class="col-md-6 form-group"><label>Phone (Mobile 1)</label><input type="text" class="form-control" [(ngModel)]="edit.phone"></div>
              <div class="col-md-6 form-group"><label>Chapter</label><input type="text" class="form-control" [(ngModel)]="edit.region"></div>
            </div>
            <h5 class="text-primary border-bottom pb-2 mt-4"><i class="mdi mdi-clipboard-list-outline"></i> Commitment References</h5>
            <div class="table-responsive">
              <table class="table table-sm table-bordered">
                <thead class="bg-light">
                  <tr><th>Reference ID</th><th>Sponsor Year</th><th>Referral</th><th>Date transf 1st installment</th><th>Payment number of months</th><th>Confirm Credit Date</th></tr>
                </thead>
                <tbody>
                  <tr *ngIf="!references.length"><td colspan="6" class="text-center">No reference history found.</td></tr>
                  <tr *ngFor="let r of references">
                    <td><strong>{{ r.reference_id }}</strong></td>
                    <td>{{ r.sponsor_year || '-' }}</td>
                    <td>{{ r.referral || '-' }}</td>
                    <td>{{ r.installment_date || 'N/A' }}</td>
                    <td>{{ r.payment_months || '0' }}</td>
                    <td>{{ r.confirm_credit_date || 'N/A' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" (click)="edit = null">Cancel</button>
            <button type="button" class="btn btn-info" (click)="saveEdit()" [disabled]="saving">
              <i class="mdi" [ngClass]="saving ? 'mdi-loading mdi-spin' : 'mdi-content-save'"></i> {{ saving ? 'Saving...' : 'Update Profile' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <div *ngIf="uploading" class="loading-overlay">
      <div class="spinner-border text-success" role="status" style="width: 4rem; height: 4rem;"></div>
      <h3 class="mt-3 text-dark">Processing Sponsor Data...</h3>
      <p class="text-muted">Please wait while the system updates the records.</p>
    </div>
  `
})
export class AdminSponsorshipsComponent implements OnInit {
  sponsors: SponsorRow[] = [];
  search = '';
  lookup = '';
  page = 1;
  pageSize = 10;
  message = '';
  error = '';

  showUpload = false;
  file: File | null = null;
  uploading = false;

  edit: { userId: string; name: string; email: string; phone: string; region: string } | null = null;
  editName = '';
  references: SponsorReference[] = [];
  saving = false;

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<SponsorRow[]>('/admin/sponsorships').subscribe({
      next: (s) => (this.sponsors = s),
      error: (e) => (this.error = errorText(e, 'Could not load sponsors.'))
    });
  }

  /** Same as the DataTable's global search: any cell containing the text. */
  get filtered(): SponsorRow[] {
    const f = this.search.trim().toLowerCase();
    if (!f) return this.sponsors;
    return this.sponsors.filter((s) =>
      [s.name, s.user_id, s.all_references, s.email, s.phone, s.region].some((v) => (v ?? '').toLowerCase().includes(f)));
  }
  get rows(): SponsorRow[] { return pageOf(this.filtered, this.page, this.pageSize); }

  /** The "Go" box just searches the table, as in Flask. */
  applyLookup(): void {
    this.search = this.lookup.trim();
    this.page = 1;
  }

  onFile(event: Event): void {
    this.file = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  upload(): void {
    if (!this.file) return;
    const form = new FormData();
    form.append('file', this.file);
    this.showUpload = false;
    this.uploading = true;
    this.api.post<{ message: string }>('/admin/sponsors/bulk-upload', form).subscribe({
      next: (r) => { this.uploading = false; this.file = null; this.message = r.message; this.load(); },
      error: (e) => { this.uploading = false; this.error = errorText(e, 'Upload failed.'); }
    });
  }

  openEdit(userId: string): void {
    this.api.get<{ profile: Record<string, string | null>; references: SponsorReference[] }>(`/admin/sponsors/${encodeURIComponent(userId)}`).subscribe({
      next: (res) => {
        const p = res.profile;
        this.editName = p['name'] ?? '';
        this.edit = { userId, name: p['name'] ?? '', email: p['email'] ?? '', phone: p['phone'] ?? '', region: p['region'] ?? '' };
        this.references = res.references ?? [];
      },
      error: (e) => (this.error = errorText(e, 'Could not load sponsor details.'))
    });
  }

  saveEdit(): void {
    if (!this.edit) return;
    this.saving = true;
    const { userId, ...body } = this.edit;
    this.api.put<{ message: string }>(`/admin/sponsors/${encodeURIComponent(userId)}`, body).subscribe({
      next: () => { this.saving = false; this.edit = null; this.message = 'Profile Updated Successfully!'; this.load(); },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Update failed'); }
    });
  }
}
