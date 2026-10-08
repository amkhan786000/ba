import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { ChapterService } from '../core/services/chapter.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { PagerComponent, PageState, PaginatePipe } from '../shared/pager/pager.component';
import { CardTableDirective } from '../shared/card-table.directive';

export interface Chapter {
  chapterId?: number;
  chapterName: string;
  description: string | null;
  leadName?: string | null;
  leadPhone?: string | null;
  leadEmail?: string | null;
  /** Inactive chapters are not offered in chapter dropdowns. */
  active?: boolean;
  createdAt?: string | null;
  updatedAt?: string | null;
}

/** Chapters list: the options of the Chapter dropdown on users. */
@Component({
  selector: 'app-admin-chapters',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent, PagerComponent, PaginatePipe, CardTableDirective],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Manage Chapters</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row mb-3">
      <div class="col-12">
        <a *ngIf="canEdit && !scoped" routerLink="/admin/chapters/new" class="btn btn-primary btn-responsive"><i class="mdi mdi-plus mr-1"></i>Add New Chapter</a>
      </div>
    </div>

    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title mb-3">Chapter List</h4>
            <div class="row mb-3">
              <div class="col-12 col-md-4">
                <input type="text" class="form-control" placeholder="Search Chapter, Lead or Description..." [(ngModel)]="search" />
              </div>
            </div>
            <div class="table-responsive">
              <table class="table table-striped table-centered mb-0">
                <thead><tr><th>Chapter Name</th><th>Chapter Lead</th><th>Lead Contact</th><th>Status</th><th>Description</th><th>Actions</th></tr></thead>
                <tbody>
                  <tr *ngIf="!visible.length"><td colspan="6" class="text-center text-muted">No chapters found.</td></tr>
                  <tr *ngFor="let c of visible | paginate: pg.page : pg.size">
                    <td>{{ c.chapterName }}</td>
                    <td>{{ c.leadName || '-' }}</td>
                    <td>
                      <div *ngIf="c.leadPhone">{{ c.leadPhone }}</div>
                      <div *ngIf="c.leadEmail" class="small text-muted">{{ c.leadEmail }}</div>
                      <span *ngIf="!c.leadPhone && !c.leadEmail">-</span>
                    </td>
                    <td><span class="badge" [ngClass]="c.active !== false ? 'badge-success' : 'badge-secondary'">{{ c.active !== false ? 'Active' : 'Inactive' }}</span></td>
                    <td>{{ c.description }}</td>
                    <td>
                      <a *ngIf="canEdit" [routerLink]="['/admin/chapters', c.chapterId, 'edit']" class="btn btn-sm btn-primary waves-effect">Edit</a>
                      <button *ngIf="canEdit && !scoped" type="button" class="btn btn-sm btn-danger waves-effect ml-1" (click)="remove(c)">Delete</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <app-pager [state]="pg" [total]="visible.length"></app-pager>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminChaptersComponent implements OnInit {
  readonly pg = new PageState();
  chapters: Chapter[] = [];
  search = '';
  message = '';
  error = '';

  constructor(private api: ApiService, private chapterList: ChapterService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('CHAPTERS', 'EDIT'); }
  /** Chapter leads look after only their own chapter: no adding or deleting. */
  get scoped(): boolean { return this.auth.currentUser()?.scope === 'CHAPTER'; }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.api.get<Chapter[]>('/admin/chapters').subscribe({
      next: (c) => (this.chapters = this.scoped ? c.filter((x) => x.chapterId === this.auth.currentUser()?.chapterId) : c),
      error: (e) => (this.error = errorText(e, 'Could not load chapters.'))
    });
  }

  get visible(): Chapter[] {
    const f = this.search.toLowerCase();
    return this.chapters.filter((c) =>
      [c.chapterName, c.description, c.leadName, c.leadPhone, c.leadEmail].some((v) => (v ?? '').toLowerCase().includes(f)));
  }

  remove(c: Chapter): void {
    if (!confirm('Are you sure you want to delete this chapter?')) return;
    this.api.delete(`/admin/chapters/${c.chapterId}`).subscribe({
      next: () => { this.chapterList.refresh(); this.message = 'Chapter deleted successfully!'; this.load(); },
      error: (e) => (this.error = errorText(e, 'Could not delete the chapter.'))
    });
  }
}
