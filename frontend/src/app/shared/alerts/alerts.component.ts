import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

/** Dismissible success/error alerts, standing in for Flask's flash() messages. */
@Component({
  selector: 'app-alerts',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div *ngIf="message" class="alert alert-success alert-dismissible fade show mt-2" role="alert">
      {{ message }}
      <button type="button" class="close" (click)="message = ''; messageChange.emit('')"><span>&times;</span></button>
    </div>
    <div *ngIf="error" class="alert alert-danger alert-dismissible fade show mt-2" role="alert">
      {{ error }}
      <button type="button" class="close" (click)="error = ''; errorChange.emit('')"><span>&times;</span></button>
    </div>
  `
})
export class AlertsComponent {
  @Input() message = '';
  @Input() error = '';
  @Output() messageChange = new EventEmitter<string>();
  @Output() errorChange = new EventEmitter<string>();
}

/** Pulls the backend's { error } message out of an HttpErrorResponse. */
export function errorText(err: unknown, fallback: string): string {
  const e = err as { error?: { error?: string; message?: string } | string };
  if (typeof e?.error === 'string' && e.error) return e.error;
  if (e?.error && typeof e.error === 'object') return e.error.error ?? e.error.message ?? fallback;
  return fallback;
}
