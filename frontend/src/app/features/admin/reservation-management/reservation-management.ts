import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { ReservationService, Reservation } from '../../../core/services/reservation.service';
import { InvoiceService } from '../../../core/services/invoice.service';
import { Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { DialogModule } from 'primeng/dialog';
import { TooltipModule } from 'primeng/tooltip';
import { HotelServiceService, HotelServiceDTO } from '../../../core/services/hotel-service.service';
import { CheckoutResult } from '../../../core/services/property-checkout.service';
import { ReservationCheckoutComponent } from './reservation-checkout.component';
import { ActionCode, FunctionCode, PermissionService } from '../../../core/services/permission.service';
import { Observable, finalize } from 'rxjs';
import { ReservationAmendmentWorkspaceComponent } from '../../../shared/reservation-amendment/reservation-amendment-workspace.component';

@Component({
  selector: 'app-reservation-management',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TableModule,
    ButtonModule,
    TagModule,
    CardModule,
    ToastModule,
    DialogModule,
    TooltipModule,
    ReservationCheckoutComponent,
    ReservationAmendmentWorkspaceComponent,
  ],
  providers: [MessageService],
  templateUrl: './reservation-management.html',
  styleUrls: ['./reservation-management.css'],
})
export class ReservationManagement implements OnInit {
  reservations: Reservation[] = [];
  services: HotelServiceDTO[] = [];
  reservationsLoading = false;
  reservationsError = '';
  searchQuery = '';
  statusFilter = '';
  page = 0;
  readonly pageSize = 10;
  totalElements = 0;
  totalPages = 0;
  selectedReservation: Reservation | null = null;
  detailLoading = false;
  detailError = '';
  showDetailDialog = false;

  showCheckoutDialog = false;
  selectedReservationId: number | null = null;
  amendmentReservationId: number | null = null;
  showAmendmentDialog = false;
  private permissionService = inject(PermissionService);
  readonly canUpdateReservation = this.permissionService.hasPermission(FunctionCode.RESERVATION, ActionCode.UPDATE);
  readonly canAmendReservation = this.permissionService.hasPermission(FunctionCode.RESERVATION_AMEND, ActionCode.UPDATE);
  readonly canCheckIn = this.permissionService.hasPermission(FunctionCode.CHECKIN, ActionCode.UPDATE);
  readonly canCancelOperational = this.permissionService.hasPermission(FunctionCode.RESERVATION_CANCEL, ActionCode.UPDATE);
  readonly canMarkNoShow = this.permissionService.hasPermission(FunctionCode.RESERVATION_NO_SHOW, ActionCode.UPDATE);
  readonly lifecycleActionKey = signal<string | null>(null);

  constructor(
    private reservationService: ReservationService, 
    private invoiceService: InvoiceService,
    private hotelServiceService: HotelServiceService,
    private messageService: MessageService,
    private router: Router
  ) {}

  ngOnInit() {
    this.loadReservations();
    this.hotelServiceService.getServices().subscribe({
      next: data => this.services = data,
      error: () => this.services = [],
    });
  }

  loadReservations(resetPage = false) {
    if (resetPage) this.page = 0;
    this.reservationsLoading = true;
    this.reservationsError = '';
    this.reservationService.searchReservations({
      status: this.statusFilter || undefined,
      query: this.searchQuery,
      page: this.page,
      size: this.pageSize,
    }).pipe(finalize(() => this.reservationsLoading = false)).subscribe({
      next: result => {
        this.reservations = result.content;
        this.page = result.page;
        this.totalElements = result.totalElements;
        this.totalPages = result.totalPages;
      },
      error: () => {
        this.reservations = [];
        this.totalElements = 0;
        this.totalPages = 0;
        this.reservationsError = 'Không thể tải danh sách đặt phòng. Vui lòng thử lại.';
      },
    });
  }

  goToPage(nextPage: number) {
    if (nextPage < 0 || nextPage >= this.totalPages || nextPage === this.page) return;
    this.page = nextPage;
    this.loadReservations();
  }

  openReservationDetail(reservationId: number | undefined) {
    if (!reservationId) return;
    this.showDetailDialog = true;
    this.detailLoading = true;
    this.detailError = '';
    this.selectedReservation = null;
    this.reservationService.getReservationById(reservationId)
      .pipe(finalize(() => this.detailLoading = false))
      .subscribe({
        next: reservation => this.selectedReservation = reservation,
        error: () => this.detailError = 'Không thể tải chi tiết đặt phòng.',
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

  getStatusLabel(status: string | undefined): string {
    if (!status) return 'Chưa xác định';
    return ({
      PENDING: 'Chờ xác nhận',
      PENDING_PAYMENT: 'Chờ thanh toán',
      CONFIRMED: 'Đã xác nhận',
      CHECKED_IN: 'Đã nhận phòng',
      CHECKED_OUT: 'Đã trả phòng',
      CANCELLED: 'Đã hủy',
      NO_SHOW: 'Khách không đến',
    } as Record<string, string>)[status] || status;
  }

  getEventLabel(eventType: string): string {
    return ({
      RESERVATION_CREATED: 'Đã tạo đặt phòng',
      RESERVATION_STATUS_CHANGED: 'Đã đổi trạng thái',
      ROOMS_ASSIGNED: 'Đã xếp phòng cụ thể',
    } as Record<string, string>)[eventType] || eventType;
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

  openCheckoutWorkspace(res: Reservation) {
    if (!res.id) return;
    this.selectedReservationId = res.id;
    this.showCheckoutDialog = true;
  }

  openAmendmentWorkspace(res: Reservation) {
    if (!res.id) return;
    this.amendmentReservationId = res.id;
    this.showAmendmentDialog = true;
  }

  handleAmendmentApplied(reservationId: number) {
    this.showAmendmentDialog = false;
    this.amendmentReservationId = null;
    this.messageService.add({
      severity: 'success',
      summary: 'Đã thay đổi đặt phòng',
      detail: `Đặt phòng RES-${reservationId} đã được cập nhật theo báo giá máy chủ.`,
    });
    this.loadReservations();
    this.openReservationDetail(reservationId);
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
