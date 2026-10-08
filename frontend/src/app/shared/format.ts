/**
 * Helpers for values coming straight out of JDBC maps, where dates may arrive as
 * ISO strings ("2024-05-01T00:00:00"), plain dates ("2024-05-01") or epoch millis.
 */
export function asDate(v: unknown): Date | null {
  if (v === null || v === undefined || v === '') return null;
  if (v instanceof Date) return v;
  if (typeof v === 'number') return new Date(v);
  const s = String(v);
  // Treat a bare yyyy-MM-dd as a local date, like the Flask pages did.
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s);
  if (m) return new Date(+m[1], +m[2] - 1, +m[3]);
  const d = new Date(s);
  return isNaN(d.getTime()) ? null : d;
}

/** Same output as JavaScript's toLocaleDateString(), which the Flask pages used. */
export function localDate(v: unknown, fallback = '--'): string {
  const d = asDate(v);
  return d ? d.toLocaleDateString() : fallback;
}

/** yyyy-MM-dd for <input type="date">. */
export function isoDate(v: unknown): string {
  const d = asDate(v);
  if (!d) return '';
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

/** Uploaded files are served here (sign-in required). */
export const FILE_PREFIX = '/api/files/';

/**
 * Link to an uploaded file. Files need sign-in (/api/files/<name>); AppComponent opens these links with the
 * user's token, so templates can keep using plain <a [href]> links.
 */
export function uploadUrl(path: unknown): string | null {
  if (!path) return null;
  return FILE_PREFIX + encodeURIComponent(String(path).split(/[\\/]/).pop() ?? '');
}

