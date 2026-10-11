import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { AlertsComponent, errorText } from '../../shared/alerts/alerts.component';
import { isoDate } from '../../shared/format';
import { RebSectionRow, RebSession } from './reb.models';

interface SummaryRow { student_id: number; name: string; roll_no: string | null; current_para: number | null; paras_completed: number; last_lesson: string | null }
interface Lesson {
  id: number; date: string; kind: string; para: number; surah: string | null; from_ayah: number | null; to_ayah: number | null;
  quality: string; completed_para: boolean; remark: string | null; teacher_name: string | null;
}

const KINDS: Record<string, string> = { SABAQ: 'Sabaq (new lesson)', SABQI: 'Sabqi (recent revision)', MANZIL: 'Manzil (older revision)' };
const QUALITY: Record<string, string> = { EXCELLENT: 'Excellent', GOOD: 'Good', NEEDS_REVISION: 'Needs revision' };

/**
 * Rahbar-e-Banaat > Quran / Hifz: each girl's current para and paras completed, class by class; open a girl to see
 * her lessons and add today's sabaq, sabqi or manzil. Teachers see their own classes.
 */
@Component({
  selector: 'app-reb-quran',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Quran / Hifz Progress</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row">
      <div class="col-lg-5">
        <div class="card"><div class="card-body">
          <label for="h-section">Class</label>
          <select id="h-section" class="form-control mb-3" [(ngModel)]="sectionId" (ngModelChange)="loadSummary()">
            <option [ngValue]="null">-- Choose --</option><option *ngFor="let s of sections" [ngValue]="s.id">{{ s.label }}</option></select>
          <div class="list-group">
            <a *ngFor="let r of summary" class="list-group-item list-group-item-action py-2 d-flex justify-content-between align-items-center"
               [class.active]="r.student_id === girl?.student_id" tabindex="0" (click)="openGirl(r)" (keydown.enter)="openGirl(r)">
              <span><span [class.text-muted]="r.student_id !== girl?.student_id">{{ r.roll_no }}</span> {{ r.name }}</span>
              <small>{{ r.current_para ? 'Para ' + r.current_para : '–' }} · {{ r.paras_completed }} done</small></a>
          </div>
          <p *ngIf="sectionId && !summary.length" class="text-muted small mb-0">No girls in this class.</p>
        </div></div>
      </div>
      <div class="col-lg-7" *ngIf="girl">
        <div class="card"><div class="card-body">
          <h4 class="header-title">{{ girl.name }}</h4>
          <p class="mb-2">{{ detail?.current_para ? 'Now on para ' + detail?.current_para : 'No lessons yet' }} · <strong>{{ detail?.paras_completed ?? 0 }}</strong> of 30 paras completed
            <span *ngIf="detail?.completed?.length" class="small text-muted">({{ detail?.completed?.join(', ') }})</span></p>
          <div *ngIf="canEdit" class="border rounded p-2 mb-3">
            <div class="row">
              <div class="col-6 col-md-4 form-group mb-2"><label for="l-date">Day</label><input id="l-date" type="date" class="form-control form-control-sm" [max]="today" [(ngModel)]="form.date"></div>
              <div class="col-6 col-md-4 form-group mb-2"><label for="l-kind">Lesson</label>
                <select id="l-kind" class="form-control form-control-sm" [(ngModel)]="form.kind"><option *ngFor="let k of kindKeys" [value]="k">{{ kinds[k] }}</option></select></div>
              <div class="col-4 col-md-4 form-group mb-2"><label for="l-para">Para *</label><input id="l-para" type="number" min="1" max="30" class="form-control form-control-sm" [(ngModel)]="form.para"></div>
              <div class="col-8 col-md-6 form-group mb-2"><label for="l-surah">Surah</label><input id="l-surah" class="form-control form-control-sm" [(ngModel)]="form.surah"></div>
              <div class="col-6 col-md-3 form-group mb-2"><label for="l-from">From ayah</label><input id="l-from" type="number" min="1" class="form-control form-control-sm" [(ngModel)]="form.fromAyah"></div>
              <div class="col-6 col-md-3 form-group mb-2"><label for="l-to">To ayah</label><input id="l-to" type="number" min="1" class="form-control form-control-sm" [(ngModel)]="form.toAyah"></div>
              <div class="col-md-6 form-group mb-2"><label for="l-quality">How well she knew it</label>
                <select id="l-quality" class="form-control form-control-sm" [(ngModel)]="form.quality"><option *ngFor="let k of qualityKeys" [value]="k">{{ quality[k] }}</option></select></div>
              <div class="col-md-6 form-group mb-2 d-flex align-items-end"><div class="custom-control custom-checkbox mb-1">
                <input type="checkbox" class="custom-control-input" id="l-done" [(ngModel)]="form.completedPara"><label class="custom-control-label" for="l-done">She completed this para</label></div></div>
              <div class="col-12 form-group mb-2"><label for="l-remark">Remark</label><input id="l-remark" class="form-control form-control-sm" [(ngModel)]="form.remark"></div>
            </div>
            <button class="btn btn-sm btn-primary" (click)="add()" [disabled]="busy || !form.para">Save lesson</button>
          </div>
          <div class="table-responsive"><table class="table table-sm mb-0">
            <thead><tr><th>Day</th><th>Lesson</th><th>Para</th><th>Surah / ayahs</th><th>Knew it</th><th></th></tr></thead>
            <tbody>
              <tr *ngIf="!detail?.entries?.length"><td colspan="6" class="text-muted">No lessons recorded yet.</td></tr>
              <tr *ngFor="let l of detail?.entries">
                <td class="text-nowrap">{{ l.date | date: 'd MMM' }}</td><td>{{ l.kind === 'SABAQ' ? 'Sabaq' : l.kind === 'SABQI' ? 'Sabqi' : 'Manzil' }}</td>
                <td>{{ l.para }}<span *ngIf="l.completed_para" class="badge badge-success ml-1">done</span></td>
                <td>{{ l.surah }}<span *ngIf="l.from_ayah"> {{ l.from_ayah }}<span *ngIf="l.to_ayah">–{{ l.to_ayah }}</span></span>
                  <div class="small text-muted" *ngIf="l.remark">{{ l.remark }}</div></td>
                <td [class.text-danger]="l.quality === 'NEEDS_REVISION'">{{ quality[l.quality] }}</td>
                <td class="text-right"><button *ngIf="canEdit" class="btn btn-xs btn-link text-danger" (click)="remove(l)">Remove</button></td>
              </tr>
            </tbody>
          </table></div>
        </div></div>
      </div>
    </div>
  `
})
export class RebQuranComponent implements OnInit {
  readonly kinds = KINDS;
  readonly kindKeys = Object.keys(KINDS);
  readonly quality = QUALITY;
  readonly qualityKeys = Object.keys(QUALITY);
  readonly today = isoDate(new Date());
  sections: RebSectionRow[] = [];
  sectionId: number | null = null;
  summary: SummaryRow[] = [];
  girl: SummaryRow | null = null;
  detail: { current_para: number | null; paras_completed: number; completed: number[]; entries: Lesson[] } | null = null;
  form = this.blank();
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('REB_QURAN', 'EDIT'); }

  private blank() {
    return { date: isoDate(new Date()), kind: 'SABAQ', para: null as number | null, surah: '', fromAyah: null as number | null, toAyah: null as number | null,
      quality: 'GOOD', completedPara: false, remark: '' };
  }

  ngOnInit(): void {
    this.api.get<RebSession[]>('/admin/reb/sessions').subscribe({
      next: (r) => {
        const sid = (r.find((s) => s.is_current) ?? r[0])?.id;
        if (sid) this.api.get<RebSectionRow[]>('/admin/reb/sections', { sessionId: sid }).subscribe({
          next: (s) => { this.sections = s; if (s.length === 1) { this.sectionId = s[0].id; this.loadSummary(); } }
        });
      },
      error: (e) => (this.error = errorText(e, 'Could not load the classes.'))
    });
  }

  loadSummary(): void {
    this.girl = null;
    this.summary = [];
    if (!this.sectionId) return;
    this.api.get<SummaryRow[]>('/admin/reb/quran', { sectionId: this.sectionId }).subscribe({
      next: (r) => (this.summary = r), error: (e) => (this.error = errorText(e, 'Could not load the class.'))
    });
  }

  openGirl(r: SummaryRow): void {
    this.girl = r;
    this.loadGirl();
  }

  private loadGirl(): void {
    if (!this.girl) return;
    this.api.get<NonNullable<typeof this.detail>>(`/admin/reb/students/${this.girl.student_id}/quran`).subscribe({
      next: (d) => { this.detail = d; if (!this.form.para && d.current_para) this.form.para = d.current_para; },
      error: (e) => (this.error = errorText(e, 'Could not load her lessons.'))
    });
  }

  add(): void {
    if (!this.girl) return;
    this.busy = true;
    this.api.post<{ message: string }>('/admin/reb/quran', { ...this.form, studentId: this.girl.student_id }).subscribe({
      next: (r) => {
        this.busy = false;
        this.message = r.message;
        const para = this.form.para;
        this.form = { ...this.blank(), para: this.form.completedPara && para ? Math.min(30, para + 1) : para };
        this.loadGirl();
        this.loadSummaryKeep();
      },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not save the lesson.'); }
    });
  }

  private loadSummaryKeep(): void {
    if (!this.sectionId) return;
    this.api.get<SummaryRow[]>('/admin/reb/quran', { sectionId: this.sectionId }).subscribe({ next: (r) => (this.summary = r) });
  }

  remove(l: Lesson): void {
    if (!confirm('Remove this lesson?')) return;
    this.api.delete<{ message: string }>(`/admin/reb/quran/${l.id}`).subscribe({
      next: () => { this.loadGirl(); this.loadSummaryKeep(); }, error: (e) => (this.error = errorText(e, 'Could not remove it.'))
    });
  }
}
