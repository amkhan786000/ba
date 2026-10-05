import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { errorText } from '../alerts/alerts.component';

/** The review fields every progress row now carries. */
export interface Reviewable {
  progress_id: number;
  review_status?: string | null;
  review_comment?: string | null;
  reviewed_at?: string | null;
  grantee_name?: string | null;
  session?: string | null;
  year?: string | number | null;
  marks?: string | number | null;
}

/** Review status badge for a progress row, plus (when allowed) a Review button with an approve / reject dialog. */
@Component({
  selector: 'app-progress-review',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <span class="badge" [ngClass]="badge" [title]="row.review_comment || ''">{{ status }}</span>
    <div *ngIf="row.review_comment && showComment" class="small text-muted mt-1 review-comment">“{{ row.review_comment }}”</div>
    <button *ngIf="canReview" type="button" class="btn btn-xs btn-outline-primary ml-1" (click)="openDialog()">Review</button>

    <div class="modal d-block" *ngIf="open" (click)="open = false">
      <div class="modal-dialog modal-dialog-centered" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Review progress report</h5>
            <button type="button" class="close" (click)="open = false">&times;</button>
          </div>
          <div class="modal-body">
            <p class="mb-3">
              <strong>{{ row.grantee_name }}</strong> · {{ row.session }} {{ row.year }} · marks <strong>{{ row.marks }}</strong>
            </p>
            <div *ngIf="error" class="alert alert-danger">{{ error }}</div>
            <label class="d-block">Decision</label>
            <div class="segmented mb-3">
              <button type="button" [class.active]="choice === 'Approved'" (click)="choice = 'Approved'"><i class="mdi mdi-check"></i>Approve</button>
              <button type="button" [class.active]="choice === 'Rejected'" (click)="choice = 'Rejected'"><i class="mdi mdi-close"></i>Send back</button>
            </div>
            <div class="form-group mb-0">
              <label for="reviewComment">Comment {{ choice === 'Rejected' ? '(required)' : '(optional)' }}</label>
              <textarea id="reviewComment" class="form-control" rows="3" [(ngModel)]="comment"
                        [placeholder]="choice === 'Rejected' ? 'Tell the student what to fix or re-upload' : 'Well done!'"></textarea>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="open = false">Cancel</button>
            <button type="button" class="btn btn-primary" [disabled]="saving || (choice === 'Rejected' && !comment.trim())" (click)="save()">
              {{ saving ? 'Saving…' : 'Save review' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class ProgressReviewComponent {
  @Input({ required: true }) row!: Reviewable;
  @Input() canReview = false;
  @Input() showComment = true;
  @Output() reviewed = new EventEmitter<Reviewable>();

  open = false;
  choice: 'Approved' | 'Rejected' = 'Approved';
  comment = '';
  saving = false;
  error = '';

  constructor(private api: ApiService) {}

  get status(): string { return this.row.review_status || 'Pending'; }

  get badge(): string {
    switch (this.status) {
      case 'Approved': return 'badge-success';
      case 'Rejected': return 'badge-danger';
      default: return 'badge-warning';
    }
  }

  openDialog(): void {
    this.choice = this.status === 'Rejected' ? 'Rejected' : 'Approved';
    this.comment = this.row.review_comment ?? '';
    this.error = '';
    this.open = true;
  }

  save(): void {
    this.saving = true;
    this.api.post<Reviewable>(`/progress/${this.row.progress_id}/review`, { status: this.choice, comment: this.comment }).subscribe({
      next: (r) => {
        this.saving = false;
        this.open = false;
        Object.assign(this.row, { review_status: r.review_status, review_comment: r.review_comment, reviewed_at: r.reviewed_at });
        this.reviewed.emit(this.row);
      },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save the review.'); }
    });
  }
}
