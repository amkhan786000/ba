import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { inr } from '../charts/money';

export interface BarDatum { label: string; value: number | string }

/**
 * Single-series horizontal bar chart (counts per category).
 * One colour, value labels at the bar ends, hover tooltip and highlight, no legend needed.
 */
@Component({
  selector: 'app-bar-chart',
  standalone: true,
  imports: [CommonModule],
  styles: [`
    .bars { display: flex; flex-direction: column; gap: 10px; }
    .bar-row { display: grid; grid-template-columns: minmax(90px, 32%) 1fr; align-items: center; gap: 10px; padding: 2px 0; border-radius: 4px; cursor: default; }
    .bar-row:hover { background: #f4f6f2; }
    .bar-label { font-size: 13px; color: #3d4a43; text-transform: capitalize; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .bar-track { display: flex; align-items: center; gap: 8px; min-width: 0; }
    .bar-fill { height: 18px; background: #4f7a00; border-radius: 0 4px 4px 0; min-width: 2px; transition: opacity .15s; }
    .bar-row:hover .bar-fill { opacity: .85; }
    .bar-value { font-size: 13px; font-weight: 600; color: #17231c; font-variant-numeric: tabular-nums; }
    .empty { color: #6b776f; font-size: 13px; padding: 24px 0; text-align: center; }
  `],
  template: `
    <div *ngIf="!data?.length" class="empty">{{ emptyText }}</div>
    <div class="bars" *ngIf="data?.length" role="list">
      <div class="bar-row" *ngFor="let d of data" role="listitem" [title]="d.label + ': ' + text(d) + (unit ? '' : ' (' + pct(d) + '%)')">
        <span class="bar-label">{{ d.label }}</span>
        <span class="bar-track">
          <span class="bar-fill" [style.width.%]="width(d)" [style.background]="colour"></span>
          <span class="bar-value">{{ text(d) }}</span>
        </span>
      </div>
    </div>
  `
})
export class BarChartComponent {
  @Input() data: BarDatum[] = [];
  /** Values are rupee amounts. */
  @Input() money = false;
  @Input() emptyText = 'No data for this year.';
  @Input() colour = '#4f7a00';
  /** Shown after values, e.g. "%". */
  @Input() unit = '';
  /** Bars against 100 (percentages) instead of the largest value. */
  @Input() max100 = false;

  private get max(): number { return this.max100 ? 100 : Math.max(1, ...this.data.map((d) => Number(d.value) || 0)); }
  private get total(): number { return this.data.reduce((a, d) => a + (Number(d.value) || 0), 0) || 1; }

  /** Leaves room for the value label at the end of the longest bar. */
  width(d: BarDatum): number { return ((Number(d.value) || 0) / this.max) * 85; }
  text(d: BarDatum): string { return this.money ? inr(d.value) : String(d.value) + this.unit; }
  pct(d: BarDatum): number { return Math.round(((Number(d.value) || 0) / this.total) * 100); }
}
