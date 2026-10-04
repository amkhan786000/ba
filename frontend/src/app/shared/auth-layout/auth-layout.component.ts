import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

/** Split-screen frame for the sign-in pages: brand story on the left, the form card on the right. */
@Component({
  selector: 'app-auth-layout',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="auth">
      <section class="auth-hero">
        <img src="assets/theme/images/logo_ba1.png" alt="Bihar Anjuman" />
        <div>
          <h1>Guiding every student <span>from application to graduation.</span></h1>
          <p>Rahbar brings students, sponsors, convenors and coordinators together to manage scholarships,
             payments and progress in one place.</p>
          <div class="auth-points">
            <div><i class="mdi mdi-school"></i>Scholarships</div>
            <div><i class="mdi mdi-hand-heart"></i>Sponsorships</div>
            <div><i class="mdi mdi-chart-line"></i>Progress</div>
          </div>
        </div>
        <small>© {{ year }} Bihar Anjuman · Connecting people to serve humanity</small>
      </section>
      <section class="auth-panel">
        <div class="auth-card" [class.auth-card--wide]="wide">
          <div class="auth-mobile-brand"><span class="brand-mark">ba</span>Rahbar</div>
          <h2>{{ heading }}</h2>
          <p class="auth-sub" *ngIf="subheading">{{ subheading }}</p>
          <ng-content></ng-content>
        </div>
      </section>
    </div>
  `
})
export class AuthLayoutComponent {
  @Input() heading = '';
  @Input() subheading = '';
  @Input() wide = false;
  readonly year = new Date().getFullYear();
}
