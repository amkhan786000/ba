import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AlertsComponent, errorText } from '../../shared/alerts/alerts.component';
import { CardTableDirective } from '../../shared/card-table.directive';
import { RebSession } from './reb.models';

interface PromotionRow {
  enrollment_id: number; student_id: number; admission_no: string; name: string; from_section: string; done: boolean;
  action: 'PROMOTE' | 'DETAIN' | 'COMPLETE' | 'LEAVE' | 'SKIP'; next_class?: string; target_section_id: number | null; detain_section_id: number | null;
  /** The section chosen for PROMOTE, kept when switching to DETAIN and back. */
  promote_section_id?: number | null;
}

/**
 * Rahbar-e-Banaat > Girls > Year-end promotion: moves every girl of one session into the next: up a class, the
 * same class again, passed out (no next class) or left. Can be run again: girls already moved are left alone.
 */
@Component({
  selector: 'app-reb-promotion',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, CardTableDirective],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">
      <a routerLink="/admin/reb/students" class="text-muted mr-1" aria-label="Back to girls"><i class="mdi mdi-arrow-left"></i></a>Year-end promotion</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="card"><div class="card-body">
      <p class="text-muted">Add the new session and its sections in REB Setup first. Then choose the two sessions, check each girl and apply.</p>
      <div class="d-flex flex-wrap align-items-end">
        <div class="form-group mr-3"><label for="p-from">From session</label>
          <select id="p-from" class="form-control" [(ngModel)]="fromId"><option *ngFor="let s of sessions" [ngValue]="s.id">{{ s.name }}</option></select></div>
        <div class="form-group mr-3"><label for="p-to">Into session</label>
          <select id="p-to" class="form-control" [(ngModel)]="toId"><option *ngFor="let s of sessions" [ngValue]="s.id">{{ s.name }}</option></select></div>
        <button class="btn btn-outline-primary mb-3" (click)="preview()" [disabled]="!fromId || !toId || fromId === toId || busy">Show girls</button>
      </div>
    </div></div>

    <div class="card" *ngIf="rows">
      <div class="card-body">
        <p class="mb-2"><strong>{{ pending.length }}</strong> girl{{ pending.length === 1 ? '' : 's' }} to move<span *ngIf="rows.length > pending.length">, {{ rows.length - pending.length }} already moved</span>.</p>
        <div class="table-responsive"><table class="table table-sm table-centered mb-3">
          <thead><tr><th>Girl</th><th>Now in</th><th>Action</th><th>Into</th></tr></thead>
          <tbody>
            <tr *ngIf="!rows.length"><td colspan="4" class="text-center text-muted">No girls studying in this session.</td></tr>
            <tr *ngFor="let r of rows" [class.text-muted]="r.done">
              <td><strong>{{ r.name }}</strong><div class="small text-muted">{{ r.admission_no }}</div></td>
              <td>{{ r.from_section }}</td>
              <td>
                <span *ngIf="r.done">Already moved</span>
                <select *ngIf="!r.done" class="form-control form-control-sm" [(ngModel)]="r.action" (ngModelChange)="actionChanged(r)" [attr.aria-label]="'Action for ' + r.name">
                  <option value="PROMOTE">Promote{{ r.next_class ? ' to ' + r.next_class : '' }}</option>
                  <option value="DETAIN">Same class again</option>
                  <option value="COMPLETE">Passed out</option>
                  <option value="LEAVE">Left</option>
                  <option value="SKIP">Decide later</option>
                </select>
              </td>
              <td>
                <select *ngIf="!r.done && (r.action === 'PROMOTE' || r.action === 'DETAIN')" class="form-control form-control-sm" [(ngModel)]="r.target_section_id" [attr.aria-label]="'Section for ' + r.name">
                  <option [ngValue]="null">-- Choose --</option>
                  <option *ngFor="let s of targets" [ngValue]="s.id">{{ s.label }}</option>
                </select>
              </td>
            </tr>
          </tbody>
        </table></div>
        <div class="d-flex justify-content-between align-items-center flex-wrap">
          <span class="small text-danger mb-2" *ngIf="missing">{{ missing }} girl{{ missing === 1 ? ' has' : 's have' }} no class chosen in the new session.</span>
          <button class="btn btn-primary ml-auto mb-2" (click)="apply()" [disabled]="busy || !pending.length || missing > 0">{{ busy ? 'Applying…' : 'Apply' }}</button>
        </div>
      </div>
    </div>

    <div *ngIf="confirming" class="modal fade show d-block" tabindex="-1" (click)="confirming = false">
      <div class="modal-dialog" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">Apply the promotion?</h5></div>
          <div class="modal-body">{{ summary }}</div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="confirming = false">Cancel</button>
            <button type="button" class="btn btn-primary" (click)="confirmApply()">Apply</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class RebPromotionComponent implements OnInit {
  sessions: RebSession[] = [];
  fromId: number | null = null;
  toId: number | null = null;
  rows: PromotionRow[] | null = null;
  targets: { id: number; label: string }[] = [];
  confirming = false;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService) {}

  get pending(): PromotionRow[] { return (this.rows ?? []).filter((r) => !r.done && r.action !== 'SKIP'); }
  get missing(): number { return this.pending.filter((r) => (r.action === 'PROMOTE' || r.action === 'DETAIN') && !r.target_section_id).length; }

  get summary(): string {
    const n = (a: string) => this.pending.filter((r) => r.action === a).length;
    return `${n('PROMOTE')} promoted, ${n('DETAIN')} in the same class again, ${n('COMPLETE')} passed out, ${n('LEAVE')} left.`;
  }

  ngOnInit(): void {
    this.api.get<RebSession[]>('/admin/reb/sessions').subscribe({
      next: (r) => {
        this.sessions = r; // newest first
        const current = r.findIndex((s) => s.is_current);
        if (current > 0) { this.fromId = r[current].id; this.toId = r[current - 1].id; }
        else if (r.length > 1) { this.fromId = r[1].id; this.toId = r[0].id; }
      },
      error: (e) => (this.error = errorText(e, 'Could not load the sessions.'))
    });
  }

  preview(): void {
    this.busy = true;
    this.api.get<{ rows: PromotionRow[]; sections: { id: number; label: string }[] }>('/admin/reb/promotion', { fromSessionId: this.fromId, toSessionId: this.toId }).subscribe({
      next: (r) => { this.busy = false; this.rows = r.rows.map((x) => ({ ...x, promote_section_id: x.target_section_id })); this.targets = r.sections; },
      error: (e) => { this.busy = false; this.rows = null; this.error = errorText(e, 'Could not prepare the promotion.'); }
    });
  }

  actionChanged(r: PromotionRow): void {
    if (r.action === 'DETAIN') r.target_section_id = r.detain_section_id;
    if (r.action === 'PROMOTE') r.target_section_id = r.promote_section_id ?? null;
  }

  apply(): void { this.confirming = true; }

  confirmApply(): void {
    this.confirming = false;
    this.busy = true;
    const rows = this.pending.map((r) => ({ enrollmentId: r.enrollment_id, action: r.action, targetSectionId: r.target_section_id }));
    this.api.post<{ message: string }>('/admin/reb/promotion', { fromSessionId: this.fromId, toSessionId: this.toId, rows }).subscribe({
      next: (r) => { this.busy = false; this.message = r.message; this.preview(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not apply the promotion.'); }
    });
  }
}
