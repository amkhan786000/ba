import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AlumniForm, AlumniRow, alumniForm } from '../admin/admin-alumni.component';

/** Student > Alumni Profile: once graduated, the student tells Rahbar what they do now. */
@Component({
  selector: 'app-student-alumni',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Alumni Profile</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="card" *ngIf="loaded && !graduated">
      <div class="card-body">
        <h4 class="header-title">Not yet</h4>
        <p class="mb-0 text-muted">
          Your alumni profile opens once you graduate and the office marks your study status as <strong>Graduated</strong>.
          Then you can tell us what you do next (job, higher studies, business) and stay in touch with Rahbar.
        </p>
      </div>
    </div>

    <div class="card" *ngIf="loaded && graduated && form">
      <div class="card-body">
        <h4 class="header-title">Congratulations on graduating!</h4>
        <p class="text-muted">Tell us what you are doing now. It helps Rahbar show the impact of its sponsors and connect alumni with current students.</p>
        <form (ngSubmit)="save()">
          <div class="row">
            <div class="col-md-6 form-group"><label for="st">Doing now</label>
              <select id="st" class="form-control" name="st" [(ngModel)]="form.currentStatus">
                <option value="">Choose…</option>
                <option *ngFor="let s of statusList" [value]="s.code">{{ s.label }}</option>
              </select>
            </div>
            <div class="col-md-6 form-group"><label for="gy">Graduation year</label><input id="gy" type="number" class="form-control" name="gy" [(ngModel)]="form.graduationYear"></div>
            <div class="col-md-6 form-group"><label for="org">Organisation / university</label><input id="org" class="form-control" name="org" maxlength="200" [(ngModel)]="form.organisation"></div>
            <div class="col-md-6 form-group"><label for="rt">Job title / course</label><input id="rt" class="form-control" name="rt" maxlength="200" [(ngModel)]="form.roleTitle"></div>
            <div class="col-md-6 form-group"><label for="city">City</label><input id="city" class="form-control" name="city" maxlength="100" [(ngModel)]="form.city"></div>
            <div class="col-md-6 form-group"><label for="country">Country</label><input id="country" class="form-control" name="country" maxlength="100" [(ngModel)]="form.country"></div>
            <div class="col-12 form-group"><label for="li">LinkedIn <span class="text-muted">(optional)</span></label>
              <input id="li" class="form-control" name="li" placeholder="https://www.linkedin.com/in/..." [(ngModel)]="form.linkedinUrl"></div>
            <div class="col-12 form-group"><label for="notes">Anything else <span class="text-muted">(optional)</span></label>
              <textarea id="notes" class="form-control" name="notes" rows="2" maxlength="2000" [(ngModel)]="form.notes"></textarea></div>
            <div class="col-12 form-group">
              <div class="custom-control custom-checkbox">
                <input type="checkbox" class="custom-control-input" id="consent" name="consent" [(ngModel)]="form.consentToContact">
                <label class="custom-control-label" for="consent">I'm happy to be contacted by Rahbar (mentoring students, events, giving back)</label>
              </div>
            </div>
          </div>
          <button type="submit" class="btn btn-primary" [disabled]="saving">{{ saving ? 'Saving…' : 'Save' }}</button>
        </form>
      </div>
    </div>
  `
})
export class StudentAlumniComponent implements OnInit {
  loaded = false;
  graduated = false;
  form: AlumniForm | null = null;
  statusList: { code: string; label: string }[] = [];
  saving = false;
  message = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  private load(): void {
    this.api.get<{ graduated: boolean; profile: AlumniRow; statuses: Record<string, string> }>('/student/alumni-profile').subscribe({
      next: (r) => {
        this.loaded = true;
        this.graduated = r.graduated;
        this.form = alumniForm(r.profile);
        this.statusList = Object.entries(r.statuses).map(([code, label]) => ({ code, label }));
      },
      error: (e) => (this.error = errorText(e, 'Could not load your alumni profile.'))
    });
  }

  save(): void {
    if (!this.form) return;
    this.saving = true;
    this.api.put('/student/alumni-profile', this.form).subscribe({
      next: () => { this.saving = false; this.message = 'Thank you! Your alumni profile was saved.'; this.load(); },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save your profile.'); }
    });
  }
}
