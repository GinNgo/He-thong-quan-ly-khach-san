import { Routes } from '@angular/router';
import { LoginComponent } from './features/auth/login/login.component';
import { AdminLayout } from './layout/admin-layout/admin-layout';
import { UserManagement } from './features/admin/user-management/user-management';
import { RoomTypeManagement } from './features/admin/room-type-management/room-type-management';
import { RoomManagement } from './features/admin/room-management/room-management';
import { ServiceManagement } from './features/admin/service-management/service-management';
import { ReservationManagement } from './features/admin/reservation-management/reservation-management';
import { ReservationCreate } from './features/admin/reservation-create/reservation-create';
import { InvoiceManagement } from './features/admin/invoice-management/invoice-management';
import { Dashboard } from './features/admin/dashboard/dashboard';
import { authGuard } from './core/guards/auth-guard';
import { permissionGuard } from './core/guards/permission.guard';
import { FunctionCode, ActionCode } from './core/services/permission.service';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  {
    path: 'admin',
    component: AdminLayout,
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', component: Dashboard, canActivate: [permissionGuard], data: { functionCode: FunctionCode.REPORT, actionCode: ActionCode.VIEW } },
      { path: 'users', component: UserManagement, canActivate: [permissionGuard], data: { functionCode: FunctionCode.USER, actionCode: ActionCode.VIEW } },
      { path: 'room-types', component: RoomTypeManagement, canActivate: [permissionGuard], data: { functionCode: FunctionCode.ROOM_TYPE, actionCode: ActionCode.VIEW } },
      { path: 'rooms', component: RoomManagement, canActivate: [permissionGuard], data: { functionCode: FunctionCode.ROOM, actionCode: ActionCode.VIEW } },
      { path: 'services', component: ServiceManagement, canActivate: [permissionGuard], data: { functionCode: FunctionCode.HOTEL, actionCode: ActionCode.VIEW } },
      { path: 'reservations', component: ReservationManagement, canActivate: [permissionGuard], data: { functionCode: FunctionCode.RESERVATION, actionCode: ActionCode.VIEW } },
      { path: 'reservations/create', component: ReservationCreate, canActivate: [permissionGuard], data: { functionCode: FunctionCode.RESERVATION, actionCode: ActionCode.CREATE } },
      { path: 'invoices', component: InvoiceManagement, canActivate: [permissionGuard], data: { functionCode: FunctionCode.INVOICE, actionCode: ActionCode.VIEW } },
      { path: 'modules', loadComponent: () => import('./features/system/module-management/module-management').then(m => m.ModuleManagementComponent), canActivate: [permissionGuard], data: { functionCode: FunctionCode.SYSTEM, actionCode: ActionCode.VIEW } },
      { path: 'roles', loadComponent: () => import('./features/system/role-management/role-management').then(m => m.RoleManagementComponent), canActivate: [permissionGuard], data: { functionCode: FunctionCode.ROLE, actionCode: ActionCode.VIEW } },
      { path: 'roles/permissions/:id', loadComponent: () => import('./features/system/role-permission/role-permission').then(m => m.RolePermissionComponent), canActivate: [permissionGuard], data: { functionCode: FunctionCode.ROLE, actionCode: ActionCode.UPDATE } },
      { path: '404', loadComponent: () => import('./features/error/not-found/not-found.component').then(m => m.NotFoundComponent) },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: '**', redirectTo: '404' }
    ]
  },
  { path: '403', loadComponent: () => import('./features/error/forbidden/forbidden.component').then(m => m.ForbiddenComponent) },
  { path: '**', redirectTo: 'login' }
];
