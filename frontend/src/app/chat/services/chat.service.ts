import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from '../../auth/services/auth.service';

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
    private http: HttpClient,
    private authService: AuthService
  ) {}

  createSession(userId: number, topic: string = ''): Observable<ChatSession> {
    const params = { userId: userId.toString(), ...(topic && { topic }) };
    return this.http.post<ChatSession>(
      `${this.apiUrl}/session`,
      {},
      { params, headers: this.authService.getAuthHeaders() }
    );
  }

  sendMessage(request: ChatMessageRequest): Observable<ChatMessageResponse> {
    return this.http.post<ChatMessageResponse>(
      `${this.apiUrl}/message`,
      request,
      { headers: this.authService.getAuthHeaders() }
    );
  }

  getChatHistory(sessionId: number): Observable<ChatMessage[]> {
    return this.http.get<ChatMessage[]>(
      `${this.apiUrl}/history/${sessionId}`,
      { headers: this.authService.getAuthHeaders() }
    );
  }

  getUserSessions(userId: number): Observable<ChatSession[]> {
    return this.http.get<ChatSession[]>(
      `${this.apiUrl}/sessions/${userId}`,
      { headers: this.authService.getAuthHeaders() }
    );
  }
}
