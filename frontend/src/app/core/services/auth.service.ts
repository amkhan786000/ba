import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthUser } from '../models/user.model';

interface LoginResponse {
  otpRequired: boolean;
  token?: string;
  userId: string;
  name?: string;
  roleId?: number;
  status?: string;
  message?: string;
  mustChangePassword?: boolean;
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

  verifyOtp(userId: string, otp: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${environment.apiBaseUrl}/auth/verify-otp`, { userId, otp })
      .pipe(tap((res) => this.persistSession(res)));
  }

  register(payload: unknown): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${environment.apiBaseUrl}/auth/register`, payload);
  }

  resetPassword(payload: unknown): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${environment.apiBaseUrl}/auth/reset-password`, payload);
  }

  persistSession(res: LoginResponse): void {
    if (!res.token) return;
    localStorage.setItem(TOKEN_KEY, res.token);
    const user: AuthUser = {
      userId: res.userId, name: res.name ?? '', roleId: res.roleId ?? 0, status: res.status ?? '',
      mustChangePassword: !!res.mustChangePassword
    };
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    this.currentUser.set(user);
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
    return raw ? JSON.parse(raw) : null;
  }
}
