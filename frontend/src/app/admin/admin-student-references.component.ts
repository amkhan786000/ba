import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';

interface Numbered { id: number; user_id: string | null; name: string; chapter: string | null; college_type: string; reference_no: string }
interface Overview {
  year: number; years: number[]; students: number; without_govt: number; without_private: number;
  numbered: Numbered[]; generated?: Numbered[];
}

/**
 * Students > Reference Numbers (Super Admin and Application Admin): gives the current session year's students their
 * reference number. Govt college: M001-2026, ...; Private, Doesn't matter or not set: PVT001-2026, ...
 */
@Component({
  selector: 'app-admin-student-references',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Reference Numbers</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>
    <p *ngIf="!admin" class="text-muted">Only the Super Admin and the Application Admin can generate reference numbers.</p>

    <ng-container *ngIf="admin && data">
      <div class="card"><div class="card-body">
        <h4 class="header-title">Generate reference numbers</h4>
        <p class="text-muted small">Every student of the chosen session year who has no reference number gets one, oldest record
          first. The college type in BA Details decides the prefix: <strong>Govt</strong> gets M001-{{ data.year }},
          M002-{{ data.year }}, ...; <strong>Private</strong>, <strong>Doesn't matter</strong> or not set gets PVT001-{{ data.year }}, ...
          Numbers already given never change; new ones continue from the highest.</p>
        <div class="row align-items-end">
          <div class="col-md-3 form-group">
            <label for="year">Session year</label>
            <select id="year" class="form-control" [(ngModel)]="year">
              <option *ngFor="let y of data.years" [ngValue]="y">{{ y }}</option>
            </select>
          </div>
          <div class="col-md-9 form-group">
            <p class="mb-2">{{ data.students }} student{{ data.students === 1 ? '' : 's' }} in {{ data.year }}.
              <strong>{{ waiting }}</strong> without a reference number
              <span class="text-muted">({{ data.without_govt }} Govt, {{ data.without_private }} Private / other)</span>.</p>
            <button class="btn btn-primary" (click)="generate()" [disabled]="busy || !waiting">
              <i class="mdi mdi-numeric mr-1"></i>Generate {{ waiting || '' }} reference number{{ waiting === 1 ? '' : 's' }}</button>
          </div>
        </div>
      </div></div>

      <div class="card" *ngIf="data.generated?.length"><div class="card-body">
        <h4 class="header-title">Just generated ({{ data.generated!.length }})</h4>
        <ng-container *ngTemplateOutlet="table; context: { rows: data.generated }"></ng-container>
      </div></div>

      <div class="card"><div class="card-body">
        <h4 class="header-title">Reference numbers for {{ data.year }} ({{ data.numbered.length }})</h4>
        <p *ngIf="!data.numbered.length" class="text-muted mb-0">None yet.</p>
        <ng-container *ngIf="data.numbered.length">
          <ng-container *ngTemplateOutlet="table; context: { rows: data.numbered }"></ng-container>
        </ng-container>
      </div></div>
    </ng-container>

    <ng-template #table let-rows="rows">
      <div class="table-responsive"><table class="table table-sm mb-0">
        <thead class="thead-light"><tr><th>Reference No.</th><th>Student ID</th><th>Name</th><th>Chapter</th><th>College type</th></tr></thead>
        <tbody><tr *ngFor="let r of rows">
          <td><strong>{{ r.reference_no }}</strong></td><td>{{ r.user_id || '-' }}</td><td>{{ r.name }}</td>
          <td>{{ r.chapter || '-' }}</td><td>{{ r.college_type }}</td>
        </tr></tbody>
      </table></div>
    </ng-template>
  `
})
export class AdminStudentReferencesComponent implements OnInit {
  data: Overview | null = null;
  year: number | null = null;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get admin(): boolean { return this.auth.isAdmin(); }
  get waiting(): number { return this.data ? this.data.without_govt + this.data.without_private : 0; }

  ngOnInit(): void {
    if (!this.admin) return;
    this.api.get<Overview>('/admin/student-references').subscribe({
      next: (d) => { this.data = d; this.year = d.year; },
      error: (e) => (this.error = errorText(e, 'Could not load the students.'))
    });
  }

  generate(): void {
    if (!this.year || !confirm(`Give ${this.waiting} student(s) of ${this.year} a reference number? Numbers can't be taken back here.`)) return;
    this.busy = true;
    this.error = '';
    this.api.post<Overview>('/admin/student-references/generate', { year: this.year }).subscribe({
      next: (d) => {
        this.busy = false;
        this.data = d;
        this.message = `${d.generated?.length ?? 0} reference number(s) generated.`;
      },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not generate the reference numbers.'); }
    });
  }
}
