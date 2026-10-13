import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AppDatePipe } from '../app-date';
import { ColumnChartComponent, SeriesData } from './column-chart.component';
import { StatTileComponent } from './stat-tile.component';
import { inr } from './money';

/** GET /admin/books/{rahbar|reb}/balance: one book's money (Rahbar's or REB's, never both). */
export interface FundBalance {
  received: number; spent: number; balance: number;
  receivedThisYear: number; spentThisYear: number; receivedThisMonth: number; spentThisMonth: number;
  year: number; lastEntry: string | null; months: SeriesData; closingBalance: number[];
}

/**
 * Rahbar funds at a glance: balance in hand, received and spent (since the first entry, this year, this month) and
 * the last 12 months. Used on Rahbar Income & Expenses, the Dashboard and the Chapter Dashboard.
 */
@Component({
  selector: 'app-fund-summary',
  standalone: true,
  imports: [CommonModule, RouterLink, AppDatePipe, ColumnChartComponent, StatTileComponent],
  styles: [`
    .tiles { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 12px; margin-bottom: 16px; }
    .card .header-title small { text-transform: none; font-weight: normal; color: #6b776f; }
  `],
  template: `
    <ng-container *ngIf="fund as f">
      <div class="tiles">
        <app-stat-tile label="Balance in hand" icon="mdi-safe" [tone]="+f.balance < 0 ? 'danger' : 'success'" [value]="money(f.balance)"
                       [sub]="'received ' + money(f.received) + ' · spent ' + money(f.spent)"
                       [link]="link ? linkPath : null" linkText="Income & Expenses"></app-stat-tile>
        <app-stat-tile [label]="'Received in ' + f.year" icon="mdi-arrow-down-bold-circle" tone="primary" [value]="money(f.receivedThisYear)"
                       [sub]="'this month: ' + money(f.receivedThisMonth)"></app-stat-tile>
        <app-stat-tile [label]="'Spent in ' + f.year" icon="mdi-arrow-up-bold-circle" tone="warning" [value]="money(f.spentThisYear)"
                       [sub]="'this month: ' + money(f.spentThisMonth)"></app-stat-tile>
        <app-stat-tile *ngIf="showLast" label="Last entry" icon="mdi-calendar-check" tone="info"
                       [value]="f.lastEntry ? (f.lastEntry | appDate) : 'None yet'" sub="books up to date until"></app-stat-tile>
      </div>
      <div class="card" *ngIf="chart">
        <div class="card-body">
          <h4 class="header-title mb-3">Received and spent <small>last 12 months</small></h4>
          <app-column-chart [data]="f.months" [money]="true" [colours]="['#15803d', '#c0262d']"
                            emptyText="Nothing recorded in the last 12 months."></app-column-chart>
        </div>
      </div>
    </ng-container>
  `
})
export class FundSummaryComponent {
  @Input() fund: FundBalance | null | undefined;
  /** Show the 12-month chart. */
  @Input() chart = true;
  /** Link the balance tile to Rahbar Income & Expenses. */
  @Input() link = true;
  /** The book's page: Rahbar's or REB's Income & Expenses. */
  @Input() linkPath = '/admin/finance';
  @Input() showLast = true;
  readonly money = inr;
}
