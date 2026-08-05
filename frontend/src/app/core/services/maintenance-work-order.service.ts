import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type MaintenancePriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
export type MaintenanceWorkOrderStatus = 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export interface MaintenanceWorkOrderHistory {
  fromStatus?: MaintenanceWorkOrderStatus;
  toStatus?: MaintenanceWorkOrderStatus;
  reason?: string;
  createdAt: string;
}

export interface MaintenanceWorkOrder {
  id: number;
  propertyId: number;
  roomId: number;
  roomNumber?: string;
  reason: string;
  priority: MaintenancePriority;
  assigneeUserId?: number;
  scheduledStart?: string;
  scheduledEnd?: string;
  status: MaintenanceWorkOrderStatus;
  bookingImpact?: boolean;
  resolutionNote?: string;
  history?: MaintenanceWorkOrderHistory[];
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateMaintenanceWorkOrderRequest {
  propertyId: number;
  roomId: number;
  reason: string;
  priority: MaintenancePriority;
  assigneeUserId?: number;
  scheduledStart?: string;
  scheduledEnd?: string;
}

@Injectable({ providedIn: 'root' })
export class MaintenanceWorkOrderService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/v1/maintenance-work-orders`;

  getAll(propertyId: number, roomId: number): Observable<MaintenanceWorkOrder[]> {
    const params = new HttpParams().set('propertyId', propertyId).set('roomId', roomId);
    return this.http.get<MaintenanceWorkOrder[]>(this.baseUrl, { params });
  }

  create(request: CreateMaintenanceWorkOrderRequest): Observable<MaintenanceWorkOrder> {
    return this.http.post<MaintenanceWorkOrder>(this.baseUrl, request);
  }

  start(id: number): Observable<MaintenanceWorkOrder> { return this.transition(id, 'start', {}); }
  complete(id: number, resolutionNote?: string): Observable<MaintenanceWorkOrder> { return this.transition(id, 'complete', { resolutionNote }); }
  reopen(id: number, reason: string): Observable<MaintenanceWorkOrder> { return this.transition(id, 'reopen', { reason }); }
  cancel(id: number, reason: string): Observable<MaintenanceWorkOrder> { return this.transition(id, 'cancel', { reason }); }

  private transition(id: number, action: 'start' | 'complete' | 'reopen' | 'cancel', body: { reason?: string; resolutionNote?: string }): Observable<MaintenanceWorkOrder> {
    return this.http.post<MaintenanceWorkOrder>(`${this.baseUrl}/${id}/${action}`, body);
  }
}
