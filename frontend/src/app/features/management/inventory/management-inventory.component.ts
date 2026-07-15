import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ManagementApiService, ManagedProperty } from '../../../core/services/management-api.service';

@Component({ selector: 'app-management-inventory', standalone: true, imports: [CommonModule, FormsModule], templateUrl: './management-inventory.component.html', styleUrl: './management-inventory.component.css' })
export class ManagementInventoryComponent implements OnInit {
  private api = inject(ManagementApiService); private route = inject(ActivatedRoute);
  mode: 'room-types' | 'rooms' = 'room-types'; properties: ManagedProperty[] = []; propertyId?: number; rows: any[] = []; roomTypes: any[] = []; loading = true; error = ''; showForm = false;
  roomTypeForm: any = { code: '', nameVi: '', nameEn: '', bedType: 'DOUBLE', bedCount: 1, maxAdults: 2, maxChildren: 1, maxGuests: 3, basePrice: 0, status: 'ACTIVE' };
  bulkForm: any = { roomTypeId: undefined, fromNumber: 101, toNumber: 105, floor: 1, status: 'AVAILABLE' };

  ngOnInit(): void { this.mode = this.route.snapshot.data['mode'] || 'room-types'; this.api.context().subscribe({ next: context => { this.properties = context.properties; this.propertyId = context.activePropertyId; this.reload(); }, error: e => { this.error = e?.error?.message || 'Không thể tải context.'; this.loading = false; } }); }
  reload(): void { if (!this.propertyId) { this.rows = []; this.loading = false; return; } this.loading = true; const request = this.mode === 'room-types' ? this.api.roomTypes(this.propertyId) : this.api.rooms(this.propertyId); request.subscribe({ next: rows => { this.rows = rows; this.loading = false; if (this.mode === 'rooms') this.api.roomTypes(this.propertyId!).subscribe(types => this.roomTypes = types); }, error: e => { this.error = e?.error?.message || 'Không thể tải dữ liệu.'; this.loading = false; } }); }
  save(): void { if (!this.propertyId) return; this.error = ''; if (this.mode === 'room-types') { this.api.createRoomType({ ...this.roomTypeForm, hotelId: this.propertyId }).subscribe({ next: () => { this.showForm = false; this.reload(); }, error: e => this.error = e?.error?.message || 'Không thể thêm loại phòng.' }); } else { this.api.bulkRooms({ ...this.bulkForm, hotelId: this.propertyId }).subscribe({ next: () => { this.showForm = false; this.reload(); }, error: e => this.error = e?.error?.message || 'Không thể tạo dải phòng.' }); } }
}
