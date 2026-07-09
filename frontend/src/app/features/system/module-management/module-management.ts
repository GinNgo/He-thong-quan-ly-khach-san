import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ConfirmationService, MessageService, TreeNode } from 'primeng/api';
import { TreeTableModule } from 'primeng/treetable';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { environment } from '../../../../environments/environment';

interface AppFunction {
  id: number;
  moduleId: number;
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
}

type PageFormMode = 'add' | 'edit';
type PageEntityType = 'module' | 'function';

@Component({
  selector: 'app-module-management',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TreeTableModule,
    DialogModule,
    ButtonModule,
    InputTextModule,
    ToastModule,
    ConfirmDialogModule
  ],
  providers: [MessageService, ConfirmationService],
  templateUrl: './module-management.html',
  styleUrl: './module-management.css'
})
export class ModuleManagementComponent implements OnInit {
  nodes: TreeNode[] = [];
  selectedNode: TreeNode | null = null;
  loading = false;

  displayDialog = false;
  dialogMode: PageFormMode = 'add';
  entityType: PageEntityType = 'function';

  formData: {
    id: number | null;
    moduleId: number | null;
    code: string;
    name: string;
    url: string;
    icon: string;
    sortOrder: number;
  } = this.createEmptyForm();

  private http = inject(HttpClient);
  private messageService = inject(MessageService);
  private confirmationService = inject(ConfirmationService);
  private apiUrl = environment.apiUrl;

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this.loading = true;
    this.http.get<AppModule[]>(`${this.apiUrl}/modules`).subscribe({
      next: (modules) => {
        this.http.get<AppFunction[]>(`${this.apiUrl}/functions`).subscribe({
          next: (functions) => {
            this.nodes = modules.map((module) => ({
              data: { ...module, type: 'module' },
              key: `module-${module.id}`,
              expanded: true,
              children: functions
                .filter((func) => func.moduleId === module.id)
                .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
                .map((func) => ({
                  data: { ...func, type: 'function' },
                  key: `function-${func.id}`
                }))
            }));
            this.loading = false;
          },
          error: () => this.handleLoadError()
        });
      },
      error: () => this.handleLoadError()
    });
  }

  selectNode(rowNode: TreeNode) {
    this.selectedNode = rowNode;
  }

  openAddModule() {
    this.entityType = 'module';
    this.dialogMode = 'add';
    this.formData = this.createEmptyForm();
    this.displayDialog = true;
  }

  openAddFunction() {
    const moduleId = this.getSelectedModuleId();
    if (!moduleId) {
      this.messageService.add({ severity: 'warn', summary: 'Chua chon nhom', detail: 'Hay chon mot nhom menu truoc khi them trang.' });
      return;
    }

    this.entityType = 'function';
    this.dialogMode = 'add';
    this.formData = {
      ...this.createEmptyForm(),
      moduleId
    };
    this.displayDialog = true;
  }

  editNode(rowNode: TreeNode) {
    const data = rowNode.data;
    this.selectedNode = rowNode;
    this.dialogMode = 'edit';
    this.entityType = data.type;
    this.formData = {
      id: data.id,
      moduleId: data.moduleId || null,
      code: data.code || '',
      name: data.name || '',
      url: data.url || '',
      icon: data.icon || '',
      sortOrder: data.sortOrder || 0
    };
    this.displayDialog = true;
  }

  deleteNode(rowNode: TreeNode) {
    const data = rowNode.data;
    const endpoint = data.type === 'module' ? 'modules' : 'functions';
    const label = data.type === 'module' ? 'nhom menu' : 'trang';

    this.confirmationService.confirm({
      message: `Ban co chac muon xoa ${label} "${data.name}"?`,
      header: 'Xac nhan xoa',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.http.delete(`${this.apiUrl}/${endpoint}/${data.id}`).subscribe({
          next: () => {
            this.messageService.add({ severity: 'success', summary: 'Thanh cong', detail: 'Da xoa du lieu.' });
            this.loadData();
          },
          error: () => this.messageService.add({ severity: 'error', summary: 'Loi', detail: 'Khong the xoa du lieu nay.' })
        });
      }
    });
  }

  save() {
    this.formData.code = this.formData.code.trim().toUpperCase();
    this.formData.name = this.formData.name.trim();

    if (!this.formData.code || !this.formData.name) {
      this.messageService.add({ severity: 'error', summary: 'Thieu thong tin', detail: 'Vui long nhap ma va ten.' });
      return;
    }

    const endpoint = this.entityType === 'module' ? 'modules' : 'functions';
    const payload = this.entityType === 'module'
      ? { code: this.formData.code, name: this.formData.name }
      : {
          code: this.formData.code,
          name: this.formData.name,
          moduleId: this.formData.moduleId,
          url: this.formData.url,
          icon: this.formData.icon,
          sortOrder: Number(this.formData.sortOrder) || 0
        };

    const request = this.dialogMode === 'add'
      ? this.http.post(`${this.apiUrl}/${endpoint}`, payload)
      : this.http.put(`${this.apiUrl}/${endpoint}/${this.formData.id}`, payload);

    request.subscribe({
      next: () => {
        this.displayDialog = false;
        this.messageService.add({ severity: 'success', summary: 'Thanh cong', detail: 'Da luu cau hinh trang.' });
        this.loadData();
      },
      error: () => this.messageService.add({ severity: 'error', summary: 'Loi', detail: 'Khong the luu cau hinh trang.' })
    });
  }

  get dialogTitle(): string {
    const action = this.dialogMode === 'add' ? 'Them' : 'Cap nhat';
    const target = this.entityType === 'module' ? 'nhom menu' : 'trang';
    return `${action} ${target}`;
  }

  private getSelectedModuleId(): number | null {
    if (!this.selectedNode) return null;
    const data = this.selectedNode.data;
    return data.type === 'module' ? data.id : data.moduleId;
  }

  private createEmptyForm() {
    return {
      id: null,
      moduleId: null,
      code: '',
      name: '',
      url: '',
      icon: 'pi pi-circle',
      sortOrder: 0
    };
  }

  private handleLoadError() {
    this.loading = false;
    this.messageService.add({ severity: 'error', summary: 'Loi', detail: 'Khong the tai danh sach trang.' });
  }
}
