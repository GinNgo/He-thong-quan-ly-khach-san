import { Component, inject, OnInit } from '@angular/core';
import { SharedModule } from '@app/shared/shared.module';
import { RoleService, Role } from '@app/core/services/role.service';

@Component({
  selector: 'app-role-management',
  standalone: true,
  imports: [SharedModule],
  templateUrl: './role-management.component.html'
})
export class RoleManagementComponent implements OnInit {
  roles: Role[] = [];
  loading = true;

  displayDialog = false;
  roleDialogMode: 'create' | 'edit' = 'create';
  roleForm: any = {
    id: null,
    code: '',
    name: '',
    description: ''
  };

  private roleService = inject(RoleService);

  ngOnInit(): void {
    this.loadRoles();
  }

  loadRoles(): void {
    this.loading = true;
    this.roleService.getRoles().subscribe({
      next: (data) => {
        this.roles = data;
        this.loading = false;
      },
      error: (err) => {
        console.error('Error fetching roles', err);
        this.loading = false;
      }
    });
  }

  openNew() {
    this.roleForm = { id: null, code: '', name: '', description: '' };
    this.roleDialogMode = 'create';
    this.displayDialog = true;
  }

  editRole(role: Role) {
    this.roleForm = { ...role };
    this.roleDialogMode = 'edit';
    this.displayDialog = true;
  }

  saveRole() {
    if (this.roleDialogMode === 'create') {
      this.roleService.createRole(this.roleForm).subscribe(() => {
        this.displayDialog = false;
        this.loadRoles();
      });
    } else {
      this.roleService.updateRole(this.roleForm.id, this.roleForm).subscribe(() => {
        this.displayDialog = false;
        this.loadRoles();
      });
    }
  }

  deleteRole(role: Role) {
    if (confirm(`Bạn có chắc muốn xóa vai trò ${role.name}?`)) {
      this.roleService.deleteRole(role.id).subscribe(() => {
        this.loadRoles();
      });
    }
  }
}
