import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../core/services/api.service';
import { AppDatePipe } from '../../shared/app-date';

interface Version { id: number; value?: string; savedBy?: string; savedAt: string }

/**
 * "History" under an editor: earlier saved versions of one text, newest first. Choosing one puts it back in the
 * editor; saving then makes it the current version (and a new history entry).
 */
@Component({
  selector: 'app-website-revisions',
  standalone: true,
  imports: [AppDatePipe, CommonModule],
  template: `
    <details class="website-revisions mt-1" (toggle)="onToggle($event)">
      <summary class="small text-muted">History</summary>
      <p *ngIf="loading" class="small text-muted mb-1">Loading…</p>
      <p *ngIf="!loading && error" class="small text-danger mb-1">{{ error }}</p>
      <p *ngIf="!loading && !error && !versions.length" class="small text-muted mb-1">No earlier versions yet.</p>
      <ul class="list-unstyled mb-0">
        <li *ngFor="let v of versions; let first = first" class="border-bottom py-1">
          <div class="small"><strong>{{ v.savedAt | appDate: 'datetime' }}</strong> · {{ v.savedBy || 'system' }}
            <span *ngIf="first" class="badge badge-light border ml-1">current</span></div>
          <div class="small text-muted" style="white-space: pre-wrap">{{ preview(v.value) }}</div>
          <button *ngIf="!first" type="button" class="btn btn-xs btn-outline-secondary mt-1" (click)="use.emit(v.value ?? '')">Use this version</button>
        </li>
      </ul>
    </details>
  `
})
export class WebsiteRevisionsComponent {
  @Input({ required: true }) target = '';
  @Input() lang = 'en';
  @Output() use = new EventEmitter<string>();

  versions: Version[] = [];
  loading = false;
  error = '';

  constructor(private api: ApiService) {}

  onToggle(e: Event): void {
    if ((e.target as HTMLDetailsElement).open) this.load();
  }

  preview(v?: string): string {
    if (!v) return '(empty: built-in text)';
    return v.length > 280 ? v.slice(0, 280) + '…' : v;
  }

  private load(): void {
    this.loading = true;
    this.error = '';
    this.api.get<Version[]>('/admin/website/revisions', { target: this.target, lang: this.lang }).subscribe({
      next: (v) => { this.versions = v; this.loading = false; },
      error: () => { this.error = 'Could not load the history.'; this.loading = false; }
    });
  }
}
