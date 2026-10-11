import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/services/auth.service';
import { WebsiteRecordsComponent } from './website-records.component';

/**
 * Admin > Website > Knowledge Hub: the posts of the website's Media & Knowledge Hub and, for people with
 * "Website: Knowledge Hub categories", its categories.
 */
@Component({
  selector: 'app-website-blog',
  standalone: true,
  imports: [CommonModule, WebsiteRecordsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Knowledge Hub</h4></div></div></div>
    <ul class="nav nav-tabs mb-3" *ngIf="canCategories">
      <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'posts'" (click)="$event.preventDefault(); tab = 'posts'">Posts</a></li>
      <li class="nav-item"><a class="nav-link" href="" [class.active]="tab === 'categories'" (click)="$event.preventDefault(); tab = 'categories'">Categories</a></li>
    </ul>
    <app-website-records *ngIf="tab === 'posts' || !canCategories" resource="blog" [embedded]="true"></app-website-records>
    <app-website-records *ngIf="tab === 'categories' && canCategories" resource="categories" [embedded]="true"></app-website-records>
  `
})
export class WebsiteBlogComponent {
  tab: 'posts' | 'categories' = 'posts';

  constructor(private auth: AuthService) {}

  get canCategories(): boolean { return this.auth.can('WEBSITE_BLOG_CATEGORIES', 'VIEW'); }
}
