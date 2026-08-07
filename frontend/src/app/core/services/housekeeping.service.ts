import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type HousekeepingStatus = 'PENDING' | 'CLAIMED' | 'IN_PROGRESS' | 'COMPLETED';

export interface HousekeepingTask {
  id: number;
  hotelId: number;
  roomId: number;
  roomNumber: string;
  reservationId: number | null;
  status: HousekeepingStatus;
  assignedToUserId: number | null;
  assignedToUsername: string | null;
  assignedToName: string | null;
  assignedAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  note: string | null;
  version: number;
  staleAssignment: boolean;
  roomStatus: string;
  roomHousekeepingStatus: string;
  roomMaintenanceStatus: string;
  roomReleased: boolean;
}

export interface HousekeepingAssignee {
  userId: number;
  username: string;
  fullName: string | null;
}

@Injectable({ providedIn: 'root' })
export class HousekeepingService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/housekeeping`;

  list(propertyId: number, status?: HousekeepingStatus): Observable<HousekeepingTask[]> {
    let params = new HttpParams().set('propertyId', propertyId);
    if (status) params = params.set('status', status);
    return this.http.get<HousekeepingTask[]>(`${this.url}/tasks`, { params });
  }

  assignees(propertyId: number): Observable<HousekeepingAssignee[]> {
    return this.http.get<HousekeepingAssignee[]>(`${this.url}/assignees`, {
      params: new HttpParams().set('propertyId', propertyId),
    });
  }

  claim(taskId: number, expectedVersion?: number): Observable<HousekeepingTask> {
    return this.http.post<HousekeepingTask>(`${this.url}/tasks/${taskId}/claim`, { expectedVersion });
  }

  assign(taskId: number, userId: number, expectedVersion?: number): Observable<HousekeepingTask> {
    return this.http.post<HousekeepingTask>(`${this.url}/tasks/${taskId}/assign`, { userId, expectedVersion });
  }

  start(taskId: number, expectedVersion?: number): Observable<HousekeepingTask> {
    return this.http.post<HousekeepingTask>(`${this.url}/tasks/${taskId}/start`, { expectedVersion });
  }

  complete(taskId: number, expectedVersion: number): Observable<HousekeepingTask> {
    return this.http.post<HousekeepingTask>(`${this.url}/tasks/${taskId}/complete`, { expectedVersion });
  }
}
