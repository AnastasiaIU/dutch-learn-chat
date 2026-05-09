import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, BehaviorSubject } from 'rxjs';
import { timeout } from 'rxjs/operators';
import { LoggerService } from '../../shared/services/logger.service';

export interface AuthResponse {
  userId: number;
  username: string;
  email: string;
  token: string;
  languageLevel: string;
  role: string;
}

export interface UserRegistration {
  email: string;
  username: string;
  password: string;
  languageLevel: string;
}

export interface UserLogin {
  email: string;
  password: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = 'http://localhost:8080/api/auth';
  private authSubject = new BehaviorSubject<AuthResponse | null>(this.getStoredAuth());
  public auth$ = this.authSubject.asObservable();

  constructor(
    private readonly http: HttpClient,
    private readonly logger: LoggerService,
  ) {}

  register(data: UserRegistration): Observable<AuthResponse> {
    this.logger.info('Auth register request started', {
      languageLevel: data.languageLevel,
      usernameLength: data.username.length,
    });

    return this.http
      .post<AuthResponse>(`${this.apiUrl}/register`, data)
      .pipe(timeout(10000));
  }

  login(data: UserLogin): Observable<AuthResponse> {
    this.logger.info('Auth login request started');

    return this.http
      .post<AuthResponse>(`${this.apiUrl}/login`, data)
      .pipe(timeout(10000));
  }

  logout(): void {
    this.logger.info('Auth logout');
    localStorage.removeItem('auth');
    this.authSubject.next(null);
  }

  setAuth(auth: AuthResponse): void {
    const normalized = this.normalizeAuth(auth);
    localStorage.setItem('auth', JSON.stringify(normalized));
    this.authSubject.next(normalized);
    this.logger.info('Auth state updated', {
      userId: normalized.userId,
      role: normalized.role,
      languageLevel: normalized.languageLevel,
    });
  }

  getAuth(): AuthResponse | null {
    return this.authSubject.value;
  }

  getToken(): string | null {
    const auth = this.getValidAuth();
    return auth ? auth.token : null;
  }

  isAuthenticated(): boolean {
    return this.getValidAuth() !== null;
  }

  getRole(): string {
    const auth = this.getAuth();
    return auth?.role?.toUpperCase() ?? 'LEARNER';
  }

  isAdmin(): boolean {
    return this.getRole() === 'ADMIN';
  }

  private getValidAuth(): AuthResponse | null {
    const auth = this.getAuth();
    if (!auth || !auth.token) {
      return null;
    }

    if (this.isTokenExpired(auth.token)) {
      this.logger.warn('Auth token expired or invalid, clearing auth state');
      this.logout();
      return null;
    }

    return auth;
  }

  private getStoredAuth(): AuthResponse | null {
    const auth = localStorage.getItem('auth');
    if (!auth) {
      return null;
    }

    try {
      return this.normalizeAuth(JSON.parse(auth) as AuthResponse);
    } catch {
      this.logger.warn('Stored auth payload could not be parsed and was ignored');
      return null;
    }
  }

  private normalizeAuth(auth: AuthResponse): AuthResponse {
    return {
      ...auth,
      role: (auth.role ?? 'LEARNER').toUpperCase(),
    };
  }

  getAuthHeaders(): HttpHeaders {
    const token = this.getToken();
    const headers = new HttpHeaders({
      'Content-Type': 'application/json'
    });

    if (!token) {
      this.logger.warn('Auth token missing while creating auth headers');
      return headers;
    }

    return headers.set('Authorization', `Bearer ${token}`);
  }

  private isTokenExpired(token: string): boolean {
    const payload = this.decodeJwtPayload(token);
    if (!payload || typeof payload.exp !== 'number') {
      return true;
    }

    return Date.now() >= payload.exp * 1000;
  }

  private decodeJwtPayload(token: string): { exp?: number } | null {
    const parts = token.split('.');
    if (parts.length !== 3) {
      return null;
    }

    try {
      const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const padded = base64.padEnd(base64.length + (4 - (base64.length % 4 || 4)), '=');
      const decoded = atob(padded);
      return JSON.parse(decoded) as { exp?: number };
    } catch {
      return null;
    }
  }
}
