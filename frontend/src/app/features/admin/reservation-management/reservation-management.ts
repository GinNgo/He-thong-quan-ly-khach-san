import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { ReservationService, Reservation } from '../../../core/services/reservation.service';
import { PaymentService } from '../../../core/services/payment.service';
import { InvoiceService } from '../../../core/services/invoice.service';
import { Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputNumberModule } from 'primeng/inputnumber';
import { TooltipModule } from 'primeng/tooltip';
import { FormsModule } from '@angular/forms';
import { HotelServiceService, HotelServiceDTO } from '../../../core/services/hotel-service.service';
import { CheckoutResult } from '../../../core/services/property-checkout.service';
import { ReservationCheckoutComponent } from './reservation-checkout.component';
import { ActionCode, FunctionCode, PermissionService } from '../../../core/services/permission.service';
import { Observable, finalize } from 'rxjs';

@Component({
  selector: 'app-reservation-management',
  standalone: true,
  imports: [
    CommonModule,
    TableModule,
    ButtonModule,
    TagModule,
    CardModule,
    ToastModule,
    DialogModule,
    SelectModule,
    InputNumberModule,
    TooltipModule,
    FormsModule,
    ReservationCheckoutComponent,
  ],
  providers: [MessageService],
  templateUrl: './reservation-management.html',
  styleUrls: ['./reservation-management.css'],
})
export class ReservationManagement implements OnInit {
  reservations: Reservation[] = [];
  services: HotelServiceDTO[] = [];
  
  showAddServiceDialog = false;
  showCheckoutDialog = false;
  selectedReservationId: number | null = null;
  newServiceItem = { serviceId: 0, quantity: 1 };
  private permissionService = inject(PermissionService);
  readonly canUpdateReservation = this.permissionService.hasPermission(FunctionCode.RESERVATION, ActionCode.UPDATE);
  readonly canCheckIn = this.permissionService.hasPermission(FunctionCode.CHECKIN, ActionCode.UPDATE);
  readonly canCancelOperational = this.permissionService.hasPermission(FunctionCode.RESERVATION_CANCEL, ActionCode.UPDATE);
  readonly canMarkNoShow = this.permissionService.hasPermission(FunctionCode.RESERVATION_NO_SHOW, ActionCode.UPDATE);
  readonly lifecycleActionKey = signal<string | null>(null);

  constructor(
    private reservationService: ReservationService, 
    private paymentService: PaymentService,
    private invoiceService: InvoiceService,
    private hotelServiceService: HotelServiceService,
    private messageService: MessageService,
    private router: Router
  ) {}

  ngOnInit() {
    this.loadReservations();
  }

  loadReservations() {
    this.reservationService.getAllReservations().subscribe(data => {
      this.reservations = data;
    });
    this.hotelServiceService.getServices().subscribe(data => {
      this.services = data;
    });
  }

  getSeverity(status: string | undefined): "success" | "secondary" | "info" | "warn" | "danger" | "contrast" | undefined {
    if (!status) return 'info';
    switch (status) {
      case 'CONFIRMED': return 'success';
      case 'PENDING': return 'warn';
      case 'CHECKED_IN': return 'info';
      case 'CHECKED_OUT': return 'secondary';
      case 'CANCELLED': return 'danger';
      default: return 'info';
    }
  }

  updateStatus(id: number | undefined, status: string) {
    if (!id) return;
    this.runLifecycleAction(
      id,
      `STATUS_${status}`,
      this.reservationService.updateReservationStatus(id, status),
      'Đã cập nhật trạng thái đặt phòng',
    );
  }

  checkIn(id: number | undefined) {
    if (!id) return;
    this.runLifecycleAction(id, 'CHECK_IN', this.reservationService.checkIn(id), 'Đã nhận phòng');
  }

  cancelOperational(id: number | undefined) {
    if (!id) return;
    this.runLifecycleAction(
      id,
      'CANCEL',
      this.reservationService.cancelOperational(id),
      'Đã hủy đặt phòng',
    );
  }

  markNoShow(id: number | undefined) {
    if (!id) return;
    this.runLifecycleAction(
      id,
      'NO_SHOW',
      this.reservationService.markNoShow(id),
      'Đã đánh dấu khách không đến',
    );
  }

  isLifecycleBusy(id: number | undefined, action: string): boolean {
    return Boolean(id && this.lifecycleActionKey() === `${action}:${id}`);
  }

  createNew() {
    this.router.navigate(['/admin/reservations/create']);
  }

  viewTimeline() {
    this.router.navigate(['/admin/reservations/timeline']);
  }

  openAddServiceDialog(res: Reservation) {
    if (!res.id) return;
    this.selectedReservationId = res.id;
    this.newServiceItem = { serviceId: 0, quantity: 1 };
    this.showAddServiceDialog = true;
  }

  openCheckoutWorkspace(res: Reservation) {
    if (!res.id) return;
    this.selectedReservationId = res.id;
    this.showCheckoutDialog = true;
  }

  handleCheckoutCompleted(result: CheckoutResult) {
    this.messageService.add({
      severity: 'success',
      summary: 'Đã trả phòng',
      detail: `Đã chốt hóa đơn ${result.invoiceNumber}`,
    });
    this.showCheckoutDialog = false;
    this.loadReservations();
  }

  submitAddService() {
    if (!this.selectedReservationId || !this.newServiceItem.serviceId || this.newServiceItem.quantity < 1) {
      this.messageService.add({ severity: 'error', summary: 'Lỗi', detail: 'Vui lòng chọn dịch vụ và số lượng hợp lệ' });
      return;
    }
    
    this.reservationService.addExtraService(this.selectedReservationId, this.newServiceItem.serviceId, this.newServiceItem.quantity)
      .subscribe({
        next: (res) => {
          this.messageService.add({ severity: 'success', summary: 'Thành công', detail: 'Đã thêm dịch vụ' });
          this.showAddServiceDialog = false;
          this.loadReservations();
        },
        error: (err) => {
          this.messageService.add({ severity: 'error', summary: 'Lỗi', detail: 'Thêm dịch vụ thất bại' });
        }
      });
  }

  processPayment(res: Reservation) {
    if (!res.id || !res.totalAmount) return;
    this.paymentService.processPayment({
      reservationId: res.id,
      amount: res.totalAmount,
      paymentMethod: 'CASH'
    }).subscribe({
      next: (data) => {
        this.messageService.add({ severity: 'success', summary: 'Thành công', detail: 'Đã thanh toán Booking RES-' + res.id });
        this.loadReservations();
      },
      error: (err) => {
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail: 'Thanh toán thất bại' });
      }
    });
  }

  generateInvoice(resId: number | undefined) {
    if (!resId) return;
    this.invoiceService.generateInvoice(resId).subscribe({
      next: (data) => {
        this.messageService.add({ severity: 'success', summary: 'Thành công', detail: 'Đã xuất hóa đơn ' + data.invoiceCode });
      },
      error: (err) => {
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail: 'Chưa có thanh toán để xuất hóa đơn' });
      }
    });
  }

  private runLifecycleAction(
    reservationId: number,
    action: string,
    request$: Observable<Reservation>,
    successDetail: string,
  ) {
    if (this.lifecycleActionKey()) return;
    this.lifecycleActionKey.set(`${action}:${reservationId}`);
    request$
      .pipe(finalize(() => this.lifecycleActionKey.set(null)))
      .subscribe({
        next: () => {
          this.messageService.add({
            severity: 'success',
            summary: 'Thành công',
            detail: successDetail,
          });
          this.loadReservations();
        },
        error: () => {
          this.messageService.add({
            severity: 'error',
            summary: 'Không thể thực hiện',
            detail: 'Tài khoản không có quyền hoặc trạng thái đặt phòng không còn phù hợp.',
          });
        },
      });
  }
}
