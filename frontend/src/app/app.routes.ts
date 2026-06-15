import { Routes } from '@angular/router';
import { LoginComponent } from './features/auth/login/login.component';
import { AdminLayout } from './layout/admin-layout/admin-layout';
import { UserManagement } from './features/admin/user-management/user-management';
import { RoomTypeManagement } from './features/admin/room-type-management/room-type-management';
import { RoomManagement } from './features/admin/room-management/room-management';
import { ServiceManagement } from './features/admin/service-management/service-management';
import { authGuard } from './core/guards/auth-guard';
import { roleGuard } from './core/guards/role-guard';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  {
    path: 'admin',
    component: AdminLayout,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'] },
    children: [
      { path: 'users', component: UserManagement },
      { path: 'room-types', component: RoomTypeManagement },
      { path: 'rooms', component: RoomManagement },
      { path: 'services', component: ServiceManagement },
      { path: '', redirectTo: 'rooms', pathMatch: 'full' }
    ]
  }
];
