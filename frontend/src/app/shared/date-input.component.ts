import { Component, ElementRef, HostBinding, Input, ViewChild, forwardRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { dateFormat, formatAppDate } from './app-date';

const MONTHS = ['jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec'];

/**
 * A date field that shows and accepts dates in the app's date format (Administration > Application Settings,
 * DD/MM/YYYY by default), instead of the browser's own date box, which follows the computer's region. The calendar
 * button opens the browser's calendar. The value stays yyyy-MM-dd, as with <input type="date">:
 *   <app-date-input id="paid-on" [(ngModel)]="paymentDate" [max]="today"></app-date-input>
 */
@Component({
  selector: 'app-date-input',
  standalone: true,
  imports: [CommonModule],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => DateInputComponent), multi: true }],
  styles: [`
    :host { display: block; position: relative; }
    .date-input { display: flex; align-items: stretch; }
    .date-input input.text { flex: 1 1 auto; min-width: 0; border-top-right-radius: 0; border-bottom-right-radius: 0; }
    .date-input .pick { border: 1px solid #ced4da; border-left: 0; background: #fff; padding: 0 .6rem; border-radius: 0 .25rem .25rem 0; color: #6c757d; }
    .date-input .pick:disabled { background: #e9ecef; }
    .native { position: absolute; left: 0; bottom: 0; width: 1px; height: 1px; opacity: 0; pointer-events: none; border: 0; padding: 0; }
  `],
  template: `
    <div class="date-input">
      <input #text type="text" class="form-control text" [class.form-control-sm]="small" [class.is-invalid]="invalid"
             [id]="id" [attr.name]="name" [attr.aria-label]="ariaLabel" [placeholder]="placeholder" [value]="display" [disabled]="disabled"
             [required]="required" inputmode="numeric" autocomplete="off"
             (input)="typed($any($event.target).value)" (blur)="tidy(); onTouched()">
      <button type="button" class="pick" [class.btn-sm]="small" (click)="openPicker()" [disabled]="disabled" aria-label="Choose a date from a calendar" tabindex="-1">
        <i class="mdi mdi-calendar-month-outline"></i></button>
      <input #native type="date" class="native" tabindex="-1" aria-hidden="true" [min]="min || ''" [max]="max || ''" [value]="value || ''"
             (change)="picked($any($event.target).value)">
    </div>
  `
})
export class DateInputComponent implements ControlValueAccessor {
  /** The id goes on the visible field (so <label for> works), not on this element. */
  @Input() id: string | null = null;
  @HostBinding('attr.id') get hostId(): null { return null; }
  @Input() name: string | null = null;
  @Input() min: string | null = null;
  @Input() max: string | null = null;
  @Input() required = false;
  @Input() small = false;
  @Input() ariaLabel: string | null = null;
  @Input() disabled = false;

  @ViewChild('native') native!: ElementRef<HTMLInputElement>;

  value: string | null = null;
  display = '';
  invalid = false;
  private onChange: (v: string | null) => void = () => {};
  onTouched: () => void = () => {};

  get placeholder(): string {
    const f = dateFormat();
    return f === 'D MMM YYYY' ? 'e.g. 31 Dec 2026' : f;
  }

  writeValue(v: string | null): void {
    this.value = v || null;
    this.display = this.value ? formatAppDate(this.value) : '';
    this.invalid = false;
  }
  registerOnChange(fn: (v: string | null) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }
  setDisabledState(d: boolean): void { this.disabled = d; }

  /** Typing: the value changes as soon as the text is a complete, valid date (or empty). */
  typed(text: string): void {
    this.display = text;
    const t = text.trim();
    if (!t) { this.set(null); this.invalid = false; return; }
    const iso = parseDate(t, dateFormat());
    this.invalid = !iso || !this.inRange(iso);
    if (iso && !this.invalid) this.set(iso);
  }

  /** Leaving the field: show the date neatly in the app's format. */
  tidy(): void {
    if (this.value && !this.invalid) this.display = formatAppDate(this.value);
  }

  picked(iso: string): void {
    this.invalid = false;
    this.set(iso || null);
    this.display = iso ? formatAppDate(iso) : '';
  }

  openPicker(): void {
    const el = this.native?.nativeElement;
    if (!el) return;
    const withPicker = el as HTMLInputElement & { showPicker?: () => void };
    try {
      if (withPicker.showPicker) { withPicker.showPicker(); return; }
    } catch { /* not allowed here: fall back */ }
    el.focus();
    el.click();
  }

  private set(v: string | null): void {
    if (v === this.value) return;
    this.value = v;
    this.onChange(v);
  }

  private inRange(iso: string): boolean {
    return (!this.min || iso >= this.min) && (!this.max || iso <= this.max);
  }
}

/** "31/12/2026", "31-12-2026", "31.12.26", "2026-12-31" or "31 Dec 2026" (by the app's format) -> "2026-12-31"; null when invalid. */
export function parseDate(text: string, format: string): string | null {
  const t = text.trim().toLowerCase();
  let d: number, m: number, y: number;
  const named = /^(\d{1,2})[\s.-]+([a-z]{3,})[\s.,-]+(\d{2,4})$/.exec(t);
  if (named) {
    d = +named[1]; m = MONTHS.indexOf(named[2].slice(0, 3)) + 1; y = +named[3];
  } else {
    const parts = t.split(/[^0-9]+/).filter(Boolean).map(Number);
    if (parts.length !== 3) return null;
    if (format === 'YYYY-MM-DD' || String(t.split(/[^0-9]+/)[0]).length === 4) [y, m, d] = parts;
    else if (format === 'MM/DD/YYYY') [m, d, y] = parts;
    else [d, m, y] = parts;
  }
  if (y < 100) y += 2000;
  if (!m || m < 1 || m > 12 || d < 1 || y < 1900 || y > 2200) return null;
  const date = new Date(y, m - 1, d);
  if (date.getMonth() !== m - 1 || date.getDate() !== d) return null; // e.g. 31/02
  const p = (n: number) => String(n).padStart(2, '0');
  return `${y}-${p(m)}-${p(d)}`;
}
