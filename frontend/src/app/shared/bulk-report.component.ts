import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface BulkProblem { row: number; reference: string | null; type: 'skipped' | 'failed' | 'warning'; reason: string }
export interface BulkReport {
  totalRows: number; created: number; updated: number; skipped: number; failed: number; mappings: number;
  problems: BulkProblem[];
}

/** Result window after a CSV bulk upload: totals plus every row that was skipped, failed or needs attention. */
@Component({
  selector: 'app-bulk-report',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="modal d-block" (click)="closed.emit()">
      <div class="modal-dialog modal-lg modal-dialog-centered" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title"><i class="mdi mdi-clipboard-check-outline mr-1"></i>{{ title }}</h5>
            <button type="button" class="close" (click)="closed.emit()">&times;</button>
          </div>
          <div class="modal-body">
            <div class="bulk-stats">
              <div><span>Rows read</span><strong>{{ report.totalRows }}</strong></div>
              <div class="ok"><span>{{ createdLabel }}</span><strong>{{ report.created }}</strong></div>
              <div class="ok"><span>{{ updatedLabel }}</span><strong>{{ report.updated }}</strong></div>
              <div *ngIf="showMappings" class="ok"><span>Students mapped</span><strong>{{ report.mappings }}</strong></div>
              <div [class.warn]="report.skipped"><span>Skipped</span><strong>{{ report.skipped }}</strong></div>
              <div [class.bad]="report.failed"><span>Failed</span><strong>{{ report.failed }}</strong></div>
            </div>

            <div *ngIf="!report.problems.length" class="alert alert-success mb-0 mt-3">
              <i class="mdi mdi-check-circle-outline mr-1"></i>Every row was processed without problems.
            </div>

            <ng-container *ngIf="report.problems.length">
              <h6 class="mt-4 mb-2">Rows that need your attention</h6>
              <div class="table-responsive table-fixed-head" style="max-height: 320px">
                <table class="table table-sm mb-0">
                  <thead><tr><th>Row</th><th>Reference</th><th>Result</th><th>Reason</th></tr></thead>
                  <tbody>
                    <tr *ngFor="let p of report.problems">
                      <td>{{ p.row }}</td>
                      <td>{{ p.reference || '--' }}</td>
                      <td><span class="badge" [ngClass]="p.type === 'failed' ? 'badge-danger' : 'badge-warning'">{{ p.type }}</span></td>
                      <td class="text-wrap">{{ p.reason }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <p class="small text-muted mt-2 mb-0">Row numbers match the lines in your CSV file (line 1 is the header). Fix those rows and upload the file again; rows that were saved are simply updated.</p>
            </ng-container>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-primary" (click)="closed.emit()">Done</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class BulkReportComponent {
  @Input({ required: true }) report!: BulkReport;
  @Input() title = 'Upload results';
  @Input() createdLabel = 'Added';
  @Input() updatedLabel = 'Updated';
  @Input() showMappings = false;
  @Output() closed = new EventEmitter<void>();
}
