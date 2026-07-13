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
import { finalize, timeout } from 'rxjs/operators';

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
  public authService = inject(AuthService);

  properties: Hotel[] = [];
  loading = false;
  isAdmin = false;

  ngOnInit(): void {
    this.isAdmin = this.authService.getRoles().includes('SUPER_ADMIN');
    this.loadProperties();
  }

  loadProperties(): void {
    this.loading = true;
    this.propertyService.getAllProperties().pipe(
      timeout(10000),
      finalize(() => {
        this.loading = false;
      })
    ).subscribe({
      next: (data) => {
        this.properties = data;
      },
      error: (error) => {
        const detail = error?.error?.message || 'Không thể tải danh sách cơ sở.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail });
      }
    });
  }

  submit(property: Hotel): void {
    this.propertyService.submitProperty(property.id).pipe(timeout(10000)).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Thành công', detail: 'Đã gửi yêu cầu duyệt.' });
        this.loadProperties();
      },
      error: (error) => {
        const detail = error?.error?.message || 'Không thể gửi yêu cầu duyệt.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail });
      }
    });
  }

  approve(property: Hotel): void {
    this.propertyService.approveProperty(property.id).pipe(timeout(10000)).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Thành công', detail: 'Đã duyệt cơ sở.' });
        this.loadProperties();
      },
      error: (error) => {
        const detail = error?.error?.message || 'Không thể duyệt cơ sở.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail });
      }
    });
  }

  reject(property: Hotel): void {
    this.propertyService.rejectProperty(property.id).pipe(timeout(10000)).subscribe({
      next: () => {
        this.messageService.add({ severity: 'warn', summary: 'Thành công', detail: 'Đã từ chối cơ sở.' });
        this.loadProperties();
      },
      error: (error) => {
        const detail = error?.error?.message || 'Không thể từ chối cơ sở.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail });
      }
    });
  }
}
