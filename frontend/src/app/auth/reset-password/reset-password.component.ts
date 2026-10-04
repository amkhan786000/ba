import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="d-flex justify-content-center align-items-center" style="min-height:100vh;">
      <div class="card shadow-sm p-4" style="width: 380px;">
        <h5 class="mb-3">Reset password</h5>
        <div *ngIf="message" class="alert alert-success">{{ message }}</div>
        <div *ngIf="error" class="alert alert-danger">{{ error }}</div>
        <form (ngSubmit)="submit()" *ngIf="!message">
          <input class="form-control mb-2" placeholder="Email" name="email" [(ngModel)]="form.email" required>
          <input class="form-control mb-2" type="password" placeholder="New password" name="newPassword" [(ngModel)]="form.newPassword" required>
          <input class="form-control mb-3" type="password" placeholder="Confirm password" name="confirmPassword" [(ngModel)]="form.confirmPassword" required>
          <button class="btn btn-primary w-100" type="submit">Reset password</button>
        </form>
        <a routerLink="/login" class="mt-3 d-block text-center">Back to sign in</a>
      </div>
    </div>
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
