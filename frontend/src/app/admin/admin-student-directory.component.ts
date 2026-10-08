import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, Subscription, debounceTime } from 'rxjs';
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
import { STUDY_STATUSES, studyStatusBadge, studyStatusLabel } from '../shared/study-status';

interface StudentRow {
  id: number; user_id: string; name: string; email: string | null; phone: string | null;
  sponsor_id: number | null; sponsor_code: string | null; sponsor_name: string | null; status: string | null;
  study_status: string | null;
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
}

interface Installment {
  n: number; due: string; expected: string;
  payment: Row | null; actualDate: string; amount: string; receipt: string | null; proof: string | null;
}

/** A sponsor, convenor or coordinator a student can be mapped to (from /admin/sponsorships). */
interface SponsorOption { id: number; user_id: string; name: string }

/** BA (Rahbar) details of a student: RCC center, sponsor and chapter - all optional. */
interface BaDetails { rccName: string; sponsorId: number | null; chapterId: number | null }

/** Port of templates/admin/student_directory.html (server-side table, profile modal with 5 tabs, payments, CSV upload, manual registration). */
@Component({
  selector: 'app-admin-student-directory',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, PagerComponent, BulkReportComponent],
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
              <button *ngIf="canEdit" type="button" class="btn btn-success btn-responsive mr-md-2" (click)="showUpload = true"><i class="mdi mdi-file-upload"></i> Upload CSV</button>
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
                <label>Search:<input type="search" class="form-control form-control-sm" [(ngModel)]="search" (ngModelChange)="search$.next($event)"></label>
              </div>
            </div>

            <div class="table-responsive">
              <table class="table table-bordered table-striped nowrap" style="width:100%">
                <thead>
                  <tr><th>Ref_Id</th><th>Name</th><th>Email</th><th>Phone</th><th>Sponsor ID</th><th>Sponsor Name</th><th>Study</th><th>Status</th><th>Actions</th></tr>
                </thead>
                <tbody>
                  <tr *ngIf="loading"><td colspan="9" class="text-center text-muted">Processing...</td></tr>
                  <tr *ngIf="!loading && !rows.length"><td colspan="9" class="text-center text-muted">No matching records found</td></tr>
                  <tr *ngFor="let r of rows">
                    <td>{{ r.user_id }}</td><td>{{ r.name }}</td><td>{{ r.email }}</td><td>{{ r.phone }}</td>
                    <td>{{ r.sponsor_code || '-' }}</td><td>{{ r.sponsor_name || 'Unassigned' }}</td>
                    <td><span class="badge" [ngClass]="studyBadge(r.study_status)">{{ studyLabel(r.study_status) }}</span></td>
                    <td><span class="badge" [ngClass]="(r.status || '').toLowerCase() === 'active' ? 'badge-success' : 'badge-danger'">{{ r.status }}</span></td>
                    <td><button class="btn btn-xs btn-primary" (click)="openDetails(r.id)">View Details</button></td>
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
            <ul class="nav nav-pills nav-fill mb-3">
              <li class="nav-item" *ngFor="let t of tabs"><a class="nav-link" [class.active]="tab === t.id" (click)="tab = t.id">{{ t.label }}</a></li>
            </ul>

            <!-- Personal -->
            <div *ngIf="tab === 'profile'">
              <div class="row">
                <div class="col-md-6 form-group"><label>Full Name</label><input type="text" class="form-control" [(ngModel)]="edit.name" [disabled]="!editing"></div>
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
                <select class="form-control" [(ngModel)]="ba.sponsorId" [disabled]="!editing">
                  <option [ngValue]="null">No sponsor</option>
                  <option *ngFor="let sp of sponsorOptions()" [ngValue]="sp.id">{{ sp.name }} ({{ sp.user_id }})</option>
                </select>
                <small *ngIf="details.sponsor?.['email']" class="text-muted">Current sponsor's email: {{ details.sponsor?.['email'] }}</small>
              </div>
              <div class="col-md-4 form-group"><label>Chapter</label>
                <select class="form-control" [(ngModel)]="ba.chapterId" [disabled]="!editing">
                  <option [ngValue]="null">No chapter</option>
                  <option *ngFor="let c of chapterOptions(details.profile['chapter_id'])" [ngValue]="c.chapterId">{{ c.chapterName }}</option>
                </select>
              </div>
            </div>

            <!-- Study status (separate from the account's Active / Inactive) -->
            <div *ngIf="tab === 'study'">
              <p class="text-muted small">
                Graduated and Dropped-out students no longer get payment dues or reminders; students on hold keep their
                sponsor but get no reminders. This is separate from the login account's Active / Inactive.
              </p>
              <p>
                Current: <span class="badge" [ngClass]="studyBadge(details.profile['study_status'])">{{ studyLabel(details.profile['study_status']) }}</span>
                <span *ngIf="details.profile['study_status_date']" class="text-muted"> since {{ fmt(details.profile['study_status_date'], '') }}</span>
                <span *ngIf="details.profile['study_status_note']" class="text-muted"> · {{ details.profile['study_status_note'] }}</span>
              </p>
              <div class="row" *ngIf="canEdit">
                <div class="col-md-3 form-group"><label>New status</label>
                  <select class="form-control" [(ngModel)]="study.status">
                    <option *ngFor="let st of studyStatuses" [value]="st">{{ studyLabel(st) }}</option>
                  </select>
                </div>
                <div class="col-md-3 form-group"><label>Effective date</label><input type="date" class="form-control" [(ngModel)]="study.date"></div>
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
              <div class="table-responsive">
                <div *ngIf="!details.course?.['assigned_at']" class="alert alert-warning">No course assigned.</div>
                <table *ngIf="details.course?.['assigned_at']" class="table table-bordered table-schedule text-center">
                  <thead><tr><th>#</th><th>Exp. Date</th><th>Exp. Amt</th><th>Actual Date</th><th>Amt</th><th>Receipt</th><th>Spent</th><th>Action</th></tr></thead>
                  <tbody>
                    <tr *ngFor="let i of schedule" [class.table-success]="i.payment">
                      <td>{{ i.n }}</td><td>{{ i.due }}</td><td>₹{{ i.expected }}</td>
                      <td>{{ i.actualDate }}</td><td>{{ i.amount }}</td>
                      <td><a *ngIf="i.receipt; else dash" [href]="i.receipt" target="_blank">View</a></td>
                      <td><a *ngIf="i.proof; else dash" [href]="i.proof" target="_blank">View</a></td>
                      <td>
                        <button *ngIf="canEdit && i.payment" class="btn btn-xs btn-warning" (click)="openPayment(i)">Edit</button>
                        <button *ngIf="canEdit && !i.payment" class="btn btn-xs btn-success" (click)="openPayment(i)">Pay Now</button>
                      </td>
                    </tr>
                  </tbody>
                </table>
                <ng-template #dash>--</ng-template>
              </div>
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
              <div class="form-group"><label>Amount (INR)</label><input type="number" class="form-control" name="amount" [(ngModel)]="pay.amount" required></div>
              <div class="form-group"><label>Date</label><input type="date" class="form-control" name="paymentDate" [(ngModel)]="pay.paymentDate" required></div>
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
                <div class="col-md-4 form-group"><label>Admission Year</label><input type="number" name="year" class="form-control" [(ngModel)]="manual.year"></div>
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
              </div>
              <h5 class="text-primary border-bottom pb-2 mt-3">Step 4: BA Details</h5>
              <div class="row">
                <div class="col-md-4 form-group"><label>RCC Center</label>
                  <select name="rccName" class="form-control" [(ngModel)]="manual.rccName">
                    <option value="">No RCC center</option>
                    <option *ngFor="let r of rccOptions('')" [value]="r">{{ r }}</option>
                  </select>
                </div>
                <div class="col-md-4 form-group"><label>Sponsor</label>
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
                <small>Columns: Student Reference, Student Name, Email, Mobile Student, Father Name, Address, Course (Branch), RCC Non-RCC, Mobile-1, Mobile-2.
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
  ba: BaDetails = { rccName: '', sponsorId: null, chapterId: null };
  schedule: Installment[] = [];
  study = { status: 'STUDYING', date: '', note: '' };
  readonly studyStatuses = STUDY_STATUSES;

  pay: { actionType: 'create' | 'edit'; paymentId?: unknown; amount: number | null; paymentDate: string; receipt: File | null } | null = null;
  manual: Record<string, any> | null = null;
  showUpload = false;
  csv: File | null = null;

  constructor(private api: ApiService, private chapterList: ChapterService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('STUDENTS', 'EDIT'); }

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
    this.api.get<{ recordsTotal: number; recordsFiltered: number; data: StudentRow[] }>('/admin/students', {
      start: (this.page - 1) * this.pageSize, length: this.pageSize, search: this.search.trim()
    }).subscribe({
      next: (r) => { this.loading = false; this.rows = r.data; this.filteredTotal = r.recordsFiltered; },
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
        this.ba = { rccName: s(p['rcc_name']), sponsorId: res.sponsor?.['id'] ?? null, chapterId: p['chapter_id'] ?? null };
        this.edit = {
          name: s(p['name']), email: s(p['email']), phone: s(p['phone']),
          fatherName: s(p['father_name']), motherName: s(p['mother_name']), address: s(p['address']),
          fatherProfession: s(p['father_profession']), motherProfession: s(p['mother_profession']),
          fatherMobile: s(p['father_mobile']), motherMobile: s(p['mother_mobile']), averageAnnualSalary: s(p['average_annual_salary']),
          institutionId: s(c['institution_id']), courseId: s(c['course_id']),
          accountName: s(b['account_name']), bankName: s(b['bank_name']), accountNumber: s(b['account_number']), ifscCode: s(b['ifsc_code'])
        };
        this.schedule = this.buildSchedule(res);
        this.study = { status: p['study_status'] || 'STUDYING', date: isoDate(new Date()), note: '' };
      },
      error: (e) => (this.error = errorText(e, 'Could not load student details.'))
    });
  }

  /** Same rules as generateSchedule() in the Flask page: quarterly installments from the assignment date. */
  private buildSchedule(res: StudentDetails): Installment[] {
    const start = asDate(res.course?.['assigned_at']);
    if (!start) return [];
    const expected = (parseFloat(String(res.profile['annual_schedule_amount'] ?? 0)) / 4).toFixed(2);
    const total = (parseInt(String(res.course?.['number_of_semesters'] ?? 0), 10) / 2) * 4;
    const paid = [...res.payments].sort((a, b) => (asDate(a['payment_date'])?.getTime() ?? 0) - (asDate(b['payment_date'])?.getTime() ?? 0));
    const out: Installment[] = [];
    for (let i = 1; i <= total; i++) {
      const due = new Date(start);
      due.setMonth(start.getMonth() + 3 * i);
      const p = paid[i - 1] ?? null;
      out.push({
        n: i, due: due.toLocaleDateString(), expected, payment: p,
        actualDate: p ? localDate(p['payment_date']) : '--',
        amount: p ? `₹${p['amount']}` : '--',
        receipt: p ? uploadUrl(p['receipt_url']) : null,
        proof: p ? uploadUrl(p['student_proof_url']) : null
      });
    }
    return out;
  }

  saveProfile(): void {
    const e = this.edit;
    const body: Record<string, unknown> = {
      name: e['name'], email: e['email'], phone: e['phone'],
      rccName: this.ba.rccName, sponsorId: this.ba.sponsorId, chapterId: this.ba.chapterId,
      fatherName: e['fatherName'], motherName: e['motherName'], address: e['address'],
      fatherProfession: e['fatherProfession'], motherProfession: e['motherProfession'],
      fatherMobile: e['fatherMobile'], motherMobile: e['motherMobile'], averageAnnualSalary: e['averageAnnualSalary'],
      accountNumber: e['accountNumber'], bankName: e['bankName'], ifscCode: e['ifscCode'], accountName: e['accountName']
    };
    if (e['institutionId'] && e['courseId']) { body['institutionId'] = e['institutionId']; body['courseId'] = e['courseId']; }
    this.busy = true;
    this.api.put<{ message: string }>(`/admin/students/${encodeURIComponent(this.currentId)}`, body).subscribe({
      next: (r) => { this.busy = false; this.closeDetails(); this.message = r.message; this.load(); },
      error: (err) => { this.busy = false; this.error = errorText(err, 'Could not save the student.'); }
    });
  }

  studyLabel(status: unknown): string { return studyStatusLabel(status as string | null); }
  studyBadge(status: unknown): string { return studyStatusBadge(status as string | null); }

  saveStudyStatus(): void {
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

  openPayment(i: Installment): void {
    this.pay = i.payment
      ? { actionType: 'edit', paymentId: i.payment['payment_id'], amount: Number(i.payment['amount']), paymentDate: isoDate(i.payment['payment_date']), receipt: null }
      : { actionType: 'create', amount: Number(i.expected), paymentDate: isoDate(new Date()), receipt: null };
  }

  savePayment(): void {
    if (!this.pay) return;
    const f = new FormData();
    f.append('actionType', this.pay.actionType);
    if (this.pay.paymentId !== undefined) f.append('paymentId', String(this.pay.paymentId));
    f.append('granteeId', this.currentId);
    f.append('amount', String(this.pay.amount ?? 0));
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
      userId: '', name: '', email: '', sex: 'M', chapterId: null, year: 2024, phone: '', alumnus: 'N',
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
