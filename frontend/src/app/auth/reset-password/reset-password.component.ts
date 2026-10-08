import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { AuthLayoutComponent } from '../../shared/auth-layout/auth-layout.component';

/** "Forgot password": 1) email a 6-digit code, 2) enter the code and a new password. */
@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AuthLayoutComponent],
  template: `
    <app-auth-layout heading="Reset your password"
                     [subheading]="step === 'email' ? 'Enter your account email. We will send you a code.' : 'Enter the code from the email and choose a new password.'">
      <div *ngIf="done" class="alert alert-success">
        <i class="mdi mdi-check-circle-outline mr-1"></i>{{ done }}
      </div>
      <div *ngIf="info && !done" class="alert alert-info">{{ info }}</div>
      <div *ngIf="error" class="alert alert-danger">{{ error }}</div>

      <form *ngIf="step === 'email' && !done" (ngSubmit)="sendCode()">
        <div class="form-group">
          <label for="email">Email address</label>
          <div class="input-icon">
            <i class="mdi mdi-email-outline"></i>
            <input class="form-control" id="email" type="email" placeholder="you@example.com" name="email" [(ngModel)]="email" autocomplete="email" required>
          </div>
        </div>
        <button class="btn btn-primary btn-block mt-4" type="submit" [disabled]="busy || !email.trim()">
          <span *ngIf="busy" class="spinner-border spinner-border-sm mr-1"></span>Send code
        </button>
      </form>

      <form *ngIf="step === 'code' && !done" (ngSubmit)="reset()">
        <div class="form-group">
          <label for="code">Code from the email</label>
          <div class="input-icon">
            <i class="mdi mdi-shield-key-outline"></i>
            <input class="form-control" id="code" name="code" inputmode="numeric" maxlength="6" placeholder="6-digit code" [(ngModel)]="code" autocomplete="one-time-code" required>
          </div>
        </div>
        <div class="form-group">
          <label for="newPassword">New password</label>
          <div class="input-icon">
            <i class="mdi mdi-lock-outline"></i>
            <input class="form-control" id="newPassword" type="password" placeholder="At least 8 characters, letters and numbers" name="newPassword" [(ngModel)]="newPassword" autocomplete="new-password" required>
          </div>
        </div>
        <div class="form-group">
          <label for="confirmPassword">Confirm password</label>
          <div class="input-icon">
            <i class="mdi mdi-lock-check-outline"></i>
            <input class="form-control" id="confirmPassword" type="password" placeholder="Repeat the new password" name="confirmPassword" [(ngModel)]="confirmPassword" autocomplete="new-password" required>
          </div>
        </div>
        <button class="btn btn-primary btn-block mt-4" type="submit" [disabled]="busy">
          <span *ngIf="busy" class="spinner-border spinner-border-sm mr-1"></span>Reset password
        </button>
        <div class="text-center mt-3 small">
          Didn't get it? Check your spam folder, or <a href="" (click)="$event.preventDefault(); again()">send a new code</a>.
        </div>
      </form>

      <div class="auth-links center"><a routerLink="/login"><i class="mdi mdi-arrow-left mr-1"></i>Back to sign in</a></div>
    </app-auth-layout>
  `
})
export class ResetPasswordComponent {
  step: 'email' | 'code' = 'email';
  email = '';
  code = '';
  newPassword = '';
  confirmPassword = '';
  busy = false;
  info = '';
  done = '';
  error = '';

  constructor(private auth: AuthService) {}

  sendCode(): void {
    this.busy = true;
    this.error = '';
    this.auth.forgotPassword(this.email.trim()).subscribe({
      next: (res) => { this.busy = false; this.info = res.message; this.step = 'code'; },
      error: (err) => { this.busy = false; this.error = err?.error?.error ?? 'Could not send the code.'; }
    });
  }

  again(): void { this.step = 'email'; this.code = ''; this.info = ''; this.error = ''; }

  reset(): void {
    this.busy = true;
    this.error = '';
    this.auth.resetPassword({ email: this.email.trim(), code: this.code.trim(), newPassword: this.newPassword, confirmPassword: this.confirmPassword })
      .subscribe({
        next: (res) => { this.busy = false; this.done = res.message; },
        error: (err) => { this.busy = false; this.error = err?.error?.error ?? 'Could not reset the password.'; }
      });
  }
}
