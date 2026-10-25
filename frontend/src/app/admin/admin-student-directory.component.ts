import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, Subscription, debounceTime } from 'rxjs';
import { FEATURES } from '../core/models/features';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { BulkReport, BulkReportComponent } from '../shared/bulk-report.component';
import { PagerComponent } from '../shared/pager/pager.component';
import { asDate, isoDate, localDate, uploadUrl } from '../shared/format';
import { CourseRow } from './admin-courses.component';
import { Institution } from './admin-course-edit.component';
import { Chapter } from './admin-chapters.component';
import { RccCenter } from './admin-rcc-centers.component';
import { ChapterService } from '../core/services/chapter.service';
import { studyStatusBadge } from '../shared/study-status';
import { StudyStatusService } from '../core/services/study-status.service';
import { InstallmentRow, installmentBadge, takesPayments } from '../shared/installments';
import { CardTableDirective } from '../shared/card-table.directive';
import { DateInputComponent } from '../shared/date-input.component';

interface StudentRow {
  id: number; user_id: string; name: string; email: string | null; phone: string | null;
  sponsor_id: number | null; sponsor_code: string | null; sponsor_name: string | null; status: string | null;
  study_status: string | null;
  /** Delete (Super Admin): allowed only with nothing but the user and their application / family details. */
  can_delete?: boolean; delete_blockers?: string[];
}

type Row = Record<string, any>;

interface StudentDetails {
  profile: Row;
  bank: Row | null;
  course: Row | null;
  sponsor: Row | null;
  payments: Row[];
  documents: Row[];
  statusHistory?: Row[];
  /** Every sponsor the student has had, newest first (sponsor_name / sponsor_code, started_at, ended_at, end_reason). */
  sponsorHistory?: Row[];
  /** Books & materials handed out to the student. */
  materials?: Row[];
  /** The current sponsorship on behalf of an anonymous sponsor: on_behalf, donor_chapter_id, donor_label, on_behalf_text. */
  onBehalf?: Row;
}


/** A sponsor, convenor or coordinator a student can be mapped to (from /admin/sponsorships). */
interface SponsorOption { id: number; user_id: string; name: string; care_of?: string | null }

/** BA (Rahbar) details of a student: RCC center, sponsor and chapter - all optional. */
interface BaDetails {
  rccName: string; sponsorId: number | null; chapterId: number | null; collegeType: string; selfFinanced: boolean; sponsorChangeReason?: string;
  onBehalf?: boolean; donorChapterId?: number | null; donorLabel?: string;
}

/** Port of templates/admin/student_directory.html (server-side table, profile modal with 5 tabs, payments, CSV upload, manual registration). */
@Component({
  selector: 'app-admin-student-directory',
  standalone: true,
  imports: [DateInputComponent, CommonModule, FormsModule, AlertsComponent, PagerComponent, BulkReportComponent, CardTableDirective],
  styles: [`
    .nav-pills .nav-link { cursor: pointer; }
    .table-schedule td, .table-schedule th { vertical-align: middle; }
    .btn-xs { padding: .15rem .45rem; font-size: .75rem; }
  `],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Student Directory</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="row">
      <div class="col-12">
        <div class="card">
          <div class="card-body">
            <div class="mb-3">
              <button *ngIf="canEdit && bulkUpload" type="button" class="btn btn-success btn-responsive mr-md-2" (click)="showUpload = true"><i class="mdi mdi-file-upload"></i> Upload CSV</button>
              <button *ngIf="canEdit" type="button" class="btn btn-primary btn-responsive" (click)="openManual()"><i class="mdi mdi-account-plus"></i> Register Student</button>
            </div>

            <div class="row dt-toolbar mb-2">
              <div class="col-sm-6">
                <label>Show
                  <select class="form-control form-control-sm" [(ngModel)]="pageSize" (ngModelChange)="page = 1; load()">
                    <option [ngValue]="10">10</option><option [ngValue]="25">25</option><option [ngValue]="50">50</option><option [ngValue]="100">100</option>
                  </select> entries</label>
              </div>
              <div class="col-sm-6 text-sm-right">
                <label>Search:<input type="search" class="form-control form-control-sm" [(ngModel)]="search" (ngModelChange)="search$.next($event)"
                  placeholder="Name, ID, phone, chapter, course, status…" title="Every word must match: name, Ref ID, email, phone, sponsor, chapter, institution, course, study status or status"></label>
              </div>
            </div>

            <div class="row dt-filters mb-2">
              <div class="col-6 col-md-2 mb-2">
                <select class="form-control form-control-sm" [(ngModel)]="fChapter" (ngModelChange)="refilter()" aria-label="Chapter">
                  <option [ngValue]="null">All chapters</option>
                  <option *ngFor="let c of chapters" [ngValue]="c.chapterId">{{ c.chapterName }}</option>
                </select>
              </div>
              <div class="col-6 col-md-2 mb-2">
                <select class="form-control form-control-sm" [(ngModel)]="fStudy" (ngModelChange)="refilter()" aria-label="Study status">
                  <option value="">Any study status</option>
                  <option *ngFor="let st of studyStatuses" [value]="st.code">{{ st.label }}</option>
                </select>
              </div>
              <div class="col-6 col-md-2 mb-2">
                <select class="form-control form-control-sm" [(ngModel)]="fStatus" (ngModelChange)="refilter()" aria-label="Status">
                  <option value="">Active or inactive</option><option value="Active">Active</option><option value="Inactive">Inactive</option>
                </select>
              </div>
              <div class="col-6 col-md-2 mb-2">
                <select class="form-control form-control-sm" [(ngModel)]="fSponsored" (ngModelChange)="refilter()" aria-label="Sponsor">
                  <option value="">With or without sponsor</option><option value="yes">Has a sponsor</option><option value="no">No sponsor</option>
                </select>
              </div>
              <div class="col-6 col-md-2 mb-2">
                <select class="form-control form-control-sm" [(ngModel)]="fInst" (ngModelChange)="fCourse = null; refilter()" aria-label="Institution">
                  <option value="">All institutions</option>
                  <option *ngFor="let i of institutions" [value]="i.institutionId">{{ i.institutionName }}</option>
                </select>
              </div>
              <div class="col-6 col-md-2 mb-2">
                <select class="form-control form-control-sm" [(ngModel)]="fCourse" (ngModelChange)="refilter()" [disabled]="!fInst" aria-label="Course">
                  <option [ngValue]="null">{{ fInst ? 'All courses' : 'Choose an institution first' }}</option>
                  <option *ngFor="let c of coursesFor(fInst)" [ngValue]="c.course_id">{{ c.course_name }}</option>
                </select>
              </div>
              <div class="col-6 col-md-2 mb-2">
                <select class="form-control form-control-sm" [(ngModel)]="fYear" (ngModelChange)="refilter()" aria-label="Session year">
                  <option value="">All session years</option>
                  <option *ngFor="let y of years" [value]="'' + y">Session {{ y }}</option>
                  <option value="none">Session year not set</option>
                </select>
              </div>
              <div class="col-12 small" *ngIf="filtering"><a href="" (click)="$event.preventDefault(); clearFilters()">Clear search and filters</a></div>
            </div>

            <div class="table-responsive">
              <table class="table table-bordered table-striped nowrap" style="width:100%">
                <thead>
                  <tr><th>Ref_Id</th><th>Name</th><th class="tablet-hide">Email</th><th>Phone</th><th class="tablet-hide">Sponsor ID</th><th>Sponsor Name</th><th>Study</th><th>Status</th><th>Actions</th></tr>
                </thead>
                <tbody>
                  <tr *ngIf="loading"><td colspan="9" class="text-center text-muted">Processing...</td></tr>
                  <tr *ngIf="!loading && !rows.length"><td colspan="9" class="text-center text-muted">No matching records found</td></tr>
                  <tr *ngFor="let r of rows">
                    <td>{{ r.user_id }}</td><td>{{ r.name }}</td><td class="tablet-hide">{{ r.email }}</td><td>{{ r.phone }}</td>
                    <td class="tablet-hide">{{ r.sponsor_code || '-' }}</td><td>{{ r.sponsor_name || 'Unassigned' }}</td>
                    <td><span class="badge" [ngClass]="studyBadge(r.study_status)">{{ studyLabel(r.study_status) }}</span></td>
                    <td><span class="badge" [ngClass]="(r.status || '').toLowerCase() === 'active' ? 'badge-success' : 'badge-danger'">{{ r.status }}</span></td>
                    <td class="text-nowrap"><button class="btn btn-xs btn-primary" (click)="openDetails(r.id)">View Details</button>
                      <span class="d-inline-block ml-1" [title]="deleteTitle(r)">
                        <button class="btn btn-xs btn-outline-danger" (click)="deleteStudent(r)" [disabled]="!r.can_delete || deleting"
                                [style.pointer-events]="r.can_delete ? null : 'none'" aria-label="Delete student"><i class="mdi mdi-delete"></i></button>
                      </span></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <app-pager [total]="filteredTotal" [page]="page" [pageSize]="pageSize" (pageChange)="page = $event; load()"></app-pager>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== Student profile modal ===== -->
    <div *ngIf="details" class="modal fade show d-block" tabindex="-1" (click)="closeDetails()">
      <div class="modal-dialog modal-xl modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header">
            <h4 class="modal-title">
              Student Profile: {{ details.profile['name'] }}
              <span class="badge ml-2 font-13" [ngClass]="studyBadge(details.profile['study_status'])">{{ studyLabel(details.profile['study_status']) }}</span>
            </h4>
            <button type="button" class="close" (click)="closeDetails()">&times;</button>
          </div>
          <div class="modal-body">
            <!-- Phones: the tabs as a dropdown (eight tabs take a third of the screen otherwise). -->
            <select class="form-control mb-3 d-sm-none" [(ngModel)]="tab" aria-label="Section">
              <option *ngFor="let t of visibleTabs" [value]="t.id">{{ t.label }}</option>
            </select>
            <ul class="nav nav-pills nav-fill mb-3 d-none d-sm-flex">
              <li class="nav-item" *ngFor="let t of visibleTabs"><a class="nav-link" [class.active]="tab === t.id" (click)="tab = t.id">{{ t.label }}</a></li>
            </ul>

            <!-- Personal -->
            <div *ngIf="tab === 'profile'">
              <div class="row">
                <div class="col-md-6 form-group"><label>Full Name</label><input type="text" class="form-control" [(ngModel)]="edit.name" [disabled]="!editing"></div>
                <div class="col-md-6 form-group"><label>Reference ID</label><input type="text" class="form-control" [(ngModel)]="edit.refId" [disabled]="!editing" placeholder="Not assigned yet"></div>
                <div class="col-md-6 form-group"><label>Email</label><input type="email" class="form-control" [(ngModel)]="edit.email" [disabled]="!editing"></div>
                <div class="col-md-6 form-group"><label>Phone</label><input type="text" class="form-control" [(ngModel)]="edit.phone" [disabled]="!editing"></div>
              </div>
            </div>

            <!-- Family -->
            <div *ngIf="tab === 'family'" class="row">
              <div class="col-md-6 form-group"><label>Father Name</label><input type="text" class="form-control" [(ngModel)]="edit.fatherName" [disabled]="!editing"></div>
              <div class="col-md-6 form-group"><label>Mother Name</label><input type="text" class="form-control" [(ngModel)]="edit.motherName" [disabled]="!editing"></div>
              <div class="col-md-6 form-group"><label>Father Profession</label><input type="text" class="form-control" [(ngModel)]="edit.fatherProfession" [disabled]="!editing"></div>
              <div class="col-md-6 form-group"><label>Mother Profession</label><input type="text" class="form-control" [(ngModel)]="edit.motherProfession" [disabled]="!editing"></div>
              <div class="col-md-6 form-group"><label>Father Mobile</label><input type="text" class="form-control" [(ngModel)]="edit.fatherMobile" [disabled]="!editing"></div>
              <div class="col-md-6 form-group"><label>Mother Mobile</label><input type="text" class="form-control" [(ngModel)]="edit.motherMobile" [disabled]="!editing"></div>
              <div class="col-md-6 form-group"><label>Average Annual Family Income</label><input type="number" min="0" class="form-control" [(ngModel)]="edit.averageAnnualSalary" [disabled]="!editing"></div>
              <div class="col-12 form-group"><label>Address</label><textarea class="form-control" rows="2" [(ngModel)]="edit.address" [disabled]="!editing"></textarea></div>
            </div>

            <!-- Academic -->
            <div *ngIf="tab === 'course'" class="row">
              <div class="col-md-6 form-group"><label>College</label>
                <select class="form-control" [(ngModel)]="edit.institutionId" (ngModelChange)="edit.courseId = ''" [disabled]="!editing">
                  <option value="">Not Assigned</option>
                  <option *ngFor="let i of institutions" [value]="i.institutionId">{{ i.institutionName }}</option>
                </select>
              </div>
              <div class="col-md-6 form-group"><label>Course</label>
                <select class="form-control" [(ngModel)]="edit.courseId" [disabled]="!editing">
                  <option value="">{{ edit.institutionId ? 'Select Course' : 'Select College First' }}</option>
                  <option *ngFor="let c of coursesFor(edit.institutionId)" [value]="'' + c.course_id">{{ c.course_name }}</option>
                </select>
              </div>
              <div class="col-md-6 form-group"><label>Session Year</label>
                <input type="number" min="1990" max="2100" class="form-control" [(ngModel)]="edit.year" [disabled]="!editing" placeholder="e.g. 2025">
                <small class="text-muted">With the college type (BA Details), picks the Payment Config (amount and frequency).</small>
              </div>
              <div class="col-md-6 form-group"><label>Payment Start Date</label>
                <app-date-input [(ngModel)]="edit.paymentStartDate" [disabled]="!editing"></app-date-input>
                <small class="text-muted">The first installment is due on this date.</small>
              </div>
              <div class="col-12 text-muted small">Assigned on: {{ fmt(details.course?.['assigned_at'], 'N/A') }}</div>
            </div>

            <!-- BA Details -->
            <div *ngIf="tab === 'ba'" class="row">
              <div class="col-md-4 form-group"><label>RCC Center</label>
                <select class="form-control" [(ngModel)]="ba.rccName" [disabled]="!editing">
                  <option value="">No RCC center</option>
                  <option *ngFor="let r of rccOptions(ba.rccName)" [value]="r">{{ r }}</option>
                </select>
              </div>
              <div class="col-md-4 form-group"><label>Sponsor</label>
                <select class="form-control" [(ngModel)]="ba.sponsorId" [disabled]="!editing || !canMap">
                  <option [ngValue]="null">No sponsor</option>
                  <option *ngFor="let sp of sponsorOptions()" [ngValue]="sp.id">{{ sp.name }} ({{ sp.user_id }}){{ sp.care_of ? ' c/o ' + sp.care_of : '' }}</option>
                </select>
                <small *ngIf="details.sponsor?.['email']" class="text-muted">Current sponsor's email: {{ details.sponsor?.['email'] }}</small>
                <small *ngIf="editing && !canMap" class="text-muted d-block">Changing the sponsor needs the Sponsorships (manage) permission.</small>
                <small *ngIf="!editing && details.onBehalf?.['on_behalf']" class="text-info d-block">{{ details.onBehalf?.['on_behalf_text'] }}</small>
                <div *ngIf="editing && canMap && ba.sponsorId" class="mt-2">
                  <div class="custom-control custom-checkbox">
                    <input type="checkbox" class="custom-control-input" id="baOnBehalf" [(ngModel)]="ba.onBehalf">
                    <label class="custom-control-label" for="baOnBehalf">On behalf of an anonymous sponsor</label>
                  </div>
                  <ng-container *ngIf="ba.onBehalf">
                    <select class="form-control form-control-sm mt-1" [(ngModel)]="ba.donorChapterId" aria-label="Anonymous sponsor's chapter">
                      <option [ngValue]="null">-- Their chapter --</option>
                      <option *ngFor="let c of chapterOptions(ba.donorChapterId ?? null)" [ngValue]="c.chapterId">{{ c.chapterName }}</option>
                    </select>
                    <input class="form-control form-control-sm mt-1" maxlength="80" [(ngModel)]="ba.donorLabel" placeholder="Label, e.g. Donor A (never a name)">
                  </ng-container>
                </div>
              </div>
              <div class="col-md-4 form-group"><label>Chapter</label>
                <select class="form-control" [(ngModel)]="ba.chapterId" [disabled]="!editing">
                  <option [ngValue]="null">No chapter</option>
                  <option *ngFor="let c of chapterOptions(details.profile['chapter_id'])" [ngValue]="c.chapterId">{{ c.chapterName }}</option>
                </select>
              </div>
              <div class="col-md-4 form-group"><label>College Type</label>
                <select class="form-control" [(ngModel)]="ba.collegeType" [disabled]="!editing">
                  <option value="">Not set</option>
                  <option value="GOVT">Govt</option>
                  <option value="PRIVATE">Private</option>
                  <option value="ANY">Doesn't matter</option>
                </select>
                <small class="text-muted">With the session year, picks the Payment Config (Govt or Private college) for the installments.</small>
              </div>
              <div class="col-md-4 form-group"><label>Self financed</label>
                <select class="form-control" [(ngModel)]="ba.selfFinanced" [disabled]="!editing">
                  <option [ngValue]="false">No</option>
                  <option [ngValue]="true">Yes</option>
                </select>
                <small class="text-muted">Yes: the student pays for their own studies.</small>
              </div>
              <div class="col-12 form-group" *ngIf="editing && sponsorChanged">
                <label>Why is the sponsor changing? <span class="text-muted small">(kept in the sponsor history)</span></label>
                <input class="form-control" maxlength="300" [(ngModel)]="ba.sponsorChangeReason"
                       placeholder="e.g. Sponsor declined to continue">
              </div>
              <div class="col-12" *ngIf="details.materials?.length">
                <h6 class="mt-2">Books & materials received</h6>
                <table class="table table-sm mb-2"><tbody>
                  <tr *ngFor="let m of details.materials"><td>{{ fmt(m['given_on'], '') }}</td><td>{{ m['item'] }}</td><td>{{ m['quantity'] }}</td><td class="small">{{ m['note'] }}</td></tr>
                </tbody></table>
              </div>
              <div class="col-12">
                <h6 class="mt-2">Sponsor history</h6>
                <div class="table-responsive">
                  <table class="table table-sm mb-0">
                    <thead class="thead-light"><tr><th>Sponsor</th><th>From</th><th>Until</th><th>Why it ended</th></tr></thead>
                    <tbody>
                      <tr *ngIf="!details.sponsorHistory?.length"><td colspan="4" class="text-center text-muted">No sponsor yet.</td></tr>
                      <tr *ngFor="let h of details.sponsorHistory">
                        <td>{{ h['sponsor_name'] }} <small class="text-muted">{{ h['sponsor_code'] }}</small>
                          <div *ngIf="h['on_behalf_text']" class="small text-info">{{ h['on_behalf_text'] }}</div></td>
                        <td>{{ fmt(h['started_at']) }}</td>
                        <td>{{ h['ended_at'] ? fmt(h['ended_at']) : '' }}<span *ngIf="!h['ended_at']" class="badge badge-success">Current</span></td>
                        <td>{{ h['end_reason'] || (h['ended_at'] ? '--' : '') }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>

            <!-- Study status (separate from the account's Active / Inactive) -->
            <div *ngIf="tab === 'study'">
              <p class="text-muted small">
                Graduated and Dropped-out students no longer get payment dues or reminders (a graduate's unpaid installments
                show as "Not needed"); students on hold keep their sponsor but get no reminders. Graduated is final: it
                can't be changed afterwards. This is separate from the login account's Active / Inactive.
              </p>
              <p>
                Current: <span class="badge" [ngClass]="studyBadge(details.profile['study_status'])">{{ studyLabel(details.profile['study_status']) }}</span>
                <span *ngIf="details.profile['study_status_date']" class="text-muted"> since {{ fmt(details.profile['study_status_date'], '') }}</span>
                <span *ngIf="details.profile['study_status_note']" class="text-muted"> · {{ details.profile['study_status_note'] }}</span>
              </p>
              <div *ngIf="canEdit && graduated" class="alert alert-light border small py-2">
                <i class="mdi mdi-lock-outline"></i> Graduated is final, so the study status can't be changed.</div>
              <div class="row" *ngIf="canEdit && !graduated">
                <div class="col-md-3 form-group"><label>New status</label>
                  <select class="form-control" [(ngModel)]="study.status">
                    <option *ngFor="let st of studyStatuses" [value]="st.code">{{ st.label }}</option>
                  </select>
                </div>
                <div class="col-md-3 form-group"><label>Effective date</label><app-date-input [(ngModel)]="study.date"></app-date-input></div>
                <div class="col-md-4 form-group"><label>Note <span class="text-muted">(optional)</span></label>
                  <input type="text" class="form-control" maxlength="500" [(ngModel)]="study.note" placeholder="e.g. Completed B.Tech">
                </div>
                <div class="col-md-2 form-group d-flex align-items-end">
                  <button type="button" class="btn btn-primary btn-block" (click)="saveStudyStatus()" [disabled]="busy || !study.status">Update</button>
                </div>
              </div>
              <h5 class="mt-2">History</h5>
              <div class="table-responsive">
                <table class="table table-sm mb-0">
                  <thead><tr><th>Effective</th><th>Status</th><th>Note</th><th>Recorded</th></tr></thead>
                  <tbody>
                    <tr *ngIf="!details.statusHistory?.length"><td colspan="4" class="text-center text-muted">No changes recorded yet.</td></tr>
                    <tr *ngFor="let h of details.statusHistory">
                      <td>{{ fmt(h['effective_date'], '--') }}</td>
                      <td><span class="badge" [ngClass]="studyBadge(h['status'])">{{ studyLabel(h['status']) }}</span></td>
                      <td>{{ h['note'] || '' }}</td>
                      <td class="small text-muted">{{ fmt(h['created_at'], '') }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- Bank -->
            <div *ngIf="tab === 'bank'" class="row">
              <div class="col-md-6 form-group"><label>A/C Holder Name</label><input type="text" class="form-control" [(ngModel)]="edit.accountName" [disabled]="!editing"></div>
              <div class="col-md-6 form-group"><label>Bank Name</label><input type="text" class="form-control" [(ngModel)]="edit.bankName" [disabled]="!editing"></div>
              <div class="col-md-6 form-group"><label>Account Number</label><input type="text" class="form-control" [(ngModel)]="edit.accountNumber" [disabled]="!editing"></div>
              <div class="col-md-6 form-group"><label>IFSC Code</label><input type="text" class="form-control" [(ngModel)]="edit.ifscCode" [disabled]="!editing"></div>
            </div>

            <!-- Payment schedule -->
            <div *ngIf="tab === 'payment'">
              <h5 class="text-primary mb-3">Installment Tracker</h5>
              <p class="text-muted small">
                Created when the student is mapped to a sponsor, from the course's semesters, the Payment Config of the session year
                ({{ details.profile['session_year'] || 'not set' }}) and the payment start date ({{ fmt(details.profile['payment_start_date'], 'not set') }}).
              </p>
              <div *ngIf="installmentProblem" class="alert alert-warning py-2">
                <i class="mdi mdi-information-outline mr-1"></i>{{ installments.length ? 'No further installments: ' : 'No installments yet: ' }}{{ installmentProblem }}
              </div>
              <div class="table-responsive" *ngIf="installments.length">
                <table class="table table-bordered table-schedule text-center">
                  <thead><tr><th>Installment</th><th>Due date</th><th>Status</th><th>Sponsor</th><th>Paid on</th><th>Receipt</th><th>Spent</th><th *ngIf="canPay">Action</th></tr></thead>
                  <tbody>
                    <tr *ngFor="let i of installments">
                      <td class="font-weight-bold">Installment {{ i.installment_no }}</td>
                      <td>{{ fmt(i.due_date) }}</td>
                      <td><span class="badge" [ngClass]="installmentBadge(i.status)">{{ i.status }}</span></td>
                      <td><small>{{ sponsorLabel(i.sponsor_id) }}</small></td>
                      <td>{{ i.paid_date ? fmt(i.paid_date) : '--' }}</td>
                      <td><a *ngIf="link(i.receipt_url) as l; else dash" [href]="l" target="_blank">View</a></td>
                      <td><a *ngIf="link(i.student_proof_url) as l; else dash" [href]="l" target="_blank">View</a></td>
                      <td *ngIf="canPay">
                        <button *ngIf="i.payment_id" class="btn btn-xs btn-warning" (click)="openPayment(i)">Edit</button>
                        <button *ngIf="!i.payment_id && takesPayments(details.profile['study_status'])" class="btn btn-xs btn-success" (click)="openPayment(i)">Pay Now</button>
                        <span *ngIf="!i.payment_id && !takesPayments(details.profile['study_status'])" class="badge badge-light border" title="Payments are only for students who are studying">Payments closed</span>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <ng-template #dash>--</ng-template>
            </div>

            <!-- Docs -->
            <div *ngIf="tab === 'docs'" class="table-responsive">
              <table class="table table-sm table-striped">
                <thead><tr><th>Date</th><th>Session</th><th>Marks</th><th>Action</th></tr></thead>
                <tbody>
                  <tr *ngIf="!details.documents.length"><td colspan="4" class="text-center">No documents.</td></tr>
                  <tr *ngFor="let d of details.documents">
                    <td>{{ fmt(d['created_at']) }}</td>
                    <td>{{ d['session'] }} ({{ d['year'] }})</td>
                    <td>{{ d['marks'] }}</td>
                    <td><a [href]="file(d['file_path'])" target="_blank" class="btn btn-xs btn-outline-info">View</a></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
          <div class="modal-footer justify-content-between flex-column flex-md-row">
            <div class="mb-2 mb-md-0">
              <button *ngIf="canEdit && isActive" type="button" class="btn btn-warning" (click)="action('deactivate')">Deactivate</button>
              <button *ngIf="canEdit && !isActive" type="button" class="btn btn-success" (click)="action('activate')">Activate</button>
            </div>
            <div>
              <button type="button" class="btn btn-secondary" (click)="closeDetails()">Close</button>
              <button *ngIf="canEdit && !editing" type="button" class="btn btn-info ml-1" (click)="editing = true">Edit Details</button>
              <button *ngIf="editing" type="button" class="btn btn-success ml-1" (click)="saveProfile()" [disabled]="busy">Save Profile</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== Payment modal ===== -->
    <div *ngIf="pay" class="modal fade show d-block" tabindex="-1" style="z-index: 1065;" (click)="pay = null">
      <div class="modal-dialog modal-dialog-centered" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header bg-success text-white"><h5 class="text-white m-0">Process Payment</h5><button type="button" class="close text-white" (click)="pay = null">&times;</button></div>
          <form #pf="ngForm" (ngSubmit)="savePayment()">
            <div class="modal-body">
              <div class="form-group"><label>Amount paid (INR)</label><input type="number" step="0.01" min="1" class="form-control" name="amount" [(ngModel)]="pay.amount" [required]="pay.actionType === 'create'">
                <small class="text-muted">{{ pay.actionType === 'edit' ? 'Leave empty to keep the amount saved with this payment. ' : 'As on the bank receipt. ' }}Rahbar keeps it; pages show the installment, not the amount.</small></div>
              <div class="form-group"><label>Date</label><app-date-input name="paymentDate" [(ngModel)]="pay.paymentDate" [required]="true"></app-date-input></div>
              <div class="form-group"><label>Receipt</label><input type="file" class="form-control-file" (change)="pay.receipt = fileOf($event)"></div>
            </div>
            <div class="modal-footer"><button type="submit" class="btn btn-success" [disabled]="pf.invalid || busy">Record Payment</button></div>
          </form>
        </div>
      </div>
    </div>

    <!-- ===== Manual registration modal ===== -->
    <div *ngIf="manual" class="modal fade show d-block" tabindex="-1" (click)="manual = null">
      <div class="modal-dialog modal-xl modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header bg-primary text-white"><h4 class="modal-title text-white">Manual Student Registration</h4><button type="button" class="close text-white" (click)="manual = null">&times;</button></div>
          <form #mf="ngForm" (ngSubmit)="saveManual()">
            <div class="modal-body">
              <h5 class="text-primary border-bottom pb-2">Step 1: Profile</h5>
              <div class="row">
                <div class="col-md-3 form-group"><label>Student Ref_Id</label><input type="text" name="userId" class="form-control" placeholder="M001-2024" [(ngModel)]="manual.userId" required></div>
                <div class="col-md-4 form-group"><label>Full Name</label><input type="text" name="name" class="form-control" [(ngModel)]="manual.name" required></div>
                <div class="col-md-3 form-group"><label>Email</label><input type="email" name="email" class="form-control" placeholder="Optional" [(ngModel)]="manual.email"></div>
                <div class="col-md-2 form-group"><label>Sex</label><select name="sex" class="form-control" [(ngModel)]="manual.sex"><option value="M">Male</option><option value="F">Female</option></select></div>
              </div>
              <div class="row">
                <div class="col-md-4 form-group"><label>Session Year</label><input type="number" name="year" min="1990" max="2100" class="form-control" [(ngModel)]="manual.year"></div>
                <div class="col-md-4 form-group"><label>Student Mobile</label><input type="text" name="phone" class="form-control" [(ngModel)]="manual.phone" required></div>
                <div class="col-md-4 form-group"><label>Rahbar Alumnus?</label><select name="alumnus" class="form-control" [(ngModel)]="manual.alumnus"><option value="N">No</option><option value="Y">Yes</option></select></div>
              </div>
              <h5 class="text-primary border-bottom pb-2 mt-3">Step 2: Family</h5>
              <div class="row">
                <div class="col-md-3 form-group"><label>Father Name</label><input type="text" name="fatherName" class="form-control" [(ngModel)]="manual.fatherName"></div>
                <div class="col-md-3 form-group"><label>Father Profession</label><input type="text" name="fatherProfession" class="form-control" [(ngModel)]="manual.fatherProfession"></div>
                <div class="col-md-3 form-group"><label>Mother Name</label><input type="text" name="motherName" class="form-control" [(ngModel)]="manual.motherName"></div>
                <div class="col-md-3 form-group"><label>Mother Profession</label><input type="text" name="motherProfession" class="form-control" [(ngModel)]="manual.motherProfession"></div>
                <div class="col-md-4 form-group"><label>Annual Salary (INR)</label><input type="number" name="salary" class="form-control" [(ngModel)]="manual.salary"></div>
                <div class="col-md-4 form-group"><label>Father Mobile</label><input type="text" name="fatherMobile" class="form-control" [(ngModel)]="manual.fatherMobile"></div>
                <div class="col-md-4 form-group"><label>Mother Mobile</label><input type="text" name="motherMobile" class="form-control" [(ngModel)]="manual.motherMobile"></div>
                <div class="col-12 form-group"><label>Full Address</label><textarea name="address" class="form-control" rows="2" [(ngModel)]="manual.address"></textarea></div>
              </div>
              <h5 class="text-primary border-bottom pb-2 mt-3">Step 3: Academic</h5>
              <div class="row">
                <div class="col-md-6 form-group"><label>College</label>
                  <select name="institutionId" class="form-control" [(ngModel)]="manual.institutionId" (ngModelChange)="manual.courseId = ''">
                    <option value="">Select College</option>
                    <option *ngFor="let i of institutions" [value]="i.institutionId">{{ i.institutionName }}</option>
                  </select>
                </div>
                <div class="col-md-6 form-group"><label>Course</label>
                  <select name="courseId" class="form-control" [(ngModel)]="manual.courseId">
                    <option value="">{{ manual.institutionId ? 'Select Course' : 'Select College First' }}</option>
                    <option *ngFor="let c of coursesFor(manual.institutionId)" [value]="'' + c.course_id">{{ c.course_name }}</option>
                  </select>
                </div>
                <div class="col-md-6 form-group"><label>Payment Start Date</label>
                  <app-date-input name="paymentStartDate" [(ngModel)]="manual.paymentStartDate"></app-date-input>
                  <small class="text-muted">The first installment is due on this date; the next ones follow the Payment Config of the session year.</small>
                </div>
              </div>
              <h5 class="text-primary border-bottom pb-2 mt-3">Step 4: BA Details</h5>
              <div class="row">
                <div class="col-md-4 form-group"><label>RCC Center</label>
                  <select name="rccName" class="form-control" [(ngModel)]="manual.rccName">
                    <option value="">No RCC center</option>
                    <option *ngFor="let r of rccOptions('')" [value]="r">{{ r }}</option>
                  </select>
                </div>
                <div class="col-md-4 form-group" *ngIf="canMap"><label>Sponsor</label>
                  <select name="sponsorId" class="form-control" [(ngModel)]="manual.sponsorId">
                    <option [ngValue]="null">No sponsor</option>
                    <option *ngFor="let sp of sponsors" [ngValue]="sp.id">{{ sp.name }} ({{ sp.user_id }})</option>
                  </select>
                </div>
                <div class="col-md-4 form-group"><label>Chapter</label>
                  <select name="chapterId" class="form-control" [(ngModel)]="manual.chapterId">
                    <option [ngValue]="null">No chapter</option>
                    <option *ngFor="let c of chapterOptions(null)" [ngValue]="c.chapterId">{{ c.chapterName }}</option>
                  </select>
                </div>
              </div>
              <h5 class="text-primary border-bottom pb-2 mt-3">Step 5: Bank</h5>
              <div class="row">
                <div class="col-md-4 form-group"><label>Bank Name</label><input type="text" name="bankName" class="form-control" [(ngModel)]="manual.bankName"></div>
                <div class="col-md-4 form-group"><label>Account No</label><input type="text" name="accountNumber" class="form-control" [(ngModel)]="manual.accountNumber"></div>
                <div class="col-md-4 form-group"><label>IFSC Code</label><input type="text" name="ifscCode" class="form-control" [(ngModel)]="manual.ifscCode"></div>
              </div>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-secondary" (click)="manual = null">Cancel</button>
              <button type="submit" class="btn btn-primary" [disabled]="mf.invalid || busy">Register Student</button>
            </div>
          </form>
        </div>
      </div>
    </div>

    <!-- ===== CSV upload modal ===== -->
    <div *ngIf="showUpload" class="modal fade show d-block" tabindex="-1" (click)="showUpload = false">
      <div class="modal-dialog modal-dialog-centered" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header"><h5 class="modal-title"><i class="mdi mdi-file-upload mr-1"></i>Bulk upload students</h5><button type="button" class="close" (click)="showUpload = false">&times;</button></div>
          <form (ngSubmit)="upload()">
            <div class="modal-body">
              <div class="alert alert-info">
                <small>Columns: Student Reference, Student Name, Email, Mobile Student, Father Name, Address, Course (Branch), RCC Non-RCC, Mobile-1, Mobile-2, Session Year, Payment Start Date (yyyy-mm-dd; optional).
                Existing students (same Student Reference) are updated. New accounts get the default password and must change it at first sign-in.</small>
                <div class="mt-2"><button type="button" class="btn btn-sm btn-outline-primary" (click)="template()"><i class="mdi mdi-download"></i> Download CSV template</button></div>
              </div>
              <input type="file" class="form-control-file" accept=".csv" (change)="csv = fileOf($event)" required>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-light" (click)="showUpload = false">Cancel</button>
              <button type="submit" class="btn btn-primary" [disabled]="!csv">Upload &amp; process</button>
            </div>
          </form>
        </div>
      </div>
    </div>

    <app-bulk-report *ngIf="report" [report]="report" title="Student upload results" createdLabel="New students"
                     (closed)="report = null"></app-bulk-report>

    <div *ngIf="busyOverlay" class="loading-overlay">
      <div class="spinner-border text-success" role="status" style="width: 4rem; height: 4rem;"></div>
      <h3 class="mt-3 text-dark">Processing...</h3>
      <p class="text-muted">Please wait while the system updates the records.</p>
    </div>
  `
})
export class AdminStudentDirectoryComponent implements OnInit, OnDestroy {
  readonly tabs = [
    { id: 'profile', label: 'Personal' }, { id: 'family', label: 'Family' }, { id: 'course', label: 'Academic' },
    { id: 'ba', label: 'BA Details' }, { id: 'study', label: 'Study Status' }, { id: 'bank', label: 'Bank Details' },
    { id: 'payment', label: 'Payment Schedule' }, { id: 'docs', label: 'Docs' }
  ];

  rows: StudentRow[] = [];
  filteredTotal = 0;
  page = 1;
  pageSize = 10;
  search = '';
  search$ = new Subject<string>();
  private sub?: Subscription;
  loading = false;

  institutions: Institution[] = [];
  courses: CourseRow[] = [];
  rccCenters: RccCenter[] = [];
  sponsors: SponsorOption[] = [];
  chapters: Chapter[] = [];

  message = '';
  error = '';
  busy = false;
  busyOverlay = false;

  // profile modal
  currentId = '';
  details: StudentDetails | null = null;
  tab = 'profile';
  editing = false;
  edit: Record<string, string> = {};
  ba: BaDetails = { rccName: '', sponsorId: null, chapterId: null, collegeType: '', selfFinanced: false };
  /** The sponsor when the profile was opened: a change asks for a reason (kept in the sponsor history). */
  private originalSponsorId: number | null = null;
  get sponsorChanged(): boolean { return this.ba.sponsorId !== this.originalSponsorId; }
  installments: InstallmentRow[] = [];
  installmentProblem: string | null = null;
  readonly installmentBadge = installmentBadge;
  study = { status: 'STUDYING', date: '', note: '' };
  /** CSV bulk upload is switched off (core/models/features.ts). */
  readonly bulkUpload = FEATURES.bulkUpload;
  fChapter: number | null = null;
  fStudy = '';
  fStatus = '';
  fSponsored = '';
  /** Session year filter ('' all, 'none' not set) and the years there are. */
  fYear = '';
  years: number[] = [];
  fInst = '';
  fCourse: number | null = null;

  pay: { actionType: 'create' | 'edit'; paymentId?: unknown; installmentId?: number; amount: number | null; paymentDate: string; receipt: File | null } | null = null;
  manual: Record<string, any> | null = null;
  showUpload = false;
  csv: File | null = null;

  constructor(private api: ApiService, private chapterList: ChapterService, private auth: AuthService, private statuses: StudyStatusService) {}

  /** The study statuses (from the server), for the filter and the Study tab. */
  get studyStatuses(): { code: string; label: string }[] { return this.statuses.list; }

  get filtering(): boolean {
    return !!(this.search.trim() || this.fChapter !== null || this.fStudy || this.fStatus || this.fSponsored || this.fInst || this.fYear);
  }

  /** A filter changed: back to page 1 and ask the server. */
  refilter(): void { this.page = 1; this.load(); }

  deleting = false;

  deleteTitle(r: StudentRow): string {
    return r.can_delete ? 'Delete this student' : "Can't delete: " + (r.delete_blockers ?? []).join('; ');
  }

  /** Super Admin: deletes a student who only has their account and application / family details. */
  deleteStudent(r: StudentRow): void {
    if (!r.can_delete || !confirm(`Delete ${r.name} (${r.user_id || 'no ID'}) permanently? This removes their account and application details and can't be undone.`)) return;
    this.deleting = true;
    this.api.delete<{ message: string }>(`/admin/students/${r.id}`).subscribe({
      next: (m) => { this.deleting = false; this.message = m.message; this.load(); },
      error: (e) => { this.deleting = false; this.error = errorText(e, 'Could not delete the student.'); }
    });
  }

  clearFilters(): void {
    this.search = '';
    this.fChapter = null; this.fStudy = ''; this.fStatus = ''; this.fSponsored = ''; this.fInst = ''; this.fCourse = null; this.fYear = '';
    this.refilter();
  }

  get canEdit(): boolean { return this.auth.can('STUDENTS', 'EDIT'); }
  readonly takesPayments = takesPayments;
  /** Mapping a student to a sponsor (or changing it) needs Sponsorships: manage, not only Students: manage. */
  get canMap(): boolean { return this.auth.can('SPONSORSHIPS', 'EDIT'); }

  /** Active chapters, plus the given one when it is inactive. */
  chapterOptions(currentId: number | null | undefined): Chapter[] {
    return ChapterService.options(this.chapters, currentId);
  }

  ngOnInit(): void {
    this.sub = this.search$.pipe(debounceTime(300)).subscribe(() => { this.page = 1; this.load(); });
    this.api.get<Institution[]>('/admin/institutions').subscribe({ next: (i) => (this.institutions = i) });
    this.api.get<CourseRow[]>('/admin/courses').subscribe({ next: (c) => (this.courses = c) });
    this.api.get<RccCenter[]>('/admin/rcc-centers').subscribe({ next: (r) => (this.rccCenters = r) });
    this.api.get<SponsorOption[]>('/admin/sponsorships').subscribe({ next: (s) => (this.sponsors = s) });
    this.chapterList.list().subscribe({ next: (c) => (this.chapters = c) });
    this.load();
  }

  ngOnDestroy(): void { this.sub?.unsubscribe(); }

  load(): void {
    this.loading = true;
    this.api.get<{ recordsTotal: number; recordsFiltered: number; data: StudentRow[]; years?: number[] }>('/admin/students', {
      start: (this.page - 1) * this.pageSize, length: this.pageSize, search: this.search.trim(),
      chapterId: this.fChapter, studyStatus: this.fStudy, status: this.fStatus, sponsored: this.fSponsored,
      institutionId: this.fInst, courseId: this.fCourse, sessionYear: this.fYear
    }).subscribe({
      next: (r) => { this.loading = false; this.rows = r.data; this.filteredTotal = r.recordsFiltered; this.years = r.years ?? []; },
      error: (e) => { this.loading = false; this.error = errorText(e, 'Could not load students.'); }
    });
  }

  coursesFor(institutionId: string | undefined): CourseRow[] {
    return institutionId ? this.courses.filter((c) => String(c.institution_id) === String(institutionId)) : [];
  }

  /** RCC center names; a stored name that is no longer in the list stays selectable instead of being blanked. */
  rccOptions(current: string): string[] {
    const names = this.rccCenters.map((r) => r.centerName).filter((n) => !!n);
    return current && !names.includes(current) ? [current, ...names] : names;
  }

  /** Sponsors to choose from; the student's current sponsor stays listed even when inactive. */
  sponsorOptions(): SponsorOption[] {
    const current = this.details?.sponsor;
    if (!current || this.sponsors.some((s) => s.id === current['id'])) return this.sponsors;
    return [{ id: current['id'], user_id: current['user_id'], name: current['name'] }, ...this.sponsors];
  }

  fmt(v: unknown, fallback = '--'): string { return localDate(v, fallback); }
  file(p: unknown): string { return uploadUrl(p) ?? '#'; }
  fileOf(event: Event): File | null { return (event.target as HTMLInputElement).files?.[0] ?? null; }

  get isActive(): boolean { return String(this.details?.profile['status'] ?? '').toLowerCase() === 'active'; }

  // ---------------- profile modal

  /** id: the student's users.id. */
  openDetails(id: number): void {
    this.currentId = String(id);
    this.tab = 'profile';
    this.refreshDetails();
  }

  closeDetails(): void { this.details = null; this.editing = false; }

  private refreshDetails(): void {
    this.api.get<StudentDetails>(`/admin/students/${encodeURIComponent(this.currentId)}`).subscribe({
      next: (res) => {
        this.details = res;
        this.editing = false;
        const p = res.profile, b = res.bank ?? {}, c = res.course ?? {};
        const s = (v: unknown) => (v === null || v === undefined ? '' : String(v));
        this.ba = { rccName: s(p['rcc_name']), sponsorId: res.sponsor?.['id'] ?? null, chapterId: p['chapter_id'] ?? null, collegeType: s(p['college_type']), selfFinanced: p['self_financed'] === true, sponsorChangeReason: '',
                    onBehalf: !!res.onBehalf?.['on_behalf'], donorChapterId: (res.onBehalf?.['donor_chapter_id'] as number | null) ?? null,
                    donorLabel: s(res.onBehalf?.['donor_label']) };
        this.originalSponsorId = this.ba.sponsorId;
        this.edit = {
          name: s(p['name']), refId: s(p['user_id']), email: s(p['email']), phone: s(p['phone']),
          fatherName: s(p['father_name']), motherName: s(p['mother_name']), address: s(p['address']),
          fatherProfession: s(p['father_profession']), motherProfession: s(p['mother_profession']),
          fatherMobile: s(p['father_mobile']), motherMobile: s(p['mother_mobile']), averageAnnualSalary: s(p['average_annual_salary']),
          institutionId: s(c['institution_id']), courseId: s(c['course_id']),
          year: s(p['session_year']), paymentStartDate: isoDate(p['payment_start_date']),
          accountName: s(b['account_name']), bankName: s(b['bank_name']), accountNumber: s(b['account_number']), ifscCode: s(b['ifsc_code'])
        };
        this.loadInstallments();
        this.study = { status: p['study_status'] || 'STUDYING', date: isoDate(new Date()), note: '' };
      },
      error: (e) => (this.error = errorText(e, 'Could not load student details.'))
    });
  }

  /** The stored installments (needs the Payment records permission). */
  private loadInstallments(): void {
    this.installments = [];
    this.installmentProblem = null;
    if (!this.canSeeInstallments) return;
    this.api.get<{ installments: InstallmentRow[]; problem: string | null }>(`/admin/students/${encodeURIComponent(this.currentId)}/installments`).subscribe({
      next: (r) => { this.installments = r.installments; this.installmentProblem = r.problem; },
      error: (e) => (this.error = errorText(e, 'Could not load the payment schedule.'))
    });
  }

  get canSeeInstallments(): boolean { return this.auth.can('PAYMENT_RECORDS'); }
  get canPay(): boolean { return this.auth.can('PAYMENT_RECORDS', 'EDIT') || this.canEdit; }
  get visibleTabs(): { id: string; label: string }[] {
    return this.tabs.filter((t) => t.id !== 'payment' || this.canSeeInstallments);
  }

  /** Sponsor of an installment: the current sponsor's name, else (an earlier sponsor) just "earlier sponsor". */
  sponsorLabel(sponsorId: number): string {
    const sp = this.sponsors.find((s) => s.id === sponsorId);
    return sp ? `${sp.name} (${sp.user_id})` : 'Earlier sponsor';
  }

  link(path: string | null): string | null { return uploadUrl(path); }

  saveProfile(): void {
    const e = this.edit;
    const body: Record<string, unknown> = {
      name: e['name'], refId: e['refId'], email: e['email'], phone: e['phone'],
      rccName: this.ba.rccName, sponsorId: this.ba.sponsorId, chapterId: this.ba.chapterId, collegeType: this.ba.collegeType, selfFinanced: this.ba.selfFinanced,
      sponsorChangeReason: this.sponsorChanged ? this.ba.sponsorChangeReason : null,
      ...(this.canMap && this.ba.sponsorId ? { onBehalf: !!this.ba.onBehalf, donorChapterId: this.ba.onBehalf ? this.ba.donorChapterId : null,
        donorLabel: this.ba.onBehalf ? this.ba.donorLabel : null } : {}),
      fatherName: e['fatherName'], motherName: e['motherName'], address: e['address'],
      fatherProfession: e['fatherProfession'], motherProfession: e['motherProfession'],
      fatherMobile: e['fatherMobile'], motherMobile: e['motherMobile'], averageAnnualSalary: e['averageAnnualSalary'],
      accountNumber: e['accountNumber'], bankName: e['bankName'], ifscCode: e['ifscCode'], accountName: e['accountName'],
      year: e['year'], paymentStartDate: e['paymentStartDate']
    };
    if (e['institutionId'] && e['courseId']) { body['institutionId'] = e['institutionId']; body['courseId'] = e['courseId']; }
    this.busy = true;
    this.api.put<{ message: string }>(`/admin/students/${encodeURIComponent(this.currentId)}`, body).subscribe({
      next: (r) => { this.busy = false; this.closeDetails(); this.message = r.message; this.load(); },
      error: (err) => { this.busy = false; this.error = errorText(err, 'Could not save the student.'); }
    });
  }

  studyLabel(status: unknown): string { return this.statuses.label(status as string | null); }
  studyBadge(status: unknown): string { return studyStatusBadge(status as string | null); }

  /** Graduated is final: the study status can't be changed afterwards. */
  get graduated(): boolean { return this.details?.profile?.['study_status'] === 'GRADUATED'; }

  saveStudyStatus(): void {
    if (this.study.status === 'GRADUATED' && !confirm('Mark as Graduated? This is final: the study status can\'t be changed afterwards, '
        + 'and any installments not paid yet will be marked "Not needed".')) return;
    this.busy = true;
    this.error = '';
    this.api.post<{ message: string }>(`/admin/students/${encodeURIComponent(this.currentId)}/study-status`, this.study).subscribe({
      next: (r) => { this.busy = false; this.message = r.message; this.refreshDetails(); this.load(); },
      error: (err) => { this.busy = false; this.error = errorText(err, 'Could not update the study status.'); }
    });
  }

  action(kind: 'activate' | 'deactivate'): void {
    this.api.post(`/admin/students/${encodeURIComponent(this.currentId)}/action`, { action: kind }).subscribe({
      next: () => { this.refreshDetails(); this.load(); },
      error: (err) => (this.error = errorText(err, 'Action failed.'))
    });
  }

  // ---------------- payments

  openPayment(i: InstallmentRow): void {
    this.pay = i.payment_id
      ? { actionType: 'edit', paymentId: i.payment_id, amount: null, paymentDate: isoDate(i.paid_date), receipt: null }
      : { actionType: 'create', installmentId: i.installment_id, amount: null, paymentDate: isoDate(new Date()), receipt: null };
  }

  savePayment(): void {
    if (!this.pay) return;
    const f = new FormData();
    f.append('actionType', this.pay.actionType);
    if (this.pay.paymentId !== undefined) f.append('paymentId', String(this.pay.paymentId));
    if (this.pay.installmentId !== undefined) f.append('installmentId', String(this.pay.installmentId));
    f.append('granteeId', this.currentId);
    if (this.pay.amount != null) f.append('amount', String(this.pay.amount)); // editing without one keeps the saved amount
    f.append('paymentDate', this.pay.paymentDate);
    if (this.pay.receipt) f.append('receipt', this.pay.receipt);
    this.busy = true;
    this.api.post<{ message: string }>('/admin/payments/record', f).subscribe({
      next: (r) => { this.busy = false; this.pay = null; this.message = r.message; this.refreshDetails(); },
      error: (err) => { this.busy = false; this.pay = null; this.error = errorText(err, 'Could not record the payment.'); }
    });
  }

  // ---------------- manual registration & CSV

  openManual(): void {
    this.manual = {
      userId: '', name: '', email: '', sex: 'M', chapterId: null, year: new Date().getFullYear(), paymentStartDate: '', phone: '', alumnus: 'N',
      fatherName: '', fatherProfession: '', motherName: '', motherProfession: '', salary: 0, fatherMobile: '', motherMobile: '', address: '',
      institutionId: '', courseId: '', rccName: '', sponsorId: null, bankName: '', accountNumber: '', ifscCode: ''
    };
  }

  saveManual(): void {
    if (!this.manual) return;
    const m = this.manual;
    const courseName = this.courses.find((c) => String(c.course_id) === String(m['courseId']))?.course_name ?? null;
    const body = { ...m, email: m['email'] || null, courseName };
    this.busy = true;
    this.api.post<{ message: string }>('/admin/students/manual-add', body).subscribe({
      next: (r) => { this.busy = false; this.manual = null; this.message = r.message; this.load(); },
      error: (err) => { this.busy = false; this.error = errorText(err, 'Could not register the student.'); }
    });
  }

  /** Result of the last bulk upload (shown in a results window). */
  report: BulkReport | null = null;

  template(): void {
    this.api.download('/admin/templates/students', undefined, 'students_template.csv').subscribe({
      error: (e) => (this.error = errorText(e, 'Could not download the template.'))
    });
  }

  upload(): void {
    if (!this.csv) return;
    const f = new FormData();
    f.append('file', this.csv);
    this.showUpload = false;
    this.busyOverlay = true;
    this.api.post<{ message: string; report?: BulkReport }>('/admin/students/bulk-upload', f).subscribe({
      next: (r) => { this.busyOverlay = false; this.csv = null; this.message = r.message; this.report = r.report ?? null; this.load(); },
      error: (err) => { this.busyOverlay = false; this.error = errorText(err, 'Upload failed.'); }
    });
  }
}
