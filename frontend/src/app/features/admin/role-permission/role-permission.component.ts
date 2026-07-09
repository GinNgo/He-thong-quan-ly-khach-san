import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SharedModule } from '@app/shared/shared.module';
import { AppFunction, AppModule, Role, RoleService } from '@app/core/services/role.service';
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

  actions = [
    { label: 'Xem', value: 1 },
    { label: 'Them', value: 2 },
    { label: 'Sua', value: 4 },
    { label: 'Xoa', value: 8 },
    { label: 'Xuat', value: 16 },
    { label: 'Duyet', value: 32 }
  ];

  private roleService = inject(RoleService);
  private messageService = inject(MessageService);
  private route = inject(ActivatedRoute);

  ngOnInit(): void {
    this.loadRoles();
  }

  loadRoles(): void {
    this.roleService.getRoles().subscribe({
      next: (data) => {
        this.roles = data;
        const requestedRoleId = Number(this.route.snapshot.queryParamMap.get('roleId'));
        this.selectedRole = this.roles.find((role) => role.id === requestedRoleId) || this.roles[0] || null;
        this.loadPermissions();
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Loi', detail: 'Khong the tai danh sach vai tro.' });
      }
    });
  }

  onRoleChange(role: Role) {
    this.selectedRole = role;
    this.loadPermissions();
  }

  loadPermissions(): void {
    if (!this.selectedRole) {
      this.modules = [];
      return;
    }

    this.loading = true;
    this.roleService.getRolePermissionsTree(this.selectedRole.id).subscribe({
      next: (data) => {
        this.modules = data;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.messageService.add({ severity: 'error', summary: 'Loi', detail: 'Khong the tai ma tran phan quyen.' });
      }
    });
  }

  hasPermission(func: AppFunction, actionValue: number): boolean {
    return ((func.actionMask || 0) & actionValue) === actionValue;
  }

  togglePermission(func: AppFunction, actionValue: number, checked: boolean) {
    const currentMask = func.actionMask || 0;
    func.actionMask = checked ? currentMask | actionValue : currentMask & ~actionValue;
  }

  toggleModule(module: AppModule, actionValue: number, checked: boolean) {
    module.functions.forEach((func) => this.togglePermission(func, actionValue, checked));
  }

  moduleHasPermission(module: AppModule, actionValue: number): boolean {
    return module.functions.length > 0 && module.functions.every((func) => this.hasPermission(func, actionValue));
  }

  savePermissions() {
    if (!this.selectedRole) return;

    const permissions = this.modules.flatMap((module) =>
      module.functions.map((func) => ({
        functionId: func.id,
        actionMask: func.actionMask || 0
      }))
    );

    this.saving = true;
    this.roleService.updateRolePermissions(this.selectedRole.id, { permissions }).subscribe({
      next: () => {
        this.saving = false;
        this.messageService.add({ severity: 'success', summary: 'Thanh cong', detail: 'Da cap nhat phan quyen.' });
      },
      error: () => {
        this.saving = false;
        this.messageService.add({ severity: 'error', summary: 'Loi', detail: 'Khong the luu phan quyen.' });
      }
    });
  }
}
