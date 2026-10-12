import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';

interface Note {
  id: number; kind: string; kind_label: string; message: string; status: 'PENDING' | 'APPROVED' | 'RETURNED';
  written_at: string; reviewed_at: string | null; review_note: string | null;
}
interface Mine { sponsor_name: string | null; notes: Note[]; kinds: Record<string, string>; max_length: number }

/** Student > Write to my sponsor: a short update or thank-you note; the office checks it before the sponsor sees it. */
@Component({
  selector: 'app-student-notes',
  standalone: true,
  imports: [AppDatePipe, CommonModule, FormsModule, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box mt-2"><h4 class="page-title">Write to my sponsor</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="card" *ngIf="data">
      <div class="card-body">
        <ng-container *ngIf="data.sponsor_name; else noSponsor">
          <p class="text-muted">Tell <strong>{{ data.sponsor_name }}</strong> how your studies are going, or say thank you. The office reads
            your note first and then passes it on (you'll see when it has been sent).</p>
          <div class="form-group">
            <label for="n-kind">What is it?</label>
            <select id="n-kind" class="form-control" style="max-width: 260px" [(ngModel)]="kind">
              <option *ngFor="let k of kindKeys" [value]="k">{{ data.kinds[k] }}</option>
            </select>
          </div>
          <div class="form-group">
            <label for="n-text">Your note</label>
            <textarea id="n-text" class="form-control" rows="5" [maxlength]="data.max_length" [(ngModel)]="text"
                      placeholder="e.g. I finished my third semester with 78%. Thank you for your support!"></textarea>
            <small class="text-muted">{{ text.length }} / {{ data.max_length }}</small>
          </div>
          <button class="btn btn-primary" (click)="send()" [disabled]="busy || text.trim().length < 10">{{ busy ? 'Sending…' : 'Send to the office' }}</button>
        </ng-container>
        <ng-template #noSponsor><p class="mb-0 text-muted">You don't have a sponsor at the moment, so there is no one to write to yet.</p></ng-template>
      </div>
    </div>

    <div class="card" *ngIf="data?.notes?.length">
      <div class="card-body">
        <h5 class="mt-0">My notes</h5>
        <div *ngFor="let n of data!.notes" class="border-bottom py-2">
          <div class="d-flex justify-content-between">
            <small class="text-muted">{{ n.kind_label }} · {{ n.written_at | appDate }}</small>
            <span class="badge" [ngClass]="n.status === 'APPROVED' ? 'badge-success' : n.status === 'RETURNED' ? 'badge-warning' : 'badge-light border'">
              {{ n.status === 'APPROVED' ? 'Sent to your sponsor' : n.status === 'RETURNED' ? 'Returned' : 'Waiting for the office' }}</span>
          </div>
          <p class="mb-1" style="white-space: pre-line">{{ n.message }}</p>
          <p *ngIf="n.status === 'RETURNED'" class="small text-warning mb-0">The office says: {{ n.review_note }}</p>
        </div>
      </div>
    </div>
  `
})
export class StudentNotesComponent implements OnInit {
  data: Mine | null = null;
  kind = 'UPDATE';
  text = '';
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService) {}

  get kindKeys(): string[] { return Object.keys(this.data?.kinds ?? {}); }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<Mine>('/student/notes').subscribe({ next: (d) => (this.data = d), error: (e) => (this.error = errorText(e, 'Could not load your notes.')) });
  }

  send(): void {
    this.busy = true;
    this.api.post<{ message: string }>('/student/notes', { kind: this.kind, message: this.text }).subscribe({
      next: (r) => { this.busy = false; this.text = ''; this.message = r.message; this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not send your note.'); }
    });
  }
}
