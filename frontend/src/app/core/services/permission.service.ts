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
  CHECKIN = 'CHECKIN',
  CHECKOUT = 'CHECKOUT',
  INVOICE = 'INVOICE',
  REPORT = 'REPORT',
  AI_CHAT = 'AI_CHAT',
  CUSTOMER = 'CUSTOMER',
  PROPERTY_IMPORT = 'PROPERTY_IMPORT',
  PROPERTY_CLAIM = 'PROPERTY_CLAIM'
}

@Injectable({
  providedIn: 'root'
})
export class PermissionService {

  constructor() { }

  getPermissions(): { function: string, actionMask: number }[] {
    return this.readStoredUser()?.permissions || [];
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
    const user = this.readStoredUser();
    if (user) {
      const roles = user.roles || [];
      return user.username === 'admin' || roles.includes('SUPER_ADMIN') || roles.includes('ADMIN');
    }
    return false;
  }

  private readStoredUser(): any | null {
    try {
      const storage = globalThis.localStorage;
      const raw = storage?.getItem('user');
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
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
