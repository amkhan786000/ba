import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CHART_COLOURS, short } from './money';
import { Series, SeriesData } from './column-chart.component';

/**
 * One line per series across the labels (e.g. years), with a dot and hover value at every point and a legend with
 * each series' latest value and its change from the point before, so growth or decline reads at a glance. Counts only.
 */
@Component({
  selector: 'app-line-chart',
  standalone: true,
  imports: [CommonModule],
  styles: [`
    svg { width: 100%; height: auto; display: block; }
    .grid { stroke: #e5e9e4; }
    .axis { font-size: 11px; fill: #6b776f; }
    polyline { fill: none; stroke-width: 2.5; stroke-linejoin: round; stroke-linecap: round; }
    circle { stroke: #fff; stroke-width: 1.5; }
    .legend { display: flex; flex-wrap: wrap; gap: 6px 14px; font-size: 12px; color: #3d4a43; margin-top: 6px; }
    .legend i { display: inline-block; width: 12px; height: 3px; border-radius: 2px; margin-right: 5px; vertical-align: 3px; }
    .up { color: #15803d; } .down { color: #c0262d; }
    .empty { color: #6b776f; font-size: 13px; padding: 40px 0; text-align: center; }
  `],
  template: `
    <div *ngIf="isEmpty" class="empty">{{ emptyText }}</div>
    <ng-container *ngIf="!isEmpty">
      <svg [attr.viewBox]="'0 0 ' + W + ' ' + H" role="img" [attr.aria-label]="ariaLabel">
        <g *ngFor="let t of ticks">
          <line class="grid" [attr.x1]="L" [attr.x2]="W - R" [attr.y1]="y(t)" [attr.y2]="y(t)"></line>
          <text class="axis" [attr.x]="L - 6" [attr.y]="y(t) + 4" text-anchor="end">{{ short(t) }}</text>
        </g>
        <text *ngFor="let label of data!.labels; let i = index" class="axis" [attr.x]="x(i)" [attr.y]="H - B + 15" text-anchor="middle">{{ label }}</text>
        <g *ngFor="let s of data!.series; let j = index">
          <polyline [attr.points]="points(s)" [attr.stroke]="colour(j)"></polyline>
          <circle *ngFor="let v of s.values; let i = index" [attr.cx]="x(i)" [attr.cy]="y(val(s, i))" r="3.5" [attr.fill]="colour(j)">
            <title>{{ data!.labels[i] }} · {{ s.name }}: {{ val(s, i) }}</title>
          </circle>
        </g>
      </svg>
      <div class="legend">
        <span *ngFor="let s of data!.series; let j = index"><i [style.background]="colour(j)"></i>{{ s.name }}: <strong>{{ last(s) }}</strong>
          <span *ngIf="change(s) as c" [class.up]="c > 0" [class.down]="c < 0">({{ c > 0 ? '▲' : '▼' }} {{ c > 0 ? c : -c }})</span></span>
      </div>
    </ng-container>
  `
})
export class LineChartComponent {
  @Input() data: SeriesData | null | undefined;
  @Input() emptyText = 'Nothing yet.';
  @Input() ariaLabel = 'Chart';
  @Input() colours: string[] = CHART_COLOURS;

  readonly W = 640; readonly H = 240; readonly L = 40; readonly R = 14; readonly B = 22; readonly T = 10;
  readonly short = short;

  get isEmpty(): boolean { return !this.data || !this.data.series.some((s) => s.values.some((v) => Number(v) > 0)); }
  /** A multiple of 4 so the gridlines fall on whole numbers. */
  private get max(): number {
    const m = Math.max(1, ...this.data!.series.flatMap((s) => s.values.map((v) => Number(v) || 0)));
    const step = Math.pow(10, Math.floor(Math.log10(m)));
    return Math.ceil(Math.ceil(m / step) * step / 4) * 4;
  }
  get ticks(): number[] { const m = this.max; return [0, m / 4, m / 2, (3 * m) / 4, m]; }

  x(i: number): number {
    const n = this.data!.labels.length;
    return n <= 1 ? (this.L + this.W - this.R) / 2 : this.L + 10 + (i * (this.W - this.R - this.L - 20)) / (n - 1);
  }
  y(v: number): number { return this.T + (this.H - this.B - this.T) * (1 - v / this.max); }
  val(s: Series, i: number): number { return Number(s.values[i]) || 0; }
  points(s: Series): string { return s.values.map((_, i) => `${this.x(i)},${this.y(this.val(s, i))}`).join(' '); }
  last(s: Series): number { return this.val(s, s.values.length - 1); }
  /** Latest value minus the one before (0 or null: no arrow). */
  change(s: Series): number | null { return s.values.length < 2 ? null : this.last(s) - this.val(s, s.values.length - 2); }
  colour(j: number): string { return this.colours[j % this.colours.length]; }
}
