import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, from, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private base = environment.apiBaseUrl;

  constructor(private http: HttpClient) {}

  get<T>(path: string, params?: Record<string, unknown>) {
    return this.http.get<T>(`${this.base}${path}`, { params: this.clean(params) as never });
  }
  post<T>(path: string, body: unknown) {
    return this.http.post<T>(`${this.base}${path}`, body);
  }
  put<T>(path: string, body: unknown) {
    return this.http.put<T>(`${this.base}${path}`, body);
  }
  delete<T>(path: string) {
    return this.http.delete<T>(`${this.base}${path}`);
  }

  /**
   * Downloads a file (reports etc.) and saves it in the browser.
   * Errors come back as JSON inside a Blob, so they're unwrapped into the usual { error } shape.
   */
  download(path: string, params?: Record<string, unknown>, fallbackName = 'download'): Observable<void> {
    return this.http
      .get(`${this.base}${path}`, { params: this.clean(params) as never, responseType: 'blob', observe: 'response' })
      .pipe(
        switchMap(async (res) => {
          const disposition = res.headers.get('Content-Disposition') ?? '';
          const match = /filename="?([^";]+)"?/.exec(disposition);
          const url = URL.createObjectURL(res.body as Blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = match ? match[1] : fallbackName;
          a.click();
          URL.revokeObjectURL(url);
        }),
        catchError((err: HttpErrorResponse) => {
          if (err.error instanceof Blob) {
            return from(err.error.text()).pipe(
              switchMap((text) => {
                let body: unknown = { error: text };
                try { body = JSON.parse(text); } catch { /* not JSON */ }
                return throwError(() => ({ ...err, error: body }));
              })
            );
          }
          return throwError(() => err);
        })
      );
  }

  private clean(params?: Record<string, unknown>) {
    if (!params) return undefined;
    const out: Record<string, string> = {};
    for (const [k, v] of Object.entries(params)) {
      if (v !== null && v !== undefined && v !== '') out[k] = String(v);
    }
    return out;
  }
}
