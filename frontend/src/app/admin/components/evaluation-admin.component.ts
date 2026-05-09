import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthService } from '../../auth/services/auth.service';
import { LoggerService } from '../../shared/services/logger.service';

interface CefrEvaluation {
  targetLevel: string;
  dataAvailable: boolean;
  vocabularySize: number;
  vocabularyCoverage: number;
  totalWordCount: number;
  unknownWordCount: number;
  unknownWords: string[];
  sentenceCount: number;
  averageSentenceLength: number;
  maxSentenceLength: number;
  violations: string[];
}

interface EvaluationMessage {
  id: number;
  sessionId: number | null;
  createdAt: string;
  content: string;
  model: string;
  modelTag: string;
  promptVersion: string;
  responseSource: string;
  languageLevel: string;
  cefrEvaluation?: CefrEvaluation | null;
}

@Component({
  selector: 'app-evaluation-admin',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="evaluation-admin">
      <h2>Evaluation Admin</h2>
      <div class="controls">
        <label>
          Model tag
          <input
            type="text"
            [(ngModel)]="modelTag"
            placeholder="baseline"
            (keyup.enter)="loadMessages()"
          />
        </label>
        <label>
          Limit
          <input type="number" [(ngModel)]="limit" min="1" max="200" />
        </label>
        <button type="button" (click)="loadMessages()" [disabled]="loading">
          {{ loading ? 'Loading...' : 'Refresh' }}
        </button>
      </div>

      <p class="status" *ngIf="errorMessage">{{ errorMessage }}</p>
      <p class="status" *ngIf="!errorMessage && !loading">
        Showing {{ messages.length }} assistant responses.
      </p>

      <div class="table" *ngIf="messages.length > 0">
        <div class="row header">
          <div>Created</div>
          <div>Model tag</div>
          <div>Model</div>
          <div>Prompt</div>
          <div>Source</div>
          <div>Level</div>
          <div>Coverage</div>
          <div>Max sentence</div>
          <div>Unknown</div>
          <div>Preview</div>
        </div>
        <div class="row" *ngFor="let message of messages">
          <div>{{ message.createdAt }}</div>
          <div>{{ message.modelTag || '-' }}</div>
          <div>{{ message.model || '-' }}</div>
          <div>{{ message.promptVersion || '-' }}</div>
          <div>{{ message.responseSource || '-' }}</div>
          <div>{{ message.languageLevel || '-' }}</div>
          <div>{{ formatCoverage(message.cefrEvaluation) }}</div>
          <div>{{ message.cefrEvaluation?.maxSentenceLength ?? '-' }}</div>
          <div>{{ message.cefrEvaluation?.unknownWordCount ?? '-' }}</div>
          <div>{{ preview(message.content) }}</div>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
    .evaluation-admin {
      display: flex;
      flex-direction: column;
      gap: 16px;
      padding: 24px;
    }
    .controls {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: flex-end;
    }
    label {
      display: flex;
      flex-direction: column;
      gap: 6px;
      font-weight: 600;
    }
    input {
      padding: 6px 10px;
      border-radius: 6px;
      border: 1px solid #ccc;
      min-width: 140px;
    }
    button {
      padding: 8px 14px;
      border-radius: 6px;
      border: 1px solid #222;
      background: #222;
      color: #fff;
      cursor: pointer;
    }
    button:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
    .status {
      color: #444;
    }
    .table {
      display: grid;
      gap: 8px;
      border-top: 1px solid #e0e0e0;
      padding-top: 12px;
      font-size: 13px;
    }
    .row {
      display: grid;
      grid-template-columns: 150px 120px 140px 140px 90px 70px 80px 110px 80px 1fr;
      gap: 12px;
      align-items: start;
    }
    .row.header {
      font-weight: 700;
      text-transform: uppercase;
      font-size: 11px;
      color: #666;
    }
    @media (max-width: 1200px) {
      .row {
        grid-template-columns: 120px 100px 120px 120px 80px 60px 70px 90px 70px 1fr;
      }
    }
    @media (max-width: 900px) {
      .row {
        grid-template-columns: 1fr;
      }
      .row.header {
        display: none;
      }
    }
    `
  ]
})
export class EvaluationAdminComponent implements OnInit {
  private readonly apiUrl = 'http://localhost:8080/api/evaluation';
  messages: EvaluationMessage[] = [];
  modelTag = 'baseline';
  limit = 50;
  loading = false;
  errorMessage = '';

  constructor(
    private readonly http: HttpClient,
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly logger: LoggerService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.loadMessages();
  }

  loadMessages(): void {
    this.loading = true;
    this.errorMessage = '';

    if (!this.authService.isAuthenticated() || !this.authService.isAdmin()) {
      this.logger.warn('Evaluation admin access denied due to missing admin auth');
      this.errorMessage = 'Je sessie is verlopen. Log opnieuw in.';
      this.loading = false;
      void this.router.navigate(['/auth']);
      return;
    }

    let params = new HttpParams().set('limit', String(this.limit));
    if (this.modelTag.trim().length > 0) {
      params = params.set('modelTag', this.modelTag.trim());
    }

    console.log('Sending request to', `${this.apiUrl}/messages`);
    this.http.get<EvaluationMessage[]>(
      `${this.apiUrl}/messages`,
      { headers: this.authService.getAuthHeaders(), params }
    ).subscribe({
      next: (messages) => {
        console.log('Received messages', messages);
        this.messages = messages ?? [];
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: (error) => {
        console.error('HTTP Error received', error);
        this.logger.error('Evaluation messages request failed', {
          status: error?.status ?? 'unknown',
        });
        if (error?.status === 401 || error?.status === 403) {
          this.authService.logout();
          this.errorMessage = 'Je sessie is verlopen. Log opnieuw in.';
          this.loading = false;
          this.cdr.detectChanges();
          void this.router.navigate(['/auth']);
          return;
        }
        this.errorMessage = 'Failed to load evaluation messages.';
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  formatCoverage(evaluation?: CefrEvaluation | null): string {
    if (!evaluation || !Number.isFinite(evaluation.vocabularyCoverage)) {
      return '-';
    }
    return `${Math.round(evaluation.vocabularyCoverage * 100)}%`;
  }

  preview(content: string): string {
    if (!content) {
      return '-';
    }
    return content.length > 140 ? `${content.slice(0, 140)}...` : content;
  }
}