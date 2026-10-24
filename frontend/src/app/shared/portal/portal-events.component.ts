import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../core/services/api.service';
import { AlertsComponent, errorText } from '../alerts/alerts.component';
import { AppDatePipe } from '../app-date';
import { EVENT_KINDS } from '../../admin/events.component';

type Row = Record<string, any>;

/** Student and sponsor portals > Events: upcoming events for me; sign up or cancel. */
@Component({
  selector: 'app-portal-events',
  standalone: true,
  imports: [CommonModule, AlertsComponent, AppDatePipe],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Events</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>
    <p *ngIf="rows && !rows.length" class="text-muted">No upcoming events right now.</p>
    <div class="card" *ngFor="let e of rows"><div class="card-body">
      <div class="d-flex flex-wrap" style="gap: 8px">
        <div class="mr-auto">
          <h5 class="mt-0 mb-1">{{ e['title'] }} <span class="badge badge-light border">{{ kinds[e['kind']] }}</span>
            <span *ngIf="e['status'] === 'CANCELLED'" class="badge badge-danger">Cancelled</span></h5>
          <div class="text-muted"><i class="mdi mdi-calendar"></i> {{ e['starts_at'] | appDate: 'datetime' }}{{ e['ends_at'] ? ' – ' + (e['ends_at'] | appDate: 'datetime') : '' }}</div>
          <div *ngIf="e['venue']" class="text-muted"><i class="mdi mdi-map-marker"></i> {{ e['venue'] }}</div>
          <div *ngIf="e['online_link']"><i class="mdi mdi-video"></i> <a [href]="e['online_link']" target="_blank" rel="noopener">Join online</a></div>
        </div>
        <div class="text-right">
          <ng-container *ngIf="e['registered']">
            <span class="badge badge-success">You're registered</span><br>
            <a href="" class="small" (click)="$event.preventDefault(); register(e, false)">Cancel my place</a></ng-container>
          <ng-container *ngIf="!e['registered'] && e['status'] === 'SCHEDULED'">
            <button *ngIf="e['registration_open'] && e['places_left'] !== 0" class="btn btn-primary btn-sm" (click)="register(e, true)" [disabled]="busy">Register</button>
            <span *ngIf="e['places_left'] === 0" class="badge badge-secondary">Full</span>
            <span *ngIf="!e['registration_open']" class="small text-muted">No sign-up needed</span></ng-container>
          <div *ngIf="e['places_left'] != null && e['places_left'] > 0" class="small text-muted">{{ e['places_left'] }} places left</div>
        </div>
      </div>
      <p *ngIf="e['description']" class="mt-2 mb-0" style="white-space: pre-line">{{ e['description'] }}</p>
    </div></div>
  `
})
export class PortalEventsComponent implements OnInit {
  readonly kinds = EVENT_KINDS;
  rows: Row[] | null = null;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<Row[]>('/portal/events').subscribe({ next: (r) => (this.rows = r), error: (e) => (this.error = errorText(e, 'Could not load the events.')) });
  }

  register(e: Row, on: boolean): void {
    this.busy = true;
    this.api.put<{ message: string }>(`/portal/events/${e['id']}/registration`, { registered: on }).subscribe({
      next: (m) => { this.busy = false; this.message = m.message; this.load(); },
      error: (err) => { this.busy = false; this.error = errorText(err, 'Could not save.'); }
    });
  }
}
