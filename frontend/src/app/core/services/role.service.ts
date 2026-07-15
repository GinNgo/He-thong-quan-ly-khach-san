import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface Role {
  id: number;
  code: string;
  name: string;
  description: string;
  status?: string;
  systemRole?: boolean;
  userCount?: number;
  roleType?: 'SYSTEM' | 'CUSTOM';
  updatedAt?: string;
}

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

  createRole(role: any): Observable<Role> {
    return this.http.post<any>(this.apiUrl, role).pipe(
      map(response => response?.data ?? response)
    );
  }

  updateRole(id: number, role: any): Observable<Role> {
    return this.http.put<any>(`${this.apiUrl}/${id}`, role).pipe(
      map(response => response?.data ?? response)
    );
  }

  deleteRole(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  getRolePermissionsTree(roleId: number): Observable<AppModule[]> {
    return this.http.get<any>(`${this.rolePermUrl}/tree/${roleId}`).pipe(
      map(response => Array.isArray(response) ? response : (response?.items ?? response?.data ?? []))
    );
  }

  updateRolePermissions(roleId: number, data: UpdateRolePermissionsRequest): Observable<void> {
    return this.http.post<void>(`${this.rolePermUrl}/${roleId}`, data);
  }
}
