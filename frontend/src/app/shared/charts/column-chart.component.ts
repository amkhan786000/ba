import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CHART_COLOURS, inr, inrShort, short } from './money';

export interface Series { name: string; values: (number | string)[] }
export interface SeriesData { labels: string[]; series: Series[] }

/** Vertical bars per label (e.g. month), one bar per series side by side, with a legend and hover values. */
@Component({
  selector: 'app-column-chart',
  standalone: true,
  imports: [CommonModule],
  styles: [`
    svg { width: 100%; height: auto; display: block; }
    .grid { stroke: #e5e9e4; }
    .axis { font-size: 11px; fill: #6b776f; }
    rect.bar { transition: opacity .15s; }
    rect.bar:hover { opacity: .75; }
    .legend { display: flex; flex-wrap: wrap; gap: 14px; font-size: 12px; color: #3d4a43; margin-top: 6px; }
    .legend i { display: inline-block; width: 10px; height: 10px; border-radius: 2px; margin-right: 5px; vertical-align: -1px; }
    .empty { color: #6b776f; font-size: 13px; padding: 40px 0; text-align: center; }
  `],
  template: `
    <div *ngIf="isEmpty" class="empty">{{ emptyText }}</div>
    <ng-container *ngIf="!isEmpty">
      <svg [attr.viewBox]="'0 0 ' + W + ' ' + H" role="img" [attr.aria-label]="ariaLabel">
        <g *ngFor="let t of ticks">
          <line class="grid" [attr.x1]="L" [attr.x2]="W" [attr.y1]="y(t)" [attr.y2]="y(t)"></line>
          <text class="axis" [attr.x]="L - 6" [attr.y]="y(t) + 4" text-anchor="end">{{ axis(t) }}</text>
        </g>
        <g *ngFor="let label of data!.labels; let i = index">
          <rect *ngFor="let s of data!.series; let j = index" class="bar"
                [attr.x]="barX(i, j)" [attr.width]="barW" [attr.y]="y(val(s, i))" [attr.height]="H - B - y(val(s, i))"
                [attr.fill]="colour(j)" rx="2">
            <title>{{ label }} · {{ s.name }}: {{ text(val(s, i)) }}</title>
          </rect>
          <text *ngIf="i % labelStep === 0" class="axis" [attr.x]="L + slot * i + slot / 2" [attr.y]="H - B + 15" text-anchor="middle">{{ label }}</text>
        </g>
      </svg>
      <div class="legend" *ngIf="data!.series.length > 1">
        <span *ngFor="let s of data!.series; let j = index"><i [style.background]="colour(j)"></i>{{ s.name }}<ng-container *ngIf="!unit">: <strong>{{ text(sum(s)) }}</strong></ng-container></span>
      </div>
    </ng-container>
  `
})
export class ColumnChartComponent {
  @Input() data: SeriesData | null | undefined;
  /** Amounts in rupees (axis and tooltips as ₹). */
  @Input() money = false;
  /** Shown after values, e.g. "%". */
  @Input() unit = '';
  /** Fixed top of the scale (e.g. 100 for percentages). */
  @Input() max100 = false;
  @Input() emptyText = 'Nothing yet for this period.';
  @Input() ariaLabel = 'Chart';
  /** Colours by series index (defaults to the theme palette). */
  @Input() colours: string[] = CHART_COLOURS;

  readonly W = 640; readonly H = 230; readonly L = 46; readonly B = 22; readonly T = 8;

  get isEmpty(): boolean { return !this.data || !this.data.series.some((s) => s.values.some((v) => Number(v) > 0)); }
  get slot(): number { return (this.W - this.L) / Math.max(1, this.data!.labels.length); }
  /** With many bars (e.g. 30 days) only every n-th label fits under the axis. */
  get labelStep(): number { return Math.max(1, Math.ceil(this.data!.labels.length / 12)); }
  get barW(): number { return Math.max(3, (this.slot * 0.72) / this.data!.series.length); }
  private get max(): number {
    if (this.max100) return 100;
    const m = Math.max(1, ...this.data!.series.flatMap((s) => s.values.map((v) => Number(v) || 0)));
    const step = Math.pow(10, Math.floor(Math.log10(m)));
    return Math.ceil(m / step) * step;
  }
  get ticks(): number[] { const m = this.max; return [0, m / 4, m / 2, (3 * m) / 4, m]; }

  y(v: number): number { return this.T + (this.H - this.B - this.T) * (1 - v / this.max); }
  barX(i: number, j: number): number { return this.L + this.slot * i + this.slot * 0.14 + j * this.barW; }
  val(s: Series, i: number): number { return Number(s.values[i]) || 0; }
  sum(s: Series): number { return s.values.reduce((a: number, v) => a + (Number(v) || 0), 0); }
  colour(j: number): string { return this.colours[j % this.colours.length]; }
  axis(v: number): string { return this.money ? inrShort(v) : short(v) + this.unit; }
  text(v: number): string { return this.money ? inr(v) : v.toLocaleString('en-IN') + this.unit; }
}
