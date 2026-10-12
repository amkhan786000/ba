import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface Stage { label: string; value: number | string }

/** Stages of a process (e.g. received → interview → accepted → admitted) with how many go on to the next one. */
@Component({
  selector: 'app-funnel-chart',
  standalone: true,
  imports: [CommonModule],
  styles: [`
    .stage { display: grid; grid-template-columns: 90px 1fr 70px; align-items: center; gap: 10px; margin-bottom: 8px; }
    .label { font-size: 13px; color: #3d4a43; }
    .track { display: flex; justify-content: center; }
    .fill { height: 28px; border-radius: 4px; background: #4f7a00; color: #fff; font-weight: 600; font-size: 13px;
            display: flex; align-items: center; justify-content: center; min-width: 32px; }
    .conv { font-size: 12px; color: #6b776f; text-align: right; }
    .empty { color: #6b776f; font-size: 13px; padding: 40px 0; text-align: center; }
  `],
  template: `
    <div *ngIf="!first" class="empty">{{ emptyText }}</div>
    <ng-container *ngIf="first">
      <div class="stage" *ngFor="let s of data; let i = index">
        <span class="label">{{ s.label }}</span>
        <span class="track"><span class="fill" [style.width.%]="width(s)" [style.opacity]="1 - i * 0.15">{{ s.value }}</span></span>
        <span class="conv" [title]="i ? 'Of the stage before' : ''">{{ i ? conversion(i) + '%' : '' }}</span>
      </div>
    </ng-container>
  `
})
export class FunnelChartComponent {
  @Input() data: Stage[] = [];
  @Input() emptyText = 'No applications in this period.';

  get first(): number { return Number(this.data?.[0]?.value) || 0; }
  width(s: Stage): number { return Math.max(8, ((Number(s.value) || 0) / (this.first || 1)) * 100); }
  conversion(i: number): number {
    const before = Number(this.data[i - 1].value) || 0;
    return before ? Math.round(((Number(this.data[i].value) || 0) / before) * 100) : 0;
  }
}
