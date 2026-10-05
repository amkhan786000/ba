import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { errorText } from './alerts/alerts.component';

/** Current / new / confirm password form with live rule checks; used by the forced change and the profile page. */
@Component({
  selector: 'app-password-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div *ngIf="error" class="alert alert-danger">{{ error }}</div>
    <form #f="ngForm" (ngSubmit)="submit()">
      <div class="form-group">
        <label for="currentPassword">{{ currentLabel }}</label>
        <input class="form-control" id="currentPassword" type="password" name="currentPassword" autocomplete="current-password"
               [(ngModel)]="currentPassword" required>
      </div>
      <div class="form-group">
        <label for="newPassword">New password</label>
        <input class="form-control" id="newPassword" type="password" name="newPassword" autocomplete="new-password"
               [(ngModel)]="newPassword" required>
      </div>
      <div class="form-group">
        <label for="confirmPassword">Confirm new password</label>
        <input class="form-control" id="confirmPassword" type="password" name="confirmPassword" autocomplete="new-password"
               [(ngModel)]="confirmPassword" required>
      </div>
      <ul class="pw-rules">
        <li [class.ok]="newPassword.length >= 8"><i class="mdi" [ngClass]="newPassword.length >= 8 ? 'mdi-check-circle' : 'mdi-circle-outline'"></i>At least 8 characters</li>
        <li [class.ok]="hasLetterAndNumber"><i class="mdi" [ngClass]="hasLetterAndNumber ? 'mdi-check-circle' : 'mdi-circle-outline'"></i>A letter and a number</li>
        <li [class.ok]="matches"><i class="mdi" [ngClass]="matches ? 'mdi-check-circle' : 'mdi-circle-outline'"></i>Both new passwords match</li>
      </ul>
      <button class="btn btn-primary" [class.btn-block]="block" type="submit" [disabled]="saving || !valid">
        <span *ngIf="saving" class="spinner-border spinner-border-sm mr-2"></span>{{ submitLabel }}
      </button>
    </form>
  `
})
export class PasswordFormComponent {
  @Input() currentLabel = 'Current password';
  @Input() submitLabel = 'Change password';
  @Input() block = false;
  @Output() changed = new EventEmitter<string>();

  currentPassword = '';
  newPassword = '';
  confirmPassword = '';
  saving = false;
  error = '';

  constructor(private api: ApiService) {}

  get hasLetterAndNumber(): boolean { return /[A-Za-z]/.test(this.newPassword) && /\d/.test(this.newPassword); }
  get matches(): boolean { return !!this.newPassword && this.newPassword === this.confirmPassword; }
  get valid(): boolean { return !!this.currentPassword && this.newPassword.length >= 8 && this.hasLetterAndNumber && this.matches; }

  submit(): void {
    this.error = '';
    this.saving = true;
    this.api.post<{ message: string }>('/account/change-password', {
      currentPassword: this.currentPassword, newPassword: this.newPassword, confirmPassword: this.confirmPassword
    }).subscribe({
      next: (r) => {
        this.saving = false;
        this.currentPassword = this.newPassword = this.confirmPassword = '';
        this.changed.emit(r.message);
      },
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not change the password.'); }
    });
  }
}
