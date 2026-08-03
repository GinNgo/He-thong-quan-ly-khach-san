import { Injectable } from '@angular/core';
import { AuthService, PermissionMask } from './auth';

export enum ActionCode {
  VIEW = 1,
  CREATE = 2,
  UPDATE = 4,
  DELETE = 8,
  EXPORT = 16,
  APPROVE = 32
}

export enum FunctionCode {
  SYSTEM = 'SYSTEM',
  HOTEL = 'HOTEL',
  HOTEL_SERVICE = 'HOTEL_SERVICE',
  BOOKING = 'BOOKING',
  FINANCE = 'FINANCE',
  RESERVATION_PAYMENT = 'RESERVATION_PAYMENT',
  AI = 'AI',
  USER = 'USER',
  ROLE = 'ROLE',
  ROLE_PERMISSION = 'ROLE_PERMISSION',
  ROOM = 'ROOM',
  ROOM_TYPE = 'ROOM_TYPE',
  RESERVATION = 'RESERVATION',
  RESERVATION_ASSIGNMENT = 'RESERVATION_ASSIGNMENT',
  RESERVATION_CANCEL = 'RESERVATION_CANCEL',
  RESERVATION_NO_SHOW = 'RESERVATION_NO_SHOW',
  CHECKIN = 'CHECKIN',
  CHECKOUT = 'CHECKOUT',
  INVOICE = 'INVOICE',
  REPORT = 'REPORT',
  AI_CHAT = 'AI_CHAT',
  CUSTOMER = 'CUSTOMER',
  PROPERTY_IMPORT = 'PROPERTY_IMPORT',
  PROPERTY_CLAIM = 'PROPERTY_CLAIM',
  PROPERTY_PAYMENT_CONFIG = 'PROPERTY_PAYMENT_CONFIG',
  PROPERTY_REFUND = 'PROPERTY_REFUND',
  PLATFORM_REFUND = 'PLATFORM_REFUND',
  PLATFORM_BILLING = 'PLATFORM_BILLING',
  PLATFORM_REVENUE = 'PLATFORM_REVENUE',
  PAYMENT_READINESS = 'PAYMENT_READINESS',
  AUDIT_LOG = 'AUDIT_LOG'
}

@Injectable({
  providedIn: 'root'
})
export class PermissionService {

  constructor(private readonly authService: AuthService) { }

  // Client checks only shape presentation; every protected action is re-authorized by the backend.
  getPermissions(): PermissionMask[] {
    return this.authService.getPermissions();
  }

  hasPermission(functionCode: string, actionCode: number): boolean {
    const isSuperAdmin = this.isSuperAdmin();
    if (isSuperAdmin) {
      return true;
    }

    const permissions = this.getPermissions();
    const perm = permissions.find(p => p.function === functionCode);
    
    if (!perm) {
      return false;
    }

    return (perm.actionMask & actionCode) === actionCode;
  }

  isSuperAdmin(): boolean {
    const roles = this.authService.getRoles();
    return roles.includes('SUPER_ADMIN') || roles.includes('ROLE_SUPER_ADMIN');
  }

  canView(functionCode: string): boolean {
    return this.hasPermission(functionCode, ActionCode.VIEW);
  }

  canCreate(functionCode: string): boolean {
    return this.hasPermission(functionCode, ActionCode.CREATE);
  }

  canUpdate(functionCode: string): boolean {
    return this.hasPermission(functionCode, ActionCode.UPDATE);
  }

  canDelete(functionCode: string): boolean {
    return this.hasPermission(functionCode, ActionCode.DELETE);
  }

  canExport(functionCode: string): boolean {
    return this.hasPermission(functionCode, ActionCode.EXPORT);
  }

  canApprove(functionCode: string): boolean {
    return this.hasPermission(functionCode, ActionCode.APPROVE);
  }
}
