import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { LoggerService } from '../../shared/services/logger.service';

export const adminGuard: CanActivateFn = (_route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const logger = inject(LoggerService);

  if (authService.isAdmin()) {
    return true;
  }

  logger.warn('Navigation blocked by admin guard', {
    requestedUrl: state.url,
    role: authService.getRole(),
  });

  return router.createUrlTree(['/chat']);
};