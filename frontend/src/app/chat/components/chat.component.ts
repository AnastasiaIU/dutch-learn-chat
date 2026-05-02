import { AfterViewInit, ChangeDetectorRef, Component, ElementRef, NgZone, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { ChatService, ChatMessage } from '../services/chat.service';
import { AuthService } from '../../auth/services/auth.service';
import { LoggerService } from '../../shared/services/logger.service';

interface VocabularyWord {
  word: string;
  explanation: string;
}

interface ChatDisplayMessage {
  id: number;
  role: string;
  content: string;
  languageUsed: string;
  createdAt: string;
  mainContent: string;
  vocabulary: VocabularyWord[];
  isUser: boolean;
}

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="chat-shell">
      <header class="chat-header">
        <div class="brand">
          <span class="brand-flag">🇳🇱</span>
          <div>
            <div class="brand-title">Nederlands Oefenen</div>
            <div class="brand-subtitle">A2/B1 Gesprekspartner</div>
          </div>
        </div>

        <div class="session-info">
          <div class="session-info-item">
            <div class="session-info-label">Gebruiker</div>
            <div class="session-info-value">{{ currentUsername || 'Onbekend' }}</div>
          </div>
          <div class="session-info-item">
            <div class="session-info-label">Niveau</div>
            <div class="session-info-value">{{ currentLanguageLevel || 'A2/B1' }}</div>
          </div>
          <div class="session-info-item topic">
            <div class="session-info-label">Onderwerp</div>
            <div class="session-info-value">
              {{ currentTopic || 'Nog niet gekozen' }}
              <button class="topic-edit-btn" (click)="changeTopic()" title="Onderwerp wijzigen">✎</button>
            </div>
          </div>
        </div>

        <div class="header-actions">
          <button class="admin-button" *ngIf="isAdmin" (click)="openAdminDashboard()">
            Admin
          </button>
          <button class="vocab-toggle" (click)="toggleVocabularyPanel()">
            📖 Woorden
            <span *ngIf="vocabularyWords.length > 0">({{ vocabularyWords.length }})</span>
          </button>
          <button class="logout-button" (click)="logout()">Uitloggen</button>
        </div>
      </header>

      <div class="chat-body">
        <section class="conversation-column">
          <div class="messages-container">
            <div class="message-row" *ngFor="let message of messages; trackBy: trackByMessage">
              <div class="assistant-avatar" *ngIf="!message.isUser">🌷</div>

              <div class="message-stack" [class.user]="message.isUser">
                <div class="message-bubble" [class.user]="message.isUser" [class.assistant]="!message.isUser">
                  {{ message.mainContent }}
                </div>

                <div class="message-vocab" *ngIf="message.vocabulary.length > 0">
                  <div class="message-vocab-title">📚 Moeilijke woorden</div>
                  <div class="message-vocab-line" *ngFor="let item of message.vocabulary">
                    <span class="word">{{ item.word }}</span>
                    <span class="explanation">: {{ item.explanation }}</span>
                  </div>
                </div>

                <div class="message-time">{{ message.createdAt | date:'shortTime' }}</div>
              </div>
            </div>

            <div class="message-row" *ngIf="isLoading">
              <div class="assistant-avatar">🌷</div>
              <div class="typing-indicator">···</div>
            </div>

            <div class="message-row" *ngIf="messages.length === 0">
              <div class="empty-state">Start een gesprek om te oefenen.</div>
            </div>
          </div>

          <div class="composer">
            <textarea
              #messageInput
              [(ngModel)]="userMessage"
              name="userMessage"
              rows="2"
              [placeholder]="isTopicChangePending ? 'Typ hier het nieuwe onderwerp...' : (currentSessionId ? 'Schrijf hier in het Nederlands... (of in English)' : 'Kies een onderwerp en stuur je eerste bericht...')"
              (keydown)="onInputKeyDown($event)"
            ></textarea>

            <button
              type="button"
              (click)="sendMessage()"
              [disabled]="!userMessage.trim() || isLoading"
            >
              →
            </button>
          </div>

          <div class="error-banner" *ngIf="errorMessage">
            {{ errorMessage }}
          </div>
        </section>

        <aside class="vocab-panel" *ngIf="showVocabularyPanel">
          <div class="vocab-header">
            <div class="vocab-label">Woordenboek</div>
            <div class="vocab-title">Mijn Woorden</div>
            <div class="vocab-count">{{ vocabularyWords.length }} woorden opgeslagen</div>
          </div>

          <div class="vocab-list" *ngIf="vocabularyWords.length > 0; else emptyVocabulary">
            <div class="vocab-item" *ngFor="let item of vocabularyWords">
              <div class="vocab-word">{{ item.word }}</div>
              <div class="vocab-explanation">{{ item.explanation }}</div>
              <button class="vocab-delete" (click)="deleteVocabularyWord(item.word)">×</button>
            </div>
          </div>

          <ng-template #emptyVocabulary>
            <div class="vocab-empty">Woorden uit gesprekken worden hier opgeslagen.</div>
          </ng-template>
        </aside>
      </div>
    </div>
  `,
  styleUrls: ['./chat.component.scss']
})
export class ChatComponent implements OnInit, AfterViewInit {
  private readonly vocabularyStorageKey = 'dutch-learn-chat.vocabulary';
  private readonly maxSessionRecoveryAttempts = 1;
  private readonly maxTopicLength = 500;
  private localMessageId = -1;
  private isSessionInitializing = false;
  private pendingMessage: string | null = null;
  private pendingMessageShown = false;
  private sessionRecoveryAttempts = 0;
  @ViewChild('messageInput') private messageInputRef?: ElementRef<HTMLTextAreaElement>;

  messages: ChatDisplayMessage[] = [];
  vocabularyWords: VocabularyWord[] = [];
  showVocabularyPanel: boolean = true;
  userMessage: string = '';
  isLoading: boolean = false;
  errorMessage: string = '';
  isTopicChangePending: boolean = false;
  currentSessionId: number | null = null;
  currentTopic: string = '';
  currentUsername: string = '';
  currentLanguageLevel: string = '';
  isAdmin: boolean = false;

  constructor(
    private readonly chatService: ChatService,
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly logger: LoggerService,
    private readonly zone: NgZone,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    const auth = this.authService.getAuth();

    this.logger.info('Chat component initialized');
    this.isAdmin = this.authService.isAdmin();
    this.currentUsername = auth?.username ?? '';
    this.currentLanguageLevel = auth?.languageLevel ?? '';
    this.vocabularyWords = this.loadStoredVocabulary();
    this.messages = [this.createWelcomeMessage()];
  }

  ngAfterViewInit(): void {
    this.focusComposerInput();
  }

  trackByMessage(index: number, message: ChatDisplayMessage): number {
    return message.id ?? index;
  }

  toggleVocabularyPanel(): void {
    this.showVocabularyPanel = !this.showVocabularyPanel;
  }

  changeTopic(): void {
    if (this.isTopicChangePending) {
      return;
    }

    this.isTopicChangePending = true;
    this.userMessage = '';
    this.errorMessage = '';

    const assistantPrompt = this.toDisplayMessage({
      id: this.localMessageId--,
      role: 'ASSISTANT',
      content: 'Welk onderwerp wil je oefenen? Typ het hieronder.',
      languageUsed: 'nl',
      createdAt: new Date().toISOString(),
    });

    this.messages.push(assistantPrompt);
    setTimeout(() => this.scrollToBottom(), 100);
    this.focusComposerInput();
  }

  onInputKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }

  private ensureSession(initialTopic: string = '', onReady?: () => void): void {
    if (this.currentSessionId) {
      this.logger.debug('Chat session already available', { sessionId: this.currentSessionId });
      onReady?.();
      return;
    }

    if (this.isSessionInitializing) {
      return;
    }

    const auth = this.authService.getAuth();

    if (!auth) {
      this.logger.warn('Chat session initialization blocked because user is not authenticated');
      void this.router.navigate(['/auth']);
      return;
    }

    this.isSessionInitializing = true;
    const topicForSession = (this.currentTopic || initialTopic).trim();
    this.logger.info('Chat session initialization started', { userId: auth.userId });

    this.chatService.createSession(auth.userId, topicForSession).subscribe({
      next: (session) => {
        this.currentSessionId = session.id;
        this.currentTopic = (session.topic ?? '').trim() || topicForSession;
        this.isSessionInitializing = false;
        this.logger.info('Chat session initialization completed', { sessionId: session.id });
        this.loadChatHistory();
        onReady?.();
        this.sendPendingMessageIfAny();
      },
      error: (error: unknown) => {
        this.isSessionInitializing = false;
        this.errorMessage = this.mapChatError(error, 'Kan chatsessie niet starten. Log opnieuw in.');
        this.logger.error('Chat session initialization failed', {
          status: error instanceof HttpErrorResponse ? error.status : 'unknown',
        });
        if (error instanceof HttpErrorResponse && [400, 401, 403].includes(error.status)) {
          this.authService.logout();
          void this.router.navigate(['/auth']);
        }
      }
    });
  }

  sendMessage(): void {
    const content = this.userMessage.trim();

    if (this.isTopicChangePending) {
      if (!content || this.isLoading) {
        return;
      }
      this.handleTopicChangeMessage(content);
      return;
    }

    if (!content || this.isLoading) {
      return;
    }

    this.logger.info('Send message requested', {
      sessionId: this.currentSessionId,
      messageLength: content.length,
    });

    if (!this.currentSessionId) {
      this.pendingMessage = content;
      this.pendingMessageShown = false;
      this.errorMessage = 'Chatsessie wordt gestart. Je bericht wordt automatisch verzonden.';
      this.ensureSession(this.currentTopic || this.buildSessionTopic(content));
      return;
    }

    this.sendMessageWithSession(content, false);
  }

  deleteVocabularyWord(word: string): void {
    this.vocabularyWords = this.vocabularyWords.filter((item) => item.word.toLowerCase() !== word.toLowerCase());
    this.storeVocabulary();
  }

  logout(): void {
    this.authService.logout();
    this.currentSessionId = null;
    this.currentTopic = '';
    void this.router.navigate(['/auth']);
  }

  openAdminDashboard(): void {
    void this.router.navigate(['/admin/evaluation']);
  }

  private loadChatHistory(): void {
    if (!this.currentSessionId) {
      return;
    }

    this.logger.debug('Loading chat history', { sessionId: this.currentSessionId });

    this.chatService.getChatHistory(this.currentSessionId).subscribe({
      next: (history) => {
        if (history.length === 0) {
          this.logger.debug('Chat history is empty', { sessionId: this.currentSessionId });
          return;
        }

        this.messages = history.map((message) => this.toDisplayMessage(message));
        this.logger.info('Chat history loaded', {
          sessionId: this.currentSessionId,
          messageCount: history.length,
        });

        for (const message of this.messages) {
          if (!message.isUser && message.vocabulary.length > 0) {
            this.captureVocabulary(message.vocabulary);
          }
        }
      },
      error: (error: unknown) => {
        this.errorMessage = this.mapChatError(error, 'Chatgeschiedenis laden is mislukt.');
        this.logger.warn('Failed to load chat history', {
          sessionId: this.currentSessionId,
          status: error instanceof HttpErrorResponse ? error.status : 'unknown',
        });
      }
    });
  }

  private sendPendingMessageIfAny(): void {
    if (!this.pendingMessage || !this.currentSessionId || this.isLoading) {
      return;
    }

    const messageToSend = this.pendingMessage;
    const wasAlreadyShown = this.pendingMessageShown;
    this.pendingMessage = null;
    this.pendingMessageShown = false;
    this.sendMessageWithSession(messageToSend, wasAlreadyShown);
  }

  private sendMessageWithSession(content: string, messageAlreadyShown: boolean): void {
    if (!this.currentSessionId) {
      this.pendingMessage = content;
      this.pendingMessageShown = messageAlreadyShown;
      this.ensureSession(this.currentTopic || this.buildSessionTopic(content));
      return;
    }

    if (!messageAlreadyShown) {
      const localUserMessage = this.createLocalUserMessage(content);
      this.messages.push(localUserMessage);
      this.userMessage = '';
      this.focusComposerInput();
    }

    this.updateLoadingState(true);
    this.errorMessage = '';

    const request = {
      sessionId: this.currentSessionId,
      userMessage: content,
      language: 'auto'
    };

    this.chatService.sendMessage(request)
      .pipe(finalize(() => {
        this.updateLoadingState(false);
        this.focusComposerInput();
      }))
      .subscribe({
      next: (response) => {
        try {
          const assistantSource = response?.assistantMessage ?? {
            id: this.localMessageId--,
            role: 'ASSISTANT',
            content: 'Er ging iets mis met het antwoord. Probeer opnieuw.',
            languageUsed: 'nl',
            createdAt: new Date().toISOString(),
          };

          const assistant = this.toDisplayMessage(assistantSource);
          this.messages.push(assistant);
          this.captureVocabulary(assistant.vocabulary);
          this.sessionRecoveryAttempts = 0;

          this.logger.info('Send message completed', {
            sessionId: this.currentSessionId,
            assistantMessageId: assistant.id,
            vocabularyCount: assistant.vocabulary.length,
          });
        } catch (processingError) {
          this.errorMessage = 'Antwoord ontvangen, maar verwerken is mislukt. Probeer opnieuw.';
          this.logger.error('Send message response processing failed', {
            sessionId: request.sessionId,
            errorType: processingError instanceof Error ? processingError.name : 'unknown',
          });
        }
      },
      error: (error: unknown) => {
        if (
          error instanceof HttpErrorResponse
          && error.status === 400
          && this.sessionRecoveryAttempts < this.maxSessionRecoveryAttempts
        ) {
          this.sessionRecoveryAttempts += 1;
          this.currentSessionId = null;
          this.pendingMessage = content;
          this.pendingMessageShown = true;
          this.updateLoadingState(false);
          this.errorMessage = 'Chatsessie vernieuwen... Je bericht wordt opnieuw verzonden.';
          this.logger.warn('Session recovery triggered after send message failure', {
            previousSessionId: request.sessionId,
            status: error.status,
            recoveryAttempt: this.sessionRecoveryAttempts,
          });
          this.ensureSession(this.currentTopic || this.buildSessionTopic(content));
          return;
        }

        this.errorMessage = this.mapChatError(error, 'Bericht verzenden is mislukt. Probeer het opnieuw.');
        this.logger.error('Send message failed', {
          sessionId: request.sessionId,
          status: error instanceof HttpErrorResponse ? error.status : 'unknown',
        });
      }
    });
  }

  private handleTopicChangeMessage(topic: string): void {
    let topicToSet = topic.trim();

    if (!topicToSet) {
      this.errorMessage = 'Vul een onderwerp in om door te gaan.';
      return;
    }

    if (topicToSet.length > this.maxTopicLength) {
      topicToSet = topicToSet.slice(0, this.maxTopicLength);
    }

    this.isTopicChangePending = false;
    this.errorMessage = '';
    const localUserMessage = this.createLocalUserMessage(topicToSet);
    this.messages.push(localUserMessage);
    this.userMessage = '';
    this.focusComposerInput();

    if (!this.currentSessionId) {
      this.currentTopic = topicToSet;
      const assistant = this.toDisplayMessage({
        id: this.localMessageId--,
        role: 'ASSISTANT',
        content: `We praten nu over: ${topicToSet}. Wat wil je daarover vertellen?`,
        languageUsed: 'nl',
        createdAt: new Date().toISOString(),
      });
      this.messages.push(assistant);
      setTimeout(() => this.scrollToBottom(), 100);
      return;
    }

    this.updateLoadingState(true);
    this.chatService.updateSessionTopic(this.currentSessionId, topicToSet).pipe(
      finalize(() => {
        this.updateLoadingState(false);
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: (response) => {
        this.currentTopic = topicToSet;
        const assistantSource = response.assistantMessage ?? {
          id: this.localMessageId--,
          role: 'ASSISTANT',
          content: `We praten nu over: ${topicToSet}. Wat wil je daarover vertellen?`,
          languageUsed: 'nl',
          createdAt: new Date().toISOString(),
        };
        const assistant = this.toDisplayMessage(assistantSource);
        this.messages.push(assistant);
        setTimeout(() => this.scrollToBottom(), 100);
      },
      error: (error) => {
        this.errorMessage = 'Kan onderwerp niet wijzigen. Probeer het opnieuw.';
        this.logger.error('Error changing topic', { error });
      }
    });
  }

  private buildSessionTopic(prompt: string): string {
    const trimmedPrompt = prompt.trim();

    if (trimmedPrompt.length <= this.maxTopicLength) {
      return trimmedPrompt;
    }

    this.logger.warn('Topic derived from first prompt was truncated', {
      originalLength: trimmedPrompt.length,
      maxTopicLength: this.maxTopicLength,
    });

    return trimmedPrompt.slice(0, this.maxTopicLength);
  }

  private focusComposerInput(): void {
    setTimeout(() => {
      const input = this.messageInputRef?.nativeElement;
      if (!input) {
        return;
      }

      input.focus();
      const cursorPosition = input.value.length;
      input.setSelectionRange(cursorPosition, cursorPosition);
    }, 0);
  }

  private updateLoadingState(value: boolean): void {
    this.zone.run(() => {
      this.isLoading = value;
      this.cdr.markForCheck();
    });
  }

  private mapChatError(error: unknown, fallback: string): string {
    if (typeof error === 'object' && error !== null && 'name' in error && (error as { name: string }).name === 'TimeoutError') {
      return 'Het antwoord duurt te lang. Probeer opnieuw.';
    }

    if (!(error instanceof HttpErrorResponse)) {
      return fallback;
    }

    if (error.status === 0) {
      return 'Kan geen verbinding maken met de server. Controleer of backend draait op poort 8080.';
    }

    if (error.status === 401 || error.status === 403) {
      return 'Je sessie is verlopen. Log opnieuw in.';
    }

    if (error.status === 400) {
      return 'De chatsessie is ongeldig. Log opnieuw in om een nieuwe sessie te maken.';
    }

    if (error.status >= 500) {
      return 'Er ging iets mis op de server. Probeer het zo opnieuw.';
    }

    return fallback;
  }

  private toDisplayMessage(message: ChatMessage): ChatDisplayMessage {
    const role = (message.role ?? 'ASSISTANT').toUpperCase();
    const vocabulary = this.extractVocabulary(message.content ?? '');
    const mainContent = this.stripVocabularySection(message.content ?? '');
    const createdAt = this.normalizeCreatedAt(message.createdAt);

    return {
      id: typeof message.id === 'number' ? message.id : this.localMessageId--,
      role,
      content: message.content ?? '',
      languageUsed: message.languageUsed ?? 'nl',
      createdAt,
      mainContent,
      vocabulary,
      isUser: role === 'USER',
    };
  }

  private createWelcomeMessage(): ChatDisplayMessage {
    return this.toDisplayMessage({
      id: this.localMessageId--,
      role: 'ASSISTANT',
      content:
        'Hallo! Ik ben je Nederlandse gesprekspartner.\n\n' +
        'Kies eerst een onderwerp om mee te oefenen. Je kunt een onderwerp uit de lijst kiezen of je eigen onderwerp typen.\n\n' +
        'Bijvoorbeeld:\n' +
        '• Boodschappen doen\n' +
        '• Op het werk\n' +
        '• In de trein\n' +
        '• Het weer\n\n' +
        'Schrijf nu welk onderwerp je wilt oefenen.',
      languageUsed: 'nl',
      createdAt: new Date().toISOString(),
    });
  }

  private createLocalUserMessage(content: string): ChatDisplayMessage {
    return this.toDisplayMessage({
      id: this.localMessageId--,
      role: 'USER',
      content,
      languageUsed: 'auto',
      createdAt: new Date().toISOString(),
    });
  }

  private extractVocabulary(content: string): VocabularyWord[] {
    const match = content.match(/📚\s*Moeilijke woorden:\s*([\s\S]*)$/i);
    if (!match) {
      return [];
    }

    return match[1]
      .split('\n')
      .map((line) => line.trim())
      .filter((line) => line.length > 0)
      .map((line) => {
        const parsed = line.match(/^[-•]\s*(.+?):\s*(.+)$/);
        if (!parsed) {
          return null;
        }

        return {
          word: parsed[1].trim(),
          explanation: parsed[2].trim(),
        } as VocabularyWord;
      })
      .filter((item): item is VocabularyWord => item !== null);
  }

  private stripVocabularySection(content: string): string {
    return content.replace(/\n?\s*📚\s*Moeilijke woorden:[\s\S]*$/i, '').trim();
  }

  private normalizeCreatedAt(value: string | undefined): string {
    if (!value) {
      return new Date().toISOString();
    }

    const parsed = Date.parse(value);
    if (!Number.isNaN(parsed)) {
      return new Date(parsed).toISOString();
    }

    // Some backends return timezone-less timestamps; try UTC interpretation.
    const parsedAsUtc = Date.parse(`${value}Z`);
    if (!Number.isNaN(parsedAsUtc)) {
      return new Date(parsedAsUtc).toISOString();
    }

    this.logger.warn('Invalid message timestamp received', {
      timestampLength: value.length,
    });
    return new Date().toISOString();
  }

  private captureVocabulary(words: VocabularyWord[]): void {
    if (words.length === 0) {
      return;
    }

    const existing = new Set(this.vocabularyWords.map((item) => item.word.toLowerCase()));
    const uniqueNewWords = words.filter((item) => !existing.has(item.word.toLowerCase()));

    if (uniqueNewWords.length === 0) {
      return;
    }

    this.vocabularyWords = [...this.vocabularyWords, ...uniqueNewWords];
    this.storeVocabulary();
  }

  private loadStoredVocabulary(): VocabularyWord[] {
    const stored = localStorage.getItem(this.vocabularyStorageKey);
    if (!stored) {
      return [];
    }

    try {
      const parsed = JSON.parse(stored) as VocabularyWord[];
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  private storeVocabulary(): void {
    localStorage.setItem(this.vocabularyStorageKey, JSON.stringify(this.vocabularyWords));
  }

  private scrollToBottom(): void {
    const container = document.querySelector('.messages-container');
    if (container) {
      container.scrollTop = container.scrollHeight;
    }
  }
}
