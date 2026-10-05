import { Component, EventEmitter, Input, Output, Pipe, PipeTransform } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

type PageItem = { label: string; page: number; active?: boolean; disabled?: boolean; dots?: boolean };

/** Current page (1-based) and page size of one table. Pass it to <app-pager [state]> and the `paginate` pipe. */
export class PageState {
  constructor(public size = 10, public page = 1) {}
  /** Back to the first page (after a filter or search change). */
  reset(): void { this.page = 1; }
}

/**
 * "Showing x to y of z" + page size + Previous / 1 2 … n / Next.
 * Two ways to use it:
 *  - client-side: <tr *ngFor="let r of rows | paginate: pg.page : pg.size"> + <app-pager [state]="pg" [total]="rows.length">
 *  - server-side: <app-pager [state]="pg" [total]="total" (pageChange)="load()"> and send pg.page / pg.size to the API
 * The older [page] / [pageSize] / (pageChange) bindings still work.
 */
@Component({
  selector: 'app-pager',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="pager" *ngIf="total > 0 || showEmpty">
      <div class="pager-info">
        <span>Showing <strong>{{ start }}</strong>–<strong>{{ end }}</strong> of <strong>{{ total }}</strong></span>
        <label *ngIf="state && sizes.length && total > sizes[0]" class="pager-size">
          Rows
          <select class="form-control form-control-sm" [ngModel]="size" (ngModelChange)="changeSize($event)">
            <option *ngFor="let s of sizes" [ngValue]="s">{{ s }}</option>
          </select>
        </label>
      </div>
      <nav *ngIf="pages > 1">
        <ul class="pagination mb-0">
          <li *ngFor="let p of items" class="page-item" [class.active]="p.active" [class.disabled]="p.disabled || p.dots">
            <span *ngIf="p.dots" class="page-link">…</span>
            <a *ngIf="!p.dots" class="page-link" href="#" (click)="go($event, p)" [attr.aria-label]="p.label">
              <i *ngIf="p.label === 'Previous'" class="mdi mdi-chevron-left"></i>
              <i *ngIf="p.label === 'Next'" class="mdi mdi-chevron-right"></i>
              <ng-container *ngIf="p.label !== 'Previous' && p.label !== 'Next'">{{ p.label }}</ng-container>
            </a>
          </li>
        </ul>
      </nav>
    </div>
  `
})
export class PagerComponent {
  @Input() total = 0;
  /** Preferred: shared page state (the pager updates it). */
  @Input() state?: PageState;
  @Input() page = 1;
  @Input() pageSize = 10;
  /** Page sizes offered (only with [state]); pass [] to hide the selector. */
  @Input() sizes: number[] = [10, 25, 50, 100];
  @Input() showEmpty = false;
  @Output() pageChange = new EventEmitter<number>();
  @Output() pageSizeChange = new EventEmitter<number>();

  get size(): number { return this.state ? this.state.size : this.pageSize; }
  get pages(): number { return Math.max(1, Math.ceil(this.total / this.size)); }
  /** Current page, kept inside 1..pages (a filter may have shrunk the list). */
  get current(): number { return Math.min(Math.max(this.state ? this.state.page : this.page, 1), this.pages); }
  get start(): number { return this.total === 0 ? 0 : (this.current - 1) * this.size + 1; }
  get end(): number { return Math.min(this.current * this.size, this.total); }

  get items(): PageItem[] {
    const total = this.pages;
    const cur = this.current;
    const items: PageItem[] = [];
    if (total <= 1) return items;
    items.push({ label: 'Previous', page: cur - 1, disabled: cur === 1 });
    for (let i = 1; i <= total; i++) {
      if (i === 1 || i === total || Math.abs(i - cur) <= 1) {
        items.push({ label: String(i), page: i, active: i === cur });
      } else if (i === cur - 2 || i === cur + 2) {
        items.push({ label: '…', page: i, dots: true });
      }
    }
    items.push({ label: 'Next', page: cur + 1, disabled: cur === total });
    return items;
  }

  go(event: Event, p: PageItem): void {
    event.preventDefault();
    if (p.disabled || p.dots) return;
    if (this.state) this.state.page = p.page;
    this.pageChange.emit(p.page);
  }

  changeSize(size: number): void {
    if (this.state) {
      this.state.size = size;
      this.state.page = 1;
    }
    this.pageSizeChange.emit(size);
    this.pageChange.emit(1);
  }
}

/** Returns one page of rows. */
export function pageOf<T>(rows: T[], page: number, size = 10): T[] {
  return rows.slice((page - 1) * size, page * size);
}

/** `rows | paginate: page : size` - one page of an in-memory list (page is clamped to the last page). */
@Pipe({ name: 'paginate', standalone: true })
export class PaginatePipe implements PipeTransform {
  transform<T>(rows: readonly T[] | null | undefined, page: number, size: number): T[] {
    if (!rows) return [];
    const pages = Math.max(1, Math.ceil(rows.length / size));
    const p = Math.min(Math.max(page, 1), pages);
    return rows.slice((p - 1) * size, p * size);
  }
}
