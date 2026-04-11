import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ChatService, ChatMessage } from '../services/chat.service';
import { AuthService } from '../../auth/services/auth.service';

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
        <div class="header-actions">
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
              [(ngModel)]="userMessage"
              name="userMessage"
              rows="2"
              placeholder="Schrijf hier in het Nederlands... (of in English)"
              [disabled]="isLoading"
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
export class ChatComponent implements OnInit {
  private readonly vocabularyStorageKey = 'dutch-learn-chat.vocabulary';
  private localMessageId = -1;

  messages: ChatDisplayMessage[] = [];
  vocabularyWords: VocabularyWord[] = [];
  showVocabularyPanel: boolean = true;
  userMessage: string = '';
  isLoading: boolean = false;
  errorMessage: string = '';
  currentSessionId: number | null = null;

  constructor(
    private chatService: ChatService,
    private authService: AuthService,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.vocabularyWords = this.loadStoredVocabulary();
    this.messages = [this.createWelcomeMessage()];
    this.initializeChat();
  }

  trackByMessage(index: number, message: ChatDisplayMessage): number {
    return message.id ?? index;
  }

  toggleVocabularyPanel(): void {
    this.showVocabularyPanel = !this.showVocabularyPanel;
  }

  onInputKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }

  initializeChat(): void {
    const auth = this.authService.getAuth();

    if (!auth) {
      void this.router.navigate(['/auth']);
      return;
    }

    this.chatService.createSession(auth.userId, 'Friendly Conversation').subscribe({
      next: (session) => {
        this.currentSessionId = session.id;
        this.loadChatHistory();
      },
      error: (error) => {
        this.errorMessage = 'Failed to create chat session';
        console.error(error);
      }
    });
  }

  sendMessage(): void {
    const content = this.userMessage.trim();

    if (!content || this.isLoading) {
      return;
    }

    if (!this.currentSessionId) {
      this.errorMessage = 'Log in om berichten te versturen.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    const request = {
      sessionId: this.currentSessionId,
      userMessage: content,
      language: 'auto'
    };

    this.chatService.sendMessage(request).subscribe({
      next: (response) => {
        const user = this.toDisplayMessage(response.userMessage);
        const assistant = this.toDisplayMessage(response.assistantMessage);

        this.messages.push(user, assistant);
        this.captureVocabulary(assistant.vocabulary);
        this.userMessage = '';
        this.isLoading = false;
      },
      error: (error) => {
        this.errorMessage = 'Failed to send message. Please try again.';
        console.error(error);
        this.isLoading = false;
      }
    });
  }

  deleteVocabularyWord(word: string): void {
    this.vocabularyWords = this.vocabularyWords.filter((item) => item.word.toLowerCase() !== word.toLowerCase());
    this.storeVocabulary();
  }

  logout(): void {
    this.authService.logout();
    this.currentSessionId = null;
    void this.router.navigate(['/auth']);
  }

  private loadChatHistory(): void {
    if (!this.currentSessionId) {
      return;
    }

    this.chatService.getChatHistory(this.currentSessionId).subscribe({
      next: (history) => {
        if (history.length === 0) {
          return;
        }

        this.messages = history.map((message) => this.toDisplayMessage(message));

        for (const message of this.messages) {
          if (!message.isUser && message.vocabulary.length > 0) {
            this.captureVocabulary(message.vocabulary);
          }
        }
      },
      error: (error) => {
        console.error('Failed to load chat history', error);
      }
    });
  }

  private toDisplayMessage(message: ChatMessage): ChatDisplayMessage {
    const role = (message.role ?? 'ASSISTANT').toUpperCase();
    const vocabulary = this.extractVocabulary(message.content ?? '');
    const mainContent = this.stripVocabularySection(message.content ?? '');

    return {
      id: typeof message.id === 'number' ? message.id : this.localMessageId--,
      role,
      content: message.content ?? '',
      languageUsed: message.languageUsed ?? 'nl',
      createdAt: message.createdAt ?? new Date().toISOString(),
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
        'Hallo! Ik ben je Nederlandse gesprekspartner. Hoe gaat het met je?\n\n' +
        'Wil je oefenen met een onderwerp? Bijvoorbeeld:\n' +
        '• Boodschappen doen\n' +
        '• Op het werk\n' +
        '• In de trein\n' +
        '• Het weer',
      languageUsed: 'nl',
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
}
