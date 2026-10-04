import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';

@Component({
  selector: 'app-public-apply',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container py-5" style="max-width: 640px;">
      <h3 class="mb-4">Rahbar Scholarship Application</h3>
      <div *ngIf="message" class="alert alert-success">{{ message }}</div>
      <div *ngIf="error" class="alert alert-danger">{{ error }}</div>
      <form (ngSubmit)="submit()" *ngIf="!message">
        <input class="form-control mb-2" placeholder="Student name" name="name" [(ngModel)]="form.name" required>
        <input class="form-control mb-2" placeholder="Father's name" name="fatherName" [(ngModel)]="form.fatherName">
        <input class="form-control mb-2" placeholder="Mother's name" name="motherName" [(ngModel)]="form.motherName">
        <input class="form-control mb-2" placeholder="Address" name="address" [(ngModel)]="form.address">
        <input class="form-control mb-2" placeholder="Father's mobile (10 digits)" name="fatherMobile" [(ngModel)]="form.fatherMobile" required>
        <input class="form-control mb-2" placeholder="Mother's mobile (10 digits)" name="motherMobile" [(ngModel)]="form.motherMobile" required>
        <input class="form-control mb-2" placeholder="Student mobile (10 digits)" name="studentMobile" [(ngModel)]="form.studentMobile" required>
        <input class="form-control mb-2" placeholder="Course applied" name="courseApplied" [(ngModel)]="form.courseApplied">
        <input class="form-control mb-3" placeholder="RCC name" name="rccName" [(ngModel)]="form.rccName">
        <button class="btn btn-primary w-100" type="submit">Submit application</button>
      </form>
    </div>
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
