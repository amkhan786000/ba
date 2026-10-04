import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { AuthLayoutComponent } from '../../shared/auth-layout/auth-layout.component';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AuthLayoutComponent],
  template: `
    <app-auth-layout heading="Create your account" subheading="Join Rahbar as a sponsor or a student. An administrator activates new accounts." [wide]="true">
      <div *ngIf="message" class="alert alert-success">
        <i class="mdi mdi-check-circle-outline mr-1"></i>{{ message }}
      </div>
      <div *ngIf="error" class="alert alert-danger">{{ error }}</div>
      <form (ngSubmit)="submit()" *ngIf="!message">
        <label class="d-block">I am joining as</label>
        <div class="segmented">
          <button type="button" [class.active]="form.role == 5" (click)="form.role = 5"><i class="mdi mdi-hand-heart"></i>Sponsor</button>
          <button type="button" [class.active]="form.role == 6" (click)="form.role = 6"><i class="mdi mdi-school"></i>Student</button>
        </div>
        <div class="form-group">
          <label for="name">Full name</label>
          <input class="form-control" id="name" placeholder="Your full name" name="name" [(ngModel)]="form.name" autocomplete="name" required>
        </div>
        <div class="row">
          <div class="col-sm-6 form-group">
            <label for="email">Email</label>
            <input class="form-control" id="email" type="email" placeholder="you@example.com" name="email" [(ngModel)]="form.email" autocomplete="email" required>
          </div>
          <div class="col-sm-6 form-group">
            <label for="contact">Contact number</label>
            <input class="form-control" id="contact" type="tel" placeholder="Mobile number" name="contact" [(ngModel)]="form.contact" autocomplete="tel" required>
          </div>
        </div>
        <div class="row">
          <div class="col-sm-6 form-group">
            <label for="sex">Gender</label>
            <select class="form-control" id="sex" name="sex" [(ngModel)]="form.sex" required>
              <option value="M">Male</option><option value="F">Female</option>
            </select>
          </div>
          <div class="col-sm-6 form-group">
            <label for="password">Password</label>
            <input class="form-control" id="password" type="password" placeholder="Choose a password" name="password" [(ngModel)]="form.password" autocomplete="new-password" required>
          </div>
        </div>
        <button class="btn btn-primary btn-block mt-2" type="submit">Create account</button>
      </form>
      <div class="auth-links center">
        <span class="text-muted font-weight-normal mr-1">Already have an account?</span><a routerLink="/login">Sign in</a>
      </div>
    </app-auth-layout>
  `
})
export class RegisterComponent {
  form: any = { role: 5, sex: 'M' };
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
