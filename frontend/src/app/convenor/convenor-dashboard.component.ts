import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { ChapterService } from '../core/services/chapter.service';
import { Chapter } from '../admin/admin-chapters.component';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { BarChartComponent, BarDatum } from '../shared/bar-chart/bar-chart.component';

interface ConvenorDashboard {
  convenor: { name: string; email: string | null; phone: string | null; chapter_name: string | null };
  grantees: { user_id: string; name: string; email: string | null; phone: string | null; paymentStatus: 'paid' | 'unpaid' }[];
  applicationsByStatus: BarDatum[];
  sponsorsByRegion: BarDatum[];
}

export const CHAPTER_NOT_SET = 'Your chapter is not set';

/** Port of templates/convenor/dashboard.html (charts use real data; per-student payment status). */
@Component({
  selector: 'app-convenor-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, BarChartComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Convenor Dashboard</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <!-- Flask redirected to a profile page that didn't exist when the chapter was missing; this sets it in place. -->
    <div class="row" *ngIf="needsChapter">
      <div class="col-md-6">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Set Your Chapter</h4>
            <p class="text-muted">Your chapter is not set. Choose the chapter you are responsible for.</p>
            <form (ngSubmit)="saveChapter()">
              <div class="form-group">
                <select class="form-control" name="chapterId" [(ngModel)]="chapterId" required>
                  <option [ngValue]="null">Select a chapter</option>
                  <option *ngFor="let c of chapterOptions(null)" [ngValue]="c.chapterId">{{ c.chapterName }}</option>
                </select>
              </div>
              <button type="submit" class="btn btn-primary" [disabled]="chapterId === null">Save Chapter</button>
            </form>
          </div>
        </div>
      </div>
    </div>

    <ng-container *ngIf="data">
      <div class="row">
        <div class="col-md-6">
          <div class="card">
            <div class="card-body">
              <h4 class="header-title">Convenor Details</h4>
              <div class="mt-3">
                <p><strong>Name:</strong> {{ data.convenor.name }}</p>
                <p><strong>Email:</strong> {{ data.convenor.email }}</p>
                <p><strong>Phone:</strong> {{ data.convenor.phone }}</p>
                <p><strong>Chapter:</strong> {{ data.convenor.chapter_name }}</p>
              </div>
            </div>
          </div>
        </div>
        <div class="col-md-6">
          <div class="card">
            <div class="card-body">
              <h4 class="header-title">Assigned Student</h4>
              <div class="mt-3">
                <ul class="list-group">
                  <li *ngIf="!data.grantees.length" class="list-group-item text-muted">No students assigned.</li>
                  <li *ngFor="let g of data.grantees" class="list-group-item">
                    <strong>{{ g.name }}</strong> - {{ g.email }} - {{ g.phone }} -
                    <span [ngClass]="g.paymentStatus === 'paid' ? 'text-success' : 'text-danger'">{{ g.paymentStatus }}</span>
                  </li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="row">
        <div class="col-xl-6">
          <div class="card-box">
            <h4 class="header-title mb-4">Applications by Status</h4>
            <app-bar-chart [data]="data.applicationsByStatus"></app-bar-chart>
            <p class="text-muted mb-0 mt-3 text-truncate">Applications from {{ data.convenor.chapter_name }}</p>
          </div>
        </div>
        <div class="col-xl-6">
          <div class="card-box">
            <h4 class="header-title mb-4">Sponsors by Chapter</h4>
            <app-bar-chart [data]="data.sponsorsByRegion"></app-bar-chart>
          </div>
        </div>
      </div>
    </ng-container>
  `
})
export class ConvenorDashboardComponent implements OnInit {
  data: ConvenorDashboard | null = null;
  needsChapter = false;
  chapterId: number | null = null;
  chapters: Chapter[] = [];
  message = '';
  error = '';

  constructor(private api: ApiService, private chapterList: ChapterService) {}

  /** Active chapters, plus the given one when it is inactive. */
  chapterOptions(currentId: number | null | undefined): Chapter[] {
    return ChapterService.options(this.chapters, currentId);
  }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<ConvenorDashboard>('/convenor/dashboard').subscribe({
      next: (d) => { this.data = d; this.needsChapter = false; },
      error: (e) => {
        const text = errorText(e, 'Could not load the dashboard.');
        if (text.startsWith(CHAPTER_NOT_SET)) {
          this.needsChapter = true;
          this.chapterList.list().subscribe({ next: (c) => (this.chapters = c) });
        } else {
          this.error = text;
        }
      }
    });
  }

  saveChapter(): void {
    this.api.post<{ message: string }>('/convenor/profile', { chapterId: this.chapterId }).subscribe({
      next: (r) => { this.message = r.message; this.load(); },
      error: (e) => (this.error = errorText(e, 'Could not save your chapter.'))
    });
  }
}
