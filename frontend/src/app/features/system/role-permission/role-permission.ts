import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { TreeTableModule } from 'primeng/treetable';
import { TreeNode, MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { SelectModule } from 'primeng/select';
import { ToastModule } from 'primeng/toast';

@Component({
  selector: 'app-role-permission',
  standalone: true,
  imports: [CommonModule, TreeTableModule, ButtonModule, CheckboxModule, FormsModule, SelectModule, ToastModule],
  providers: [MessageService],
  template: `
    <p-toast></p-toast>
    <div class="card shadow-sm border-0">
      <div class="card-header bg-white border-0 py-3 d-flex justify-content-between align-items-center border-bottom">
        <h5 class="mb-0 fw-bold text-primary">Phân quyền</h5>
      </div>
      <div class="card-body">
        <div class="d-flex mb-3 gap-2 align-items-center">
          <p-select [options]="roles" [(ngModel)]="selectedRoleId" optionLabel="name" optionValue="id" placeholder="Chọn chức danh" (onChange)="loadPermissions()"></p-select>
          <button pButton label="Cập nhật" class="p-button-primary p-button-sm" (click)="savePermissions()" [disabled]="!selectedRoleId"></button>
        </div>
        
        <p-treeTable [value]="treeData" [columns]="cols" styleClass="p-treetable-sm p-treetable-gridlines">
          <ng-template pTemplate="header" let-columns>
            <tr>
              <th *ngFor="let col of columns; let i = index" [ngClass]="{'text-center': i > 0}">
                {{col.header}}
              </th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-rowNode let-rowData="rowData" let-columns="columns">
            <tr [ngClass]="{'bg-light fw-bold': rowData.type === 'module'}">
              <td *ngFor="let col of columns; let i = index" [ngClass]="{'text-center': i > 0}">
                <p-treeTableToggler [rowNode]="rowNode" *ngIf="i === 0"></p-treeTableToggler>
                
                <ng-container *ngIf="i === 0">
                  <i [class]="rowData.icon + ' me-2 text-muted'" *ngIf="rowData.icon"></i>
                  {{rowData[col.field]}}
                </ng-container>

                <ng-container *ngIf="i > 0">
                  <!-- Action Checkboxes -->
                  <p-checkbox [binary]="true" [(ngModel)]="rowData.actions[col.field]" (onChange)="onCheckboxChange(rowNode, col.field)"></p-checkbox>
                </ng-container>
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="emptymessage">
            <tr>
                <td [attr.colspan]="cols.length" class="text-center p-4">Không có dữ liệu</td>
            </tr>
          </ng-template>
        </p-treeTable>
      </div>
    </div>
  `,
  styles: [`
    :host ::ng-deep .p-treetable .p-treetable-tbody > tr > td {
        padding: 0.5rem 1rem;
    }
  `]
})
export class RolePermissionComponent implements OnInit {
  roles: any[] = [];
  selectedRoleId: number | null = null;
  treeData: TreeNode[] = [];
  cols: any[] = [];

  // Action Codes (must match backend)
  readonly ACTION_VIEW = 1;
  readonly ACTION_CREATE = 2;
  readonly ACTION_UPDATE = 4;
  readonly ACTION_DELETE = 8;

  private http = inject(HttpClient);
  private messageService = inject(MessageService);

  ngOnInit() {
    // Only View, Create(Add), Update(Edit), Delete based on user's screenshot
    this.cols = [
      { field: 'name', header: '' },
      { field: 'view', header: 'Xem' },
      { field: 'update', header: 'Sửa' },
      { field: 'delete', header: 'Xóa' }
    ];
    
    this.loadRoles();
  }
  
  loadRoles() {
    this.http.get<any[]>('http://localhost:8080/api/roles').subscribe(res => {
      this.roles = res;
      if (this.roles.length > 0) {
        this.selectedRoleId = this.roles[0].id;
        this.loadPermissions();
      }
    });
  }

  loadPermissions() {
    if (!this.selectedRoleId) return;
    
    this.http.get<any[]>(`http://localhost:8080/api/role-permissions/tree/${this.selectedRoleId}`).subscribe(modules => {
      this.treeData = modules.map(m => {
        
        let allView = true, allUpdate = true, allDelete = true;
        
        const children = m.functions.map((f: any) => {
          const mask = f.actionMask || 0;
          const view = (mask & this.ACTION_VIEW) !== 0;
          const update = ((mask & this.ACTION_CREATE) !== 0) || ((mask & this.ACTION_UPDATE) !== 0);
          const del = (mask & this.ACTION_DELETE) !== 0;
          
          if (!view) allView = false;
          if (!update) allUpdate = false;
          if (!del) allDelete = false;
          
          return {
            data: {
              id: f.id,
              name: f.name,
              type: 'function',
              icon: f.icon || 'pi pi-file',
              mask: mask,
              actions: { view, update, delete: del }
            }
          };
        });
        
        // If there are no children, don't check the parent boxes
        if (children.length === 0) {
            allView = false; allUpdate = false; allDelete = false;
        }

        return {
          data: { 
            id: m.id,
            name: m.name, 
            type: 'module', 
            icon: 'pi pi-folder',
            actions: { view: allView, update: allUpdate, delete: allDelete }
          },
          expanded: true,
          children: children
        };
      });
    });
  }
  
  onCheckboxChange(rowNode: any, action: string) {
    const isModule = rowNode.node.data.type === 'module';
    const checked = rowNode.node.data.actions[action];
    
    if (isModule) {
      // Cascade down to all children
      if (rowNode.node.children) {
        rowNode.node.children.forEach((child: any) => {
          child.data.actions[action] = checked;
          this.updateChildMask(child.data);
        });
      }
    } else {
      // Update this child's mask
      this.updateChildMask(rowNode.node.data);
      // Check if we need to update parent (this requires finding the parent)
      this.updateParentCheck(rowNode.parent);
    }
  }
  
  updateParentCheck(parentNode: any) {
    if (!parentNode || !parentNode.children) return;
    
    let allView = true, allUpdate = true, allDelete = true;
    parentNode.children.forEach((child: any) => {
        if (!child.data.actions.view) allView = false;
        if (!child.data.actions.update) allUpdate = false;
        if (!child.data.actions.delete) allDelete = false;
    });
    
    parentNode.data.actions.view = allView;
    parentNode.data.actions.update = allUpdate;
    parentNode.data.actions.delete = allDelete;
  }

  updateChildMask(data: any) {
    let mask = 0;
    if (data.actions.view) mask |= this.ACTION_VIEW;
    if (data.actions.update) {
        mask |= this.ACTION_CREATE;
        mask |= this.ACTION_UPDATE;
    }
    if (data.actions.delete) mask |= this.ACTION_DELETE;
    data.mask = mask;
  }

  savePermissions() {
    if (!this.selectedRoleId) return;
    
    const payload: any[] = [];
    this.treeData.forEach(moduleNode => {
      moduleNode.children?.forEach(funcNode => {
        if (funcNode.data.type === 'function' && funcNode.data.mask !== undefined) {
          payload.push({
            functionId: funcNode.data.id,
            actionMask: funcNode.data.mask
          });
        }
      });
    });

    this.http.post(`http://localhost:8080/api/role-permissions/${this.selectedRoleId}`, { permissions: payload }).subscribe(() => {
      this.messageService.add({severity:'success', summary: 'Thành công', detail: 'Đã lưu phân quyền'});
    });
  }
}
