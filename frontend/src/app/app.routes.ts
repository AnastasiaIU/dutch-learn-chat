import { Routes } from '@angular/router';
import { ChatComponent } from './chat/components/chat.component';
import { AuthComponent } from './auth/components/auth.component';
import { authGuard } from './auth/guards/auth.guard';
import { adminGuard } from './auth/guards/admin.guard';
import { EvaluationAdminComponent } from './admin/components/evaluation-admin.component';

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
    path: 'admin/evaluation',
    component: EvaluationAdminComponent,
    canActivate: [authGuard, adminGuard],
  },
  {
    path: '**',
    redirectTo: 'auth',
  },
];