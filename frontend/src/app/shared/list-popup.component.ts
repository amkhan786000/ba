import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AppDatePipe } from './app-date';

/** A column of {@link ListPopupComponent}: the row key, its heading, and how to show it. */
export interface PopupColumn { key: string; label: string; type?: 'text' | 'date' | 'badge' }

/** What a popup shows: a title, its columns and rows (while loading: loading = true). */
export interface PopupList { title: string; subtitle?: string; columns: PopupColumn[]; rows: Record<string, unknown>[]; loading: boolean; empty?: string }

/**
 * The rows behind a number: clicking a count (students of a sponsor, overdue installments, reports pending, ...)
 * opens this table in a popup. The page loads the rows; this only shows them.
 */
@Component({
  selector: 'app-list-popup',
  standalone: true,
  imports: [CommonModule, AppDatePipe],
  template: `
    <div *ngIf="list" class="modal fade show d-block" tabindex="-1" (click)="closed.emit()">
      <div class="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable" (click)="$event.stopPropagation()">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">{{ list.title }} <small *ngIf="list.subtitle" class="text-muted">{{ list.subtitle }}</small></h5>
            <button type="button" class="close" (click)="closed.emit()" aria-label="Close">&times;</button>
          </div>
          <div class="modal-body">
            <div *ngIf="list.loading" class="text-center py-4"><span class="spinner-border spinner-border-sm"></span></div>
            <div *ngIf="!list.loading" class="table-responsive">
              <table class="table table-sm table-hover mb-0">
                <thead class="thead-light"><tr><th *ngFor="let c of list.columns">{{ c.label }}</th></tr></thead>
                <tbody>
                  <tr *ngIf="!list.rows.length"><td [attr.colspan]="list.columns.length" class="text-center text-muted">{{ list.empty || 'Nothing to show.' }}</td></tr>
                  <tr *ngFor="let r of list.rows">
                    <td *ngFor="let c of list.columns">
                      <ng-container [ngSwitch]="c.type">
                        <ng-container *ngSwitchCase="'date'">{{ r[c.key] ? (r[c.key] | appDate) : '-' }}</ng-container>
                        <span *ngSwitchCase="'badge'" class="badge badge-light border">{{ r[c.key] ?? '-' }}</span>
                        <ng-container *ngSwitchDefault>{{ r[c.key] ?? '-' }}</ng-container>
                      </ng-container>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
          <div class="modal-footer">
            <small class="text-muted mr-auto" *ngIf="!list.loading">{{ list.rows.length }} row{{ list.rows.length === 1 ? '' : 's' }}</small>
            <ng-content></ng-content>
            <button type="button" class="btn btn-secondary" (click)="closed.emit()">Close</button>
          </div>
        </div>
      </div>
    </div>
  `
})
export class ListPopupComponent {
  @Input() list: PopupList | null = null;
  @Output() closed = new EventEmitter<void>();
}
