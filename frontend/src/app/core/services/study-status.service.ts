import { Injectable } from '@angular/core';
import { ApiService } from './api.service';

export interface StudyStatusOption { code: string; label: string }

/**
 * The study statuses and their labels, from the server (StudyStatus.java is the one list). Loaded once; until the
 * answer arrives a code shows as words ("ON_HOLD" -> "On hold").
 */
@Injectable({ providedIn: 'root' })
export class StudyStatusService {
  list: StudyStatusOption[] = [];
  private labels: Record<string, string> = {};

  constructor(api: ApiService) {
    api.get<StudyStatusOption[]>('/admin/study-statuses').subscribe({
      next: (r) => { this.list = r; this.labels = Object.fromEntries(r.map((s) => [s.code, s.label])); },
      error: () => { /* labels fall back to the codes as words */ }
    });
  }

  /** No status counts as Studying. */
  label(code: string | null | undefined): string {
    const c = code || 'STUDYING';
    if (this.labels[c]) return this.labels[c];
    const words = c.toLowerCase().replace(/_/g, ' ');
    return words.charAt(0).toUpperCase() + words.slice(1);
  }
}
