import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { forkJoin } from 'rxjs';
import { finalize, timeout } from 'rxjs/operators';
import { ConfirmationService, MessageService } from 'primeng/api';
import { SharedModule } from '../../../shared/shared.module';
import { AdminInventoryService, AdminPropertyOption, AdminRoom, AdminRoomType, BulkRoomRequest } from '../../../core/services/admin-inventory.service';
import { ActionCode, FunctionCode, PermissionService } from '../../../core/services/permission.service';

@Component({
  selector: 'app-room-management',
  imports: [SharedModule],
  providers: [ConfirmationService, MessageService],
  templateUrl: './room-management.html',
  styleUrl: './room-management.css',
})
export class RoomManagement implements OnInit {
  private api = inject(AdminInventoryService);
  private messages = inject(MessageService);
  private confirmations = inject(ConfirmationService);
  private permissions = inject(PermissionService);
  private cdr = inject(ChangeDetectorRef);

  rooms: AdminRoom[] = []; roomTypes: AdminRoomType[] = []; properties: AdminPropertyOption[] = [];
  loading = false; saving = false; errorMessage = '';
  searchText = ''; propertyFilter: number | null = null; roomTypeFilter: number | null = null;
  floorFilter: number | null = null; statusFilter = ''; housekeepingFilter = ''; maintenanceFilter = '';
  dialogVisible = false; bulkVisible = false; editingId: number | null = null;
  form: Partial<AdminRoom> = this.emptyForm();
  bulk: BulkRoomRequest = this.emptyBulk();

  canCreate = this.permissions.hasPermission(FunctionCode.ROOM, ActionCode.CREATE);
  canUpdate = this.permissions.hasPermission(FunctionCode.ROOM, ActionCode.UPDATE);
  canDelete = this.permissions.hasPermission(FunctionCode.ROOM, ActionCode.DELETE);
  roomStatuses = ['AVAILABLE','RESERVED','OCCUPIED','DIRTY','CLEANING','MAINTENANCE','OUT_OF_SERVICE'];
  housekeepingStatuses = ['CLEAN','DIRTY','CLEANING','INSPECTED'];
  maintenanceStatuses = ['NONE','MAINTENANCE','OUT_OF_SERVICE'];

  ngOnInit(): void { this.loadData(); }
  get availableTypeOptions(): AdminRoomType[] { const hotelId = this.editingId ? this.form.hotelId : (this.form.hotelId || this.bulk.hotelId); return this.roomTypes.filter(t => !hotelId || t.hotelId === hotelId); }
  get filteredRooms(): AdminRoom[] { const key=this.searchText.trim().toLowerCase(); return this.rooms.filter(r => (!key || r.roomNumber.toLowerCase().includes(key)) && (!this.propertyFilter || r.hotelId===this.propertyFilter) && (!this.roomTypeFilter || r.roomTypeId===this.roomTypeFilter) && (this.floorFilter===null || r.floor===this.floorFilter) && (!this.statusFilter || r.status===this.statusFilter) && (!this.housekeepingFilter || r.housekeepingStatus===this.housekeepingFilter) && (!this.maintenanceFilter || r.maintenanceStatus===this.maintenanceFilter)); }

  loadData(): void { this.loading=true; this.errorMessage=''; forkJoin({rooms:this.api.getRooms(),roomTypes:this.api.getRoomTypes(),properties:this.api.getProperties()}).pipe(timeout(15000),finalize(()=>{this.loading=false;this.cdr.detectChanges();})).subscribe({next:d=>{this.rooms=d.rooms;this.roomTypes=d.roomTypes;this.properties=d.properties;},error:e=>this.errorMessage=e?.error?.message||'Không thể tải danh sách phòng.'}); }
  propertyName(id:number):string{const p=this.properties.find(x=>x.id===id);return p?.nameVi||p?.name||`Cơ sở #${id}`;}
  resetFilters():void{this.searchText='';this.propertyFilter=null;this.roomTypeFilter=null;this.floorFilter=null;this.statusFilter='';this.housekeepingFilter='';this.maintenanceFilter='';}
  onFormPropertyChange():void{this.form.roomTypeId=undefined;}
  onBulkPropertyChange():void{this.bulk.roomTypeId=0;}
  openCreate():void{this.editingId=null;this.form=this.emptyForm();this.dialogVisible=true;}
  openEdit(room:AdminRoom):void{this.editingId=room.id;this.form={...room};this.dialogVisible=true;}
  openBulk():void{this.form=this.emptyForm();this.editingId=null;this.bulk=this.emptyBulk();this.bulkVisible=true;}

  save():void{if(this.saving||!this.form.hotelId||!this.form.roomTypeId||!this.form.roomNumber?.trim()){this.messages.add({severity:'warn',summary:'Thiếu thông tin',detail:'Vui lòng chọn cơ sở, loại phòng và nhập số phòng.'});return;}this.saving=true;const req=this.editingId?this.api.updateRoom(this.editingId,this.form):this.api.createRoom(this.form);req.pipe(finalize(()=>{this.saving=false;this.cdr.detectChanges();})).subscribe({next:()=>{this.dialogVisible=false;this.messages.add({severity:'success',summary:'Thành công',detail:'Đã lưu phòng.'});this.loadData();},error:e=>this.messages.add({severity:'error',summary:'Lỗi',detail:e?.error?.message||'Không thể lưu phòng.'})});}
  createBulk():void{if(this.saving||!this.bulk.hotelId||!this.bulk.roomTypeId||this.bulk.fromNumber>this.bulk.toNumber){this.messages.add({severity:'warn',summary:'Dữ liệu chưa hợp lệ',detail:'Vui lòng kiểm tra cơ sở, loại phòng và dải số.'});return;}this.saving=true;this.api.bulkCreateRooms(this.bulk).pipe(finalize(()=>{this.saving=false;this.cdr.detectChanges();})).subscribe({next:r=>{this.bulkVisible=false;const failed=r.failedRoomNumbers?.length?` Bỏ qua phòng trùng: ${r.failedRoomNumbers.join(', ')}.`:'';this.messages.add({severity:r.created.length?'success':'warn',summary:'Kết quả tạo phòng',detail:`Đã tạo ${r.created.length} phòng.${failed}`});this.loadData();},error:e=>this.messages.add({severity:'error',summary:'Lỗi',detail:e?.error?.message||'Không thể tạo phòng hàng loạt.'})});}
  setMaintenance(room:AdminRoom, enabled:boolean):void{const value={...room,status:enabled?'MAINTENANCE':'AVAILABLE',maintenanceStatus:enabled?'MAINTENANCE':'NONE'};this.api.updateRoom(room.id,value).subscribe({next:()=>{this.messages.add({severity:'success',summary:'Thành công',detail:enabled?'Đã đánh dấu bảo trì.':'Đã mở lại phòng.'});this.loadData();},error:e=>this.messages.add({severity:'error',summary:'Lỗi',detail:e?.error?.message||'Không thể cập nhật trạng thái phòng.'})});}
  deactivate(room:AdminRoom):void{this.confirmations.confirm({header:'Xác nhận ngừng sử dụng',message:`Ngừng sử dụng phòng ${room.roomNumber}?`,acceptLabel:'Ngừng sử dụng',rejectLabel:'Hủy',accept:()=>this.api.deleteRoom(room.id).subscribe({next:()=>{this.messages.add({severity:'success',summary:'Thành công',detail:'Đã ngừng sử dụng phòng.'});this.loadData();},error:e=>this.messages.add({severity:'error',summary:'Lỗi',detail:e?.error?.message||'Không thể ngừng sử dụng phòng.'})})});}
  private emptyForm():Partial<AdminRoom>{return{hotelId:undefined,roomTypeId:undefined,roomNumber:'',floor:1,status:'AVAILABLE',housekeepingStatus:'CLEAN',maintenanceStatus:'NONE',note:''};}
  private emptyBulk():BulkRoomRequest{return{hotelId:0,roomTypeId:0,floor:1,fromNumber:101,toNumber:110,prefix:'',status:'AVAILABLE'};}
}
