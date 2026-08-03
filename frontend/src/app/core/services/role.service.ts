import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface Role {
  id: number;
  code: string;
  name: string;
  description?: string;
  status?: 'ACTIVE' | 'INACTIVE';
  systemRole?: boolean;
  userCount?: number;
  roleType?: 'SYSTEM' | 'CUSTOM';
  updatedAt?: string;
  version?: number;
}

export const SYSTEM_ROLE_CODES = new Set([
  'SUPER_ADMIN', 'ADMIN', 'CUSTOMER', 'PROPERTY_OWNER', 'HOTEL_ADMIN',
  'HOTEL_MANAGER', 'RECEPTIONIST', 'ACCOUNTANT'
]);

export function isGovernedSystemRole(role: Role | null | undefined): boolean {
  return Boolean(role?.systemRole)
    || role?.roleType === 'SYSTEM'
    || SYSTEM_ROLE_CODES.has((role?.code || '').trim().toUpperCase());
}

export interface CreateRoleRequest {
  code: string;
  name: string;
  description?: string;
}

export interface UpdateRoleRequest extends CreateRoleRequest {}

export interface AppModule {
  id: number;
  code: string;
  name: string;
  functions: AppFunction[];
}

export interface AppFunction {
  id: number;
  code: string;
  name: string;
  moduleId: number;
  url?: string;
  icon?: string;
  sortOrder?: number;
  actionMask: number;
}

export interface UpdateRolePermissionsRequest {
  expectedVersion: number;
  permissions: Array<{
    functionId: number;
    actionMask: number;
  }>;
}

@Injectable({
  providedIn: 'root'
})
export class RoleService {
  private apiUrl = `${environment.apiUrl}/roles`;
  private rolePermUrl = `${environment.apiUrl}/role-permissions`;
  private http = inject(HttpClient);

  getRoles(): Observable<Role[]> {
    return this.http.get<any>(this.apiUrl).pipe(
      map(response => Array.isArray(response) ? response : (response?.items ?? response?.data ?? []))
    );
  }

  getRoleById(id: number): Observable<Role> {
    return this.http.get<any>(`${this.apiUrl}/${id}`).pipe(
      map(response => response?.data ?? response)
    );
  }

  createRole(role: CreateRoleRequest): Observable<Role> {
    return this.http.post<any>(this.apiUrl, role).pipe(
      map(response => response?.data ?? response)
    );
  }

  updateRole(id: number, role: UpdateRoleRequest): Observable<Role> {
    return this.http.put<any>(`${this.apiUrl}/${id}`, role).pipe(
      map(response => response?.data ?? response)
    );
  }

  deleteRole(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  reactivateRole(id: number): Observable<Role> {
    return this.http.post<any>(`${this.apiUrl}/${id}/reactivate`, {}).pipe(
      map(response => response?.data ?? response)
    );
  }

  getRolePermissionsTree(roleId: number): Observable<AppModule[]> {
    return this.http.get<any>(`${this.rolePermUrl}/tree/${roleId}`).pipe(
      map(response => Array.isArray(response) ? response : (response?.items ?? response?.data ?? []))
    );
  }

  updateRolePermissions(roleId: number, data: UpdateRolePermissionsRequest): Observable<number> {
    return this.http.post<number>(`${this.rolePermUrl}/${roleId}`, data);
  }
}
