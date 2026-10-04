import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface BarDatum { label: string; value: number }

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
    .bar-row:hover { background: #f5f6f8; }
    .bar-label { font-size: 13px; color: #6c757d; text-transform: capitalize; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .bar-track { display: flex; align-items: center; gap: 8px; min-width: 0; }
    .bar-fill { height: 18px; background: #188ae2; border-radius: 0 4px 4px 0; min-width: 2px; transition: opacity .15s; }
    .bar-row:hover .bar-fill { opacity: .85; }
    .bar-value { font-size: 13px; font-weight: 600; color: #343a40; font-variant-numeric: tabular-nums; }
    .empty { color: #98a6ad; font-size: 13px; padding: 24px 0; text-align: center; }
  `],
  template: `
    <div *ngIf="!data.length" class="empty">No data for this year.</div>
    <div class="bars" *ngIf="data.length" role="list">
      <div class="bar-row" *ngFor="let d of data" role="listitem" [title]="d.label + ': ' + d.value + ' (' + pct(d) + '%)'">
        <span class="bar-label">{{ d.label }}</span>
        <span class="bar-track">
          <span class="bar-fill" [style.width.%]="width(d)"></span>
          <span class="bar-value">{{ d.value }}</span>
        </span>
      </div>
    </div>
  `
})
export class BarChartComponent {
  @Input() data: BarDatum[] = [];

  private get max(): number { return Math.max(1, ...this.data.map((d) => Number(d.value) || 0)); }
  private get total(): number { return this.data.reduce((a, d) => a + (Number(d.value) || 0), 0) || 1; }

  /** Leaves room for the value label at the end of the longest bar. */
  width(d: BarDatum): number { return ((Number(d.value) || 0) / this.max) * 85; }
  pct(d: BarDatum): number { return Math.round(((Number(d.value) || 0) / this.total) * 100); }
}
