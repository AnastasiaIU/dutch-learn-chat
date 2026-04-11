import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';

type AuthMode = 'login' | 'register';

@Component({
  selector: 'app-auth',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="auth-shell">
      <div class="auth-card">
        <div class="auth-brand">
          <span class="flag">🇳🇱</span>
          <div>
            <div class="title">Nederlands Oefenen</div>
            <div class="subtitle">A2/B1 Gesprekspartner</div>
          </div>
        </div>

        <div class="mode-switch">
          <button [class.active]="mode === 'login'" (click)="switchMode('login')">Inloggen</button>
          <button [class.active]="mode === 'register'" (click)="switchMode('register')">Registreren</button>
        </div>

        <form (ngSubmit)="submit()" class="auth-form">
          <label *ngIf="mode === 'register'">
            Gebruikersnaam
            <input
              type="text"
              name="username"
              [(ngModel)]="registerModel.username"
              autocomplete="username"
              [disabled]="isSubmitting"
            />
          </label>

          <label *ngIf="mode === 'login'">
            E-mail
            <input
              type="email"
              name="loginEmail"
              [(ngModel)]="loginModel.email"
              autocomplete="email"
              [disabled]="isSubmitting"
            />
          </label>

          <label *ngIf="mode === 'register'">
            E-mail
            <input
              type="email"
              name="registerEmail"
              [(ngModel)]="registerModel.email"
              autocomplete="email"
              [disabled]="isSubmitting"
            />
          </label>

          <label *ngIf="mode === 'login'">
            Wachtwoord
            <input
              type="password"
              name="loginPassword"
              [(ngModel)]="loginModel.password"
              autocomplete="current-password"
              [disabled]="isSubmitting"
            />
          </label>

          <label *ngIf="mode === 'register'">
            Wachtwoord
            <input
              type="password"
              name="registerPassword"
              [(ngModel)]="registerModel.password"
              autocomplete="new-password"
              [disabled]="isSubmitting"
            />
          </label>

          <label *ngIf="mode === 'register'">
            Herhaal wachtwoord
            <input
              type="password"
              name="confirmPassword"
              [(ngModel)]="registerModel.confirmPassword"
              autocomplete="new-password"
              [disabled]="isSubmitting"
            />
          </label>

          <label *ngIf="mode === 'register'">
            Niveau
            <select
              name="languageLevel"
              [(ngModel)]="registerModel.languageLevel"
              [disabled]="isSubmitting"
            >
              <option value="A2">A2</option>
              <option value="B1">B1</option>
            </select>
          </label>

          <button class="submit" type="submit" [disabled]="isSubmitting">
            {{ isSubmitting ? 'Bezig...' : (mode === 'login' ? 'Inloggen' : 'Account maken') }}
          </button>
        </form>

        <div class="error-banner" *ngIf="errorMessage">{{ errorMessage }}</div>

        <div class="helper-text">
          Gebruik je account om je chatsessie en voortgang te bewaren.
        </div>
      </div>
    </div>
  `,
  styleUrls: ['./auth.component.scss']
})
export class AuthComponent {
  mode: AuthMode = 'login';
  isSubmitting: boolean = false;
  errorMessage: string = '';

  loginModel = {
    email: '',
    password: '',
  };

  registerModel = {
    username: '',
    email: '',
    password: '',
    confirmPassword: '',
    languageLevel: 'A2',
  };

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router,
  ) {}

  switchMode(mode: AuthMode): void {
    this.mode = mode;
    this.errorMessage = '';
  }

  submit(): void {
    if (this.isSubmitting) {
      return;
    }

    if (this.mode === 'login') {
      this.login();
      return;
    }

    this.register();
  }

  private login(): void {
    const email = this.loginModel.email.trim();
    const password = this.loginModel.password;

    if (!email || !password) {
      this.errorMessage = 'Vul e-mail en wachtwoord in.';
      return;
    }

    this.errorMessage = '';
    this.isSubmitting = true;

    this.authService.login({ email, password })
      .pipe(finalize(() => {
        this.isSubmitting = false;
      }))
      .subscribe({
        next: (response) => {
          this.authService.setAuth(response);
          void this.router.navigate(['/chat']);
        },
        error: (error: unknown) => {
          this.errorMessage = this.mapError(error, 'Inloggen is mislukt. Controleer je gegevens.');
        }
      });
  }

  private register(): void {
    const username = this.registerModel.username.trim();
    const email = this.registerModel.email.trim();
    const password = this.registerModel.password;
    const confirmPassword = this.registerModel.confirmPassword;

    if (!username || !email || !password || !confirmPassword) {
      this.errorMessage = 'Vul alle velden in om te registreren.';
      return;
    }

    if (password !== confirmPassword) {
      this.errorMessage = 'Wachtwoorden komen niet overeen.';
      return;
    }

    this.errorMessage = '';
    this.isSubmitting = true;

    this.authService.register({
      username,
      email,
      password,
      languageLevel: this.registerModel.languageLevel,
    })
      .pipe(finalize(() => {
        this.isSubmitting = false;
      }))
      .subscribe({
        next: (response) => {
          this.authService.setAuth(response);
          void this.router.navigate(['/chat']);
        },
        error: (error: unknown) => {
          this.errorMessage = this.mapError(error, 'Registratie is mislukt. Probeer het opnieuw.');
        }
      });
  }

  private mapError(error: unknown, fallback: string): string {
    if (typeof error === 'object' && error !== null && 'name' in error && (error as { name: string }).name === 'TimeoutError') {
      return 'De server reageert niet op tijd. Probeer opnieuw.';
    }

    if (!(error instanceof HttpErrorResponse)) {
      return fallback;
    }

    if (error.status === 0) {
      return 'Kan geen verbinding maken met de server. Controleer of backend draait.';
    }

    if (error.status === 401) {
      return 'Onjuiste e-mail of wachtwoord.';
    }

    if (error.status === 400) {
      return 'Deze gegevens zijn ongeldig of al in gebruik.';
    }

    return fallback;
  }
}
