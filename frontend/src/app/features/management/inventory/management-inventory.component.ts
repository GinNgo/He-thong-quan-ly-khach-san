import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ManagementApiService, ManagedProperty, OperationalExportDataset } from '../../../core/services/management-api.service';
import { FeedbackStateComponent } from '../../../shared/components/feedback-state/feedback-state.component';
import { ActionCode, FunctionCode, PermissionService } from '../../../core/services/permission.service';

@Component({ selector: 'app-management-inventory', standalone: true, imports: [CommonModule, FormsModule, FeedbackStateComponent], templateUrl: './management-inventory.component.html', styleUrl: './management-inventory.component.css' })
export class ManagementInventoryComponent implements OnInit {
  private api = inject(ManagementApiService); private route = inject(ActivatedRoute); private cdr = inject(ChangeDetectorRef); private permissions = inject(PermissionService);
  mode: 'room-types' | 'rooms' = 'room-types'; properties: ManagedProperty[] = []; propertyId?: number; rows: any[] = []; roomTypes: any[] = []; loading = true; saving = false; error = ''; showForm = false; changingRoomId?: number;
  roomTypeForm: any = { code: '', nameVi: '', nameEn: '', bedType: 'DOUBLE', bedCount: 1, maxAdults: 2, maxChildren: 1, maxGuests: 3, basePrice: 0, status: 'ACTIVE' };
  bulkForm: any = { roomTypeId: undefined, fromNumber: 101, toNumber: 105, floor: 1, status: 'AVAILABLE' };
  readonly canExport = this.permissions.hasPermission(FunctionCode.REPORT, ActionCode.EXPORT);
  exportDataset: OperationalExportDataset = 'ROOMS'; exportStatus = ''; exportFrom = ''; exportTo = ''; exporting = false; exportMessage = ''; exportChecksum = '';

  ngOnInit(): void {
    this.mode = this.route.snapshot.data['mode'] || 'room-types';
    this.api.context().subscribe({
      next: context => { this.properties = context.properties; this.propertyId = context.activePropertyId; this.reload(); this.cdr.markForCheck(); },
      error: e => { this.error = e?.error?.message || 'Không thể tải context.'; this.loading = false; this.cdr.markForCheck(); }
    });
  }

  reload(): void {
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

  save(): void {
    if (!this.propertyId || this.saving) return;
    this.error = '';
    this.saving = true;
    const request = this.mode === 'room-types'
      ? this.api.createRoomType({ ...this.roomTypeForm, hotelId: this.propertyId })
      : this.api.bulkRooms({ ...this.bulkForm, hotelId: this.propertyId });
    request.subscribe({
      next: () => { this.showForm = false; this.saving = false; this.reload(); this.cdr.markForCheck(); },
      error: e => { this.error = e?.error?.message || (this.mode === 'room-types' ? 'Không thể thêm loại phòng.' : 'Không thể tạo dải phòng.'); this.saving = false; this.cdr.markForCheck(); }
    });
  }

  toggleMaintenance(row: any): void {
    if (this.changingRoomId || row.maintenanceStatus === 'OUT_OF_SERVICE') return;
    this.error = '';
    this.changingRoomId = row.id;
    const request = row.maintenanceStatus === 'MAINTENANCE'
      ? this.api.completeRoomMaintenance(row.id)
      : this.api.startRoomMaintenance(row.id);
    request.subscribe({
      next: () => { this.changingRoomId = undefined; this.reload(); this.cdr.markForCheck(); },
      error: e => { this.error = e?.error?.message || 'Không thể chuyển trạng thái bảo trì.'; this.changingRoomId = undefined; this.cdr.markForCheck(); }
    });
  }

  exportOperational(): void {
    if (!this.canExport || !this.propertyId || this.exporting) return;
    this.exporting = true; this.exportMessage = 'Dang tao tep export...'; this.exportChecksum = '';
    this.api.operationalExport(this.propertyId, this.exportDataset, { status: this.exportStatus, from: this.exportFrom, to: this.exportTo }).subscribe({
      next: download => {
        const url = URL.createObjectURL(download.blob); const anchor = document.createElement('a');
        anchor.href = url; anchor.download = download.filename; anchor.click(); URL.revokeObjectURL(url);
        this.exporting = false; this.exportChecksum = download.checksum;
        this.exportMessage = `Da tai ${download.filename} (${download.rowCount} dong, ${download.schema}).`; this.cdr.markForCheck();
      },
      error: e => { this.exporting = false; this.exportMessage = e?.error?.message || 'Khong the tao tep export.'; this.cdr.markForCheck(); }
    });
  }
}
