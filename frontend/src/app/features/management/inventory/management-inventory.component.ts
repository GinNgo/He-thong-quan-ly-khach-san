import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ManagementApiService, ManagedProperty } from '../../../core/services/management-api.service';
import { FeedbackStateComponent } from '../../../shared/components/feedback-state/feedback-state.component';
import { AmenityAssignmentComponent } from '../../../shared/components/amenity-assignment/amenity-assignment.component';
import { ActionCode, FunctionCode, PermissionService } from '../../../core/services/permission.service';
import { PropertyGalleryComponent } from '../../../shared/components/property-gallery/property-gallery.component';
import { MaintenanceWorkOrdersComponent } from '../../../shared/components/maintenance-work-orders/maintenance-work-orders.component';

@Component({ selector: 'app-management-inventory', standalone: true, imports: [CommonModule, FormsModule, FeedbackStateComponent, AmenityAssignmentComponent, PropertyGalleryComponent, MaintenanceWorkOrdersComponent], templateUrl: './management-inventory.component.html', styleUrl: './management-inventory.component.css' })
export class ManagementInventoryComponent implements OnInit {
  private api = inject(ManagementApiService); private route = inject(ActivatedRoute); private cdr = inject(ChangeDetectorRef); private permissions = inject(PermissionService);
  mode: 'room-types' | 'rooms' = 'room-types'; properties: ManagedProperty[] = []; propertyId?: number; rows: any[] = []; roomTypes: any[] = []; loading = true; saving = false; error = ''; showForm = false; maintenanceRoom?: any; selectedAmenityRoomTypeId?: number; selectedGalleryRoomTypeId?: number; editingRoomTypeId?: number; editingRoomId?: number;
  roomTypeForm: any = { code: '', nameVi: '', nameEn: '', bedType: 'DOUBLE', bedCount: 1, maxAdults: 2, maxChildren: 1, maxGuests: 3, basePrice: 0, status: 'ACTIVE' };
  bulkForm: any = { roomTypeId: undefined, fromNumber: 101, toNumber: 105, floor: 1, status: 'AVAILABLE' };
  roomForm: any = { roomTypeId: undefined, roomNumber: '', floor: 1, note: '' };
  canCreateRoomType = this.permissions.hasPermission(FunctionCode.ROOM_TYPE, ActionCode.CREATE);
  canUpdateRoomType = this.permissions.hasPermission(FunctionCode.ROOM_TYPE, ActionCode.UPDATE);
  canDeleteRoomType = this.permissions.hasPermission(FunctionCode.ROOM_TYPE, ActionCode.DELETE);
  canCreateRoom = this.permissions.hasPermission(FunctionCode.ROOM, ActionCode.CREATE);
  canUpdateRoom = this.permissions.hasPermission(FunctionCode.ROOM, ActionCode.UPDATE);
  canDeleteRoom = this.permissions.hasPermission(FunctionCode.ROOM, ActionCode.DELETE);

  ngOnInit(): void {
    this.mode = this.route.snapshot.data['mode'] || 'room-types';
    this.api.context().subscribe({
      next: context => { this.properties = context.properties; this.propertyId = context.activePropertyId; this.reload(); this.cdr.markForCheck(); },
      error: e => { this.error = e?.error?.message || 'Không thể tải context.'; this.loading = false; this.cdr.markForCheck(); }
    });
  }

  reload(): void {
    this.selectedAmenityRoomTypeId = undefined;
    if (!this.propertyId) { this.rows = []; this.loading = false; this.cdr.markForCheck(); return; }
    this.error = '';
    this.loading = true;
    const request = this.mode === 'room-types' ? this.api.roomTypes(this.propertyId) : this.api.rooms(this.propertyId);
    request.subscribe({
      next: rows => {
        this.rows = rows;
        this.loading = false;
        if (this.mode === 'rooms') {
          this.api.roomTypes(this.propertyId!).subscribe({ next: types => { this.roomTypes = types; this.cdr.markForCheck(); }, error: () => this.cdr.markForCheck() });
        }
        this.cdr.markForCheck();
      },
      error: e => { this.error = e?.error?.message || 'Không thể tải dữ liệu.'; this.loading = false; this.cdr.markForCheck(); }
    });
  }

  manageAmenities(roomTypeId: number): void {
    this.selectedAmenityRoomTypeId = this.selectedAmenityRoomTypeId === roomTypeId ? undefined : roomTypeId;
  }

  manageGallery(roomTypeId: number): void {
    this.selectedGalleryRoomTypeId = this.selectedGalleryRoomTypeId === roomTypeId ? undefined : roomTypeId;
  }

  openCreate(): void {
    this.editingRoomTypeId = undefined;
    this.roomTypeForm = { code: '', nameVi: '', nameEn: '', bedType: 'DOUBLE', bedCount: 1, maxAdults: 2, maxChildren: 1, maxGuests: 3, basePrice: 0, status: 'ACTIVE' };
    this.showForm = true;
  }

  editRoomType(row: any): void {
    if (!this.canUpdateRoomType) return;
    this.editingRoomTypeId = row.id;
    this.selectedGalleryRoomTypeId = row.id;
    this.roomTypeForm = { ...row };
    this.showForm = true;
  }

  editRoom(row: any): void {
    if (!this.canUpdateRoom) return;
    this.editingRoomId = row.id;
    this.roomForm = { ...row };
    this.showForm = true;
  }

  saveRoom(): void {
    if (!this.propertyId || !this.editingRoomId || this.saving || !this.roomForm.roomTypeId || !String(this.roomForm.roomNumber || '').trim()) return;
    this.saving = true; this.error = '';
    this.api.updateRoom(this.editingRoomId, { ...this.roomForm, hotelId: this.propertyId }).subscribe({
      next: () => { this.saving = false; this.showForm = false; this.editingRoomId = undefined; this.reload(); this.cdr.markForCheck(); },
      error: e => { this.error = e?.error?.message || 'Không thể cập nhật phòng.'; this.saving = false; this.cdr.markForCheck(); }
    });
  }

  deactivateRoom(row: any): void {
    if (!this.canDeleteRoom || this.saving || row.status === 'OUT_OF_SERVICE') return;
    this.saving = true; this.error = '';
    this.api.deleteRoom(row.id).subscribe({
      next: () => { this.saving = false; this.reload(); this.cdr.markForCheck(); },
      error: e => { this.error = e?.error?.message || 'Không thể ngừng sử dụng phòng.'; this.saving = false; this.cdr.markForCheck(); }
    });
  }

  save(): void {
    if (this.editingRoomId) { this.saveRoom(); return; }
    if (!this.propertyId || this.saving) return;
    this.error = '';
    this.saving = true;
    const request = this.mode === 'room-types'
      ? (this.editingRoomTypeId
        ? this.api.updateRoomType(this.editingRoomTypeId, { ...this.roomTypeForm, hotelId: this.propertyId })
        : this.api.createRoomType({ ...this.roomTypeForm, hotelId: this.propertyId }))
      : this.api.bulkRooms({ ...this.bulkForm, hotelId: this.propertyId });
    request.subscribe({
      next: () => { this.showForm = false; this.editingRoomTypeId = undefined; this.saving = false; this.reload(); this.cdr.markForCheck(); },
      error: e => { this.error = e?.error?.message || (this.mode === 'room-types' ? 'Không thể thêm loại phòng.' : 'Không thể tạo dải phòng.'); this.saving = false; this.cdr.markForCheck(); }
    });
  }

  deactivateRoomType(row: any): void {
    if (!this.canDeleteRoomType || this.saving || row.status !== 'ACTIVE') return;
    this.saving = true;
    this.error = '';
    this.api.deleteRoomType(row.id).subscribe({
      next: () => { this.saving = false; this.reload(); this.cdr.markForCheck(); },
      error: e => { this.error = e?.error?.message || 'Không thể ngừng loại phòng.'; this.saving = false; this.cdr.markForCheck(); }
    });
  }

  openMaintenance(row: any): void { this.maintenanceRoom = row; }
  closeMaintenance(): void { this.maintenanceRoom = undefined; }
}
