import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { SponsorUser } from './coordinator-sponsors.component';

interface Grantee { id: number; user_id: string; name: string; email: string | null }

/** Port of templates/coordinator/assign_students.html (in Flask the menu link pointed at a POST-only route and errored). */
@Component({
  selector: 'app-coordinator-assign-students',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Assign Students to Sponsors</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>
    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Assign Students</h4>
            <form #f="ngForm" (ngSubmit)="save()">
              <div class="form-group">
                <label for="sponsor_id">Select Sponsor</label>
                <select class="form-control" id="sponsor_id" name="sponsorId" [(ngModel)]="sponsorId" required>
                  <option *ngFor="let s of sponsors" [ngValue]="s.id">{{ s.name }} ({{ s.region }})</option>
                </select>
              </div>
              <div class="form-group">
                <label for="student_ids">Select Students</label>
                <select multiple class="form-control" id="student_ids" name="studentIds" size="12" [(ngModel)]="studentIds" required>
                  <option *ngFor="let g of grantees" [ngValue]="g.id">{{ g.name }} ({{ g.email }})</option>
                </select>
                <small class="form-text text-muted">Hold Ctrl (Windows) or Cmd (Mac) to select several students. {{ studentIds.length }} selected.</small>
              </div>
              <button type="submit" class="btn btn-primary" [disabled]="f.invalid || !studentIds.length || saving">Assign Students</button>
            </form>
          </div>
        </div>
      </div>
    </div>
  `
})
export class CoordinatorAssignStudentsComponent implements OnInit {
  sponsors: SponsorUser[] = [];
  grantees: Grantee[] = [];
  /** users.id of the chosen sponsor and students. */
  sponsorId: number | null = null;
  studentIds: number[] = [];
  saving = false;
  message = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<{ sponsorsConvenors: SponsorUser[]; grantees: Grantee[] }>('/coordinator/manage-sponsors').subscribe({
      next: (r) => { this.sponsors = r.sponsorsConvenors; this.grantees = r.grantees; if (r.sponsorsConvenors.length) this.sponsorId = r.sponsorsConvenors[0].id; },
      error: (e) => (this.error = errorText(e, 'Could not load sponsors and students.'))
    });
  }

  save(): void {
    this.saving = true;
    this.api.post<{ message: string }>('/coordinator/assign-students-bulk', { sponsorId: this.sponsorId, studentIds: this.studentIds }).subscribe({
      next: (r) => { this.saving = false; this.message = r.message; this.studentIds = []; },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not assign students.'); }
    });
  }
}
