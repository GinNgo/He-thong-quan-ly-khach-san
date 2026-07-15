import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SharedModule } from '@app/shared/shared.module';
import { AppFunction, AppModule, Role, RoleService } from '@app/core/services/role.service';
import { ConfirmationService, MessageService } from 'primeng/api';
import { finalize, timeout } from 'rxjs/operators';

@Component({
  selector: 'app-role-permission',
  standalone: true,
  imports: [SharedModule],
  providers: [MessageService, ConfirmationService],
  templateUrl: './role-permission.component.html'
})
export class RolePermissionComponent implements OnInit {
  roles: Role[] = [];
  selectedRole: Role | null = null;
  modules: AppModule[] = [];
  loading = false;
  loadingRoles = false;
  saving = false;
  errorMessage = '';
  dirty = false;
  private originalMasks = new Map<number, number>();

  actions = [
    { label: 'Xem', value: 1 },
    { label: 'Thêm', value: 2 },
    { label: 'Sửa', value: 4 },
    { label: 'Xóa', value: 8 },
    { label: 'Xuất', value: 16 },
    { label: 'Duyệt', value: 32 }
  ];

  private roleService = inject(RoleService);
  private messageService = inject(MessageService);
  private route = inject(ActivatedRoute);
  private confirmationService = inject(ConfirmationService);
  private cdr = inject(ChangeDetectorRef);

  get protectedRole(): boolean { return this.selectedRole?.code === 'SUPER_ADMIN'; }

  ngOnInit(): void {
    this.loadRoles();
  }

  loadRoles(): void {
    this.loadingRoles = true;
    this.errorMessage = '';

    this.roleService.getRoles().pipe(
      timeout(10000),
      finalize(() => {
        this.loadingRoles = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: (data) => {
        this.roles = data;
        const requestedRoleId = Number(this.route.snapshot.queryParamMap.get('roleId'));
        this.selectedRole = this.roles.find((role) => role.id === requestedRoleId) || this.roles[0] || null;
        this.loadPermissions();
      },
      error: (error) => {
        this.errorMessage = error?.error?.message || 'Không thể tải danh sách vai trò.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail: this.errorMessage });
      }
    });
  }

  onRoleChange(role: Role): void {
    this.selectedRole = role;
    this.loadPermissions();
  }

  loadPermissions(): void {
    if (!this.selectedRole) {
      this.modules = [];
      return;
    }

    this.loading = true;
    this.errorMessage = '';

    this.roleService.getRolePermissionsTree(this.selectedRole.id).pipe(
      timeout(10000),
      finalize(() => {
        this.loading = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: (data) => {
        this.modules = data;
        this.originalMasks = new Map(data.flatMap(module => module.functions.map(func => [func.id, func.actionMask || 0] as [number, number])));
        this.dirty = false;
      },
      error: (error) => {
        this.errorMessage = error?.error?.message || 'Không thể tải ma trận phân quyền.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail: this.errorMessage });
      }
    });
  }

  hasPermission(func: AppFunction, actionValue: number): boolean {
    return ((func.actionMask || 0) & actionValue) === actionValue;
  }

  togglePermission(func: AppFunction, actionValue: number, checked: boolean): void {
    if (this.protectedRole) return;
    const currentMask = func.actionMask || 0;
    func.actionMask = checked ? currentMask | actionValue : currentMask & ~actionValue;
    this.updateDirty();
  }

  toggleModule(module: AppModule, actionValue: number, checked: boolean): void {
    module.functions.forEach((func) => this.togglePermission(func, actionValue, checked));
  }

  toggleAll(actionValue: number, checked: boolean): void { this.modules.forEach(module => this.toggleModule(module, actionValue, checked)); }
  allHavePermission(actionValue: number): boolean { const funcs=this.modules.flatMap(m=>m.functions); return funcs.length>0 && funcs.every(f=>this.hasPermission(f, actionValue)); }
  resetPermissions(): void { this.modules.forEach(m=>m.functions.forEach(f=>f.actionMask=this.originalMasks.get(f.id)||0)); this.dirty=false; }
  private updateDirty(): void { this.dirty=this.modules.some(m=>m.functions.some(f=>(f.actionMask||0)!==(this.originalMasks.get(f.id)||0))); }

  moduleHasPermission(module: AppModule, actionValue: number): boolean {
    return module.functions.length > 0 && module.functions.every((func) => this.hasPermission(func, actionValue));
  }

  savePermissions(): void {
    if (!this.selectedRole || this.saving || !this.dirty || this.protectedRole) return;

    this.confirmationService.confirm({ header: 'Xác nhận lưu phân quyền', message: `Áp dụng thay đổi quyền cho vai trò "${this.selectedRole.name}"?`, icon: 'pi pi-exclamation-triangle', acceptLabel: 'Lưu thay đổi', rejectLabel: 'Hủy', accept: () => this.performSave() });
  }

  private performSave(): void {
    if (!this.selectedRole) return;

    const permissions = this.modules.flatMap((module) =>
      module.functions.map((func) => ({
        functionId: func.id,
        actionMask: func.actionMask || 0
      }))
    );

    this.saving = true;
    this.roleService.updateRolePermissions(this.selectedRole.id, { permissions }).pipe(
      timeout(10000),
      finalize(() => {
        this.saving = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Thành công', detail: 'Đã cập nhật phân quyền.' });
        this.loadPermissions();
      },
      error: (error) => {
        const detail = error?.error?.message || 'Không thể lưu phân quyền.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail });
      }
    });
  }
}
