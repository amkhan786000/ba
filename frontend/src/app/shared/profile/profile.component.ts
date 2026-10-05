import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { AlertsComponent, errorText } from '../alerts/alerts.component';
import { PasswordFormComponent } from '../password-form.component';
import { localDate } from '../format';

interface Profile {
  userId: string; name: string; email: string; phone: string; sex: string | null; region: string | null;
  year: number | null; status: string | null; roleId: number; roleName: string | null; memberSince: string | null;
}

/** "My profile" for every role: contact details and password. */
@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, PasswordFormComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">My Profile</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row" *ngIf="p">
      <div class="col-12 col-xl-4">
        <div class="card profile-card">
          <div class="card-body text-center">
            <div class="profile-avatar">{{ initials }}</div>
            <h4 class="mb-1 mt-3">{{ p.name }}</h4>
            <p class="text-muted mb-3">{{ p.roleName || 'User' }}</p>
            <div class="profile-facts">
              <div><span>User ID</span><strong>{{ p.userId }}</strong></div>
              <div><span>Status</span><strong>{{ p.status || '--' }}</strong></div>
              <div *ngIf="p.year"><span>Academic year</span><strong>{{ p.year }}</strong></div>
              <div><span>Member since</span><strong>{{ date(p.memberSince) }}</strong></div>
            </div>
          </div>
        </div>
      </div>

      <div class="col-12 col-xl-8">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Contact details</h4>
            <form #f="ngForm" (ngSubmit)="save()">
              <div class="row">
                <div class="col-md-6 form-group">
                  <label for="name">Full name</label>
                  <input class="form-control" id="name" name="name" [(ngModel)]="form.name" required>
                </div>
                <div class="col-md-6 form-group">
                  <label for="sex">Gender</label>
                  <select class="form-control" id="sex" name="sex" [(ngModel)]="form.sex">
                    <option value="M">Male</option><option value="F">Female</option>
                  </select>
                </div>
                <div class="col-md-6 form-group">
                  <label for="email">Email</label>
                  <input class="form-control" id="email" type="email" name="email" [(ngModel)]="form.email" required>
                </div>
                <div class="col-md-6 form-group">
                  <label for="phone">Phone</label>
                  <input class="form-control" id="phone" type="tel" name="phone" [(ngModel)]="form.phone" required>
                </div>
                <div class="col-md-6 form-group">
                  <label for="region">Region / city</label>
                  <input class="form-control" id="region" name="region" [(ngModel)]="form.region">
                </div>
              </div>
              <button class="btn btn-primary" type="submit" [disabled]="f.invalid || saving">
                <span *ngIf="saving" class="spinner-border spinner-border-sm mr-2"></span>Save changes
              </button>
            </form>
          </div>
        </div>

        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Change password</h4>
            <app-password-form (changed)="message = $event"></app-password-form>
          </div>
        </div>
      </div>
    </div>
  `
})
export class ProfileComponent implements OnInit {
  p: Profile | null = null;
  form = { name: '', email: '', phone: '', sex: 'M', region: '' };
  saving = false;
  message = '';
  error = '';
  readonly date = localDate;

  constructor(private api: ApiService, private auth: AuthService) {}

  get initials(): string {
    const parts = (this.p?.name ?? '').trim().split(/\s+/).filter(Boolean);
    return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase() || '?';
  }

  ngOnInit(): void {
    this.api.get<Profile>('/account/profile').subscribe({
      next: (p) => this.fill(p),
      error: (e) => (this.error = errorText(e, 'Could not load your profile.'))
    });
  }

  private fill(p: Profile): void {
    this.p = p;
    this.form = { name: p.name, email: p.email, phone: p.phone, sex: p.sex || 'M', region: p.region || '' };
  }

  save(): void {
    this.saving = true;
    this.error = '';
    this.api.put<Profile>('/account/profile', this.form).subscribe({
      next: (p) => {
        this.saving = false;
        this.fill(p);
        this.auth.updateUser({ name: p.name });
        this.message = 'Your profile has been updated.';
      },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save your profile.'); }
    });
  }
}
