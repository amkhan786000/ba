import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { ChapterService } from '../../core/services/chapter.service';
import { Chapter } from '../../admin/admin-chapters.component';
import { AuthService } from '../../core/services/auth.service';
import { AlertsComponent, errorText } from '../alerts/alerts.component';
import { PasswordFormComponent } from '../password-form.component';
import { localDate } from '../format';
import { PushService } from '../../core/services/push.service';

interface Profile {
  userId: string; name: string; email: string; phone: string; sex: string | null; chapterId: number | null; chapterName: string | null;
  year: number | null; status: string | null; roleId: number; roleName: string | null; memberSince: string | null;
}

/** "My profile" for every role: contact details and password. */
@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, PasswordFormComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">My Profile</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row" *ngIf="p">
      <div class="col-12 col-xl-4">
        <div class="card profile-card">
          <div class="card-body text-center">
            <div class="profile-avatar">{{ initials }}</div>
            <h4 class="mb-1 mt-3">{{ p.name }}</h4>
            <p class="text-muted mb-3">{{ p.roleName || 'User' }}</p>
            <div class="profile-facts">
              <div><span>User ID</span><strong>{{ p.userId }}</strong></div>
              <div><span>Status</span><strong>{{ p.status || '--' }}</strong></div>
              <div *ngIf="p.year"><span>Session year</span><strong>{{ p.year }}</strong></div>
              <div><span>Member since</span><strong>{{ date(p.memberSince) }}</strong></div>
            </div>
          </div>
        </div>
      </div>

      <div class="col-12 col-xl-8">
        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Contact details</h4>
            <form #f="ngForm" (ngSubmit)="save()">
              <div class="row">
                <div class="col-md-6 form-group">
                  <label for="name">Full name</label>
                  <input class="form-control" id="name" name="name" [(ngModel)]="form.name" required>
                </div>
                <div class="col-md-6 form-group">
                  <label for="sex">Gender</label>
                  <select class="form-control" id="sex" name="sex" [(ngModel)]="form.sex">
                    <option value="M">Male</option><option value="F">Female</option>
                  </select>
                </div>
                <div class="col-md-6 form-group">
                  <label for="email">Email</label>
                  <input class="form-control" id="email" type="email" name="email" [(ngModel)]="form.email" required>
                </div>
                <div class="col-md-6 form-group">
                  <label for="phone">Phone</label>
                  <input class="form-control" id="phone" type="tel" name="phone" [(ngModel)]="form.phone" required>
                </div>
                <div class="col-md-6 form-group">
                  <label for="chapter">Chapter</label>
                  <select class="form-control" id="chapter" name="chapterId" [(ngModel)]="form.chapterId">
                    <option [ngValue]="null">No chapter</option>
                    <option *ngFor="let c of chapterOptions(p?.chapterId)" [ngValue]="c.chapterId">{{ c.chapterName }}</option>
                  </select>
                </div>
              </div>
              <button class="btn btn-primary" type="submit" [disabled]="f.invalid || saving">
                <span *ngIf="saving" class="spinner-border spinner-border-sm mr-2"></span>Save changes
              </button>
            </form>
          </div>
        </div>

        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Notifications on this device</h4>
            <p class="text-muted">
              Get your Rahbar notifications (reminders, payments, messages) as pop-ups on this phone or computer, even when Rahbar
              isn't open. It's free and you can switch it off any time.
            </p>
            <ng-container *ngIf="push.supported; else unsupported">
              <div class="d-flex flex-wrap align-items-center">
                <span class="badge mr-2 mb-2" [ngClass]="pushOn ? 'badge-success' : 'badge-light'">{{ pushOn ? 'On for this device' : 'Off for this device' }}</span>
                <button *ngIf="!pushOn" type="button" class="btn btn-primary mr-2 mb-2" (click)="enablePush()" [disabled]="pushBusy">
                  <i class="mdi mdi-bell-ring-outline mr-1"></i>{{ pushBusy ? 'Switching on…' : 'Switch on' }}
                </button>
                <ng-container *ngIf="pushOn">
                  <button type="button" class="btn btn-outline-primary mr-2 mb-2" (click)="testPush()" [disabled]="pushBusy">Send a test</button>
                  <button type="button" class="btn btn-light mb-2" (click)="disablePush()" [disabled]="pushBusy">Switch off</button>
                </ng-container>
              </div>
              <small *ngIf="push.permission === 'denied'" class="text-danger d-block">
                Notifications are blocked for this site. Allow them in your browser's site settings, then switch on again.
              </small>
            </ng-container>
            <ng-template #unsupported>
              <p class="mb-0 text-muted small">
                <ng-container *ngIf="push.needsHomeScreen; else noPush">
                  On iPhone / iPad: tap <strong>Share</strong> &rarr; <strong>Add to Home Screen</strong>, open Rahbar from the home screen and switch
                  notifications on here.
                </ng-container>
                <ng-template #noPush>This browser doesn't support notifications. Try Chrome, Edge, Firefox or Safari.</ng-template>
              </p>
            </ng-template>
          </div>
        </div>

        <div class="card">
          <div class="card-body">
            <h4 class="header-title">Change password</h4>
            <app-password-form (changed)="message = $event"></app-password-form>
          </div>
        </div>
      </div>
    </div>
  `
})
export class ProfileComponent implements OnInit {
  p: Profile | null = null;
  form = { name: '', email: '', phone: '', sex: 'M', chapterId: null as number | null };
  chapters: Chapter[] = [];
  saving = false;
  message = '';
  error = '';
  readonly date = localDate;

  pushOn = false;
  pushBusy = false;

  constructor(private api: ApiService, private auth: AuthService, private chapterList: ChapterService, public push: PushService) {}

  enablePush(): void {
    this.pushBusy = true;
    this.error = '';
    this.push.turnOn()
      .then(() => { this.pushOn = true; this.message = 'Notifications are on for this device.'; })
      .catch((e) => (this.error = e?.message || 'Could not switch notifications on.'))
      .finally(() => (this.pushBusy = false));
  }

  disablePush(): void {
    this.pushBusy = true;
    this.push.turnOff()
      .then(() => { this.pushOn = false; this.message = 'Notifications are off for this device.'; })
      .catch((e) => (this.error = errorText(e, 'Could not switch notifications off.')))
      .finally(() => (this.pushBusy = false));
  }

  testPush(): void {
    this.push.test().subscribe({
      next: (r) => (r.sent ? (this.message = r.message) : (this.error = r.message)),
      error: (e) => (this.error = errorText(e, 'Could not send a test notification.'))
    });
  }

  /** Active chapters, plus the given one when it is inactive. */
  chapterOptions(currentId: number | null | undefined): Chapter[] {
    return ChapterService.options(this.chapters, currentId);
  }

  get initials(): string {
    const parts = (this.p?.name ?? '').trim().split(/\s+/).filter(Boolean);
    return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase() || '?';
  }

  ngOnInit(): void {
    this.push.isOn().then((on) => (this.pushOn = on)).catch(() => {});
    this.chapterList.list().subscribe({ next: (c) => (this.chapters = c) });
    this.api.get<Profile>('/account/profile').subscribe({
      next: (p) => this.fill(p),
      error: (e) => (this.error = errorText(e, 'Could not load your profile.'))
    });
  }

  private fill(p: Profile): void {
    this.p = p;
    this.form = { name: p.name, email: p.email, phone: p.phone, sex: p.sex || 'M', chapterId: p.chapterId ?? null };
  }

  save(): void {
    this.saving = true;
    this.error = '';
    this.api.put<Profile>('/account/profile', this.form).subscribe({
      next: (p) => {
        this.saving = false;
        this.fill(p);
        this.auth.updateUser({ name: p.name });
        this.auth.refreshAccess().subscribe({ error: () => {} }); // e.g. clears the "add your email" banner
        this.message = 'Your profile has been updated.';
      },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save your profile.'); }
    });
  }
}
