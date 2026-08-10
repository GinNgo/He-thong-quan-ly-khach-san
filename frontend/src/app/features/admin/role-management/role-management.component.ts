import { ChangeDetectorRef, Component, inject, OnDestroy, OnInit } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { SharedModule } from '@app/shared/shared.module';
import { Role, RoleService } from '@app/core/services/role.service';
import { ConfirmationService, MessageService } from 'primeng/api';
import { PermissionService, ActionCode, FunctionCode } from '@app/core/services/permission.service';
import { Subscription } from 'rxjs';
import { filter, finalize, timeout } from 'rxjs/operators';

@Component({
  selector: 'app-role-management',
  standalone: true,
  imports: [SharedModule],
  providers: [ConfirmationService, MessageService],
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
    description: ''
  };

  private roleService = inject(RoleService);
  private confirmationService = inject(ConfirmationService);
  private messageService = inject(MessageService);
  private router = inject(Router);
  private permissionService = inject(PermissionService);
  private cdr = inject(ChangeDetectorRef);
  private routeSub?: Subscription;

  canCreate = this.permissionService.hasPermission(FunctionCode.ROLE, ActionCode.CREATE);
  canUpdate = this.permissionService.hasPermission(FunctionCode.ROLE, ActionCode.UPDATE);
  canDelete = this.permissionService.hasPermission(FunctionCode.ROLE, ActionCode.DELETE);
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

    this.roleForm = { id: 0, code: '', name: '', description: '' };
    this.roleDialogMode = 'create';
    this.displayDialog = true;
  }

  editRole(role: Role): void {
    if (!this.canUpdate) {
      this.messageService.add({ severity: 'warn', summary: 'Không đủ quyền', detail: 'Tài khoản chưa có quyền sửa vai trò.' });
      return;
    }

    this.roleForm = { ...role };
    this.roleDialogMode = 'edit';
    this.displayDialog = true;
  }

  saveRole(): void {
    if (this.saving) return;

    this.roleForm.code = this.roleForm.code.trim().toUpperCase();
    this.roleForm.name = this.roleForm.name.trim();
    this.roleForm.description = (this.roleForm.description || '').trim();

    if (!this.roleForm.code || !this.roleForm.name) {
      this.messageService.add({ severity: 'warn', summary: 'Thiếu thông tin', detail: 'Vui lòng nhập mã và tên vai trò.' });
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

    const request = this.roleDialogMode === 'create'
      ? this.roleService.createRole(this.roleForm)
      : this.roleService.updateRole(this.roleForm.id, this.roleForm);

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
    if (role.systemRole) {
      this.messageService.add({ severity: 'warn', summary: 'Vai trò hệ thống', detail: 'Không thể ngừng sử dụng vai trò hệ thống.' });
      return;
    }

    this.confirmationService.confirm({
      message: `Bạn có chắc muốn xóa vai trò "${role.name}"?`,
      header: 'Xác nhận xóa',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.saving = true;
        this.roleService.deleteRole(role.id).pipe(
          finalize(() => {
            this.saving = false;
            this.cdr.detectChanges();
          })
        ).subscribe({
          next: () => {
            this.messageService.add({ severity: 'success', summary: 'Thành công', detail: 'Đã xóa vai trò.' });
            this.loadRoles();
          },
          error: (error) => {
            const detail = error?.error?.message || 'Không thể xóa vai trò này.';
            this.messageService.add({ severity: 'error', summary: 'Lỗi', detail });
          }
        });
      }
    });
  }

  openPermissions(role: Role): void {
    this.router.navigate(['/admin/role-permissions'], { queryParams: { roleId: role.id } });
  }
}
