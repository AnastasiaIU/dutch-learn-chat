import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, BehaviorSubject } from 'rxjs';

export interface AuthResponse {
  userId: number;
  username: string;
  email: string;
  token: string;
  languageLevel: string;
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

  constructor(private http: HttpClient) {}

  register(data: UserRegistration): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/register`, data);
  }

  login(data: UserLogin): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, data);
  }

  logout(): void {
    localStorage.removeItem('auth');
    this.authSubject.next(null);
  }

  setAuth(auth: AuthResponse): void {
    localStorage.setItem('auth', JSON.stringify(auth));
    this.authSubject.next(auth);
  }

  getAuth(): AuthResponse | null {
    return this.authSubject.value;
  }

  getToken(): string | null {
    const auth = this.getAuth();
    return auth ? auth.token : null;
  }

  isAuthenticated(): boolean {
    return this.getAuth() !== null;
  }

  private getStoredAuth(): AuthResponse | null {
    const auth = localStorage.getItem('auth');
    return auth ? JSON.parse(auth) : null;
  }

  getAuthHeaders(): HttpHeaders {
    const token = this.getToken();
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });
  }
}
