import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { TreeTableModule } from 'primeng/treetable';
import { TreeNode } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-role-permission',
  standalone: true,
  imports: [CommonModule, TreeTableModule, ButtonModule, CheckboxModule, FormsModule, RouterModule],
  template: `
    <div class="card shadow-sm border-0">
      <div class="card-header bg-white border-0 py-3 d-flex justify-content-between align-items-center">
        <h5 class="mb-0 fw-bold text-primary"><i class="pi pi-shield me-2"></i>Phân quyền cho vai trò</h5>
        <div>
          <a routerLink="/admin/roles" pButton icon="pi pi-arrow-left" label="Quay lại" class="p-button-sm p-button-secondary p-button-outlined me-2"></a>
          <button pButton icon="pi pi-save" label="Lưu thay đổi" class="p-button-sm p-button-success" (click)="savePermissions()"></button>
        </div>
      </div>
      <div class="card-body p-0">
        <p-treeTable [value]="treeData" [columns]="cols" styleClass="p-treetable-sm p-treetable-gridlines">
          <ng-template pTemplate="header" let-columns>
            <tr>
              <th *ngFor="let col of columns" [style.width]="col.width || 'auto'">
                {{col.header}}
              </th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-rowNode let-rowData="rowData" let-columns="columns">
            <tr>
              <td *ngFor="let col of columns; let i = index" [ngClass]="{'bg-light fw-bold': !rowData.isFunction}">
                <p-treeTableToggler [rowNode]="rowNode" *ngIf="i === 0"></p-treeTableToggler>
                
                <ng-container *ngIf="i === 0">
                  <i [class]="rowData.icon" class="me-2" *ngIf="rowData.icon"></i>
                  {{rowData[col.field]}}
                </ng-container>

                <ng-container *ngIf="i > 0 && rowData.isFunction">
                  <!-- Action Checkboxes -->
                  <div class="d-flex justify-content-center">
                    <p-checkbox [binary]="true" [(ngModel)]="rowData.actions[col.field]" (onChange)="updateMask(rowData)"></p-checkbox>
                  </div>
                </ng-container>
              </td>
            </tr>
          </ng-template>
        </p-treeTable>
      </div>
    </div>
  `
})
export class RolePermissionComponent implements OnInit {
  roleId!: number;
  treeData: TreeNode[] = [];
  cols: any[] = [];

  // Action Codes (must match backend)
  readonly ACTION_VIEW = 1;
  readonly ACTION_CREATE = 2;
  readonly ACTION_UPDATE = 4;
  readonly ACTION_DELETE = 8;
  readonly ACTION_EXPORT = 16;
  readonly ACTION_APPROVE = 32;

  constructor(private route: ActivatedRoute, private http: HttpClient, private router: Router) {
    this.cols = [
      { field: 'name', header: 'Tên Module / Chức năng', width: '30%' },
      { field: 'view', header: 'Xem', width: '10%' },
      { field: 'create', header: 'Thêm', width: '10%' },
      { field: 'update', header: 'Sửa', width: '10%' },
      { field: 'delete', header: 'Xóa', width: '10%' },
      { field: 'export', header: 'Xuất', width: '10%' },
      { field: 'approve', header: 'Duyệt', width: '10%' },
    ];
  }

  ngOnInit() {
    this.roleId = Number(this.route.snapshot.paramMap.get('id'));
    if (this.roleId) {
      this.loadPermissions();
    }
  }

  loadPermissions() {
    this.http.get<any[]>(`http://localhost:8080/api/role-permissions/tree/${this.roleId}`).subscribe(modules => {
      this.treeData = modules.map(m => {
        return {
          data: { name: m.name, isFunction: false, icon: 'pi pi-folder' },
          expanded: true,
          children: m.functions.map((f: any) => {
            const mask = f.actionMask || 0;
            return {
              data: {
                id: f.id,
                name: f.name,
                isFunction: true,
                icon: f.icon || 'pi pi-file',
                mask: mask,
                actions: {
                  view: (mask & this.ACTION_VIEW) !== 0,
                  create: (mask & this.ACTION_CREATE) !== 0,
                  update: (mask & this.ACTION_UPDATE) !== 0,
                  delete: (mask & this.ACTION_DELETE) !== 0,
                  export: (mask & this.ACTION_EXPORT) !== 0,
                  approve: (mask & this.ACTION_APPROVE) !== 0,
                }
              }
            };
          })
        };
      });
    });
  }

  updateMask(rowData: any) {
    let mask = 0;
    if (rowData.actions.view) mask |= this.ACTION_VIEW;
    if (rowData.actions.create) mask |= this.ACTION_CREATE;
    if (rowData.actions.update) mask |= this.ACTION_UPDATE;
    if (rowData.actions.delete) mask |= this.ACTION_DELETE;
    if (rowData.actions.export) mask |= this.ACTION_EXPORT;
    if (rowData.actions.approve) mask |= this.ACTION_APPROVE;
    rowData.mask = mask;
  }

  savePermissions() {
    const payload: any[] = [];
    this.treeData.forEach(moduleNode => {
      moduleNode.children?.forEach(funcNode => {
        if (funcNode.data.isFunction && funcNode.data.mask !== undefined) {
          payload.push({
            functionId: funcNode.data.id,
            actionMask: funcNode.data.mask
          });
        }
      });
    });

    this.http.post(`http://localhost:8080/api/role-permissions/${this.roleId}`, { permissions: payload }).subscribe(() => {
      alert('Đã lưu phân quyền thành công!');
    });
  }
}
