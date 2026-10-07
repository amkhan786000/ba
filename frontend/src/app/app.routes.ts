import { Routes } from '@angular/router';
import { LoginComponent } from './auth/login/login.component';
import { VerifyOtpComponent } from './auth/verify-otp/verify-otp.component';
import { RegisterComponent } from './auth/register/register.component';
import { ResetPasswordComponent } from './auth/reset-password/reset-password.component';
import { PublicApplyComponent } from './public/apply.component';
import { ShellComponent } from './shared/layout/shell.component';
import { AdminDashboardComponent } from './admin/admin-dashboard.component';
import { AdminUsersComponent } from './admin/admin-users.component';
import { AdminUserEditComponent } from './admin/admin-user-edit.component';
import { AdminRolesComponent } from './admin/admin-roles.component';
import { AdminRoleEditComponent } from './admin/admin-role-edit.component';
import { AdminSystemConfigComponent } from './admin/admin-system-config.component';
import { AdminReportsComponent } from './admin/admin-reports.component';
import { AdminApplicationPeriodComponent } from './admin/admin-application-period.component';
import { AdminRccCentersComponent } from './admin/admin-rcc-centers.component';
import { AdminRccCenterEditComponent } from './admin/admin-rcc-center-edit.component';
import { AdminChaptersComponent } from './admin/admin-chapters.component';
import { AdminChapterEditComponent } from './admin/admin-chapter-edit.component';
import { AdminCoursesComponent } from './admin/admin-courses.component';
import { AdminCourseEditComponent } from './admin/admin-course-edit.component';
import { AdminInstitutionAddComponent } from './admin/admin-institution-add.component';
import { AdminSponsorshipsComponent } from './admin/admin-sponsorships.component';
import { AdminSponsorMapComponent } from './admin/admin-sponsor-map.component';
import { AdminStudentDirectoryComponent } from './admin/admin-student-directory.component';
import { AdminManageStudentsComponent } from './admin/admin-manage-students.component';
import { AdminApplicationsComponent } from './admin/admin-applications.component';
import { AdminApplicationDetailsComponent } from './admin/admin-application-details.component';
import { CoordinatorDashboardComponent } from './coordinator/coordinator-dashboard.component';
import { CoordinatorSponsorsComponent } from './coordinator/coordinator-sponsors.component';
import { CoordinatorMapStudentsComponent } from './coordinator/coordinator-map-students.component';
import { CoordinatorAssignStudentsComponent } from './coordinator/coordinator-assign-students.component';
import { CoordinatorPaymentsComponent } from './coordinator/coordinator-payments.component';
import { CoordinatorReportsComponent } from './coordinator/coordinator-reports.component';
import { CoordinatorSponsorsConvenorsComponent } from './coordinator/coordinator-sponsors-convenors.component';
import { ConvenorDashboardComponent } from './convenor/convenor-dashboard.component';
import { ConvenorSponsorsComponent } from './convenor/convenor-sponsors.component';
import { ConvenorProgressComponent } from './convenor/convenor-progress.component';
import { ConvenorPaymentsComponent } from './convenor/convenor-payments.component';
import { SponsorDashboardComponent } from './sponsor/sponsor-dashboard.component';
import { StudentDashboardComponent } from './student/student-dashboard.component';
import { SponsorPaymentsComponent } from './sponsor/sponsor-payments.component';
import { SponsorProgressComponent } from './sponsor/sponsor-progress.component';
import { StudentPaymentsComponent } from './student/student-payments.component';
import { StudentProgressComponent } from './student/student-progress.component';
import { OfficeDashboardComponent } from './office/office-dashboard.component';
import { AdminActivityLogComponent } from './admin/admin-activity-log.component';
import { AdminPaymentDuesComponent } from './admin/admin-payment-dues.component';
import { ChangePasswordComponent } from './auth/change-password/change-password.component';
import { TrackApplicationComponent } from './public/track.component';
import { ProfileComponent } from './shared/profile/profile.component';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { ROLE } from './core/models/user.model';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'verify-otp', component: VerifyOtpComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'reset-password', component: ResetPasswordComponent },
  { path: 'apply', component: PublicApplyComponent },
  { path: 'track-application', component: TrackApplicationComponent },
  { path: 'change-password', component: ChangePasswordComponent, canActivate: [authGuard] },

  {
    path: 'admin',
    component: ShellComponent,
    canActivate: [roleGuard([ROLE.SUPER_ADMIN, ROLE.APP_ADMIN])],
    data: {
      title: 'Admin Panel',
      links: [
        { path: '/admin/dashboard', label: 'Dashboard', icon: 'mdi-view-dashboard' },
        { path: '/admin/users', label: 'Manage Users', icon: 'mdi-account-multiple' },
        { path: '/admin/roles', label: 'Roles', icon: 'mdi-shield-account' },
        { path: '/admin/system-configuration', label: 'Payment Config', icon: 'mdi-settings' },
        { path: '/admin/payment-dues', label: 'Payment Dues', icon: 'mdi-alarm' },
        { path: '/admin/reports', label: 'Reports', icon: 'mdi-chart-bar' },
        { path: '/admin/application-period', label: 'App Period', icon: 'mdi-calendar' },
        { path: '/admin/rcc-centers', label: 'RCC Centers', icon: 'mdi-bank' },
        { path: '/admin/chapters', label: 'Chapters', icon: 'mdi-map-marker-multiple' },
        { path: '/admin/courses', label: 'Courses', icon: 'mdi-book-open' },
        { path: '/admin/sponsorships', label: 'Sponsorships', icon: 'mdi-account-switch' },
        { path: '/admin/students', label: 'Student Directory', icon: 'mdi-account-details' },
        { path: '/admin/activity', label: 'Activity Log', icon: 'mdi-history' }
      ]
    },
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'profile', component: ProfileComponent },
      { path: 'dashboard', component: AdminDashboardComponent },
      { path: 'users', component: AdminUsersComponent },
      { path: 'users/:id/edit', component: AdminUserEditComponent },
      { path: 'roles', component: AdminRolesComponent },
      { path: 'roles/new', component: AdminRoleEditComponent },
      { path: 'roles/:id/edit', component: AdminRoleEditComponent },
      { path: 'system-configuration', component: AdminSystemConfigComponent },
      { path: 'payment-dues', component: AdminPaymentDuesComponent },
      { path: 'activity', component: AdminActivityLogComponent },
      { path: 'reports', component: AdminReportsComponent },
      { path: 'application-period', component: AdminApplicationPeriodComponent },
      { path: 'rcc-centers', component: AdminRccCentersComponent },
      { path: 'rcc-centers/new', component: AdminRccCenterEditComponent },
      { path: 'rcc-centers/:id/edit', component: AdminRccCenterEditComponent },
      { path: 'chapters', component: AdminChaptersComponent },
      { path: 'chapters/new', component: AdminChapterEditComponent },
      { path: 'chapters/:id/edit', component: AdminChapterEditComponent },
      { path: 'courses', component: AdminCoursesComponent },
      { path: 'courses/new', component: AdminCourseEditComponent },
      { path: 'courses/:id/edit', component: AdminCourseEditComponent },
      { path: 'institutions/new', component: AdminInstitutionAddComponent },
      { path: 'sponsorships', component: AdminSponsorshipsComponent },
      { path: 'sponsorships/:id/map', component: AdminSponsorMapComponent },
      { path: 'students', component: AdminStudentDirectoryComponent },
      // Not in the Flask sidebar either: reached by URL (manage students) or from coordinator/convenor menus (applications)
      { path: 'manage-students', component: AdminManageStudentsComponent },
      { path: 'applications', component: AdminApplicationsComponent },
      { path: 'applications/:id', component: AdminApplicationDetailsComponent }
    ]
  },
  {
    path: 'coordinator',
    component: ShellComponent,
    canActivate: [roleGuard([ROLE.COORDINATOR])],
    data: {
      title: 'Coordinator Panel',
      links: [
        { path: '/coordinator/dashboard', label: 'Dashboard', icon: 'mdi-view-dashboard' },
        { path: '/coordinator/applications', label: 'View Applications', icon: 'mdi-file-document' },
        { path: '/coordinator/sponsors', label: 'Manage Sponsors', icon: 'mdi-account-multiple' },
        { path: '/coordinator/assign-students', label: 'Assign Students', icon: 'mdi-account-multiple-plus' },
        { path: '/coordinator/payments', label: 'Monitor Payments', icon: 'mdi-cash-multiple' },
        { path: '/coordinator/reports', label: 'Generate Reports', icon: 'mdi-chart-bar' }
      ]
    },
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'profile', component: ProfileComponent },
      { path: 'dashboard', component: CoordinatorDashboardComponent },
      { path: 'applications', component: AdminApplicationsComponent, data: { section: 'coordinator' } },
      { path: 'applications/:id', component: AdminApplicationDetailsComponent, data: { section: 'coordinator' } },
      { path: 'sponsors', component: CoordinatorSponsorsComponent },
      { path: 'sponsors/:sponsorId/map', component: CoordinatorMapStudentsComponent },
      { path: 'assign-students', component: CoordinatorAssignStudentsComponent },
      { path: 'payments', component: CoordinatorPaymentsComponent },
      { path: 'reports', component: CoordinatorReportsComponent },
      // Not in the Flask menu: reachable by URL
      { path: 'sponsors-convenors', component: CoordinatorSponsorsConvenorsComponent }
    ]
  },
  {
    path: 'convenor',
    component: ShellComponent,
    canActivate: [roleGuard([ROLE.CONVENOR])],
    data: {
      title: 'Convenor Panel',
      links: [
        { path: '/convenor/dashboard', label: 'Dashboard', icon: 'mdi-view-dashboard' },
        { path: '/convenor/applications', label: 'View Applications', icon: 'mdi-file-document' },
        { path: '/convenor/sponsors', label: 'Manage Sponsors', icon: 'mdi-account-multiple' },
        { path: '/convenor/progress', label: 'Student Progress', icon: 'mdi-chart-line' },
        { path: '/convenor/payments', label: 'Payments', icon: 'mdi-account-multiple' }
      ]
    },
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'profile', component: ProfileComponent },
      { path: 'dashboard', component: ConvenorDashboardComponent },
      { path: 'applications', component: AdminApplicationsComponent, data: { section: 'convenor' } },
      { path: 'applications/:id', component: AdminApplicationDetailsComponent, data: { section: 'convenor' } },
      { path: 'sponsors', component: ConvenorSponsorsComponent },
      { path: 'progress', component: ConvenorProgressComponent },
      { path: 'payments', component: ConvenorPaymentsComponent }
    ]
  },
  {
    path: 'sponsor',
    component: ShellComponent,
    canActivate: [roleGuard([ROLE.SPONSOR])],
    data: {
      title: 'Sponsor Panel',
      links: [
        { path: '/sponsor/dashboard', label: 'Dashboard', icon: 'mdi-view-dashboard' },
        { path: '/sponsor/payments', label: 'Payments', icon: 'mdi-cash-multiple' },
        { path: '/sponsor/progress', label: 'Student Progress', icon: 'mdi-chart-line' }
      ]
    },
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'profile', component: ProfileComponent },
      { path: 'dashboard', component: SponsorDashboardComponent },
      { path: 'payments', component: SponsorPaymentsComponent },
      { path: 'progress', component: SponsorProgressComponent }
    ]
  },
  {
    path: 'student',
    component: ShellComponent,
    canActivate: [roleGuard([ROLE.STUDENT])],
    data: {
      title: 'Student Panel',
      links: [
        { path: '/student/dashboard', label: 'Dashboard', icon: 'mdi-view-dashboard' },
        { path: '/student/payments', label: 'Payments', icon: 'mdi-cash-multiple' },
        { path: '/student/progress', label: 'Progress', icon: 'mdi-chart-line' }
      ]
    },
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'profile', component: ProfileComponent },
      { path: 'dashboard', component: StudentDashboardComponent },
      { path: 'payments', component: StudentPaymentsComponent },
      { path: 'progress', component: StudentProgressComponent }
    ]
  },

  {
    path: 'office',
    component: ShellComponent,
    canActivate: [roleGuard([ROLE.OFFICE_COORDINATOR])],
    data: {
      title: 'Office Coordinator Panel',
      links: [
        { path: '/office/dashboard', label: 'Dashboard', icon: 'mdi-view-dashboard' },
        { path: '/office/system-configuration', label: 'Payment Config', icon: 'mdi-settings' },
        { path: '/office/rcc-centers', label: 'RCC Centers', icon: 'mdi-bank' },
        { path: '/office/courses', label: 'Courses', icon: 'mdi-book-open' },
        { path: '/office/sponsorships', label: 'Sponsors', icon: 'mdi-account-switch' },
        { path: '/office/students', label: 'Student Directory', icon: 'mdi-account-details' }
      ]
    },
    // Reuses the admin pages; section: 'office' keeps their links inside /office,
    // limited: true hides sponsor contact info, profile editing and bulk upload.
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'profile', component: ProfileComponent },
      { path: 'dashboard', component: OfficeDashboardComponent },
      { path: 'system-configuration', component: AdminSystemConfigComponent },
      { path: 'rcc-centers', component: AdminRccCentersComponent, data: { section: 'office' } },
      { path: 'rcc-centers/new', component: AdminRccCenterEditComponent, data: { section: 'office' } },
      { path: 'rcc-centers/:id/edit', component: AdminRccCenterEditComponent, data: { section: 'office' } },
      { path: 'courses', component: AdminCoursesComponent, data: { section: 'office' } },
      { path: 'courses/new', component: AdminCourseEditComponent, data: { section: 'office' } },
      { path: 'courses/:id/edit', component: AdminCourseEditComponent, data: { section: 'office' } },
      { path: 'institutions/new', component: AdminInstitutionAddComponent, data: { section: 'office' } },
      { path: 'sponsorships', component: AdminSponsorshipsComponent, data: { section: 'office', limited: true } },
      { path: 'sponsorships/:id/map', component: AdminSponsorMapComponent, data: { section: 'office' } },
      { path: 'students', component: AdminStudentDirectoryComponent }
    ]
  },

  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: '**', redirectTo: 'login' }
];
