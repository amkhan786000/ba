import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';

/** Port of templates/admin/add_institution.html */
@Component({
  selector: 'app-admin-institution-add',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Add Institution</h4></div></div></div>
    <div class="row">
      <div class="col-12 col-md-8 offset-md-2">
        <app-alerts [(message)]="message" [(error)]="error"></app-alerts>
        <div class="card">
          <div class="card-body">
            <form #f="ngForm" (ngSubmit)="save()">
              <div class="form-group">
                <label for="institution_id">Institution ID</label>
                <input type="text" class="form-control" id="institution_id" name="institutionId" [(ngModel)]="form.institutionId" placeholder="Auto-generated if empty">
              </div>
              <div class="form-group">
                <label for="institution_name">Institution Name</label>
                <input type="text" class="form-control" id="institution_name" name="institutionName" [(ngModel)]="form.institutionName" required>
              </div>
              <div class="form-group">
                <label for="address">Address</label>
                <input type="text" class="form-control" id="address" name="address" [(ngModel)]="form.address" required>
              </div>
              <div class="form-group">
                <label for="contact_number">Contact Number</label>
                <input type="text" class="form-control" id="contact_number" name="contactNumber" [(ngModel)]="form.contactNumber" required>
              </div>
              <div class="form-group">
                <label for="email">Email</label>
                <input type="email" class="form-control" id="email" name="email" [(ngModel)]="form.email" required>
              </div>
              <div class="d-flex justify-content-between">
                <a routerLink="/admin/courses" class="btn btn-light">Back</a>
                <button type="submit" class="btn btn-primary waves-effect waves-light" [disabled]="f.invalid || saving">Save Institution</button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminInstitutionAddComponent {
  form = { institutionId: '', institutionName: '', address: '', contactNumber: '', email: '' };
  saving = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private router: Router) {}

  save(): void {
    this.saving = true;
    this.api.post<{ message: string }>('/admin/institutions', this.form).subscribe({
      next: () => this.router.navigate(['/admin/courses']),
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save the institution.'); }
    });
  }
}
