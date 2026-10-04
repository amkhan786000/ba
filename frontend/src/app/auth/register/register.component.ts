import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="d-flex justify-content-center align-items-center" style="min-height:100vh;">
      <div class="card shadow-sm p-4" style="width: 400px;">
        <h5 class="mb-3">Create account</h5>
        <div *ngIf="message" class="alert alert-success">{{ message }}</div>
        <div *ngIf="error" class="alert alert-danger">{{ error }}</div>
        <form (ngSubmit)="submit()" *ngIf="!message">
          <input class="form-control mb-2" placeholder="Full name" name="name" [(ngModel)]="form.name" required>
          <input class="form-control mb-2" placeholder="Email" name="email" [(ngModel)]="form.email" required>
          <input class="form-control mb-2" placeholder="Contact number" name="contact" [(ngModel)]="form.contact" required>
          <select class="form-control mb-2" name="sex" [(ngModel)]="form.sex" required>
            <option value="M">Male</option><option value="F">Female</option>
          </select>
          <select class="form-control mb-2" name="role" [(ngModel)]="form.role" required>
            <option [value]="5">Sponsor</option>
            <option [value]="6">Student / Beneficiary</option>
          </select>
          <input class="form-control mb-3" type="password" placeholder="Password" name="password" [(ngModel)]="form.password" required>
          <button class="btn btn-primary w-100" type="submit">Register</button>
        </form>
        <a routerLink="/login" class="mt-3 d-block text-center">Back to sign in</a>
      </div>
    </div>
  `
})
export class RegisterComponent {
  form: any = {};
  message = '';
  error = '';
  constructor(private auth: AuthService, private router: Router) {}
  submit(): void {
    this.auth.register(this.form).subscribe({
      next: (res) => (this.message = res.message),
      error: (err) => (this.error = err?.error?.error ?? 'Registration failed.')
    });
  }
}
