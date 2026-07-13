import { Injectable } from '@angular/core';
import { Observable, from } from 'rxjs';
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
  private apiUrl = `${environment.apiUrl}/roles`;
  private rolePermUrl = `${environment.apiUrl}/role-permissions`;

  getRoles(): Observable<Role[]> {
    return this.request<Role[]>(this.apiUrl);
  }

  getRoleById(id: number): Observable<Role> {
    return this.request<Role>(`${this.apiUrl}/${id}`);
  }

  createRole(role: any): Observable<Role> {
    return this.request<Role>(this.apiUrl, {
      method: 'POST',
      body: JSON.stringify(role)
    });
  }

  updateRole(id: number, role: any): Observable<Role> {
    return this.request<Role>(`${this.apiUrl}/${id}`, {
      method: 'PUT',
      body: JSON.stringify(role)
    });
  }

  deleteRole(id: number): Observable<void> {
    return this.request<void>(`${this.apiUrl}/${id}`, { method: 'DELETE' });
  }

  getRolePermissionsTree(roleId: number): Observable<AppModule[]> {
    return this.request<AppModule[]>(`${this.rolePermUrl}/tree/${roleId}`);
  }

  updateRolePermissions(roleId: number, data: UpdateRolePermissionsRequest): Observable<void> {
    return this.request<void>(`${this.rolePermUrl}/${roleId}`, {
      method: 'POST',
      body: JSON.stringify(data)
    });
  }

  private request<T>(url: string, options: RequestInit = {}): Observable<T> {
    return from(this.fetchJson<T>(url, options));
  }

  private async fetchJson<T>(url: string, options: RequestInit): Promise<T> {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 10000);
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...(options.headers as Record<string, string> | undefined)
    };

    if (typeof localStorage !== 'undefined') {
      const token = localStorage.getItem('token');
      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }
    }

    let response: Response;
    try {
      response = await fetch(url, {
        ...options,
        headers,
        signal: controller.signal
      });
    } catch (error: any) {
      const message = error?.name === 'AbortError'
        ? 'Yêu cầu quá thời gian chờ. Vui lòng kiểm tra backend và thử lại.'
        : 'Không thể kết nối tới máy chủ.';
      throw { status: 0, error: { message } };
    } finally {
      clearTimeout(timeoutId);
    }

    if (!response.ok) {
      let message = 'Yêu cầu không thành công.';
      try {
        const errorBody = await response.json();
        message = errorBody?.message || errorBody?.error || message;
      } catch {
        const text = await response.text();
        message = text || message;
      }
      throw { status: response.status, error: { message } };
    }

    if (response.status === 204) {
      return undefined as T;
    }

    const text = await response.text();
    return (text ? JSON.parse(text) : undefined) as T;
  }
}
