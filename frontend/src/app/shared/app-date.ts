import { Pipe, PipeTransform } from '@angular/core';
import { formatDate } from '@angular/common';
import { asDate } from './format';

/**
 * How dates are shown everywhere (Administration > Application Settings > Date format): DD/MM/YYYY unless an admin
 * chose otherwise. The setting arrives with the user's access (/account/access) and is kept here.
 */
export type DateFormat = 'DD/MM/YYYY' | 'MM/DD/YYYY' | 'YYYY-MM-DD' | 'D MMM YYYY';

const PATTERNS: Record<DateFormat, string> = {
  'DD/MM/YYYY': 'dd/MM/yyyy',
  'MM/DD/YYYY': 'MM/dd/yyyy',
  'YYYY-MM-DD': 'yyyy-MM-dd',
  'D MMM YYYY': 'd MMM yyyy'
};

let current: DateFormat = 'DD/MM/YYYY';

export function setDateFormat(f: string | null | undefined): void {
  if (f && f in PATTERNS) current = f as DateFormat;
}

export function dateFormat(): DateFormat {
  return current;
}

/** A date (or date and time) in the chosen format, e.g. 31/12/2026 or 31/12/2026, 4:05 PM; fallback when empty. */
export function formatAppDate(v: unknown, kind: 'date' | 'datetime' = 'date', fallback = ''): string {
  const d = asDate(v);
  if (!d) return fallback;
  const date = formatDate(d, PATTERNS[current], 'en-US');
  return kind === 'datetime' ? `${date}, ${formatDate(d, 'h:mm a', 'en-US')}` : date;
}

/** `value | appDate` or `value | appDate: 'datetime'`, following the chosen date format. */
@Pipe({ name: 'appDate', standalone: true, pure: false })
export class AppDatePipe implements PipeTransform {
  transform(v: unknown, kind: 'date' | 'datetime' = 'date'): string {
    return formatAppDate(v, kind);
  }
}
