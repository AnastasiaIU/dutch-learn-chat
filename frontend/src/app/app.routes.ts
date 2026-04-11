import { Routes } from '@angular/router';
import { ChatComponent } from './chat/components/chat.component';
import { AuthComponent } from './auth/components/auth.component';
import { authGuard } from './auth/guards/auth.guard';

export const appRoutes: Routes = [
  {
    path: 'auth',
    component: AuthComponent,
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'chat',
  },
  {
    path: 'chat',
    component: ChatComponent,
    canActivate: [authGuard],
  },
  {
    path: '**',
    redirectTo: 'auth',
  },
];