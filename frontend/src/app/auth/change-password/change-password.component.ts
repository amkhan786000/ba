import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { landingPath } from '../../core/models/user.model';
import { AuthLayoutComponent } from '../../shared/auth-layout/auth-layout.component';
import { PasswordFormComponent } from '../../shared/password-form.component';

/** Shown right after sign-in while the account still has the password an admin or a bulk upload gave it. */
@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [CommonModule, AuthLayoutComponent, PasswordFormComponent],
  template: `
    <app-auth-layout heading="Choose a new password"
                     subheading="For your security, replace the password you were given with one only you know.">
      <app-password-form currentLabel="Password you were given" submitLabel="Save and continue" [block]="true"
                         (changed)="done()"></app-password-form>
      <div class="auth-links center"><a href="#" (click)="signOut($event)"><i class="mdi mdi-logout-variant mr-1"></i>Sign out</a></div>
    </app-auth-layout>
  `
})
export class ChangePasswordComponent {
  constructor(private auth: AuthService, private router: Router) {}

  done(): void {
    this.auth.updateUser({ mustChangePassword: false });
    this.router.navigate([landingPath(this.auth.currentUser())]);
  }

  signOut(event: Event): void {
    event.preventDefault();
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
