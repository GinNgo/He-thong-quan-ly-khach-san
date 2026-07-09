import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Role {
  id: number;
  code: string;
  name: string;
  description: string;
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
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/roles`;
  private rolePermUrl = `${environment.apiUrl}/role-permissions`;

  getRoles(): Observable<Role[]> {
    return this.http.get<Role[]>(this.apiUrl);
  }

  getRoleById(id: number): Observable<Role> {
    return this.http.get<Role>(`${this.apiUrl}/${id}`);
  }

  createRole(role: any): Observable<Role> {
    return this.http.post<Role>(this.apiUrl, role);
  }

  updateRole(id: number, role: any): Observable<Role> {
    return this.http.put<Role>(`${this.apiUrl}/${id}`, role);
  }

  deleteRole(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  getRolePermissionsTree(roleId: number): Observable<AppModule[]> {
    return this.http.get<AppModule[]>(`${this.rolePermUrl}/tree/${roleId}`);
  }

  updateRolePermissions(roleId: number, data: UpdateRolePermissionsRequest): Observable<void> {
    return this.http.post<void>(`${this.rolePermUrl}/${roleId}`, data);
  }
}
