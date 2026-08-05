import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface User {
  id: number;
  version: number;
  username: string;
  email: string;
  fullName?: string;
  phone?: string;
  avatarUrl?: string;
  roles: any[];
  status: string;
  createdAt?: string;
  hotel?: { id: number; name: string };
  staffAssignments?: StaffAssignment[];
}

export interface PropertyOption {
  id: number;
  name: string;
}

export interface StaffRoleOption {
  id: number;
  code: string;
  name: string;
}

export interface StaffCreateRequest {
  username: string;
  email: string;
  password: string;
  fullName: string;
  phone?: string | null;
  roleIds: number[];
  hotelId: number;
}

export interface StaffUpdateRequest {
  fullName: string;
  phone?: string | null;
  password?: string | null;
  roleIds: number[];
  hotelId: number;
  assignmentReason?: string | null;
  expectedVersion: number;
  changeReason: string;
}

export interface StaffAssignment {
  id: number;
  hotelId: number;
  hotelName: string;
  status: 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';
  statusReason?: string;
  startDate?: string;
  endDate?: string;
}

export interface StaffLifecycleRequest {
  hotelId: number;
  reason: string;
  expectedVersion: number;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface ProfileUpdateRequest {
  fullName: string;
  email: string;
  phone?: string | null;
  avatarUrl?: string | null;
}

export interface AvatarUploadResponse {
  url: string;
  contentType: 'image/jpeg' | 'image/png' | 'image/webp';
  width: number;
  height: number;
}

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/users`;

  getUsers(): Observable<User[]> {
    return this.http.get<User[]>(this.apiUrl);
  }

  getStaff(): Observable<User[]> {
    return this.http.get<User[]>(`${this.apiUrl}/staff`);
  }

  getStaffProperties(): Observable<PropertyOption[]> {
    return this.http.get<PropertyOption[]>(`${this.apiUrl}/staff/properties`);
  }

  getStaffRoles(): Observable<StaffRoleOption[]> {
    return this.http.get<StaffRoleOption[]>(`${this.apiUrl}/staff/roles`);
  }

  getUserById(id: number): Observable<User> {
    return this.http.get<User>(`${this.apiUrl}/${id}`);
  }

  createUser(user: any): Observable<User> {
    return this.http.post<User>(this.apiUrl, user);
  }

  createStaff(request: StaffCreateRequest): Observable<User> {
    return this.http.post<User>(`${this.apiUrl}/staff`, request);
  }

  updateStaff(id: number, request: StaffUpdateRequest): Observable<User> {
    return this.http.put<User>(`${this.apiUrl}/staff/${id}`, request);
  }

  updateUser(id: number, user: any): Observable<User> {
    return this.http.put<User>(`${this.apiUrl}/${id}`, user);
  }

  deactivateStaff(id: number, request: StaffLifecycleRequest): Observable<User> {
    return this.http.post<User>(`${this.apiUrl}/${id}/deactivate`, request);
  }

  reactivateStaff(id: number, request: StaffLifecycleRequest): Observable<User> {
    return this.http.post<User>(`${this.apiUrl}/${id}/reactivate`, request);
  }

  getProfile(): Observable<User> {
    return this.http.get<User>(`${this.apiUrl}/me`);
  }

  updateProfile(user: ProfileUpdateRequest): Observable<User> {
    return this.http.put<User>(`${this.apiUrl}/me`, user);
  }

  uploadAvatar(file: File): Observable<AvatarUploadResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<AvatarUploadResponse>(`${environment.apiUrl}/uploads/image`, formData);
  }

  changePassword(data: ChangePasswordRequest): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/me/password`, data);
  }
}
