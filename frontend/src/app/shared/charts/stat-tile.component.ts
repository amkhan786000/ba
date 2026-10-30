import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

/** One headline number on a dashboard, with an optional line under it and a link to the screen behind it. */
@Component({
  selector: 'app-stat-tile',
  standalone: true,
  imports: [CommonModule, RouterLink],
  styles: [`
    :host { display: block; height: 100%; }
    .tile { background: var(--surface, #fff); border-radius: var(--radius-sm, 10px); padding: 16px 18px; height: 100%; box-shadow: var(--shadow-sm); border: 1px solid var(--line, #e5e9e4);
            border-left: 4px solid var(--tone); display: flex; flex-direction: column; gap: 4px; }
    .label { font-size: 12px; text-transform: uppercase; letter-spacing: .04em; color: #6b776f; display: flex; align-items: center; gap: 6px; }
    .label .mdi { font-size: 16px; color: var(--tone); }
    .value { font-size: 26px; font-weight: 700; color: #17231c; line-height: 1.2; font-variant-numeric: tabular-nums; }
    .sub { font-size: 12px; color: #3d4a43; }
    .sub.up { color: #15803d; } .sub.down { color: #c0262d; }
    .links { margin-top: auto; display: flex; gap: 14px; flex-wrap: wrap; }
    a.more { font-size: 12px; }
  `],
  template: `
    <div class="tile" [style.--tone]="toneColour">
      <span class="label"><i *ngIf="icon" class="mdi" [ngClass]="icon"></i>{{ label }}</span>
      <span class="value">{{ value ?? '–' }}</span>
      <span *ngIf="sub" class="sub" [class.up]="trend === 'up'" [class.down]="trend === 'down'">{{ sub }}</span>
      <div *ngIf="link" class="links">
        <a class="more" [routerLink]="link">{{ linkText }} →</a>
        <a *ngIf="link2" class="more" [routerLink]="link2" [queryParams]="link2Query">{{ link2Text }} →</a>
      </div>
    </div>
  `
})
export class StatTileComponent {
  @Input() label = '';
  @Input() value: string | number | null | undefined;
  @Input() sub: string | null | undefined;
  @Input() icon = '';
  @Input() link: string | null = null;
  @Input() linkText = 'Open';
  /** An optional second link beside the first, e.g. to a pre-filtered list. */
  @Input() link2: string | null = null;
  @Input() link2Text = 'Open';
  @Input() link2Query: Record<string, string> | null = null;
  @Input() tone: 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'purple' = 'primary';
  @Input() trend: 'up' | 'down' | null = null;

  get toneColour(): string {
    return { primary: '#4f7a00', success: '#15803d', warning: '#d97706', danger: '#c0262d', info: '#0e7490', purple: '#6d28d9' }[this.tone];
  }
}
