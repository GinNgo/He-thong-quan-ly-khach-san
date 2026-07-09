import { Component, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { SharedModule } from '@app/shared/shared.module';
import { Role, RoleService } from '@app/core/services/role.service';
import { ConfirmationService, MessageService } from 'primeng/api';

@Component({
  selector: 'app-role-management',
  standalone: true,
  imports: [SharedModule],
  providers: [ConfirmationService, MessageService],
  templateUrl: './role-management.component.html'
})
export class RoleManagementComponent implements OnInit {
  roles: Role[] = [];
  loading = true;

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
      error: () => {
        this.loading = false;
        this.messageService.add({ severity: 'error', summary: 'Loi', detail: 'Khong the tai danh sach vai tro.' });
      }
    });
  }

  openNew() {
    this.roleForm = { id: 0, code: '', name: '', description: '' };
    this.roleDialogMode = 'create';
    this.displayDialog = true;
  }

  editRole(role: Role) {
    this.roleForm = { ...role };
    this.roleDialogMode = 'edit';
    this.displayDialog = true;
  }

  saveRole() {
    this.roleForm.code = this.roleForm.code.trim().toUpperCase();
    this.roleForm.name = this.roleForm.name.trim();

    if (!this.roleForm.code || !this.roleForm.name) {
      this.messageService.add({ severity: 'warn', summary: 'Thieu thong tin', detail: 'Vui long nhap ma va ten vai tro.' });
      return;
    }

    const request = this.roleDialogMode === 'create'
      ? this.roleService.createRole(this.roleForm)
      : this.roleService.updateRole(this.roleForm.id, this.roleForm);

    request.subscribe({
      next: () => {
        this.displayDialog = false;
        this.messageService.add({ severity: 'success', summary: 'Thanh cong', detail: 'Da luu vai tro.' });
        this.loadRoles();
      },
      error: () => this.messageService.add({ severity: 'error', summary: 'Loi', detail: 'Khong the luu vai tro.' })
    });
  }

  deleteRole(role: Role) {
    this.confirmationService.confirm({
      message: `Ban co chac muon xoa vai tro "${role.name}"?`,
      header: 'Xac nhan xoa',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.roleService.deleteRole(role.id).subscribe({
          next: () => {
            this.messageService.add({ severity: 'success', summary: 'Thanh cong', detail: 'Da xoa vai tro.' });
            this.loadRoles();
          },
          error: () => this.messageService.add({ severity: 'error', summary: 'Loi', detail: 'Khong the xoa vai tro nay.' })
        });
      }
    });
  }

  openPermissions(role: Role) {
    this.router.navigate(['/admin/role-permissions'], { queryParams: { roleId: role.id } });
  }
}
