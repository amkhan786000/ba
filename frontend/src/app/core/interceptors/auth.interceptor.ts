import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, from, throwError } from 'rxjs';
import { ACTING_KEY, AuthService } from '../services/auth.service';

const TOKEN_KEY = 'rahbar_token';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const auth = inject(AuthService);
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
    // "View as" one of the user's roles: the server then answers with exactly that role's access.
    let acting: string | null = null;
    try { acting = sessionStorage.getItem(ACTING_KEY); } catch { /* ignore */ }
    if (acting) req = req.clone({ setHeaders: { 'X-Acting-Role': acting } });
  }
  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      // The backend refuses everything but a password change until the default password is replaced.
      const redirect = () => {
        auth.updateUser({ mustChangePassword: true });
        router.navigate(['/change-password']);
      };
      if (err.status === 403 && err.error?.code === 'PASSWORD_CHANGE_REQUIRED') {
        redirect();
      } else if (err.status === 403 && err.error instanceof Blob) {
        // File downloads get the error body as a Blob.
        from(err.error.text()).subscribe((text) => { if (text.includes('PASSWORD_CHANGE_REQUIRED')) redirect(); });
      }
      return throwError(() => err);
    })
  );
};
