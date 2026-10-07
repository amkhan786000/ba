import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { ChapterService } from '../core/services/chapter.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { Chapter } from './admin-chapters.component';

/** Add / edit a chapter. Renaming a chapter also moves its users to the new name (server side). */
@Component({
  selector: 'app-admin-chapter-edit',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">{{ isEdit ? 'Edit' : 'Add' }} Chapter</h4></div></div></div>
    <div class="row">
      <div class="col-12 col-md-8 offset-md-2">
        <app-alerts [(error)]="error"></app-alerts>
        <div class="card">
          <div class="card-body">
            <form #f="ngForm" (ngSubmit)="save()">
              <div class="form-group">
                <label for="chapter_name">Chapter Name</label>
                <input type="text" class="form-control" id="chapter_name" name="chapterName" maxlength="100" [(ngModel)]="chapter.chapterName" required />
              </div>
              <div class="form-group">
                <label for="description">Description</label>
                <input type="text" class="form-control" id="description" name="description" maxlength="255" [(ngModel)]="chapter.description" />
              </div>
              <div class="form-group">
                <label for="lead_name">Chapter Lead</label>
                <input type="text" class="form-control" id="lead_name" name="leadName" maxlength="100" [(ngModel)]="chapter.leadName" />
              </div>
              <div class="row">
                <div class="col-md-6 form-group">
                  <label for="lead_phone">Lead Phone</label>
                  <input type="tel" class="form-control" id="lead_phone" name="leadPhone" maxlength="30" [(ngModel)]="chapter.leadPhone" />
                </div>
                <div class="col-md-6 form-group">
                  <label for="lead_email">Lead Email</label>
                  <input type="email" class="form-control" id="lead_email" name="leadEmail" maxlength="150" email [(ngModel)]="chapter.leadEmail" />
                </div>
              </div>
              <div class="form-group">
                <div class="custom-control custom-switch">
                  <input type="checkbox" class="custom-control-input" id="active" name="active" [(ngModel)]="chapter.active" />
                  <label class="custom-control-label" for="active">Active</label>
                </div>
                <small class="text-muted">Inactive chapters stay on their users but are no longer offered when choosing a chapter.</small>
              </div>
              <div class="d-flex justify-content-between">
                <a routerLink="/admin/chapters" class="btn btn-light btn-lg">Back</a>
                <button type="submit" class="btn btn-primary btn-lg waves-effect waves-light" [disabled]="f.invalid || saving">
                  {{ isEdit ? 'Update Chapter' : 'Save Chapter' }}
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminChapterEditComponent implements OnInit {
  /** From the route parameter :id (absent on /admin/chapters/new). */
  @Input() id?: string;

  chapter: Chapter = { chapterName: '', description: '', leadName: '', leadPhone: '', leadEmail: '', active: true };
  saving = false;
  error = '';

  constructor(private api: ApiService, private router: Router, private chapterList: ChapterService) {}

  get isEdit(): boolean { return !!this.id; }

  ngOnInit(): void {
    if (!this.id) return;
    this.api.get<Chapter[]>('/admin/chapters').subscribe({
      next: (list) => {
        const found = list.find((c) => String(c.chapterId) === this.id);
        if (found) this.chapter = { ...found };
        else this.error = 'Chapter not found.';
      },
      error: (e) => (this.error = errorText(e, 'Could not load the chapter.'))
    });
  }

  save(): void {
    this.saving = true;
    this.api.post<Chapter>('/admin/chapters', this.chapter).subscribe({
      next: () => { this.chapterList.refresh(); this.router.navigate(['/admin/chapters']); },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save the chapter.'); }
    });
  }
}
