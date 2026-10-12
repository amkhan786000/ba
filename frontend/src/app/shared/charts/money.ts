/** Chart colours (the Rahbar theme: brand green, teal, amber, violet, red, ...), in the order series and slices use them; neighbours contrast. */
export const CHART_COLOURS = ['#4f7a00', '#0e7490', '#d97706', '#6d28d9', '#c0262d', '#db2777', '#9ccc3d', '#6b776f'];

/** ₹1,23,456 (Indian grouping, no paise). */
export function inr(v: number | string | null | undefined): string {
  const n = Number(v) || 0;
  return '₹' + Math.round(n).toLocaleString('en-IN');
}

/** Short amount for axes and tight spaces: ₹950, ₹12K, ₹3.4L, ₹1.2Cr. */
export function inrShort(v: number | string | null | undefined): string {
  return '₹' + short(Number(v) || 0);
}

/** Short number: 950, 12K, 3.4L, 1.2Cr. */
export function short(n: number): string {
  const a = Math.abs(n);
  const fmt = (x: number) => (x >= 10 ? Math.round(x).toString() : x.toFixed(1).replace(/\.0$/, ''));
  if (a >= 1e7) return fmt(n / 1e7) + 'Cr';
  if (a >= 1e5) return fmt(n / 1e5) + 'L';
  if (a >= 1e3) return fmt(n / 1e3) + 'K';
  return Math.round(n).toString();
}
