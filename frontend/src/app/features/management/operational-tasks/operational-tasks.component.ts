import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { ActionCode, PermissionService } from '../../../core/services/permission.service';
import { OperationalTask, OperationalTaskService } from '../../../core/services/operational-task.service';

@Component({
  selector: 'app-operational-tasks',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './operational-tasks.component.html'
})
export class OperationalTasksComponent implements OnInit {
  private readonly api = inject(OperationalTaskService);
  private readonly route = inject(ActivatedRoute);
  private readonly permissions = inject(PermissionService);

  tasks: OperationalTask[] = [];
  status = '';
  loading = false;
  error = '';
  hotelId?: number;
  reassignUserId?: number;

  ngOnInit(): void {
    const hotelId = Number(this.route.snapshot.queryParamMap.get('propertyId'));
    this.hotelId = Number.isInteger(hotelId) && hotelId > 0 ? hotelId : undefined;
    this.load();
  }

  load(): void {
    if (!this.hotelId) {
      this.error = 'Hãy chọn cơ sở đang quản lý để xem tác vụ.';
      return;
    }
    this.loading = true;
    this.error = '';
    this.api.list(this.hotelId, this.status as OperationalTask['status'] || undefined).pipe(
      finalize(() => this.loading = false)
    ).subscribe({
      next: tasks => this.tasks = tasks,
      error: error => this.error = error?.error?.message || 'Không thể tải hàng đợi tác vụ.'
    });
  }

  canExecute(task: OperationalTask): boolean {
    return this.permissions.hasPermission(task.functionCode, ActionCode.TASK_EXECUTE);
  }

  canReassign(): boolean {
    return this.permissions.hasPermission('OPERATIONAL_TASK', ActionCode.APPROVE);
  }

  claim(task: OperationalTask): void {
    this.api.claim(task).subscribe({ next: updated => this.replace(updated), error: error => this.showError(error) });
  }

  complete(task: OperationalTask): void {
    this.api.execute(task, 'Hoàn tất từ hàng đợi vận hành').subscribe({
      next: updated => this.replace(updated), error: error => this.showError(error)
    });
  }

  reassign(task: OperationalTask): void {
    if (!this.reassignUserId) {
      this.error = 'Nhập mã người dùng nhận tác vụ.';
      return;
    }
    this.api.reassign(task, this.reassignUserId, 'Phân công lại từ hàng đợi vận hành').subscribe({
      next: updated => this.replace(updated), error: error => this.showError(error)
    });
  }

  private replace(updated: OperationalTask): void {
    this.tasks = this.tasks.map(task => task.id === updated.id ? updated : task);
  }

  private showError(error: any): void {
    this.error = error?.error?.message || 'Không thể cập nhật tác vụ.';
  }
}
