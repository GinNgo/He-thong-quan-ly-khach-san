import { Routes } from '@angular/router';
import { LoginComponent } from './features/auth/login/login.component';
import { AdminLayout } from './layout/admin-layout/admin-layout';
import { UserManagement } from './features/admin/user-management/user-management';
import { RoomTypeManagement } from './features/admin/room-type-management/room-type-management';
import { RoomManagement } from './features/admin/room-management/room-management';
import { ServiceManagement } from './features/admin/service-management/service-management';
import { ReservationManagement } from './features/admin/reservation-management/reservation-management';
import { ReservationTimelineComponent } from './features/admin/reservation-timeline/reservation-timeline.component';
import { ReservationCreate } from './features/admin/reservation-create/reservation-create';
import { InvoiceManagement } from './features/admin/invoice-management/invoice-management';
import { Dashboard } from './features/admin/dashboard/dashboard';
import { authGuard } from './core/guards/auth-guard';
import { permissionGuard } from './core/guards/permission.guard';
import { FunctionCode, ActionCode } from './core/services/permission.service';

import { ClientLayout } from './layout/client-layout/client-layout';
import { HomeComponent } from './features/client/home/home';
import { PropertySearchPageComponent } from './features/property-search/pages/property-search-page/property-search-page';
import { RegisterComponent } from './features/auth/register/register.component';
import { AdminLoginComponent } from './features/auth/admin-login/admin-login.component';
import { AdminProfileComponent } from './features/admin/profile/profile.component';
import { ProfileComponent } from './features/client/profile/profile.component';
import { HotelDetailComponent } from './features/client/hotel-detail/hotel-detail.component';
import { BookingCheckoutComponent } from './features/client/booking-checkout/booking-checkout.component';
import { clientAuthGuard } from './core/guards/client-auth.guard';

export const routes: Routes = [
  {
    path: '',
    component: ClientLayout,
    children: [
      { path: '', component: HomeComponent, pathMatch: 'full' },
      { path: 'search', component: PropertySearchPageComponent },
      { path: 'hotel/:id', component: HotelDetailComponent },
      { path: 'booking/:roomTypeId', component: BookingCheckoutComponent, canActivate: [clientAuthGuard] },
      { path: 'profile', component: ProfileComponent, canActivate: [clientAuthGuard] }
    ]
  },
  { path: 'payment-simulator', loadComponent: () => import('./features/client/payment-simulator/payment-simulator').then(m => m.PaymentSimulatorComponent) },
  { path: 'payment-result', loadComponent: () => import('./features/client/payment-result/payment-result').then(m => m.PaymentResultComponent) },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'admin/login', component: AdminLoginComponent },
  {
    path: 'admin',
    component: AdminLayout,
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', component: Dashboard, canActivate: [permissionGuard], data: { functionCode: FunctionCode.REPORT, actionCode: ActionCode.VIEW } },
      { path: 'profile', component: AdminProfileComponent },
      { path: 'users', component: UserManagement, canActivate: [permissionGuard], data: { functionCode: FunctionCode.USER, actionCode: ActionCode.VIEW, userType: 'STAFF' } },
      { path: 'customers', component: UserManagement, canActivate: [permissionGuard], data: { functionCode: FunctionCode.CUSTOMER, actionCode: ActionCode.VIEW, userType: 'CUSTOMER' } },

      { path: 'room-types', component: RoomTypeManagement, canActivate: [permissionGuard], data: { functionCode: FunctionCode.ROOM_TYPE, actionCode: ActionCode.VIEW } },
      { path: 'rooms', component: RoomManagement, canActivate: [permissionGuard], data: { functionCode: FunctionCode.ROOM, actionCode: ActionCode.VIEW } },
      { path: 'services', component: ServiceManagement, canActivate: [permissionGuard], data: { functionCode: FunctionCode.HOTEL, actionCode: ActionCode.VIEW } },
      { path: 'reservations', component: ReservationManagement, canActivate: [permissionGuard], data: { functionCode: FunctionCode.RESERVATION, actionCode: ActionCode.VIEW } },
      { path: 'reservations/timeline', component: ReservationTimelineComponent, canActivate: [permissionGuard], data: { functionCode: FunctionCode.RESERVATION, actionCode: ActionCode.VIEW } },
      { path: 'reservations/create', component: ReservationCreate, canActivate: [permissionGuard], data: { functionCode: FunctionCode.RESERVATION, actionCode: ActionCode.CREATE } },
      { path: 'invoices', component: InvoiceManagement, canActivate: [permissionGuard], data: { functionCode: FunctionCode.INVOICE, actionCode: ActionCode.VIEW } },
      { path: 'modules', loadComponent: () => import('./features/system/module-management/module-management').then(m => m.ModuleManagementComponent), canActivate: [permissionGuard], data: { functionCode: FunctionCode.SYSTEM, actionCode: ActionCode.VIEW } },
      { path: 'chat', loadComponent: () => import('./features/admin/chat-dashboard/chat-dashboard').then(m => m.ChatDashboardComponent) },
      { path: 'properties', loadComponent: () => import('./features/admin/property-management/property-management').then(m => m.PropertyManagementComponent) },
      { path: 'plans', loadComponent: () => import('./features/admin/subscription-plans/subscription-plans').then(m => m.SubscriptionPlansComponent), canActivate: [permissionGuard], data: { functionCode: FunctionCode.SYSTEM, actionCode: ActionCode.VIEW } },
      { path: 'roles', loadComponent: () => import('./features/admin/role-management/role-management.component').then(m => m.RoleManagementComponent), canActivate: [permissionGuard], data: { functionCode: FunctionCode.ROLE, actionCode: ActionCode.VIEW } },
      { path: 'role-permissions', loadComponent: () => import('./features/admin/role-permission/role-permission.component').then(m => m.RolePermissionComponent), canActivate: [permissionGuard], data: { functionCode: FunctionCode.ROLE_PERMISSION, actionCode: ActionCode.VIEW } },
      { path: 'property-imports', loadComponent: () => import('./features/admin/property-imports/property-imports.component').then(m => m.PropertyImportsComponent), canActivate: [permissionGuard], data: { functionCode: FunctionCode.PROPERTY_IMPORT, actionCode: ActionCode.VIEW } },
      { path: 'property-claims', loadComponent: () => import('./features/admin/property-claims/property-claims.component').then(m => m.PropertyClaimsComponent), canActivate: [permissionGuard], data: { functionCode: FunctionCode.PROPERTY_CLAIM, actionCode: ActionCode.VIEW } },
      { path: '404', loadComponent: () => import('./features/error/not-found/not-found.component').then(m => m.NotFoundComponent) },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: '**', redirectTo: '404' }
    ]
  },
  { path: '403', loadComponent: () => import('./features/error/forbidden/forbidden.component').then(m => m.ForbiddenComponent) },
  { path: '**', redirectTo: '' }
];
