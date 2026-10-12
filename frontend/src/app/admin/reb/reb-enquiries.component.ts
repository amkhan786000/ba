import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { AlertsComponent, errorText } from '../../shared/alerts/alerts.component';
import { isoDate } from '../../shared/format';
import { FEE_PLANS, RELATIONS, RebSectionRow, RebSession } from './reb.models';
import { AppDatePipe } from '../../shared/app-date';

interface Enquiry {
  id: number; girl_name: string; date_of_birth: string | null; guardian_name: string; relation: string | null; phone: string; email: string | null;
  district: string | null; state: string | null; class_sought: string | null; previous_school: string | null; residential: boolean;
  message: string | null; source: string; status: string; notes: string | null; student_id: number | null; created_at: string;
}

const STATUS: Record<string, string> = { NEW: 'New', CONTACTED: 'Contacted', TEST: 'Entrance test', ADMITTED: 'Admitted', NOT_ADMITTED: 'Not admitted' };
const SOURCE: Record<string, string> = { WEBSITE: 'Website', PHONE: 'Phone', WALK_IN: 'Visit' };

/**
 * Rahbar-e-Banaat > Admission Enquiries: from the website's "Apply for admission" form, phone calls and visits.
 * Follow each one up, then admit the girl straight from her enquiry.
 */
@Component({
  selector: 'app-reb-enquiries',
  standalone: true,
  imports: [AppDatePipe, CommonModule, FormsModule, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Admission Enquiries</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="card"><div class="card-body">
      <div class="d-flex flex-wrap justify-content-between align-items-end mb-2">
        <div class="btn-group btn-group-sm mb-2 flex-wrap" role="group" aria-label="Status">
          <button type="button" class="btn" [ngClass]="status === '' ? 'btn-secondary' : 'btn-light'" (click)="status = ''">All ({{ rows.length }})</button>
          <button *ngFor="let k of statusKeys" type="button" class="btn" [ngClass]="status === k ? 'btn-secondary' : 'btn-light'" (click)="status = k">{{ statuses[k] }} ({{ count(k) }})</button>
        </div>
        <button *ngIf="canEdit" class="btn btn-primary btn-sm mb-2" (click)="newEnquiry()"><i class="mdi mdi-plus mr-1"></i>Add (phone / visit)</button>
      </div>
      <div class="table-responsive"><table class="table table-sm table-centered table-hover mb-0">
        <thead><tr><th>Girl</th><th>Parent / guardian</th><th>Class</th><th>From</th><th>Received</th><th>Status</th></tr></thead>
        <tbody>
          <tr *ngIf="!visible.length"><td colspan="6" class="text-center text-muted">No enquiries.</td></tr>
          <tr *ngFor="let e of visible" class="clickable" (click)="open(e)">
            <td><strong>{{ e.girl_name }}</strong><div class="small text-muted">{{ e.district }}{{ e.residential ? ' · hostel' : '' }}</div></td>
            <td>{{ e.guardian_name }}<div class="small text-muted">{{ e.phone }}</div></td>
            <td>{{ e.class_sought || '--' }}</td><td>{{ sources[e.source] }}</td><td>{{ e.created_at | appDate }}</td>
            <td><span class="badge" [ngClass]="e.status === 'NEW' ? 'badge-primary' : e.status === 'ADMITTED' ? 'badge-success' : 'badge-light border'">{{ statuses[e.status] }}</span></td>
          </tr>
        </tbody>
      </table></div>
    </div></div>

    <div *ngIf="sel" class="modal fade show d-block" tabindex="-1" (click)="sel = null">
      <div class="modal-dialog modal-lg modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title">{{ sel.id ? sel.d['girlName'] : 'Add an enquiry' }}</h5><button type="button" class="close" (click)="sel = null" aria-label="Close">&times;</button></div>
          <div class="modal-body">
            <fieldset [disabled]="!canEdit"><div class="row">
              <div class="col-md-6 form-group"><label for="q-girl">Girl's name *</label><input id="q-girl" class="form-control" [(ngModel)]="sel.d['girlName']"></div>
              <div class="col-md-6 form-group"><label for="q-dob">Date of birth</label><input id="q-dob" type="date" class="form-control" [(ngModel)]="sel.d['dateOfBirth']"></div>
              <div class="col-md-4 form-group"><label for="q-rel">Relation</label>
                <select id="q-rel" class="form-control" [(ngModel)]="sel.d['relation']"><option *ngFor="let r of relationKeys" [value]="r">{{ relations[r] }}</option></select></div>
              <div class="col-md-8 form-group"><label for="q-guardian">Parent / guardian *</label><input id="q-guardian" class="form-control" [(ngModel)]="sel.d['guardianName']"></div>
              <div class="col-md-6 form-group"><label for="q-phone">Phone *</label><input id="q-phone" type="tel" class="form-control" [(ngModel)]="sel.d['phone']"></div>
              <div class="col-md-6 form-group"><label for="q-email">Email</label><input id="q-email" type="email" class="form-control" [(ngModel)]="sel.d['email']"></div>
              <div class="col-md-4 form-group"><label for="q-district">District</label><input id="q-district" class="form-control" [(ngModel)]="sel.d['district']"></div>
              <div class="col-md-4 form-group"><label for="q-state">State</label><input id="q-state" class="form-control" [(ngModel)]="sel.d['state']"></div>
              <div class="col-md-4 form-group"><label for="q-class">Class wanted</label><input id="q-class" class="form-control" [(ngModel)]="sel.d['classSought']"></div>
              <div class="col-md-8 form-group"><label for="q-prev">Previous school</label><input id="q-prev" class="form-control" [(ngModel)]="sel.d['previousSchool']"></div>
              <div class="col-md-4 form-group d-flex align-items-end"><div class="custom-control custom-checkbox mb-2">
                <input type="checkbox" class="custom-control-input" id="q-res" [(ngModel)]="sel.d['residential']"><label class="custom-control-label" for="q-res">Hostel</label></div></div>
              <div class="col-12 form-group" *ngIf="sel.d['message']"><label>Message from the family</label><p class="mb-0" style="white-space: pre-line">{{ sel.d['message'] }}</p></div>
              <div class="col-md-6 form-group" *ngIf="!sel.id"><label for="q-source">Came by</label>
                <select id="q-source" class="form-control" [(ngModel)]="sel.d['source']"><option value="PHONE">Phone</option><option value="WALK_IN">Visit</option></select></div>
              <div class="col-md-6 form-group" *ngIf="sel.id && sel.d['status'] !== 'ADMITTED'"><label for="q-status">Status</label>
                <select id="q-status" class="form-control" [(ngModel)]="sel.d['status']"><option *ngFor="let k of followUpKeys" [value]="k">{{ statuses[k] }}</option></select></div>
              <div class="col-12 form-group mb-0"><label for="q-notes">Office notes</label><textarea id="q-notes" class="form-control" rows="2" [(ngModel)]="sel.d['notes']"></textarea></div>
            </div></fieldset>

            <div *ngIf="sel.id && sel.d['status'] !== 'ADMITTED' && canAdmit" class="border rounded p-2 mt-3">
              <h6 class="mt-0">Admit her</h6>
              <div class="row">
                <div class="col-md-5 form-group mb-2"><label for="ad-section">Class and section *</label>
                  <select id="ad-section" class="form-control" [(ngModel)]="admitForm.sectionId"><option [ngValue]="null">-- Choose --</option>
                    <option *ngFor="let s of sections" [ngValue]="s.id">{{ s.label }}</option></select></div>
                <div class="col-6 col-md-3 form-group mb-2"><label for="ad-plan">Fee plan</label>
                  <select id="ad-plan" class="form-control" [(ngModel)]="admitForm.feePlan"><option *ngFor="let k of planKeys" [value]="k">{{ plans[k] }}</option></select></div>
                <div class="col-6 col-md-4 form-group mb-2"><label for="ad-date">Admitted on</label><input id="ad-date" type="date" class="form-control" [(ngModel)]="admitForm.admittedOn"></div>
              </div>
              <button class="btn btn-success btn-sm" (click)="admit()" [disabled]="busy || !admitForm.sectionId">Admit and open her page</button>
            </div>
            <p *ngIf="sel.d['studentId']" class="mt-3 mb-0"><a href="" (click)="$event.preventDefault(); openGirl(sel.d['studentId'])">Open her page</a></p>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-light" (click)="sel = null">Close</button>
            <button *ngIf="canEdit" type="button" class="btn btn-primary" (click)="save()" [disabled]="busy">Save</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class RebEnquiriesComponent implements OnInit {
  readonly statuses = STATUS;
  readonly statusKeys = Object.keys(STATUS);
  readonly followUpKeys = ['NEW', 'CONTACTED', 'TEST', 'NOT_ADMITTED'];
  readonly sources = SOURCE;
  readonly relations = RELATIONS;
  readonly relationKeys = Object.keys(RELATIONS);
  readonly plans = FEE_PLANS;
  readonly planKeys = Object.keys(FEE_PLANS);
  rows: Enquiry[] = [];
  status = 'NEW';
  sections: RebSectionRow[] = [];
  sel: { id: number | null; d: Record<string, any> } | null = null;
  admitForm = { sectionId: null as number | null, feePlan: 'SELF', admittedOn: isoDate(new Date()) };
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService, private router: Router) {}

  get canEdit(): boolean { return this.auth.can('REB_ENQUIRIES', 'EDIT'); }
  get canAdmit(): boolean { return this.canEdit && this.auth.can('REB_STUDENTS', 'EDIT'); }
  get visible(): Enquiry[] { return this.rows.filter((e) => !this.status || e.status === this.status); }
  count(k: string): number { return this.rows.filter((e) => e.status === k).length; }

  ngOnInit(): void {
    this.load();
    this.api.get<RebSession[]>('/admin/reb/sessions').subscribe({
      next: (r) => {
        const sid = (r.find((s) => s.is_current) ?? r[0])?.id;
        if (sid) this.api.get<RebSectionRow[]>('/admin/reb/sections', { sessionId: sid }).subscribe({ next: (s) => (this.sections = s) });
      },
      error: () => (this.sections = [])
    });
  }

  load(): void {
    this.api.get<Enquiry[]>('/admin/reb/enquiries').subscribe({ next: (r) => (this.rows = r), error: (e) => (this.error = errorText(e, 'Could not load the enquiries.')) });
  }

  newEnquiry(): void {
    this.sel = { id: null, d: { girlName: '', dateOfBirth: '', relation: 'FATHER', guardianName: '', phone: '', email: '', district: '', state: 'Bihar',
      classSought: '', previousSchool: '', residential: true, source: 'PHONE', notes: '' } };
  }

  open(e: Enquiry): void {
    this.admitForm = { sectionId: null, feePlan: 'SELF', admittedOn: isoDate(new Date()) };
    this.sel = { id: e.id, d: { girlName: e.girl_name, dateOfBirth: e.date_of_birth ?? '', relation: e.relation ?? 'FATHER', guardianName: e.guardian_name,
      phone: e.phone, email: e.email ?? '', district: e.district ?? '', state: e.state ?? '', classSought: e.class_sought ?? '', previousSchool: e.previous_school ?? '',
      residential: e.residential, message: e.message, status: e.status, notes: e.notes ?? '', studentId: e.student_id } };
  }

  save(): void {
    const s = this.sel;
    if (!s) return;
    this.busy = true;
    const body = { ...s.d };
    delete body['message'];
    delete body['studentId'];
    if (body['status'] === 'ADMITTED') delete body['status'];
    const req = s.id ? this.api.put<{ message: string }>(`/admin/reb/enquiries/${s.id}`, body) : this.api.post<{ message: string }>('/admin/reb/enquiries', body);
    req.subscribe({
      next: (r) => { this.busy = false; this.sel = null; this.message = r.message; this.load(); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save the enquiry.'); }
    });
  }

  admit(): void {
    if (!this.sel?.id) return;
    this.busy = true;
    this.api.post<{ id: number }>(`/admin/reb/enquiries/${this.sel.id}/admit`, this.admitForm).subscribe({
      next: (r) => { this.busy = false; this.sel = null; this.openGirl(r.id); },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not admit her.'); }
    });
  }

  openGirl(id: number): void { this.router.navigate(['/admin/reb/students', id]); }
}
