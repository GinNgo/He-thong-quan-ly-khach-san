import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService } from '../../../core/services/auth';
import { HousekeepingAssignee, HousekeepingService, HousekeepingStatus, HousekeepingTask } from '../../../core/services/housekeeping.service';
import { ManagementApiService, ManagedProperty } from '../../../core/services/management-api.service';
import { ActionCode, FunctionCode, PermissionService } from '../../../core/services/permission.service';
import { FeedbackStateComponent } from '../../../shared/components/feedback-state/feedback-state.component';

@Component({
  selector: 'app-housekeeping',
  standalone: true,
  imports: [CommonModule, FormsModule, FeedbackStateComponent],
  templateUrl: './housekeeping.component.html',
  styleUrl: './housekeeping.component.css',
})
export class HousekeepingComponent implements OnInit {
  private readonly api = inject(HousekeepingService);
  private readonly managementApi = inject(ManagementApiService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly permissions = inject(PermissionService);

  properties: ManagedProperty[] = [];
  propertyId?: number;
  status?: HousekeepingStatus;
  tasks: HousekeepingTask[] = [];
  assignees: HousekeepingAssignee[] = [];
  selectedAssignee: Record<number, number | undefined> = {};
  loading = true;
  error = '';
  actionTaskId?: number;
  assigningTaskId?: number;
  completionNotice = '';
  completionBlocked = false;
  readonly hasCompletionPermission = this.permissions.hasPermission(FunctionCode.HOUSEKEEPING, ActionCode.APPROVE);

  ngOnInit(): void {
    const routePropertyId = Number(this.route.snapshot.queryParamMap.get('propertyId')) || undefined;
    this.managementApi.context(routePropertyId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: context => {
        this.properties = context.properties || [];
        this.propertyId = context.activePropertyId || this.properties[0]?.id;
        this.load();
      },
      error: error => this.fail(error, 'Không thể tải phạm vi cơ sở.'),
    });
  }

  load(): void {
    if (!this.propertyId) {
      this.tasks = [];
      this.assignees = [];
      this.loading = false;
      this.cdr.markForCheck();
      return;
    }
    this.loading = true;
    this.error = '';
    this.api.list(this.propertyId, this.status).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: tasks => {
        this.tasks = tasks;
        this.loading = false;
        this.api.assignees(this.propertyId!).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
          next: assignees => { this.assignees = assignees; this.cdr.markForCheck(); },
          error: () => this.cdr.markForCheck(),
        });
        this.cdr.markForCheck();
      },
      error: error => this.fail(error, 'Không thể tải hàng đợi housekeeping.'),
    });
  }

  claim(task: HousekeepingTask): void { this.runAction(task.id, () => this.api.claim(task.id, task.version)); }

  assign(task: HousekeepingTask): void {
    const userId = this.selectedAssignee[task.id];
    if (!userId) return;
    this.assigningTaskId = task.id;
    this.error = '';
    this.api.assign(task.id, userId, task.version).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.assigningTaskId = undefined; this.load(); },
      error: error => { this.assigningTaskId = undefined; this.fail(error, 'Không thể gán tác vụ.'); },
    });
  }

  start(task: HousekeepingTask): void { this.runAction(task.id, () => this.api.start(task.id, task.version)); }

  complete(task: HousekeepingTask): void {
    this.actionTaskId = task.id;
    this.error = '';
    this.completionNotice = '';
    this.api.complete(task.id, task.version).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: completed => {
        this.actionTaskId = undefined;
        this.completionBlocked = !completed.roomReleased;
        this.completionNotice = completed.roomReleased
          ? `Phòng ${completed.roomNumber} đã sạch và sẵn sàng mở bán.`
          : `Phòng ${completed.roomNumber} đã sạch nhưng vẫn bị chặn bởi ${completed.roomMaintenanceStatus}.`;
        this.load();
      },
      error: error => {
        this.actionTaskId = undefined;
        this.fail(error, 'Không thể hoàn tất tác vụ housekeeping.');
      },
    });
  }

  canStart(task: HousekeepingTask): boolean {
    return task.status === 'CLAIMED' && task.assignedToUserId === this.auth.getCurrentUserId() && !task.staleAssignment;
  }

  canComplete(task: HousekeepingTask): boolean {
    return this.hasCompletionPermission
      && task.status === 'IN_PROGRESS'
      && task.assignedToUserId === this.auth.getCurrentUserId();
  }

  statusLabel(status: HousekeepingStatus): string {
    return ({ PENDING: 'Chờ xử lý', CLAIMED: 'Đã nhận', IN_PROGRESS: 'Đang dọn', COMPLETED: 'Hoàn tất' } as Record<string, string>)[status] || status;
  }

  private runAction(taskId: number, request: () => Observable<HousekeepingTask>): void {
    this.actionTaskId = taskId;
    this.error = '';
    request().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.actionTaskId = undefined; this.load(); },
      error: error => { this.actionTaskId = undefined; this.fail(error, 'Không thể cập nhật tác vụ.'); },
    });
  }

  private fail(error: unknown, fallback: string): void {
    this.loading = false;
    this.error = this.apiErrorMessage(error) || fallback;
    this.cdr.markForCheck();
  }

  private apiErrorMessage(error: unknown): string | undefined {
    if (typeof error !== 'object' || error === null || !('error' in error)) return undefined;
    const payload = (error as { error?: unknown }).error;
    if (typeof payload !== 'object' || payload === null || !('message' in payload)) return undefined;
    const message = (payload as { message?: unknown }).message;
    return typeof message === 'string' && message.trim() ? message : undefined;
  }
}
