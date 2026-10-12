import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';

interface Note { id: number; student_name: string; student_ref_id: string; kind: string; message: string; date: string }

/** Sponsor > Notes from students: updates and thank-you notes from their students (checked by the office). */
@Component({
  selector: 'app-sponsor-notes',
  standalone: true,
  imports: [AppDatePipe, CommonModule, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box mt-2"><h4 class="page-title">Notes from my students</h4></div></div></div>
    <app-alerts [(error)]="error"></app-alerts>
    <div class="card"><div class="card-body">
      <p *ngIf="loaded && !notes.length" class="text-muted mb-0">No notes yet. When your students write to you, their notes appear here (and you get an email).</p>
      <div *ngFor="let n of notes" class="border-bottom py-3">
        <div class="d-flex justify-content-between align-items-start">
          <div><strong>{{ n.student_name }}</strong> <small class="text-muted">({{ n.student_ref_id }})</small></div>
          <small class="text-muted text-nowrap ml-2">{{ n.kind }} · {{ n.date | appDate }}</small>
        </div>
        <p class="mb-0 mt-1" style="white-space: pre-line">{{ n.message }}</p>
      </div>
    </div></div>
  `
})
export class SponsorNotesComponent implements OnInit {
  notes: Note[] = [];
  loaded = false;
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.get<Note[]>('/sponsor/notes').subscribe({
      next: (r) => { this.notes = r; this.loaded = true; },
      error: (e) => (this.error = errorText(e, 'Could not load the notes.'))
    });
  }
}
