import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CHART_COLOURS, inr, inrShort } from './money';

export interface Slice { label: string; value: number | string }

/** Parts of a whole: a ring with the total in the middle and a legend with values and shares. */
@Component({
  selector: 'app-donut-chart',
  standalone: true,
  imports: [CommonModule],
  styles: [`
    .wrap { display: flex; align-items: center; gap: 18px; flex-wrap: wrap; }
    svg { width: 150px; height: 150px; flex: none; }
    circle.slice { transition: opacity .15s; }
    circle.slice:hover { opacity: .75; }
    .total { font-size: 22px; font-weight: 700; fill: #17231c; }
    .caption { font-size: 10px; fill: #6b776f; text-transform: uppercase; letter-spacing: .05em; }
    ul { list-style: none; padding: 0; margin: 0; flex: 1; min-width: 150px; font-size: 13px; }
    li { display: flex; align-items: center; gap: 6px; padding: 3px 0; }
    li i { width: 10px; height: 10px; border-radius: 2px; flex: none; }
    li span { flex: 1; color: #3d4a43; }
    li em { font-style: normal; color: #6b776f; width: 38px; text-align: right; }
    .empty { color: #6b776f; font-size: 13px; padding: 40px 0; text-align: center; }
  `],
  template: `
    <div *ngIf="!total" class="empty">{{ emptyText }}</div>
    <div class="wrap" *ngIf="total">
      <svg viewBox="0 0 42 42" role="img" [attr.aria-label]="caption">
        <circle cx="21" cy="21" r="15.915" fill="none" stroke="#e5e9e4" stroke-width="5"></circle>
        <circle *ngFor="let s of arcs; let j = index" class="slice" cx="21" cy="21" r="15.915" fill="none" stroke-width="5"
                [attr.stroke]="colour(j)" [attr.stroke-dasharray]="s.pct + ' ' + (100 - s.pct)" [attr.stroke-dashoffset]="s.offset">
          <title>{{ s.label }}: {{ text(s.value) }} ({{ s.pct | number: '1.0-0' }}%)</title>
        </circle>
        <text x="21" y="21.5" text-anchor="middle" class="total" style="font-size: 7px">{{ money ? short(total) : total }}</text>
        <text x="21" y="27" text-anchor="middle" class="caption" style="font-size: 2.6px">{{ caption }}</text>
      </svg>
      <ul>
        <li *ngFor="let s of data; let j = index">
          <i [style.background]="colour(j)"></i><span>{{ s.label }}</span><strong>{{ text(+s.value || 0) }}</strong>
          <em>{{ share(s) }}%</em>
        </li>
      </ul>
    </div>
  `
})
export class DonutChartComponent {
  @Input() data: Slice[] = [];
  @Input() caption = 'total';
  @Input() money = false;
  @Input() emptyText = 'Nothing yet.';
  @Input() colours: string[] = CHART_COLOURS;

  readonly short = inrShort;

  get total(): number { return (this.data || []).reduce((a, s) => a + (Number(s.value) || 0), 0); }
  /** Slices start at 12 o'clock and go clockwise. */
  get arcs(): { label: string; value: number; pct: number; offset: number }[] {
    let done = 0;
    return (this.data || []).map((s) => {
      const value = Number(s.value) || 0;
      const pct = (value / this.total) * 100;
      const arc = { label: s.label, value, pct, offset: 25 - done };
      done += pct;
      return arc;
    });
  }
  share(s: Slice): number { return Math.round(((Number(s.value) || 0) / (this.total || 1)) * 100); }
  colour(j: number): string { return this.colours[j % this.colours.length]; }
  text(v: number): string { return this.money ? inr(v) : v.toLocaleString('en-IN'); }
}
