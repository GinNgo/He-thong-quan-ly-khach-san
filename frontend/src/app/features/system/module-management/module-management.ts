import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';

interface AppFunction {
  id: number;
  code: string;
  name: string;
  url: string;
  icon: string;
  sortOrder: number;
}

interface AppModule {
  id: number;
  code: string;
  name: string;
  functions?: AppFunction[];
}

@Component({
  selector: 'app-module-management',
  standalone: true,
  imports: [CommonModule, TableModule, ButtonModule],
  template: `
    <div class="card shadow-sm border-0">
      <div class="card-header bg-white border-0 py-3 d-flex justify-content-between align-items-center">
        <h5 class="mb-0 fw-bold text-primary"><i class="pi pi-cog me-2"></i>Cấu hình Module & Chức năng</h5>
        <button pButton icon="pi pi-plus" label="Thêm Module" class="p-button-sm p-button-primary"></button>
      </div>
      <div class="card-body p-0">
        <p-table [value]="modules" dataKey="id" [expandedRowKeys]="expandedRows" styleClass="p-datatable-sm p-datatable-striped">
          <ng-template pTemplate="header">
            <tr>
              <th style="width: 3rem"></th>
              <th>Mã Module</th>
              <th>Tên Module</th>
              <th style="width: 10rem">Thao tác</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-mod let-expanded="expanded">
            <tr>
              <td>
                <button type="button" pButton pRipple [pRowToggler]="mod" class="p-button-text p-button-rounded p-button-plain" [icon]="expanded ? 'pi pi-chevron-down' : 'pi pi-chevron-right'"></button>
              </td>
              <td class="fw-bold">{{mod.code}}</td>
              <td>{{mod.name}}</td>
              <td>
                <button pButton icon="pi pi-pencil" class="p-button-text p-button-sm p-button-warning me-2" title="Sửa"></button>
                <button pButton icon="pi pi-trash" class="p-button-text p-button-sm p-button-danger" title="Xóa"></button>
              </td>
            </tr>
          </ng-template>
          <ng-template pTemplate="rowexpansion" let-mod>
            <tr>
              <td colspan="4" class="p-3 bg-light">
                <div class="d-flex justify-content-between align-items-center mb-2">
                  <h6 class="mb-0 text-muted"><i class="pi pi-list me-2"></i>Danh sách chức năng thuộc {{mod.name}}</h6>
                  <button pButton icon="pi pi-plus" label="Thêm Chức năng" class="p-button-sm p-button-success p-button-outlined"></button>
                </div>
                <p-table [value]="getFunctions(mod.id)" [tableStyle]="{'min-width': '50rem'}">
                  <ng-template pTemplate="header">
                    <tr>
                      <th>Mã</th>
                      <th>Tên chức năng</th>
                      <th>URL</th>
                      <th>Icon</th>
                      <th>Thứ tự</th>
                    </tr>
                  </ng-template>
                  <ng-template pTemplate="body" let-func>
                    <tr>
                      <td><code>{{func.code}}</code></td>
                      <td>{{func.name}}</td>
                      <td>{{func.url}}</td>
                      <td><i [class]="func.icon"></i> {{func.icon}}</td>
                      <td>{{func.sortOrder}}</td>
                    </tr>
                  </ng-template>
                  <ng-template pTemplate="emptymessage">
                    <tr>
                      <td colspan="5" class="text-center text-muted py-3">Chưa có chức năng nào</td>
                    </tr>
                  </ng-template>
                </p-table>
              </td>
            </tr>
          </ng-template>
        </p-table>
      </div>
    </div>
  `
})
export class ModuleManagementComponent implements OnInit {
  modules: AppModule[] = [];
  allFunctions: AppFunction[] = [];
  expandedRows: any = {};

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this.http.get<AppModule[]>('http://localhost:8080/api/modules').subscribe(res => {
      this.modules = res;
      // expand all rows by default
      res.forEach(m => this.expandedRows[m.id] = true);
    });
    this.http.get<AppFunction[]>('http://localhost:8080/api/functions').subscribe(res => {
      this.allFunctions = res;
    });
  }

  getFunctions(moduleId: number): AppFunction[] {
    // API returns all functions, we map it on client for simplicity since it's small data
    // In real app, we might get them nested.
    // Wait, the API I wrote returns flat array. Wait, my AppModuleDto doesn't load functions.
    // So we fetch all functions and filter here.
    return this.allFunctions.filter((f: any) => f.moduleId === moduleId);
  }
}
