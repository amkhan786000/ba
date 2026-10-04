import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';

interface StudentUser { user_id: string; name: string; email: string | null; phone: string | null }

/** Port of templates/coordinator/map_students.html */
@Component({
  selector: 'app-coordinator-map-students',
  standalone: true,
  imports: [CommonModule, RouterLink, AlertsComponent],
  template: `
    <div class="row">
      <div class="col-12">
        <div class="page-title-box d-flex justify-content-between align-items-center">
          <h4 class="page-title">Map Students to Sponsor</h4>
          <a routerLink="/coordinator/sponsors" class="btn btn-secondary btn-sm"><i class="mdi mdi-arrow-left"></i> Back</a>
        </div>
      </div>
    </div>
    <app-alerts [(error)]="error"></app-alerts>

    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Already Mapped Students</h4>
            <table class="table table-striped">
              <thead><tr><th>Name</th><th>Email</th><th>Phone</th></tr></thead>
              <tbody>
                <tr *ngIf="!mapped.length"><td colspan="3" class="text-center text-muted">No students mapped yet.</td></tr>
                <tr *ngFor="let s of mapped"><td>{{ s.name }}</td><td>{{ s.email }}</td><td>{{ s.phone }}</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>

    <div class="row mt-4">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Select Students to Map</h4>
            <table class="table table-striped">
              <thead><tr><th>Select</th><th>Name</th><th>Email</th><th>Phone</th></tr></thead>
              <tbody>
                <tr *ngIf="!students.length"><td colspan="4" class="text-center text-muted">No unassigned students.</td></tr>
                <tr *ngFor="let s of students">
                  <td><input type="checkbox" [checked]="selected.has(s.user_id)" (change)="toggle(s.user_id)" /></td>
                  <td>{{ s.name }}</td><td>{{ s.email }}</td><td>{{ s.phone }}</td>
                </tr>
              </tbody>
            </table>
            <div class="mt-3">
              <button type="button" class="btn btn-primary" (click)="save()" [disabled]="!selected.size || saving">Map Selected Students</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class CoordinatorMapStudentsComponent implements OnInit {
  @Input() sponsorId = '';
  students: StudentUser[] = [];
  mapped: StudentUser[] = [];
  selected = new Set<string>();
  saving = false;
  error = '';

  constructor(private api: ApiService, private router: Router) {}

  ngOnInit(): void {
    this.api.get<{ students: StudentUser[]; mappedStudents: StudentUser[]; mappedStudentIds: string[] }>(
      `/coordinator/map-students/${encodeURIComponent(this.sponsorId)}`).subscribe({
      next: (r) => { this.students = r.students; this.mapped = r.mappedStudents; this.selected = new Set(r.mappedStudentIds.filter((id) => r.students.some((s) => s.user_id === id))); },
      error: (e) => (this.error = errorText(e, 'Could not load students.'))
    });
  }

  toggle(id: string): void { if (this.selected.has(id)) this.selected.delete(id); else this.selected.add(id); }

  save(): void {
    this.saving = true;
    this.api.post(`/coordinator/map-students/${encodeURIComponent(this.sponsorId)}`, { studentIds: [...this.selected] }).subscribe({
      next: () => this.router.navigate(['/coordinator/sponsors']),
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not map students.'); }
    });
  }
}
