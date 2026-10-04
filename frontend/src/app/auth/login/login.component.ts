import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { dashboardPathForRole } from '../../core/models/user.model';
import { AuthLayoutComponent } from '../../shared/auth-layout/auth-layout.component';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AuthLayoutComponent],
  templateUrl: './login.component.html'
})
export class LoginComponent {
  loginMethod: 'email' | 'phone' = 'email';
  identifier = '';
  password = '';
  error = '';
  loading = false;

  constructor(private auth: AuthService, private router: Router) {}

  submit(): void {
    this.error = '';
    this.loading = true;
    this.auth.login(this.loginMethod, this.identifier, this.password).subscribe({
      next: (res) => {
        this.loading = false;
        if (res.otpRequired) {
          this.router.navigate(['/verify-otp'], { queryParams: { userId: res.userId } });
        } else {
          this.auth.persistSession(res);
          this.router.navigate([dashboardPathForRole(res.roleId ?? 0)]);
        }
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.error ?? 'Login failed. Please check your details.';
      }
    });
  }
}
