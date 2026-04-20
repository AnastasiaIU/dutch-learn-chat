import { Injectable } from '@angular/core';
import { environment } from '../../../environments/environment';

type LogLevel = 'debug' | 'info' | 'warn' | 'error' | 'silent';

@Injectable({
  providedIn: 'root'
})
export class LoggerService {
  private readonly levelRank: Record<LogLevel, number> = {
    debug: 10,
    info: 20,
    warn: 30,
    error: 40,
    silent: 50,
  };

  private readonly minLevel: LogLevel = this.resolveMinLevel();

  debug(message: string, context?: Record<string, unknown>): void {
    this.log('debug', message, context);
  }

  info(message: string, context?: Record<string, unknown>): void {
    this.log('info', message, context);
  }

  warn(message: string, context?: Record<string, unknown>): void {
    this.log('warn', message, context);
  }

  error(message: string, context?: Record<string, unknown>): void {
    this.log('error', message, context);
  }

  private log(level: LogLevel, message: string, context?: Record<string, unknown>): void {
    if (!this.shouldLog(level)) {
      return;
    }

    const prefix = '[DutchLearn]';
    const sanitizedContext = this.sanitizeContext(context);

    switch (level) {
      case 'debug':
        console.debug(prefix, message, sanitizedContext);
        break;
      case 'info':
        console.info(prefix, message, sanitizedContext);
        break;
      case 'warn':
        console.warn(prefix, message, sanitizedContext);
        break;
      case 'error':
        console.error(prefix, message, sanitizedContext);
        break;
      default:
        break;
    }
  }

  private shouldLog(level: LogLevel): boolean {
    return this.levelRank[level] >= this.levelRank[this.minLevel];
  }

  private resolveMinLevel(): LogLevel {
    const configuredLevel = environment.logging?.level;

    if (
      configuredLevel === 'debug'
      || configuredLevel === 'info'
      || configuredLevel === 'warn'
      || configuredLevel === 'error'
      || configuredLevel === 'silent'
    ) {
      return configuredLevel;
    }

    return environment.production ? 'warn' : 'info';
  }

  private sanitizeContext(context?: Record<string, unknown>): Record<string, unknown> | undefined {
    if (!context) {
      return undefined;
    }

    const redactedKeys = ['token', 'password', 'authorization', 'email', 'userMessage', 'content'];
    const sanitized: Record<string, unknown> = {};

    for (const [key, value] of Object.entries(context)) {
      const lowerKey = key.toLowerCase();
      if (redactedKeys.some((candidate) => lowerKey.includes(candidate))) {
        sanitized[key] = '[redacted]';
        continue;
      }

      sanitized[key] = value;
    }

    return sanitized;
  }
}
