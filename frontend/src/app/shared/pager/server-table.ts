import { EMPTY, Observable, Subject, Subscription, catchError, debounceTime, merge, switchMap, tap } from 'rxjs';
import { PageState } from './pager.component';

/** What a server-paged endpoint returns (util/Paging.java), plus anything extra (e.g. summary counts). */
export interface PagedResult<T> { total: number; page: number; size: number; rows: T[] }

/**
 * A table paged on the server. The server filters and sends one page; the screen keeps only that page.
 *   readonly table = new ServerTable<Row>((p) => this.api.get('/admin/x', { q: this.q, ...p }), (e) => (this.error = ...));
 *   (ngModelChange)="table.search()"   - text typed: waits 300 ms, back to page 1
 *   (change)="table.filter()"          - dropdown / card clicked: back to page 1 now
 *   <app-pager [state]="table.pg" [total]="table.total" (pageChange)="table.load()">
 *   after a save: table.load() (same page); in ngOnDestroy: table.destroy()
 * A newer request cancels an older one, so a slow answer never overwrites a newer one.
 */
export class ServerTable<T, R extends PagedResult<T> = PagedResult<T>> {
  readonly pg: PageState;
  rows: T[] = [];
  total = 0;
  /** The whole last answer (for extras such as summary counts). */
  last: R | null = null;
  loading = false;
  private readonly typed = new Subject<void>();
  private readonly now = new Subject<void>();
  private readonly sub: Subscription;

  constructor(fetch: (page: { page: number; size: number }) => Observable<R>, onError: (e: unknown) => void, size = 25) {
    this.pg = new PageState(size);
    this.sub = merge(this.typed.pipe(debounceTime(300)), this.now).pipe(
      tap(() => (this.loading = true)),
      switchMap(() => fetch({ page: this.pg.page - 1, size: this.pg.size }).pipe(
        catchError((e) => { this.loading = false; onError(e); return EMPTY; })))
    ).subscribe((r) => {
      this.loading = false;
      // The last page can empty out (e.g. after a change elsewhere): step back to the new last page.
      if (!r.rows.length && r.total > 0 && this.pg.page > 1) {
        this.pg.page = Math.max(1, Math.ceil(r.total / this.pg.size));
        this.now.next();
        return;
      }
      this.last = r;
      this.rows = r.rows;
      this.total = r.total;
    });
  }

  /** Reloads the current page. */
  load(): void { this.now.next(); }

  /** Text typed in a search box: reload page 1 once typing pauses. */
  search(): void { this.pg.reset(); this.typed.next(); }

  /** A filter changed: reload page 1 now. */
  filter(): void { this.pg.reset(); this.now.next(); }

  destroy(): void { this.sub.unsubscribe(); }
}
