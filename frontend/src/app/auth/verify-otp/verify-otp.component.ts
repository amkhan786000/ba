import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { landingPath } from '../../core/models/user.model';
import { AuthLayoutComponent } from '../../shared/auth-layout/auth-layout.component';

@Component({
  selector: 'app-verify-otp',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AuthLayoutComponent],
  templateUrl: './verify-otp.component.html'
})
export class VerifyOtpComponent {
  /** users.id from the login step. */
  id = 0;
  otp = '';
  error = '';
  loading = false;

  constructor(private auth: AuthService, private router: Router, route: ActivatedRoute) {
    this.id = Number(route.snapshot.queryParamMap.get('id')) || 0;
  }

  submit(): void {
    this.error = '';
    this.loading = true;
    this.auth.verifyOtp(this.id, this.otp).subscribe({
      next: (res) => {
        this.loading = false;
        this.router.navigate([res.mustChangePassword ? '/change-password' : landingPath(this.auth.currentUser())]);
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.error ?? 'Invalid OTP.';
      }
    });
  }
}
