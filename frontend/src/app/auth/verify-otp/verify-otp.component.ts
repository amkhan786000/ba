import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { dashboardPathForRole } from '../../core/models/user.model';

@Component({
  selector: 'app-verify-otp',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './verify-otp.component.html'
})
export class VerifyOtpComponent {
  userId = '';
  otp = '';
  error = '';
  loading = false;

  constructor(private auth: AuthService, private router: Router, route: ActivatedRoute) {
    this.userId = route.snapshot.queryParamMap.get('userId') ?? '';
  }

  submit(): void {
    this.error = '';
    this.loading = true;
    this.auth.verifyOtp(this.userId, this.otp).subscribe({
      next: (res) => {
        this.loading = false;
        this.router.navigate([dashboardPathForRole(res.roleId ?? 0)]);
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.error ?? 'Invalid OTP.';
      }
    });
  }
}
