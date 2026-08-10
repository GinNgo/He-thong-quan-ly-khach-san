import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';

export interface OperationalTask {
  id: number;
  publicId: string;
  hotelId: number;
  taskType: string;
  functionCode: string;
  requiredAction: number;
  aggregateType: string;
  aggregateId: string;
  status: 'OPEN' | 'ASSIGNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | 'BLOCKED';
  assignedToUserId?: number;
  resultReference?: string;
  version: number;
}

@Injectable({ providedIn: 'root' })
export class OperationalTaskService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/management/tasks`;

  list(hotelId: number, status?: OperationalTask['status']) {
    let params = new HttpParams().set('hotelId', hotelId);
    if (status) params = params.set('status', status);
    return this.http.get<OperationalTask[]>(this.baseUrl, { params });
  }

  claim(task: OperationalTask) {
    return this.http.post<OperationalTask>(`${this.baseUrl}/${task.id}/claim`, null, {
      params: { expectedVersion: task.version }
    });
  }

  execute(task: OperationalTask, reason?: string) {
    return this.http.post<OperationalTask>(`${this.baseUrl}/${task.id}/execute`, {
      expectedVersion: task.version,
      command: 'COMPLETE',
      reason,
      payload: {}
    });
  }

  reassign(task: OperationalTask, assigneeUserId: number, reason: string) {
    return this.http.post<OperationalTask>(`${this.baseUrl}/${task.id}/reassign`, {
      expectedVersion: task.version,
      assigneeUserId,
      reason
    });
  }
}

