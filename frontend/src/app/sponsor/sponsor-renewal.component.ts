import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';

export interface RenewalCampaign { id: number; session_year: number; deadline: string | null; message: string | null }
export interface RenewalResponse { current_students: number; answer: string | null; students_wanted: number | null; note: string | null; answered_at: string | null }
export interface MyRenewal { campaign: RenewalCampaign | null; response: RenewalResponse | null }

/** Sponsor portal > Renewal: "Will you continue next session?", answered (and changeable) while the office asks. */
@Component({
  selector: 'app-sponsor-renewal',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, AppDatePipe],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Sponsorship Renewal</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div *ngIf="d && !d.campaign" class="card"><div class="card-body text-center py-5">
      <i class="mdi mdi-calendar-check text-primary" style="font-size: 44px"></i>
      <h5 class="mt-2">Nothing to answer right now</h5>
      <p class="text-muted mb-0">Before each new session we'll ask here whether you'll continue sponsoring.</p>
    </div></div>

    <div *ngIf="d?.campaign as c" class="card"><div class="card-body">
      <h4 class="header-title">Will you continue sponsoring in {{ c.session_year }}?</h4>
      <p>You sponsor <strong>{{ d!.response!.current_students }}</strong> student{{ d!.response!.current_students === 1 ? '' : 's' }} now.
        <span *ngIf="c.deadline">Please answer by <strong>{{ c.deadline | appDate }}</strong>.</span></p>
      <p *ngIf="c.message" class="text-muted" style="white-space: pre-line">{{ c.message }}</p>
      <div *ngIf="d!.response!.answered_at" class="alert alert-success small">You answered on {{ d!.response!.answered_at | appDate }}. You can change your answer below.</div>

      <div class="custom-control custom-radio mb-2">
        <input type="radio" id="r1" name="ans" class="custom-control-input" value="CONTINUE" [(ngModel)]="answer">
        <label class="custom-control-label" for="r1">Yes, I'll continue with my current student{{ d!.response!.current_students === 1 ? '' : 's' }}</label>
      </div>
      <div class="custom-control custom-radio mb-2">
        <input type="radio" id="r2" name="ans" class="custom-control-input" value="CHANGE" [(ngModel)]="answer">
        <label class="custom-control-label" for="r2">Yes, but with a different number of students</label>
      </div>
      <div *ngIf="answer === 'CHANGE'" class="form-group ml-4" style="max-width: 220px">
        <label for="n">How many students?</label><input id="n" type="number" min="1" max="100" class="form-control" [(ngModel)]="wanted">
      </div>
      <div class="custom-control custom-radio mb-3">
        <input type="radio" id="r3" name="ans" class="custom-control-input" value="STOP" [(ngModel)]="answer">
        <label class="custom-control-label" for="r3">No, not this time</label>
      </div>
      <div class="form-group"><label for="note">Anything you'd like us to know? <span class="text-muted small">(optional)</span></label>
        <textarea id="note" class="form-control" rows="2" maxlength="500" [(ngModel)]="note"></textarea></div>
      <button class="btn btn-primary" (click)="save()" [disabled]="busy || !answer || (answer === 'CHANGE' && !wanted)">Send my answer</button>
    </div></div>
  `
})
export class SponsorRenewalComponent implements OnInit {
  d: MyRenewal | null = null;
  answer = '';
  wanted: number | null = null;
  note = '';
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<MyRenewal>('/sponsor/renewal').subscribe({
      next: (d) => {
        this.d = d;
        this.answer = d.response?.answer ?? '';
        this.wanted = d.response?.students_wanted ?? null;
        this.note = d.response?.note ?? '';
      },
      error: (e) => (this.error = errorText(e, 'Could not load.'))
    });
  }

  save(): void {
    this.busy = true;
    this.api.put<{ message: string }>('/sponsor/renewal', { answer: this.answer, studentsWanted: this.wanted, note: this.note }).subscribe({
      next: (m) => { this.busy = false; this.message = m.message; this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not send your answer.'); }
    });
  }
}
