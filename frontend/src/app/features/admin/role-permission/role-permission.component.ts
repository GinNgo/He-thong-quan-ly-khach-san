import { Component, inject, OnInit } from '@angular/core';
import { SharedModule } from '@app/shared/shared.module';
import { RoleService, Role, AppModule, AppFunction } from '@app/core/services/role.service';
import { MessageService } from 'primeng/api';
@Component({
  selector: 'app-role-permission',
  standalone: true,
  imports: [SharedModule],
  templateUrl: './role-permission.component.html'
})
export class RolePermissionComponent implements OnInit {
  roles: Role[] = [];
  selectedRole: Role | null = null;
  
  modules: AppModule[] = [];
  loading = false;
  saving = false;

  // Mask mapping
  // VIEW = 1, CREATE = 2, UPDATE = 4, DELETE = 8, EXPORT = 16, APPROVE = 32
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

  ngOnInit(): void {
    this.loadRoles();
  }

  loadRoles(): void {
    this.roleService.getRoles().subscribe({
      next: (data) => {
        this.roles = data;
        if (this.roles.length > 0) {
          this.selectedRole = this.roles[0];
          this.loadPermissions();
        }
      }
    });
  }

  onRoleChange() {
    if (this.selectedRole) {
      this.loadPermissions();
    }
  }

  loadPermissions(): void {
    if (!this.selectedRole) return;
    this.loading = true;
    this.roleService.getRolePermissionsTree(this.selectedRole.id).subscribe({
      next: (data) => {
        this.modules = data;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  hasPermission(func: AppFunction, actionValue: number): boolean {
    return (func.actionMask & actionValue) === actionValue;
  }

  togglePermission(func: AppFunction, actionValue: number, event: any) {
    const isChecked = event.checked;
    if (isChecked) {
      func.actionMask = func.actionMask | actionValue;
    } else {
      func.actionMask = func.actionMask & ~actionValue;
    }
  }

  savePermissions() {
    if (!this.selectedRole) return;
    this.saving = true;

    // Flatten functions to send to backend
    const functionMasks: { [key: string]: number } = {};
    this.modules.forEach(m => {
      m.functions.forEach(f => {
        if (f.actionMask > 0) {
          functionMasks[f.code] = f.actionMask;
        }
      });
    });

    const payload = {
      functionMasks: functionMasks
    };

    this.roleService.updateRolePermissions(this.selectedRole.id, payload).subscribe({
      next: () => {
        this.saving = false;
        this.messageService.add({severity: 'success', summary: 'Thành công', detail: 'Cập nhật quyền thành công!'});
      },
      error: () => {
        this.saving = false;
        this.messageService.add({severity: 'error', summary: 'Lỗi', detail: 'Có lỗi xảy ra khi lưu quyền.'});
      }
    });
  }
}
