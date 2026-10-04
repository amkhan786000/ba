import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

type PageItem = { label: string; page: number; active?: boolean; disabled?: boolean; dots?: boolean };

/**
 * "Showing x to y of z entries" + Previous / 1 2 … n / Next,
 * the same pagination the Flask pages built by hand in their page scripts.
 */
@Component({
  selector: 'app-pager',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="row mt-3">
      <div class="col-sm-12 col-md-5">
        <div class="text-muted">Showing {{ start }} to {{ end }} of {{ total }} entries</div>
      </div>
      <div class="col-sm-12 col-md-7">
        <nav>
          <ul class="pagination pagination-rounded justify-content-end mb-0">
            <li *ngFor="let p of items" class="page-item" [class.active]="p.active" [class.disabled]="p.disabled || p.dots">
              <span *ngIf="p.dots" class="page-link">...</span>
              <a *ngIf="!p.dots" class="page-link" href="#" (click)="go($event, p)">{{ p.label }}</a>
            </li>
          </ul>
        </nav>
      </div>
    </div>
  `
})
export class PagerComponent {
  @Input() total = 0;
  @Input() page = 1;
  @Input() pageSize = 10;
  @Output() pageChange = new EventEmitter<number>();

  get pages(): number { return Math.ceil(this.total / this.pageSize); }
  get start(): number { return this.total === 0 ? 0 : (this.page - 1) * this.pageSize + 1; }
  get end(): number { return Math.min(this.page * this.pageSize, this.total); }

  get items(): PageItem[] {
    const total = this.pages;
    const cur = this.page;
    const items: PageItem[] = [];
    if (total <= 1) return items;
    items.push({ label: 'Previous', page: cur - 1, disabled: cur === 1 });
    for (let i = 1; i <= total; i++) {
      if (i === 1 || i === total || Math.abs(i - cur) <= 1) {
        items.push({ label: String(i), page: i, active: i === cur });
      } else if (i === cur - 2 || i === cur + 2) {
        items.push({ label: '...', page: i, dots: true });
      }
    }
    items.push({ label: 'Next', page: cur + 1, disabled: cur === total });
    return items;
  }

  go(event: Event, p: PageItem): void {
    event.preventDefault();
    if (p.disabled || p.dots) return;
    this.pageChange.emit(p.page);
  }
}

/** Returns one page of rows. */
export function pageOf<T>(rows: T[], page: number, size = 10): T[] {
  return rows.slice((page - 1) * size, page * size);
}
