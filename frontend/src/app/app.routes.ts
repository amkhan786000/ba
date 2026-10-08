import { Routes } from '@angular/router';
import { LoginComponent } from './auth/login/login.component';
import { VerifyOtpComponent } from './auth/verify-otp/verify-otp.component';
import { RegisterComponent } from './auth/register/register.component';
import { ResetPasswordComponent } from './auth/reset-password/reset-password.component';
import { PublicApplyComponent } from './public/apply.component';
import { ShellComponent } from './shared/layout/shell.component';
import { AdminDashboardComponent } from './admin/admin-dashboard.component';
import { AdminUsersComponent } from './admin/admin-users.component';
import { AdminBroadcastsComponent } from './admin/admin-broadcasts.component';
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
import { AdminActivityLogComponent } from './admin/admin-activity-log.component';
import { AdminPaymentDuesComponent } from './admin/admin-payment-dues.component';
import { AdminProgressDueDatesComponent } from './admin/admin-progress-due-dates.component';
import { AdminChapterDashboardComponent } from './admin/admin-chapter-dashboard.component';
import { AdminDataQualityComponent } from './admin/admin-data-quality.component';
import { AdminPaymentRecordsComponent } from './admin/admin-payment-records.component';
import { AdminEmailLogComponent } from './admin/admin-email-log.component';
import { ChangePasswordComponent } from './auth/change-password/change-password.component';
import { TrackApplicationComponent } from './public/track.component';
import { ProfileComponent } from './shared/profile/profile.component';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { ADMIN_LINKS, ROLE } from './core/models/user.model';
import { adminAreaGuard, adminHomeGuard, permissionGuard } from './core/guards/permission.guard';

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
    canActivate: [adminAreaGuard],
    data: {
      title: 'Admin Panel',
      // Filtered by the user's permissions in the shell (see ADMIN_LINKS).
      links: ADMIN_LINKS
    },
    children: [
      { path: '', pathMatch: 'full', canActivate: [adminHomeGuard], children: [] },
      { path: 'profile', component: ProfileComponent },
      { path: 'dashboard', component: AdminDashboardComponent, canActivate: [permissionGuard], data: { permission: 'DASHBOARD' } },
      { path: 'users', component: AdminUsersComponent, canActivate: [permissionGuard], data: { permission: 'USERS' } },
      { path: 'users/:id/edit', component: AdminUserEditComponent, canActivate: [permissionGuard], data: { permission: 'USERS' } },
      { path: 'roles', component: AdminRolesComponent, canActivate: [permissionGuard], data: { permission: 'ROLES' } },
      { path: 'roles/new', component: AdminRoleEditComponent, canActivate: [permissionGuard], data: { permission: 'ROLES' } },
      { path: 'roles/:id/edit', component: AdminRoleEditComponent, canActivate: [permissionGuard], data: { permission: 'ROLES' } },
      { path: 'system-configuration', component: AdminSystemConfigComponent, canActivate: [permissionGuard], data: { permission: 'PAYMENT_CONFIG' } },
      { path: 'payment-dues', component: AdminPaymentDuesComponent, canActivate: [permissionGuard], data: { permission: 'PAYMENT_DUES' } },
      { path: 'progress-due-dates', component: AdminProgressDueDatesComponent, canActivate: [permissionGuard], data: { permission: 'PROGRESS_DUE_DATES' } },
      { path: 'chapter-dashboard', component: AdminChapterDashboardComponent, canActivate: [permissionGuard], data: { permission: 'CHAPTER_DASHBOARD' } },
      { path: 'data-quality', component: AdminDataQualityComponent, canActivate: [permissionGuard], data: { permission: 'DATA_QUALITY' } },
      { path: 'payment-records', component: AdminPaymentRecordsComponent, canActivate: [permissionGuard], data: { permission: 'PAYMENT_RECORDS' } },
      { path: 'email-log', component: AdminEmailLogComponent, canActivate: [permissionGuard], data: { permission: 'EMAIL_LOG' } },
      { path: 'institutions/:id/edit', component: AdminInstitutionAddComponent, canActivate: [permissionGuard], data: { permission: 'COURSES' } },
      { path: 'activity', component: AdminActivityLogComponent, canActivate: [permissionGuard], data: { permission: 'ACTIVITY' } },
      { path: 'broadcasts', component: AdminBroadcastsComponent, canActivate: [permissionGuard], data: { permission: 'MESSAGES' } },
      { path: 'reports', component: AdminReportsComponent, canActivate: [permissionGuard], data: { permission: 'REPORTS' } },
      { path: 'application-period', component: AdminApplicationPeriodComponent, canActivate: [permissionGuard], data: { permission: 'APPLICATION_PERIOD' } },
      { path: 'rcc-centers', component: AdminRccCentersComponent, canActivate: [permissionGuard], data: { permission: 'RCC_CENTERS' } },
      { path: 'rcc-centers/new', component: AdminRccCenterEditComponent, canActivate: [permissionGuard], data: { permission: 'RCC_CENTERS' } },
      { path: 'rcc-centers/:id/edit', component: AdminRccCenterEditComponent, canActivate: [permissionGuard], data: { permission: 'RCC_CENTERS' } },
      { path: 'chapters', component: AdminChaptersComponent, canActivate: [permissionGuard], data: { permission: 'CHAPTERS' } },
      { path: 'chapters/new', component: AdminChapterEditComponent, canActivate: [permissionGuard], data: { permission: 'CHAPTERS' } },
      { path: 'chapters/:id/edit', component: AdminChapterEditComponent, canActivate: [permissionGuard], data: { permission: 'CHAPTERS' } },
      { path: 'courses', component: AdminCoursesComponent, canActivate: [permissionGuard], data: { permission: 'COURSES' } },
      { path: 'courses/new', component: AdminCourseEditComponent, canActivate: [permissionGuard], data: { permission: 'COURSES' } },
      { path: 'courses/:id/edit', component: AdminCourseEditComponent, canActivate: [permissionGuard], data: { permission: 'COURSES' } },
      { path: 'institutions/new', component: AdminInstitutionAddComponent, canActivate: [permissionGuard], data: { permission: 'COURSES' } },
      { path: 'sponsorships', component: AdminSponsorshipsComponent, canActivate: [permissionGuard], data: { permission: 'SPONSORSHIPS' } },
      { path: 'sponsorships/:id/map', component: AdminSponsorMapComponent, canActivate: [permissionGuard], data: { permission: 'SPONSORSHIPS' } },
      { path: 'students', component: AdminStudentDirectoryComponent, canActivate: [permissionGuard], data: { permission: 'STUDENTS' } },
      // Not in the Flask sidebar either: reached by URL (manage students) or from coordinator/convenor menus (applications)
      { path: 'manage-students', component: AdminManageStudentsComponent, canActivate: [permissionGuard], data: { permission: 'STUDENTS' } },
      { path: 'applications', component: AdminApplicationsComponent, canActivate: [permissionGuard], data: { permission: 'APPLICATIONS' } },
      { path: 'applications/:id', component: AdminApplicationDetailsComponent, canActivate: [permissionGuard], data: { permission: 'APPLICATIONS' } }
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

  // The Office Coordinator now uses the admin area (menu filtered by permissions); old links still work.
  { path: 'office', redirectTo: 'admin', pathMatch: 'full' },
  { path: 'office/:page', redirectTo: 'admin/:page' },

  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: '**', redirectTo: 'login' }
];
