import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { AuthLayoutComponent } from '../../shared/auth-layout/auth-layout.component';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AuthLayoutComponent],
  template: `
    <app-auth-layout heading="Reset your password" subheading="Enter your account email and choose a new password.">
      <div *ngIf="message" class="alert alert-success">
        <i class="mdi mdi-check-circle-outline mr-1"></i>{{ message }}
      </div>
      <div *ngIf="error" class="alert alert-danger">{{ error }}</div>
      <form (ngSubmit)="submit()" *ngIf="!message">
        <div class="form-group">
          <label for="email">Email address</label>
          <div class="input-icon">
            <i class="mdi mdi-email-outline"></i>
            <input class="form-control" id="email" type="email" placeholder="you@example.com" name="email" [(ngModel)]="form.email" autocomplete="email" required>
          </div>
        </div>
        <div class="form-group">
          <label for="newPassword">New password</label>
          <div class="input-icon">
            <i class="mdi mdi-lock-outline"></i>
            <input class="form-control" id="newPassword" type="password" placeholder="New password" name="newPassword" [(ngModel)]="form.newPassword" autocomplete="new-password" required>
          </div>
        </div>
        <div class="form-group">
          <label for="confirmPassword">Confirm password</label>
          <div class="input-icon">
            <i class="mdi mdi-lock-check-outline"></i>
            <input class="form-control" id="confirmPassword" type="password" placeholder="Repeat the new password" name="confirmPassword" [(ngModel)]="form.confirmPassword" autocomplete="new-password" required>
          </div>
        </div>
        <button class="btn btn-primary btn-block mt-4" type="submit">Reset password</button>
      </form>
      <div class="auth-links center"><a routerLink="/login"><i class="mdi mdi-arrow-left mr-1"></i>Back to sign in</a></div>
    </app-auth-layout>
  `
})
export class ResetPasswordComponent {
  form: any = {};
  message = '';
  error = '';
  constructor(private auth: AuthService) {}
  submit(): void {
    this.auth.resetPassword(this.form).subscribe({
      next: (res) => (this.message = res.message),
      error: (err) => (this.error = err?.error?.error ?? 'Could not reset password.')
    });
  }
}
