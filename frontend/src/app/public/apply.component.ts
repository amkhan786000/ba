import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AuthLayoutComponent } from '../shared/auth-layout/auth-layout.component';

@Component({
  selector: 'app-public-apply',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AuthLayoutComponent],
  template: `
    <app-auth-layout heading="Scholarship application" subheading="Tell us about the student and their family. All three mobile numbers must be different 10-digit numbers." [wide]="true">
      <div *ngIf="message" class="alert alert-success">
        <i class="mdi mdi-check-circle-outline mr-1"></i>{{ message }}
      </div>
      <div *ngIf="error" class="alert alert-danger">{{ error }}</div>
      <form (ngSubmit)="submit()" *ngIf="!message">
        <h6 class="text-uppercase text-muted small font-weight-bold mb-3">Student</h6>
        <div class="form-group">
          <label for="name">Student name</label>
          <input class="form-control" id="name" placeholder="Full name" name="name" [(ngModel)]="form.name" required>
        </div>
        <div class="row">
          <div class="col-sm-6 form-group">
            <label for="studentMobile">Student mobile</label>
            <input class="form-control" id="studentMobile" type="tel" placeholder="10 digits" name="studentMobile" [(ngModel)]="form.studentMobile" required>
          </div>
          <div class="col-sm-6 form-group">
            <label for="courseApplied">Course applied</label>
            <input class="form-control" id="courseApplied" placeholder="e.g. B.Tech" name="courseApplied" [(ngModel)]="form.courseApplied">
          </div>
        </div>
        <div class="row">
          <div class="col-sm-6 form-group">
            <label for="rccName">RCC name</label>
            <input class="form-control" id="rccName" placeholder="Centre" name="rccName" [(ngModel)]="form.rccName">
          </div>
          <div class="col-sm-6 form-group">
            <label for="address">Address</label>
            <input class="form-control" id="address" placeholder="Town / city" name="address" [(ngModel)]="form.address">
          </div>
        </div>
        <h6 class="text-uppercase text-muted small font-weight-bold mb-3 mt-2">Family</h6>
        <div class="row">
          <div class="col-sm-6 form-group">
            <label for="fatherName">Father's name</label>
            <input class="form-control" id="fatherName" name="fatherName" [(ngModel)]="form.fatherName">
          </div>
          <div class="col-sm-6 form-group">
            <label for="fatherMobile">Father's mobile</label>
            <input class="form-control" id="fatherMobile" type="tel" placeholder="10 digits" name="fatherMobile" [(ngModel)]="form.fatherMobile" required>
          </div>
        </div>
        <div class="row">
          <div class="col-sm-6 form-group">
            <label for="motherName">Mother's name</label>
            <input class="form-control" id="motherName" name="motherName" [(ngModel)]="form.motherName">
          </div>
          <div class="col-sm-6 form-group">
            <label for="motherMobile">Mother's mobile</label>
            <input class="form-control" id="motherMobile" type="tel" placeholder="10 digits" name="motherMobile" [(ngModel)]="form.motherMobile" required>
          </div>
        </div>
        <button class="btn btn-primary btn-block mt-2" type="submit">Submit application</button>
      </form>
      <div class="auth-links center"><a routerLink="/login"><i class="mdi mdi-arrow-left mr-1"></i>Back to sign in</a></div>
    </app-auth-layout>
  `
})
export class PublicApplyComponent implements OnInit {
  form: any = {};
  message = '';
  error = '';
  constructor(private api: ApiService) {}
  ngOnInit(): void {}
  submit(): void {
    this.error = '';
    this.api.post<{ message: string }>('/public/apply', this.form).subscribe({
      next: (res) => (this.message = res.message),
      error: (err) => (this.error = err?.error?.error ?? 'Could not submit application.')
    });
  }
}
