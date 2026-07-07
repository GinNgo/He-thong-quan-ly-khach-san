import { Injectable } from '@angular/core';

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
  BOOKING = 'BOOKING',
  FINANCE = 'FINANCE',
  AI = 'AI',
  USER = 'USER',
  ROLE = 'ROLE',
  ROOM = 'ROOM',
  ROOM_TYPE = 'ROOM_TYPE',
  RESERVATION = 'RESERVATION',
  CHECKIN = 'CHECKIN',
  CHECKOUT = 'CHECKOUT',
  INVOICE = 'INVOICE',
  REPORT = 'REPORT',
  AI_CHAT = 'AI_CHAT',
  CUSTOMER = 'CUSTOMER'
}

@Injectable({
  providedIn: 'root'
})
export class PermissionService {

  constructor() { }

  getPermissions(): { function: string, actionMask: number }[] {
    const userStr = localStorage.getItem('user');
    if (userStr) {
      const user = JSON.parse(userStr);
      return user.permissions || [];
    }
    return [];
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
    const userStr = localStorage.getItem('user');
    if (userStr) {
      const user = JSON.parse(userStr);
      const roles = user.roles || [];
      return user.username === 'admin' || roles.includes('SUPER_ADMIN') || roles.includes('ADMIN');
    }
    return false;
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
