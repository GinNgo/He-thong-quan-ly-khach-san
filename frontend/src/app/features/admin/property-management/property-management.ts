import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PropertyService } from '../../../core/services/property.service';
import { Hotel } from '../../../core/services/client-api.service';
import { MessageService, ConfirmationService } from 'primeng/api';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { DialogModule } from 'primeng/dialog';
import { AuthService } from '../../../core/services/auth';

@Component({
  selector: 'app-property-management',
  standalone: true,
  imports: [CommonModule, TableModule, ButtonModule, ToastModule, ConfirmDialogModule, TagModule, TooltipModule, DialogModule],
  providers: [MessageService, ConfirmationService],
  templateUrl: './property-management.html'
})
export class PropertyManagementComponent implements OnInit {
  private propertyService = inject(PropertyService);
  private messageService = inject(MessageService);
  private confirmationService = inject(ConfirmationService);
  public authService = inject(AuthService);

  properties: Hotel[] = [];
  loading: boolean = false;
  isAdmin: boolean = false;

  ngOnInit() {
    this.isAdmin = this.authService.getRoles().includes('SUPER_ADMIN');
    this.loadProperties();
  }

  loadProperties() {
    this.loading = true;
    this.propertyService.getAllProperties().subscribe({
      next: (data) => {
        this.properties = data;
        this.loading = false;
      },
      error: (err) => {
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail: 'Không thể tải danh sách cơ sở' });
        this.loading = false;
      }
    });
  }

  submit(property: Hotel) {
    this.propertyService.submitProperty(property.id).subscribe({
      next: (data) => {
        this.messageService.add({ severity: 'success', summary: 'Thành công', detail: 'Đã gửi yêu cầu duyệt' });
        this.loadProperties();
      }
    });
  }

  approve(property: Hotel) {
    this.propertyService.approveProperty(property.id).subscribe({
      next: (data) => {
        this.messageService.add({ severity: 'success', summary: 'Thành công', detail: 'Đã duyệt cơ sở' });
        this.loadProperties();
      }
    });
  }

  reject(property: Hotel) {
    this.propertyService.rejectProperty(property.id).subscribe({
      next: (data) => {
        this.messageService.add({ severity: 'warn', summary: 'Thành công', detail: 'Đã từ chối cơ sở' });
        this.loadProperties();
      }
    });
  }
}
