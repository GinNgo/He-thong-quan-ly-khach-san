import { ChangeDetectorRef, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import {
  PaymentLifecycleSummary,
  RefundSummary,
  Reservation,
  ReservationService,
} from '../../../core/services/reservation.service';
import { InvoiceService } from '../../../core/services/invoice.service';
import { Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { DialogModule } from 'primeng/dialog';
import { TooltipModule } from 'primeng/tooltip';
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
    CardModule,
    ToastModule,
    DialogModule,
    TooltipModule,
    ReservationCheckoutComponent,
  ],
  providers: [MessageService],
  templateUrl: './reservation-management.html',
  styleUrls: ['./reservation-management.css'],
})
export class ReservationManagement implements OnInit {
  reservations: Reservation[] = [];

  showCheckoutDialog = false;
  selectedReservationId: number | null = null;
  private permissionService = inject(PermissionService);
  readonly canViewServices = this.permissionService.hasPermission(FunctionCode.HOTEL_SERVICE, ActionCode.VIEW);
  readonly canUpdateReservation = this.permissionService.hasPermission(FunctionCode.RESERVATION, ActionCode.UPDATE);
  readonly canCheckIn = this.permissionService.hasPermission(FunctionCode.CHECKIN, ActionCode.UPDATE);
  readonly canCancelOperational = this.permissionService.hasPermission(FunctionCode.RESERVATION_CANCEL, ActionCode.UPDATE);
  readonly canMarkNoShow = this.permissionService.hasPermission(FunctionCode.RESERVATION_NO_SHOW, ActionCode.UPDATE);
  readonly lifecycleActionKey = signal<string | null>(null);

  constructor(
    private reservationService: ReservationService,
    private invoiceService: InvoiceService,
    private messageService: MessageService,
    private router: Router,
    private changeDetector: ChangeDetectorRef,
  ) {}

  ngOnInit() {
    this.loadReservations();
  }

  loadReservations() {
    this.reservationService.getAllReservations().subscribe({
      next: (data) => {
        this.reservations = data;
        this.changeDetector.detectChanges();
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Lá»—i',
          detail: 'KhÃ´ng thá»ƒ táº£i danh sÃ¡ch Ä‘áº·t phÃ²ng',
        });
        this.changeDetector.detectChanges();
      },
    });
  }

  getSeverity(
    status: string | undefined,
  ): 'success' | 'secondary' | 'info' | 'warn' | 'danger' | 'contrast' | undefined {
    if (!status) return 'info';
    switch (status) {
      case 'CONFIRMED':
        return 'success';
      case 'PENDING':
      case 'PENDING_PAYMENT':
        return 'warn';
      case 'CHECKED_IN':
        return 'info';
      case 'CHECKED_OUT':
        return 'secondary';
      case 'CANCELLED':
        return 'danger';
      default:
        return 'info';
    }
  }

  getReservationLabel(status?: string): string {
    return (
      (
        {
          PENDING: 'Chờ xác nhận',
          PENDING_PAYMENT: 'Chờ thanh toán',
          CONFIRMED: 'Đã xác nhận',
          CHECKED_IN: 'Đang lưu trú',
          CHECKED_OUT: 'Đã trả phòng',
          COMPLETED: 'Hoàn tất',
          CANCELLED: 'Đã hủy',
          EXPIRED: 'Đã hết hạn',
          REJECTED: 'Đã từ chối',
          NO_SHOW: 'Không đến',
        } as Record<string, string>
      )[status || ''] ||
      status ||
      'Chưa xác định'
    );
  }

  getPaymentLabel(payment?: PaymentLifecycleSummary): string {
    if (!payment) return 'Chưa có giao dịch';
    if (payment.reconciliationRequired) return 'Cần đối soát';
    return (
      (
        {
          CREATED: 'Đã tạo phiên',
          PENDING: 'Đang chờ',
          SUCCEEDED: 'Đã thanh toán',
          FAILED: 'Thất bại',
          EXPIRED: 'Hết hạn',
        } as Record<string, string>
      )[payment.status] || payment.status
    );
  }

  getPaymentTone(payment?: PaymentLifecycleSummary): string {
    if (!payment) return 'neutral';
    if (payment.reconciliationRequired) return 'warning';
    const tones: Record<string, string> = {
      SUCCEEDED: 'success',
      FAILED: 'danger',
      EXPIRED: 'neutral',
      PENDING: 'warning',
      CREATED: 'info',
    };
    return tones[payment.status] || 'neutral';
  }

  getPaymentIcon(payment?: PaymentLifecycleSummary): string {
    if (!payment) return 'pi pi-wallet';
    if (payment.reconciliationRequired) return 'pi pi-sync';
    return (
      (
        {
          SUCCEEDED: 'pi pi-check-circle',
          FAILED: 'pi pi-times-circle',
          EXPIRED: 'pi pi-clock',
          PENDING: 'pi pi-hourglass',
          CREATED: 'pi pi-wallet',
        } as Record<string, string>
      )[payment.status] || 'pi pi-wallet'
    );
  }

  getLatestRefund(reservation: Reservation): RefundSummary | undefined {
    const refunds = reservation.refunds;
    return refunds?.length ? refunds[refunds.length - 1] : undefined;
  }

  getRefundLabel(refund?: RefundSummary): string {
    if (!refund) return 'Không có yêu cầu';
    return (
      (
        {
          REQUESTED: 'Đã yêu cầu',
          PENDING_PROVIDER: 'Đang xử lý',
          SUCCEEDED: 'Đã hoàn tiền',
          FAILED: 'Cần xử lý lại',
        } as Record<string, string>
      )[refund.status] || refund.status
    );
  }

  getRefundTone(refund?: RefundSummary): string {
    if (!refund) return 'neutral';
    const tones: Record<string, string> = {
      REQUESTED: 'info',
      PENDING_PROVIDER: 'warning',
      SUCCEEDED: 'success',
      FAILED: 'danger',
    };
    return tones[refund.status] || 'neutral';
  }

  getRefundIcon(refund?: RefundSummary): string {
    if (!refund) return 'pi pi-minus-circle';
    return (
      (
        {
          REQUESTED: 'pi pi-file-plus',
          PENDING_PROVIDER: 'pi pi-hourglass',
          SUCCEEDED: 'pi pi-check-circle',
          FAILED: 'pi pi-exclamation-circle',
        } as Record<string, string>
      )[refund.status] || 'pi pi-replay'
    );
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
        this.messageService.add({
          severity: 'success',
          summary: 'Thành công',
          detail: 'Đã xuất hóa đơn ' + data.invoiceCode,
        });
      },
      error: (err) => {
        this.messageService.add({
          severity: 'error',
          summary: 'Lỗi',
          detail: 'Chưa có thanh toán để xuất hóa đơn',
        });
      },
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
