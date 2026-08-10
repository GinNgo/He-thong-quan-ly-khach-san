import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { forkJoin } from 'rxjs';
import { finalize, timeout } from 'rxjs/operators';
import { ConfirmationService, MessageService } from 'primeng/api';
import { SharedModule } from '../../../shared/shared.module';
import { AdminInventoryService, AdminPropertyOption, AdminRoomType } from '../../../core/services/admin-inventory.service';
import { ActionCode, FunctionCode, PermissionService } from '../../../core/services/permission.service';

@Component({
  selector: 'app-room-type-management',
  imports: [SharedModule],
  providers: [ConfirmationService, MessageService],
  templateUrl: './room-type-management.html',
  styleUrl: './room-type-management.css',
})
export class RoomTypeManagement implements OnInit {
  private api = inject(AdminInventoryService);
  private messages = inject(MessageService);
  private confirmations = inject(ConfirmationService);
  private permissions = inject(PermissionService);
  private cdr = inject(ChangeDetectorRef);

  roomTypes: AdminRoomType[] = [];
  properties: AdminPropertyOption[] = [];
  loading = false;
  saving = false;
  errorMessage = '';
  searchText = '';
  propertyFilter: number | null = null;
  statusFilter = '';
  dialogVisible = false;
  editingId: number | null = null;
  imageText = '';
  form: Partial<AdminRoomType> = this.emptyForm();

  canCreate = this.permissions.hasPermission(FunctionCode.ROOM_TYPE, ActionCode.CREATE);
  canUpdate = this.permissions.hasPermission(FunctionCode.ROOM_TYPE, ActionCode.UPDATE);
  canDelete = this.permissions.hasPermission(FunctionCode.ROOM_TYPE, ActionCode.DELETE);

  bedTypes = ['SINGLE', 'DOUBLE', 'TWIN', 'MULTIPLE', 'KING', 'QUEEN'];
  statuses = [{ label: 'Đang hoạt động', value: 'ACTIVE' }, { label: 'Ngừng hoạt động', value: 'INACTIVE' }];

  ngOnInit(): void { this.loadData(); }

  get filteredRoomTypes(): AdminRoomType[] {
    const keyword = this.searchText.trim().toLocaleLowerCase('vi');
    return this.roomTypes.filter(item =>
      (!keyword || `${item.code} ${item.nameVi} ${item.nameEn || ''}`.toLocaleLowerCase('vi').includes(keyword)) &&
      (!this.propertyFilter || item.hotelId === this.propertyFilter) &&
      (!this.statusFilter || item.status === this.statusFilter));
  }

  loadData(): void {
    this.loading = true; this.errorMessage = '';
    forkJoin({ roomTypes: this.api.getRoomTypes(), properties: this.api.getProperties() }).pipe(
      timeout(15000), finalize(() => { this.loading = false; this.cdr.detectChanges(); })
    ).subscribe({
      next: data => { this.roomTypes = data.roomTypes; this.properties = data.properties; },
      error: err => { this.errorMessage = err?.error?.message || 'Không thể tải danh sách loại phòng.'; }
    });
  }

  propertyName(id: number): string { const p = this.properties.find(item => item.id === id); return p?.nameVi || p?.name || `Cơ sở #${id}`; }
  formatVnd(value?: number): string { return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(value || 0); }
  resetFilters(): void { this.searchText = ''; this.propertyFilter = null; this.statusFilter = ''; }

  openCreate(): void { this.editingId = null; this.form = this.emptyForm(); this.imageText = ''; this.dialogVisible = true; }
  openEdit(item: AdminRoomType): void { this.editingId = item.id; this.form = { ...item }; this.imageText = (item.imageUrls || []).join('\n'); this.dialogVisible = true; }

  save(): void {
    if (this.saving || !this.form.hotelId || !this.form.code?.trim() || !this.form.nameVi?.trim()) {
      this.messages.add({ severity: 'warn', summary: 'Thiếu thông tin', detail: 'Vui lòng chọn cơ sở, nhập mã và tên loại phòng.' }); return;
    }
    this.form.imageUrls = this.imageText.split(/\r?\n/).map(v => v.trim()).filter(Boolean);
    this.saving = true;
    const request = this.editingId ? this.api.updateRoomType(this.editingId, this.form) : this.api.createRoomType(this.form);
    request.pipe(finalize(() => { this.saving = false; this.cdr.detectChanges(); })).subscribe({
      next: () => { this.dialogVisible = false; this.messages.add({ severity: 'success', summary: 'Thành công', detail: 'Đã lưu loại phòng.' }); this.loadData(); },
      error: err => this.messages.add({ severity: 'error', summary: 'Lỗi', detail: err?.error?.message || 'Không thể lưu loại phòng.' })
    });
  }

  deactivate(item: AdminRoomType): void {
    this.confirmations.confirm({ header: 'Xác nhận ngừng sử dụng', message: `Ngừng sử dụng loại phòng "${item.nameVi}"?`, icon: 'pi pi-exclamation-triangle', acceptLabel: 'Ngừng sử dụng', rejectLabel: 'Hủy', accept: () =>
      this.api.deleteRoomType(item.id).subscribe({ next: () => { this.messages.add({ severity: 'success', summary: 'Thành công', detail: 'Đã ngừng sử dụng loại phòng.' }); this.loadData(); }, error: err => this.messages.add({ severity: 'error', summary: 'Lỗi', detail: err?.error?.message || 'Không thể cập nhật loại phòng.' }) }) });
  }

  private emptyForm(): Partial<AdminRoomType> { return { hotelId: undefined, code: '', nameVi: '', nameEn: '', descriptionVi: '', descriptionEn: '', bedType: 'DOUBLE', bedCount: 1, area: 20, maxAdults: 2, maxChildren: 1, maxGuests: 3, basePrice: 0, status: 'ACTIVE', imageUrls: [] }; }
}
