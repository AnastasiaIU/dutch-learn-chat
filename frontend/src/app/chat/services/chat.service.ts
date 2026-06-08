import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, tap, timeout } from 'rxjs/operators';
import { AuthService } from '../../auth/services/auth.service';
import { LoggerService } from '../../shared/services/logger.service';

export interface ChatSession {
  id: number;
  topic: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessage {
  id: number;
  role: string;
  content: string;
  languageUsed: string;
  createdAt: string;
}

export interface ChatMessageRequest {
  sessionId: number;
  userMessage: string;
  language: string;
}

export interface ChatMessageResponse {
  sessionId: number;
  userMessage: ChatMessage;
  assistantMessage: ChatMessage;
}

@Injectable({
  providedIn: 'root'
})
export class ChatService {
  private apiUrl = 'http://localhost:8080/api/chat';

  constructor(
    private readonly http: HttpClient,
    private readonly authService: AuthService,
    private readonly logger: LoggerService,
  ) {}

  createSession(userId: number, topic: string = ''): Observable<ChatSession> {
    const params = { userId: userId.toString(), ...(topic && { topic }) };

    this.logger.info('Chat createSession request started', {
      userId,
      hasTopic: topic.trim().length > 0,
    });

    return this.http.post<ChatSession>(
      `${this.apiUrl}/session`,
      {},
      { params, headers: this.authService.getAuthHeaders() }
    ).pipe(
      timeout(360000),
      tap((session) => {
        this.logger.info('Chat createSession request completed', {
          userId,
          sessionId: session.id,
        });
      }),
      catchError((error: unknown) => {
        this.logger.error('Chat createSession request failed', {
          userId,
          status: this.extractHttpStatus(error),
        });
        return throwError(() => error);
      })
    );
  }

  updateSessionTopic(sessionId: number, newTopic: string): Observable<ChatMessageResponse> {
    this.logger.info('Chat updateSessionTopic request started', {
      sessionId,
      newTopic,
    });

    return this.http.put<ChatMessageResponse>(
      `${this.apiUrl}/sessions/${sessionId}/topic`,
      { topic: newTopic },
      { headers: this.authService.getAuthHeaders() }
    ).pipe(
      timeout(360000),
      tap((response) => {
        this.logger.info('Chat updateSessionTopic request completed', {
          sessionId,
        });
      }),
      catchError((error: unknown) => {
        this.logger.error('Chat updateSessionTopic request failed', {
          sessionId,
          status: this.extractHttpStatus(error),
        });
        return throwError(() => error);
      })
    );
  }

  sendMessage(request: ChatMessageRequest): Observable<ChatMessageResponse> {
    this.logger.info('Chat sendMessage request started', {
      sessionId: request.sessionId,
      language: request.language,
      messageLength: request.userMessage?.length ?? 0,
    });

    return this.http.post<ChatMessageResponse>(
      `${this.apiUrl}/message`,
      request,
      { headers: this.authService.getAuthHeaders() }
    ).pipe(
      timeout(360000),
      tap((response) => {
        this.logger.info('Chat sendMessage request completed', {
          sessionId: response.sessionId,
          assistantMessageId: response.assistantMessage?.id,
        });
      }),
      catchError((error: unknown) => {
        this.logger.error('Chat sendMessage request failed', {
          sessionId: request.sessionId,
          status: this.extractHttpStatus(error),
        });
        return throwError(() => error);
      })
    );
  }

  getChatHistory(sessionId: number): Observable<ChatMessage[]> {
    this.logger.debug('Chat history request started', { sessionId });

    return this.http.get<ChatMessage[]>(
      `${this.apiUrl}/history/${sessionId}`,
      { headers: this.authService.getAuthHeaders() }
    ).pipe(
      timeout(360000),
      tap((history) => {
        this.logger.debug('Chat history request completed', {
          sessionId,
          messageCount: history.length,
        });
      }),
      catchError((error: unknown) => {
        this.logger.warn('Chat history request failed', {
          sessionId,
          status: this.extractHttpStatus(error),
        });
        return throwError(() => error);
      })
    );
  }

  getUserSessions(userId: number): Observable<ChatSession[]> {
    this.logger.debug('Chat getUserSessions request started', { userId });

    return this.http.get<ChatSession[]>(
      `${this.apiUrl}/sessions/${userId}`,
      { headers: this.authService.getAuthHeaders() }
    ).pipe(
      timeout(360000),
      tap((sessions) => {
        this.logger.debug('Chat getUserSessions request completed', {
          userId,
          sessionCount: sessions.length,
        });
      }),
      catchError((error: unknown) => {
        this.logger.warn('Chat getUserSessions request failed', {
          userId,
          status: this.extractHttpStatus(error),
        });
        return throwError(() => error);
      })
    );
  }

  private extractHttpStatus(error: unknown): number | 'unknown' {
    return error instanceof HttpErrorResponse ? error.status : 'unknown';
  }
}
