import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthUser, ROLE, Section, canAccess, hasRole } from '../models/user.model';
import { setDateFormat } from '../../shared/app-date';

export interface AccessInfo {
  roleId: number; roleIds?: number[]; roleName: string | null; actingRoleId?: number | null; dateFormat?: string;
  roles?: { id: number; name: string; main: boolean; portal: boolean }[]; permissions: string[]; scope: string; chapterId: number | null; rccCenterId: number | null;
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
  roleIds?: number[];
  status?: string;
  message?: string;
  mustChangePassword?: boolean;
  permissions?: string[];
  scope?: string;
}

const TOKEN_KEY = 'rahbar_token';
const USER_KEY = 'rahbar_user';
/** "View as" role for this browser tab (sent to the server as X-Acting-Role). */
export const ACTING_KEY = 'rahbar_acting_role';

@Injectable({ providedIn: 'root' })
export class AuthService {
  currentUser = signal<AuthUser | null>(this.loadUser());

  constructor(private http: HttpClient) {
    setDateFormat(this.currentUser()?.dateFormat); // the last known setting until /account/access answers
    // Every tab shares one stored sign-in. When another tab signs in as someone else (or signs out), this tab must
    // follow: otherwise it keeps showing the old user and their buttons while its requests go out as the new one.
    if (typeof window !== 'undefined') {
      window.addEventListener('storage', (e) => {
        if (e.key !== TOKEN_KEY && e.key !== USER_KEY && e.key !== null) return; // null: storage was cleared
        const before = this.currentUser();
        const after = this.loadUser();
        if (!after || !localStorage.getItem(TOKEN_KEY)) {
          if (before) location.assign('/login'); // signed out in another tab
        } else if (!before || before.id !== after.id) {
          location.reload(); // another person signed in: start over as them
        } else {
          this.currentUser.set(after); // same person, e.g. profile or permissions refreshed elsewhere
        }
      });
    }
  }

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
      id: res.id, userId: res.userId ?? '', name: res.name ?? '', roleId: res.roleId ?? 0, roleIds: res.roleIds ?? (res.roleId ? [res.roleId] : []), status: res.status ?? '',
      mustChangePassword: !!res.mustChangePassword, permissions: res.permissions ?? [], scope: res.scope ?? 'ALL'
    };
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    this.currentUser.set(user);
  }

  /** Does the signed-in user have the role (main or additional)? */
  hasRole(roleId: number): boolean { return hasRole(this.currentUser(), roleId); }

  /** Super Admin, unless they are viewing as another of their roles. */
  isSuperAdmin(): boolean {
    const acting = this.currentUser()?.actingRoleId;
    return acting != null ? acting === ROLE.SUPER_ADMIN : this.hasRole(ROLE.SUPER_ADMIN);
  }

  /** May the signed-in user view (or manage) the admin section? */
  can(section: Section, level: 'VIEW' | 'EDIT' = 'VIEW'): boolean {
    return canAccess(this.currentUser(), section, level);
  }

  /** Re-reads permissions from the server, so changes made in Roles apply without signing in again. */
  refreshAccess(): Observable<AccessInfo> {
    return this.http.get<AccessInfo>(`${environment.apiBaseUrl}/account/access`).pipe(tap((a) => {
      setDateFormat(a.dateFormat);
      this.updateUser({ roleName: a.roleName ?? undefined, roleIds: a.roleIds, roles: a.roles, actingRoleId: a.actingRoleId ?? null, dateFormat: a.dateFormat,
        permissions: a.permissions, scope: a.scope,
        chapterId: a.chapterId, rccCenterId: a.rccCenterId, emailMissing: a.emailMissing });
    }));
  }

  /** Updates the stored user (e.g. after a profile edit or a password change). */
  updateUser(changes: Partial<AuthUser>): void {
    const current = this.currentUser();
    if (!current) return;
    const user = { ...current, ...changes };
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    this.currentUser.set(user);
  }

  /** "View as" one of the user's roles (null: back to all of them). Kept for this browser tab only. */
  actingRole(): number | null {
    try { const v = sessionStorage.getItem(ACTING_KEY); return v ? Number(v) : null; } catch { return null; }
  }

  /** Switches "View as" and reloads the permissions the server gives for it (the menu follows). */
  actAs(roleId: number | null): Observable<AccessInfo> {
    try {
      if (roleId === null) sessionStorage.removeItem(ACTING_KEY); else sessionStorage.setItem(ACTING_KEY, String(roleId));
    } catch { /* private mode: only this page view */ }
    return this.refreshAccess();
  }

  logout(): void {
    try { sessionStorage.removeItem(ACTING_KEY); } catch { /* ignore */ }
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
