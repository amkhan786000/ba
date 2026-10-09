import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { errorText } from './alerts/alerts.component';
import { CardTableDirective } from './card-table.directive';

interface Criterion { criterion_id: number; name: string; description: string | null; active: boolean }
interface Review {
  review_id: number; interviewer_id: number; interviewer_name: string | null; recommendation: string | null;
  comment: string | null; scores: Record<string, number | undefined>; average: number | null; updated_at: string | null;
}
interface ScoresData {
  criteria: Criterion[]; reviews: Review[]; averages: Record<string, number | null>; overall: number | null;
  mine: Review | null; minScore: number; maxScore: number;
}

export const RECOMMENDATIONS: Record<string, { label: string; badge: string }> = {
  APPROVE: { label: 'Approve', badge: 'badge-success' },
  WAITLIST: { label: 'Waitlist', badge: 'badge-warning' },
  REJECT: { label: 'Reject', badge: 'badge-danger' }
};

/**
 * Interview scoring on an application: each interviewer scores every criterion 1-10 (criteria are set by admins),
 * with a recommendation and a comment; everyone's scores and the averages are shown.
 */
@Component({
  selector: 'app-interview-scores',
  standalone: true,
  imports: [CommonModule, FormsModule, CardTableDirective],
  styles: [`
    .score-select { max-width: 90px; }
    .overall { font-size: 2rem; font-weight: 700; line-height: 1; }
  `],
  template: `
    <div class="card">
      <div class="card-body">
        <div class="d-flex justify-content-between align-items-start">
          <h4 class="header-title">Interview scores</h4>
          <div class="text-right" *ngIf="data?.overall !== null && data?.overall !== undefined">
            <div class="overall">{{ data!.overall }}<small class="text-muted font-14">/{{ data!.maxScore }}</small></div>
            <small class="text-muted">average of {{ data!.reviews.length }} interviewer(s)</small>
          </div>
        </div>
        <p *ngIf="error" class="text-danger small">{{ error }}</p>
        <p *ngIf="message" class="text-success small">{{ message }}</p>

        <ng-container *ngIf="data">
          <p *ngIf="!data.criteria.length" class="text-muted">No interview criteria yet. An admin can add them under Applications &rarr; Interview ranking.</p>

          <!-- Everyone's scores -->
          <div class="table-responsive" *ngIf="data.reviews.length">
            <table class="table table-sm mb-3">
              <thead>
                <tr><th>Interviewer</th><th *ngFor="let c of data.criteria" class="text-center">{{ c.name }}</th><th class="text-center">Avg</th><th>Recommendation</th></tr>
              </thead>
              <tbody>
                <tr *ngFor="let r of data.reviews">
                  <td>{{ r.interviewer_name }}<div *ngIf="r.comment" class="small text-muted">{{ r.comment }}</div></td>
                  <td *ngFor="let c of data.criteria" class="text-center">{{ r.scores[c.criterion_id] ?? '--' }}</td>
                  <td class="text-center"><strong>{{ r.average ?? '--' }}</strong></td>
                  <td><span *ngIf="r.recommendation" class="badge" [ngClass]="rec(r.recommendation).badge">{{ rec(r.recommendation).label }}</span></td>
                </tr>
                <tr class="table-light">
                  <td><strong>Average</strong></td>
                  <td *ngFor="let c of data.criteria" class="text-center"><strong>{{ data.averages[c.criterion_id] ?? '--' }}</strong></td>
                  <td class="text-center"><strong>{{ data.overall ?? '--' }}</strong></td>
                  <td></td>
                </tr>
              </tbody>
            </table>
          </div>
          <p *ngIf="!data.reviews.length && data.criteria.length" class="text-muted">Nobody has scored this interview yet.</p>

          <!-- My scores -->
          <ng-container *ngIf="canScore && active.length">
            <button *ngIf="!editing" type="button" class="btn btn-outline-primary btn-sm" (click)="editing = true">
              <i class="mdi mdi-star-outline mr-1"></i>{{ data.mine ? 'Edit my scores' : 'Score this interview' }}
            </button>
            <form *ngIf="editing" (ngSubmit)="save()" class="border rounded p-3 mt-2">
              <h5 class="mt-0">My scores <small class="text-muted">({{ data.minScore }}–{{ data.maxScore }})</small></h5>
              <div class="form-group row mb-2" *ngFor="let c of active">
                <label class="col-7 col-form-label" [for]="'c' + c.criterion_id">
                  {{ c.name }}<small *ngIf="c.description" class="d-block text-muted">{{ c.description }}</small>
                </label>
                <div class="col-5">
                  <select class="form-control score-select ml-auto" [id]="'c' + c.criterion_id" [name]="'c' + c.criterion_id" [(ngModel)]="form[c.criterion_id]" required>
                    <option [ngValue]="undefined" disabled>--</option>
                    <option *ngFor="let n of range" [ngValue]="n">{{ n }}</option>
                  </select>
                </div>
              </div>
              <div class="form-group">
                <label for="rec">Recommendation</label>
                <select id="rec" name="rec" class="form-control" [(ngModel)]="recommendation">
                  <option value="">No recommendation</option>
                  <option value="APPROVE">Approve</option>
                  <option value="WAITLIST">Waitlist</option>
                  <option value="REJECT">Reject</option>
                </select>
              </div>
              <div class="form-group">
                <label for="cmt">Comment <span class="text-muted">(optional)</span></label>
                <textarea id="cmt" name="cmt" class="form-control" rows="2" maxlength="2000" [(ngModel)]="comment"></textarea>
              </div>
              <div class="d-flex flex-wrap">
                <button type="submit" class="btn btn-primary mr-2 mb-1" [disabled]="saving || !complete">{{ saving ? 'Saving…' : 'Save my scores' }}</button>
                <button type="button" class="btn btn-light mr-2 mb-1" (click)="editing = false; reset()">Cancel</button>
                <button *ngIf="data.mine" type="button" class="btn btn-outline-danger mb-1 ml-auto" (click)="remove()">Remove my scores</button>
              </div>
            </form>
          </ng-container>
        </ng-container>
      </div>
    </div>
  `
})
export class InterviewScoresComponent implements OnInit {
  @Input() applicationId = '';

  data: ScoresData | null = null;
  form: Record<number, number | undefined> = {};
  recommendation = '';
  comment = '';
  editing = false;
  saving = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  /** Same rule as the server: Applications (edit), or coordinators / convenors. */
  get canScore(): boolean {
    const role = this.auth.currentUser()?.roleId;
    return this.auth.can('APPLICATIONS', 'EDIT') || role === 3 || role === 4;
  }

  get active(): Criterion[] { return (this.data?.criteria ?? []).filter((c) => c.active); }
  get range(): number[] {
    const min = this.data?.minScore ?? 1, max = this.data?.maxScore ?? 10;
    return Array.from({ length: max - min + 1 }, (_, i) => min + i);
  }
  get complete(): boolean { return this.active.every((c) => this.form[c.criterion_id] !== undefined); }

  ngOnInit(): void { this.load(); }

  private load(): void {
    this.api.get<ScoresData>(`/admin/applications/${this.applicationId}/interview-scores`).subscribe({
      next: (d) => { this.data = d; this.reset(); },
      error: (e) => (this.error = errorText(e, 'Could not load the interview scores.'))
    });
  }

  reset(): void {
    const mine = this.data?.mine;
    this.form = {};
    for (const c of this.active) this.form[c.criterion_id] = mine?.scores[c.criterion_id];
    this.recommendation = mine?.recommendation ?? '';
    this.comment = mine?.comment ?? '';
  }

  rec(code: string): { label: string; badge: string } { return RECOMMENDATIONS[code] ?? { label: code, badge: 'badge-light' }; }

  save(): void {
    this.saving = true;
    this.error = '';
    this.message = '';
    this.api.put<ScoresData>(`/admin/applications/${this.applicationId}/interview-scores`,
      { scores: this.form, recommendation: this.recommendation, comment: this.comment }).subscribe({
      next: (d) => { this.saving = false; this.data = d; this.editing = false; this.reset(); this.message = 'Your scores were saved.'; },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save your scores.'); }
    });
  }

  remove(): void {
    if (!confirm('Remove your scores for this interview?')) return;
    this.api.delete(`/admin/applications/${this.applicationId}/interview-scores`).subscribe({
      next: () => { this.editing = false; this.message = 'Your scores were removed.'; this.load(); },
      error: (e) => (this.error = errorText(e, 'Could not remove your scores.'))
    });
  }
}
