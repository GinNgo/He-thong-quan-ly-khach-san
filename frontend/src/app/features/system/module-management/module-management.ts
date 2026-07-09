import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { TreeNode, MenuItem, MessageService, ConfirmationService } from 'primeng/api';
import { TreeTableModule } from 'primeng/treetable';
import { ContextMenuModule } from 'primeng/contextmenu';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { CheckboxModule } from 'primeng/checkbox';
import { ToastModule } from 'primeng/toast';
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
  functions?: AppFunction[];
}

@Component({
  selector: 'app-module-management',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TreeTableModule, ContextMenuModule,
    DialogModule, ButtonModule, InputTextModule, CheckboxModule, ToastModule
  ],
  providers: [MessageService],
  templateUrl: './module-management.html',
  styleUrl: './module-management.css'
})
export class ModuleManagementComponent implements OnInit {
  nodes: TreeNode[] = [];
  selectedNode: TreeNode | null = null;
  items: MenuItem[] = [];
  
  displayDialog: boolean = false;
  dialogMode: 'add' | 'edit' = 'add';
  isModule: boolean = false; // true if editing/adding a module, false if function
  
  formData: any = {
    id: null,
    name: '',
    code: '',
    url: '',
    icon: '',
    svg: '',
    bullet: '',
    sortOrder: 0,
    isRoot: false,
    isSection: false,
    lockClient: false,
    isLocked: false,
    moduleId: null // only for function
  };

  private http = inject(HttpClient);
  private messageService = inject(MessageService);
  private confirmationService = inject(ConfirmationService);
  private apiUrl = environment.apiUrl;

  ngOnInit() {
    this.loadData();
    this.items = [
      { label: 'Thêm trang', icon: 'pi pi-plus', command: () => this.showDialogToAdd() },
      { label: 'Chỉnh sửa trang', icon: 'pi pi-pencil', command: () => this.showDialogToEdit() },
      { label: 'Xóa trang', icon: 'pi pi-times', command: () => this.deleteNode() },
      { separator: true },
      { label: 'Di chuyển xuống', icon: 'pi pi-arrow-down', command: () => this.moveNode(1) },
      { label: 'Di chuyển lên', icon: 'pi pi-arrow-up', command: () => this.moveNode(-1) }
    ];
  }

  loadData() {
    // API returns all functions, we map it on client for simplicity
    this.http.get<AppModule[]>(`${this.apiUrl}/modules`).subscribe(modules => {
      this.http.get<AppFunction[]>(`${this.apiUrl}/functions`).subscribe(functions => {
        this.nodes = modules.map(m => {
          const children = functions
            .filter(f => f.moduleId === m.id)
            .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
            .map(f => ({
              data: { ...f, type: 'function' },
              key: `func-${f.id}`
            }));
            
          return {
            data: { ...m, type: 'module' },
            key: `mod-${m.id}`,
            expanded: true,
            children: children
          };
        });
      });
    });
  }

  showDialogToAdd() {
    if (!this.selectedNode) return;
    this.dialogMode = 'add';
    const type = this.selectedNode.data.type;
    this.isModule = type === 'module'; // Wait, if I add page to a module, I'm adding a function
    
    this.formData = {
      name: '', code: '', url: '', icon: '', sortOrder: 0,
      svg: 'Settings-2.svg', bullet: 'Dot', isRoot: false, isSection: false,
      lockClient: false, isLocked: false,
      moduleId: type === 'module' ? this.selectedNode.data.id : this.selectedNode.data.moduleId
    };
    
    this.displayDialog = true;
  }

  showDialogToEdit() {
    if (!this.selectedNode) return;
    this.dialogMode = 'edit';
    const data = this.selectedNode.data;
    this.isModule = data.type === 'module';
    
    this.formData = {
      id: data.id,
      name: data.name,
      code: data.code,
      url: data.url || '',
      icon: data.icon || '',
      sortOrder: data.sortOrder || 0,
      svg: 'Settings-2.svg', bullet: 'Dot', isRoot: false, isSection: false,
      lockClient: false, isLocked: false,
      moduleId: data.moduleId || null
    };
    
    this.displayDialog = true;
  }

  save() {
    if (!this.formData.name || !this.formData.code) {
        this.messageService.add({severity:'error', summary: 'Lỗi', detail: 'Vui lòng nhập tên và mã trang'});
        return;
    }

    if (this.isModule) {
      const apiCall = this.dialogMode === 'add' 
        ? this.http.post(`${this.apiUrl}/modules`, this.formData)
        : this.http.put(`${this.apiUrl}/modules/${this.formData.id}`, this.formData);
        
      apiCall.subscribe(() => {
        this.displayDialog = false;
        this.loadData();
        this.messageService.add({severity:'success', summary: 'Thành công', detail: 'Đã lưu cấu hình'});
      });
    } else {
      const apiCall = this.dialogMode === 'add' 
        ? this.http.post(`${this.apiUrl}/functions`, this.formData)
        : this.http.put(`${this.apiUrl}/functions/${this.formData.id}`, this.formData);
        
      apiCall.subscribe(() => {
        this.displayDialog = false;
        this.loadData();
        this.messageService.add({severity:'success', summary: 'Thành công', detail: 'Đã lưu cấu hình'});
      });
    }
  }
        
      apiCall.subscribe(() => {
        this.displayDialog = false;
        this.loadData();
        this.messageService.add({severity:'success', summary: 'Thành công', detail: 'Đã lưu cấu hình'});
      });
    }
  }

  deleteNode() {
    if (!this.selectedNode) return;
    const data = this.selectedNode.data;
    const endpoint = data.type === 'module' ? 'modules' : 'functions';
    
    this.confirmationService.confirm({
      message: `Bạn có chắc chắn muốn xóa ${data.name}?`,
      header: 'Xác nhận xóa',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.http.delete(`${this.apiUrl}/${endpoint}/${data.id}`).subscribe(() => {
          this.loadData();
          this.messageService.add({severity:'success', summary: 'Thành công', detail: 'Đã xóa thành công'});
        });
      }
    });
  }

  moveNode(direction: number) {
    this.messageService.add({severity:'info', summary: 'Thông báo', detail: 'Chức năng di chuyển đang được cập nhật'});
  }
}
