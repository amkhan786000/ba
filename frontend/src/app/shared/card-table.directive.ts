import { AfterViewChecked, Directive, ElementRef } from '@angular/core';

/**
 * Phones show every table row as a card (see "Tables as cards on phones" in styles.scss). Each cell needs its
 * column's header as a label, so this copies the <th> text of each column to the cells' data-label attribute.
 *
 * Applied automatically to every <table class="table ..."> in components that import it. It runs after Angular
 * renders the view and only writes attributes that changed; writing an attribute doesn't trigger change
 * detection, so it can't loop (unlike a MutationObserver, which did). Cells with no header text (e.g. action
 * buttons) get no label. Add class "no-cards" to a table to keep it as a table on phones.
 */
@Directive({
  selector: 'table.table',
  standalone: true
})
export class CardTableDirective implements AfterViewChecked {
  constructor(private el: ElementRef<HTMLTableElement>) {}

  ngAfterViewChecked(): void {
    const table = this.el.nativeElement;
    const headerRow = table.tHead?.rows[table.tHead.rows.length - 1];
    if (!headerRow) return;

    // Column index -> header text (a header cell with colspan covers several columns).
    const labels: string[] = [];
    for (const th of Array.from(headerRow.cells)) {
      const text = (th.textContent ?? '').replace(/\s+/g, ' ').trim();
      for (let i = 0; i < th.colSpan; i++) labels.push(text);
    }

    for (const body of Array.from(table.tBodies)) {
      for (const row of Array.from(body.rows)) {
        let col = 0;
        for (const cell of Array.from(row.cells)) {
          // A cell spanning the whole row ("No records found") is a message, not a value.
          const label = cell.colSpan > 1 ? '' : labels[col] ?? '';
          if (cell.getAttribute('data-label') !== label) cell.setAttribute('data-label', label);
          col += cell.colSpan;
        }
      }
    }
  }
}
