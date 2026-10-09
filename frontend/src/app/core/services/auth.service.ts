import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthUser, Section, canAccess } from '../models/user.model';

export interface AccessInfo {
  roleId: number; roleName: string | null; permissions: string[]; scope: string; chapterId: number | null; rccCenterId: number | null;
  emailMissing: boolean;
}

interface LoginResponse {
  otpRequired: boolean;
  token?: string;
  /** users.id */
  id: number;
  /** The user's code, for display (absent on the OTP step). */
  userId?: string;
  name?: string;
  roleId?: number;
  status?: string;
  message?: string;
  mustChangePassword?: boolean;
  permissions?: string[];
  scope?: string;
}

const TOKEN_KEY = 'rahbar_token';
const USER_KEY = 'rahbar_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  currentUser = signal<AuthUser | null>(this.loadUser());

  constructor(private http: HttpClient) {}

  login(loginMethod: 'email' | 'phone', identifier: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${environment.apiBaseUrl}/auth/login`, { loginMethod, identifier, password });
  }

  verifyOtp(id: number, otp: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${environment.apiBaseUrl}/auth/verify-otp`, { id, otp })
      .pipe(tap((res) => this.persistSession(res)));
  }

  register(payload: unknown): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${environment.apiBaseUrl}/auth/register`, payload);
  }

  /** Emails a reset code (the reply is the same whether or not the email has an account). */
  forgotPassword(email: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${environment.apiBaseUrl}/auth/forgot-password`, { email });
  }

  resetPassword(payload: unknown): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${environment.apiBaseUrl}/auth/reset-password`, payload);
  }

  persistSession(res: LoginResponse): void {
    if (!res.token) return;
    localStorage.setItem(TOKEN_KEY, res.token);
    const user: AuthUser = {
      id: res.id, userId: res.userId ?? '', name: res.name ?? '', roleId: res.roleId ?? 0, status: res.status ?? '',
      mustChangePassword: !!res.mustChangePassword, permissions: res.permissions ?? [], scope: res.scope ?? 'ALL'
    };
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    this.currentUser.set(user);
  }

  /** May the signed-in user view (or manage) the admin section? */
  can(section: Section, level: 'VIEW' | 'EDIT' = 'VIEW'): boolean {
    return canAccess(this.currentUser(), section, level);
  }

  /** Re-reads permissions from the server, so changes made in Roles apply without signing in again. */
  refreshAccess(): Observable<AccessInfo> {
    return this.http.get<AccessInfo>(`${environment.apiBaseUrl}/account/access`).pipe(tap((a) =>
      this.updateUser({ roleName: a.roleName ?? undefined, permissions: a.permissions, scope: a.scope,
        chapterId: a.chapterId, rccCenterId: a.rccCenterId, emailMissing: a.emailMissing })));
  }

  /** Updates the stored user (e.g. after a profile edit or a password change). */
  updateUser(changes: Partial<AuthUser>): void {
    const current = this.currentUser();
    if (!current) return;
    const user = { ...current, ...changes };
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    this.currentUser.set(user);
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.currentUser.set(null);
  }

  get token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  private loadUser(): AuthUser | null {
    const raw = localStorage.getItem(USER_KEY);
    const user: AuthUser | null = raw ? JSON.parse(raw) : null;
    // Sessions saved before user ids became numeric have no id: treat them as signed out.
    if (user && typeof user.id !== 'number') {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
      return null;
    }
    return user;
  }
}
