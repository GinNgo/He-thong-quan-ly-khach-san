import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { RouterModule } from '@angular/router';

interface Role {
  id: number;
  code: string;
  name: string;
}

@Component({
  selector: 'app-role-management',
  standalone: true,
  imports: [CommonModule, TableModule, ButtonModule, TagModule, RouterModule],
  template: `
    <div class="card shadow-sm border-0">
      <div class="card-header bg-white border-0 py-3 d-flex justify-content-between align-items-center">
        <h5 class="mb-0 fw-bold text-primary"><i class="pi pi-id-card me-2"></i>Quản lý Vai trò (Roles)</h5>
        <button pButton icon="pi pi-plus" label="Thêm Vai trò" class="p-button-sm p-button-primary"></button>
      </div>
      <div class="card-body p-0">
        <p-table [value]="roles" styleClass="p-datatable-sm p-datatable-striped">
          <ng-template pTemplate="header">
            <tr>
              <th>ID</th>
              <th>Mã Vai trò (Code)</th>
              <th>Tên Vai trò</th>
              <th style="width: 15rem">Thao tác</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-role>
            <tr>
              <td>{{role.id}}</td>
              <td><p-tag [value]="role.code" [severity]="getSeverity(role.code)"></p-tag></td>
              <td class="fw-medium">{{role.name}}</td>
              <td>
                <a [routerLink]="['/admin/roles/permissions', role.id]" pButton icon="pi pi-shield" class="p-button-sm p-button-info me-2 p-button-outlined" title="Phân quyền"></a>
                <button pButton icon="pi pi-pencil" class="p-button-sm p-button-warning me-2 p-button-text" title="Sửa"></button>
                <button pButton icon="pi pi-trash" class="p-button-sm p-button-danger p-button-text" title="Xóa"></button>
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr>
              <td colspan="4" class="text-center py-4 text-muted">Chưa có vai trò nào trong hệ thống.</td>
            </tr>
          </ng-template>
        </p-table>
      </div>
    </div>
  `
})
export class RoleManagementComponent implements OnInit {
  roles: Role[] = [];

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.loadRoles();
  }

  loadRoles() {
    this.http.get<Role[]>('http://localhost:8080/api/roles').subscribe(res => {
      this.roles = res;
    });
  }

  getSeverity(code: string) {
    if (code === 'SUPER_ADMIN') return 'danger';
    if (code === 'ADMIN') return 'warn';
    if (code === 'RECEPTIONIST') return 'info';
    return 'success';
  }
}
