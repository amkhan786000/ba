import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { PagerComponent, PageState, PaginatePipe } from '../shared/pager/pager.component';
import { CardTableDirective } from '../shared/card-table.directive';

export interface AlumniRow {
  id: number; user_id: string; name: string; email: string | null; phone: string | null; chapter: string | null;
  graduated_on: string | null; course: string | null; institution: string | null;
  current_status: string | null; current_status_label: string | null; organisation: string | null; role_title: string | null;
  city: string | null; country: string | null; linkedin_url: string | null; graduation_year: number | null;
  consent_to_contact: boolean; notes: string | null; updated_at: string | null;
}

/** Alumni form fields (camelCase, as the API expects). */
export interface AlumniForm {
  currentStatus: string; organisation: string; roleTitle: string; city: string; country: string;
  linkedinUrl: string; graduationYear: number | null; consentToContact: boolean; notes: string;
}

export function alumniForm(r: AlumniRow): AlumniForm {
  return {
    currentStatus: r.current_status ?? '', organisation: r.organisation ?? '', roleTitle: r.role_title ?? '', city: r.city ?? '',
    country: r.country ?? '', linkedinUrl: r.linkedin_url ?? '', graduationYear: r.graduation_year, consentToContact: r.consent_to_contact,
    notes: r.notes ?? ''
  };
}

/** Admin > Alumni: graduated students and what they do now (jobs, higher studies, ...). */
@Component({
  selector: 'app-admin-alumni',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, PagerComponent, PaginatePipe, CardTableDirective],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Alumni</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row">
      <div class="col-6 col-md-4 col-xl-2">
        <div class="card-box clickable" [class.selected]="!status" (click)="status = ''; pg.reset()">
          <h4 class="header-title mt-0">Alumni</h4><h2>{{ rows.length }}</h2><small class="text-muted">{{ consenting }} happy to be contacted</small>
        </div>
      </div>
      <div class="col-6 col-md-4 col-xl-2" *ngFor="let s of statusList">
        <div class="card-box clickable" [class.selected]="status === s.code" (click)="status = status === s.code ? '' : s.code; pg.reset()">
          <h4 class="header-title mt-0">{{ s.label }}</h4><h2>{{ byStatus[s.code] || 0 }}</h2>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-body">
        <div class="d-flex flex-column flex-md-row justify-content-between mb-3">
          <p class="text-muted mb-2 mb-md-0 mr-md-3">
            Students whose study status is <strong>Graduated</strong>. Graduates can update their own profile from their portal
            (Alumni Profile); you can update it here too.
          </p>
          <input class="form-control" style="max-width: 280px" placeholder="Search name, organisation, city" [(ngModel)]="q" (ngModelChange)="pg.reset()">
        </div>
        <div class="table-responsive">
          <table class="table table-sm table-centered mb-0">
            <thead><tr><th>Graduate</th><th>Course</th><th>Graduated</th><th>Now</th><th>Where</th><th>Contact OK</th><th *ngIf="canEdit"></th></tr></thead>
            <tbody>
              <tr *ngIf="loading"><td colspan="7" class="text-center"><span class="spinner-border spinner-border-sm"></span></td></tr>
              <tr *ngIf="!loading && !visible.length"><td colspan="7" class="text-center text-muted">No alumni yet. Mark students as Graduated in the Student Directory (Study Status).</td></tr>
              <tr *ngFor="let r of visible | paginate: pg.page : pg.size">
                <td><strong>{{ r.name }}</strong><div class="small text-muted">{{ r.user_id }}{{ r.chapter ? ' · ' + r.chapter : '' }}</div></td>
                <td>{{ r.course || '--' }}<div class="small text-muted">{{ r.institution }}</div></td>
                <td>{{ r.graduation_year || '--' }}</td>
                <td>
                  <span *ngIf="r.current_status_label; else unknown" class="badge badge-info">{{ r.current_status_label }}</span>
                  <div class="small">{{ r.role_title }}{{ r.role_title && r.organisation ? ', ' : '' }}{{ r.organisation }}</div>
                  <ng-template #unknown><span class="text-muted">Not known yet</span></ng-template>
                </td>
                <td>{{ [r.city, r.country].filter(isText).join(', ') || '--' }}
                  <div *ngIf="r.linkedin_url"><a [href]="r.linkedin_url" target="_blank" rel="noopener" class="small">LinkedIn</a></div>
                </td>
                <td>
                  <span class="badge" [ngClass]="r.consent_to_contact ? 'badge-success' : 'badge-light'">{{ r.consent_to_contact ? 'Yes' : 'No' }}</span>
                  <div *ngIf="r.consent_to_contact" class="small text-muted">{{ r.email }}<br>{{ r.phone }}</div>
                </td>
                <td *ngIf="canEdit"><button type="button" class="btn btn-xs btn-primary" (click)="open(r)">Edit</button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <app-pager [state]="pg" [total]="visible.length"></app-pager>
      </div>
    </div>

    <div *ngIf="editing" class="modal fade show d-block" tabindex="-1" (click)="editing = null">
      <div class="modal-dialog modal-lg modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">{{ editing.row.name }} ({{ editing.row.user_id }})</h5><button type="button" class="close" (click)="editing = null">&times;</button></div>
          <form (ngSubmit)="save()">
            <div class="modal-body">
              <div class="row">
                <div class="col-md-6 form-group"><label>Doing now</label>
                  <select class="form-control" name="st" [(ngModel)]="editing.form.currentStatus">
                    <option value="">Not known yet</option>
                    <option *ngFor="let s of statusList" [value]="s.code">{{ s.label }}</option>
                  </select>
                </div>
                <div class="col-md-6 form-group"><label>Graduation year</label><input type="number" class="form-control" name="gy" [(ngModel)]="editing.form.graduationYear"></div>
                <div class="col-md-6 form-group"><label>Organisation / university</label><input class="form-control" name="org" maxlength="200" [(ngModel)]="editing.form.organisation"></div>
                <div class="col-md-6 form-group"><label>Job title / course</label><input class="form-control" name="rt" maxlength="200" [(ngModel)]="editing.form.roleTitle"></div>
                <div class="col-md-6 form-group"><label>City</label><input class="form-control" name="city" maxlength="100" [(ngModel)]="editing.form.city"></div>
                <div class="col-md-6 form-group"><label>Country</label><input class="form-control" name="country" maxlength="100" [(ngModel)]="editing.form.country"></div>
                <div class="col-12 form-group"><label>LinkedIn</label><input class="form-control" name="li" placeholder="https://www.linkedin.com/in/..." [(ngModel)]="editing.form.linkedinUrl"></div>
                <div class="col-12 form-group"><label>Notes</label><textarea class="form-control" name="notes" rows="2" maxlength="2000" [(ngModel)]="editing.form.notes"></textarea></div>
                <div class="col-12">
                  <div class="custom-control custom-checkbox">
                    <input type="checkbox" class="custom-control-input" id="consent" name="consent" [(ngModel)]="editing.form.consentToContact">
                    <label class="custom-control-label" for="consent">Happy to be contacted by Rahbar (mentoring, events, giving back)</label>
                  </div>
                </div>
              </div>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-light" (click)="editing = null">Cancel</button>
              <button type="submit" class="btn btn-primary" [disabled]="saving">{{ saving ? 'Saving…' : 'Save' }}</button>
            </div>
          </form>
        </div>
      </div>
    </div>
  `
})
export class AdminAlumniComponent implements OnInit {
  readonly pg = new PageState(25);
  rows: AlumniRow[] = [];
  byStatus: Record<string, number> = {};
  statusList: { code: string; label: string }[] = [];
  consenting = 0;
  q = '';
  status = '';
  loading = false;
  saving = false;
  message = '';
  error = '';
  editing: { row: AlumniRow; form: AlumniForm } | null = null;

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('ALUMNI', 'EDIT'); }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.api.get<{ rows: AlumniRow[]; byStatus: Record<string, number>; statuses: Record<string, string>; consenting: number }>('/admin/alumni').subscribe({
      next: (r) => {
        this.loading = false;
        this.rows = r.rows;
        this.byStatus = r.byStatus;
        this.consenting = r.consenting;
        this.statusList = Object.entries(r.statuses).map(([code, label]) => ({ code, label }));
      },
      error: (e) => { this.loading = false; this.error = errorText(e, 'Could not load the alumni.'); }
    });
  }

  get visible(): AlumniRow[] {
    const q = this.q.trim().toLowerCase();
    return this.rows.filter((r) => (!this.status || r.current_status === this.status)
      && (!q || [r.name, r.user_id, r.organisation, r.role_title, r.city, r.country, r.course].some((v) => (v ?? '').toLowerCase().includes(q))));
  }

  isText(v: string | null): boolean { return !!v; }

  open(r: AlumniRow): void { this.editing = { row: r, form: alumniForm(r) }; }

  save(): void {
    if (!this.editing) return;
    this.saving = true;
    this.api.put<AlumniRow>(`/admin/alumni/${this.editing.row.id}`, this.editing.form).subscribe({
      next: () => { this.saving = false; this.editing = null; this.message = 'Alumni profile saved.'; this.load(); },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save the profile.'); }
    });
  }
}
