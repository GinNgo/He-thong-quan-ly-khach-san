import { ChangeDetectorRef, Component, inject, OnDestroy, OnInit } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { SharedModule } from '@app/shared/shared.module';
import { CreateRoleRequest, isGovernedSystemRole, Role, RoleService, UpdateRoleRequest } from '@app/core/services/role.service';
import { MessageService } from 'primeng/api';
import { PermissionService, ActionCode, FunctionCode } from '@app/core/services/permission.service';
import { Observable, Subscription } from 'rxjs';
import { filter, finalize, timeout } from 'rxjs/operators';

@Component({
  selector: 'app-role-management',
  standalone: true,
  imports: [SharedModule],
  providers: [MessageService],
  templateUrl: './role-management.component.html'
})
export class RoleManagementComponent implements OnInit, OnDestroy {
  roles: Role[] = [];
  loading = true;
  saving = false;
  errorMessage = '';
  searchText = '';
  statusFilter = '';

  displayDialog = false;
  roleDialogMode: 'create' | 'edit' = 'create';
  roleForm: Role = {
    id: 0,
    code: '',
    name: '',
    description: '',
    version: 0
  };
  changeReason = '';
  lifecycleDialogVisible = false;
  lifecycleMode: 'deactivate' | 'reactivate' = 'deactivate';
  lifecycleRole: Role | null = null;
  lifecycleReason = '';

  private roleService = inject(RoleService);
  private messageService = inject(MessageService);
  private router = inject(Router);
  private permissionService = inject(PermissionService);
  private cdr = inject(ChangeDetectorRef);
  private routeSub?: Subscription;

  canCreate = this.permissionService.hasPermission(FunctionCode.ROLE, ActionCode.CREATE);
  canUpdate = this.permissionService.hasPermission(FunctionCode.ROLE, ActionCode.UPDATE);
  canDelete = this.permissionService.hasPermission(FunctionCode.ROLE, ActionCode.DELETE);
  canViewAudit = this.permissionService.hasPermission(FunctionCode.AUDIT_LOG, ActionCode.VIEW);
  statusOptions = [{ label: 'Đang hoạt động', value: 'ACTIVE' }, { label: 'Ngừng hoạt động', value: 'INACTIVE' }];

  get filteredRoles(): Role[] {
    const key = this.searchText.trim().toLocaleLowerCase('vi');
    return this.roles.filter(role => (!key || `${role.code} ${role.name}`.toLocaleLowerCase('vi').includes(key)) &&
      (!this.statusFilter || (role.status || 'ACTIVE') === this.statusFilter));
  }

  ngOnInit(): void {
    this.loadRoles();
    this.routeSub = this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => {
        if (event.urlAfterRedirects.split('?')[0] === '/admin/roles') {
          this.loadRoles();
        }
      });
  }

  ngOnDestroy(): void {
    this.routeSub?.unsubscribe();
  }

  loadRoles(): void {
    this.loading = true;
    this.errorMessage = '';

    this.roleService.getRoles().pipe(
      timeout(10000),
      finalize(() => {
        this.loading = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: (data) => {
        this.roles = data;
      },
      error: (error) => {
        this.errorMessage = error?.error?.message || 'Không thể tải danh sách vai trò.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail: this.errorMessage });
      }
    });
  }

  openNew(): void {
    if (!this.canCreate) {
      this.messageService.add({ severity: 'warn', summary: 'Không đủ quyền', detail: 'Tài khoản chưa có quyền thêm vai trò.' });
      return;
    }

    this.roleForm = { id: 0, code: '', name: '', description: '', version: 0 };
    this.changeReason = '';
    this.roleDialogMode = 'create';
    this.displayDialog = true;
  }

  editRole(role: Role): void {
    if (!this.canUpdate) {
      this.messageService.add({ severity: 'warn', summary: 'Không đủ quyền', detail: 'Tài khoản chưa có quyền sửa vai trò.' });
      return;
    }
    if (isGovernedSystemRole(role)) {
      this.messageService.add({ severity: 'warn', summary: 'Vai trò hệ thống', detail: 'Không thể sửa thông tin hoặc trạng thái vai trò hệ thống.' });
      return;
    }

    this.roleForm = { ...role };
    this.changeReason = '';
    this.roleDialogMode = 'edit';
    this.displayDialog = true;
  }

  saveRole(): void {
    if (this.saving) return;

    this.roleForm.code = this.roleForm.code.trim().toUpperCase();
    this.roleForm.name = this.roleForm.name.trim();
    this.roleForm.description = (this.roleForm.description || '').trim();
    this.changeReason = this.changeReason.trim();

    if (!this.roleForm.code || !this.roleForm.name || this.changeReason.length < 3) {
      this.messageService.add({ severity: 'warn', summary: 'Thiếu thông tin', detail: 'Vui lòng nhập mã, tên và lý do thay đổi ít nhất 3 ký tự.' });
      return;
    }

    if (this.roleDialogMode === 'create' && !this.canCreate) {
      this.messageService.add({ severity: 'warn', summary: 'Không đủ quyền', detail: 'Tài khoản chưa có quyền thêm vai trò.' });
      return;
    }

    if (this.roleDialogMode === 'edit' && !this.canUpdate) {
      this.messageService.add({ severity: 'warn', summary: 'Không đủ quyền', detail: 'Tài khoản chưa có quyền sửa vai trò.' });
      return;
    }

    const basePayload: CreateRoleRequest = {
      code: this.roleForm.code,
      name: this.roleForm.name,
      description: this.roleForm.description,
      reason: this.changeReason
    };
    if (this.roleDialogMode === 'edit' && this.roleForm.version === undefined) {
      this.messageService.add({ severity: 'warn', summary: 'Dữ liệu đã cũ', detail: 'Hãy tải lại vai trò trước khi cập nhật.' });
      return;
    }
    const request = this.roleDialogMode === 'create'
      ? this.roleService.createRole(basePayload)
      : this.roleService.updateRole(this.roleForm.id, {
          ...basePayload,
          expectedVersion: this.roleForm.version!
        } as UpdateRoleRequest);

    this.saving = true;
    request.pipe(
      finalize(() => {
        this.saving = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: () => {
        this.displayDialog = false;
        this.messageService.add({ severity: 'success', summary: 'Thành công', detail: 'Đã lưu vai trò.' });
        this.loadRoles();
      },
      error: (error) => {
        const detail = error?.error?.message || 'Không thể lưu vai trò.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail });
      }
    });
  }

  deleteRole(role: Role): void {
    if (!this.canDelete) {
      this.messageService.add({ severity: 'warn', summary: 'Không đủ quyền', detail: 'Tài khoản chưa có quyền xóa vai trò.' });
      return;
    }
    if (isGovernedSystemRole(role)) {
      this.messageService.add({ severity: 'warn', summary: 'Vai trò hệ thống', detail: 'Không thể ngừng sử dụng vai trò hệ thống.' });
      return;
    }

    this.openLifecycle(role, 'deactivate');
  }

  reactivateRole(role: Role): void {
    if (!this.canUpdate) {
      this.messageService.add({ severity: 'warn', summary: 'Không đủ quyền', detail: 'Tài khoản chưa có quyền kích hoạt lại vai trò.' });
      return;
    }
    if (isGovernedSystemRole(role) || role.status !== 'INACTIVE' || this.saving) return;
    this.openLifecycle(role, 'reactivate');
  }

  submitLifecycle(): void {
    if (this.saving || !this.lifecycleRole) return;
    const reason = this.lifecycleReason.trim();
    if (reason.length < 3 || this.lifecycleRole.version === undefined) {
      this.messageService.add({ severity: 'warn', summary: 'Thiếu thông tin', detail: 'Hãy tải lại dữ liệu và nhập lý do ít nhất 3 ký tự.' });
      return;
    }
    const request: Observable<Role | void> = this.lifecycleMode === 'deactivate'
      ? this.roleService.deleteRole(this.lifecycleRole.id, { expectedVersion: this.lifecycleRole.version, reason })
      : this.roleService.reactivateRole(this.lifecycleRole.id, { expectedVersion: this.lifecycleRole.version, reason });

    this.saving = true;
    request.pipe(
      finalize(() => {
        this.saving = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: () => {
        this.lifecycleDialogVisible = false;
        this.messageService.add({
          severity: 'success',
          summary: 'Thành công',
          detail: this.lifecycleMode === 'deactivate' ? 'Đã ngừng sử dụng vai trò.' : 'Đã kích hoạt lại vai trò.'
        });
        this.loadRoles();
      },
      error: (error) => {
        const detail = error?.error?.message || 'Không thể cập nhật vòng đời vai trò.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail });
      }
    });
  }

  private openLifecycle(role: Role, mode: 'deactivate' | 'reactivate'): void {
    if (role.version === undefined) {
      this.messageService.add({ severity: 'warn', summary: 'Dữ liệu đã cũ', detail: 'Hãy tải lại vai trò trước khi thay đổi trạng thái.' });
      return;
    }
    this.lifecycleRole = role;
    this.lifecycleMode = mode;
    this.lifecycleReason = '';
    this.lifecycleDialogVisible = true;
  }

  openPermissions(role: Role): void {
    this.router.navigate(['/admin/role-permissions'], { queryParams: { roleId: role.id } });
  }

  openHistory(role: Role): void {
    const base = this.router.url.startsWith('/management') ? '/management/audit-log' : '/admin/audit-log';
    this.router.navigate([base], {
      queryParams: { domain: 'ROLE', aggregateType: 'ROLE', aggregateId: role.id }
    });
  }

  isSystemRole(role: Role): boolean {
    return isGovernedSystemRole(role);
  }
}
