import { ChangeDetectorRef, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
<<<<<<< HEAD
import { TagModule } from 'primeng/tag';
import { ReservationService, Reservation, ReservationEvent } from '../../../core/services/reservation.service';
=======
import {
  PaymentLifecycleSummary,
  RefundSummary,
  Reservation,
  ReservationService,
} from '../../../core/services/reservation.service';
>>>>>>> codex/ui-functional-audit-polish
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
import { ReservationAmendmentWorkspaceComponent } from '../../../shared/reservation-amendment/reservation-amendment-workspace.component';
import { PhysicalRoomPickerComponent } from '../../../shared/physical-room-picker/physical-room-picker.component';
import { RoomAssignmentCopyService } from '../../../shared/physical-room-picker/room-assignment-copy.service';
import { CheckInReadinessComponent } from '../../../shared/check-in-readiness/check-in-readiness.component';

@Component({
  selector: 'app-reservation-management',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TableModule,
    ButtonModule,
    CardModule,
    ToastModule,
    DialogModule,
    TooltipModule,
    ReservationCheckoutComponent,
    ReservationAmendmentWorkspaceComponent,
    PhysicalRoomPickerComponent,
    CheckInReadinessComponent,
  ],
  providers: [MessageService],
  templateUrl: './reservation-management.html',
  styleUrls: ['./reservation-management.css'],
})
export class ReservationManagement implements OnInit {
  reservations: Reservation[] = [];
<<<<<<< HEAD
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
=======
>>>>>>> codex/ui-functional-audit-polish

  showCheckoutDialog = false;
  selectedReservationId: number | null = null;
  amendmentReservationId: number | null = null;
  showAmendmentDialog = false;
  showRoomPickerDialog = false;
  showCheckInDialog = false;
  checkInReservationId: number | null = null;
  roomPickerReservationId: number | null = null;
  roomPickerSelection: number[] = [];
  roomPickerAllowsMutation = false;
  private permissionService = inject(PermissionService);
<<<<<<< HEAD
  readonly roomAssignmentCopy = inject(RoomAssignmentCopyService);
=======
  readonly canViewServices = this.permissionService.hasPermission(FunctionCode.HOTEL_SERVICE, ActionCode.VIEW);
>>>>>>> codex/ui-functional-audit-polish
  readonly canUpdateReservation = this.permissionService.hasPermission(FunctionCode.RESERVATION, ActionCode.UPDATE);
  readonly canAmendReservation = this.permissionService.hasPermission(FunctionCode.RESERVATION_AMEND, ActionCode.UPDATE);
  readonly canViewCheckIn = this.permissionService.hasPermission(FunctionCode.CHECKIN, ActionCode.VIEW);
  readonly canCheckIn = this.canViewCheckIn
    && this.permissionService.hasPermission(FunctionCode.CHECKIN, ActionCode.UPDATE);
  readonly canCancelOperational = this.permissionService.hasPermission(FunctionCode.RESERVATION_CANCEL, ActionCode.UPDATE);
  readonly canMarkNoShow = this.permissionService.hasPermission(FunctionCode.RESERVATION_NO_SHOW, ActionCode.UPDATE);
  readonly canViewRoomAssignments = this.permissionService.hasPermission(
    FunctionCode.RESERVATION_ASSIGNMENT,
    ActionCode.VIEW,
  );
  readonly canManageRoomAssignments = this.permissionService.hasPermission(
    FunctionCode.RESERVATION_ASSIGNMENT,
    ActionCode.UPDATE,
  );
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
    this.hotelServiceService.getServices().subscribe({
      next: data => this.services = data,
      error: () => this.services = [],
    });
  }

<<<<<<< HEAD
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
=======
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
>>>>>>> codex/ui-functional-audit-polish
      },
    });
  }

<<<<<<< HEAD
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
=======
  getSeverity(
    status: string | undefined,
  ): 'success' | 'secondary' | 'info' | 'warn' | 'danger' | 'contrast' | undefined {
>>>>>>> codex/ui-functional-audit-polish
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

<<<<<<< HEAD
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
      ROOMS_ASSIGNED: this.roomAssignmentCopy.text('historyAssigned'),
      ROOMS_REASSIGNED: this.roomAssignmentCopy.text('historyReassigned'),
      ROOMS_RELEASED: this.roomAssignmentCopy.text('historyReleased'),
    } as Record<string, string>)[eventType] || eventType;
  }

  getAssignmentEventSummary(event: ReservationEvent): string {
    if (!['ROOMS_ASSIGNED', 'ROOMS_REASSIGNED', 'ROOMS_RELEASED'].includes(event.eventType)) return '';
    const before = this.roomIdsFromState(event.beforeState);
    const after = this.roomIdsFromState(event.afterState);
    const format = (ids: number[]) => ids.length
      ? ids.map(id => `#${id}`).join(', ')
      : this.roomAssignmentCopy.text('noneAssigned');
    return this.roomAssignmentCopy.text('historyBeforeAfter', {
      before: format(before),
      after: format(after),
    });
=======
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
>>>>>>> codex/ui-functional-audit-polish
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
    this.checkInReservationId = id;
    this.showCheckInDialog = true;
  }

  handleCheckInCompleted(reservation: Reservation) {
    this.showCheckInDialog = false;
    this.checkInReservationId = null;
    this.messageService.add({ severity: 'success', summary: 'Thành công', detail: 'Đã nhận phòng' });
    this.loadReservations();
    if (reservation.id && this.showDetailDialog) this.openReservationDetail(reservation.id);
  }

  openAssignmentFromCheckIn() {
    const reservation = this.reservations.find(item => item.id === this.checkInReservationId)
      || (this.selectedReservation?.id === this.checkInReservationId ? this.selectedReservation : null);
    if (reservation) this.openRoomPicker(reservation);
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

  openRoomPicker(res: Reservation) {
    if (!res.id || !this.canViewRoomAssignments) return;
    this.roomPickerReservationId = res.id;
    this.roomPickerSelection = [];
    this.roomPickerAllowsMutation = this.canManageRoomAssignments
      && this.canMutateRoomAssignmentStatus(res.status);
    this.showRoomPickerDialog = true;
  }

  handleRoomPickerSelection(roomIds: number[]) {
    this.roomPickerSelection = roomIds;
  }

  handleRoomAssignmentApplied(reservation: Reservation) {
    this.messageService.add({
      severity: 'success',
      summary: this.roomAssignmentCopy.text('updatedTitle'),
      detail: this.roomAssignmentCopy.text('updatedDetail', { id: reservation.id || '' }),
    });
    this.loadReservations();
    this.showRoomPickerDialog = false;
    if (reservation.id === this.checkInReservationId) {
      const current = this.checkInReservationId;
      this.checkInReservationId = null;
      queueMicrotask(() => this.checkInReservationId = current);
    }
    if (reservation.id && this.showDetailDialog) this.openReservationDetail(reservation.id);
  }

  canOpenRoomPicker(status: string | undefined): boolean {
    return this.canViewRoomAssignments && !new Set([
      'CHECKED_OUT', 'COMPLETED', 'CANCELLED', 'REJECTED', 'EXPIRED', 'NO_SHOW',
    ]).has(status || '');
  }

  canMutateRoomAssignmentStatus(status: string | undefined): boolean {
    return new Set(['PENDING', 'PENDING_PAYMENT', 'CONFIRMED']).has(status || '');
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

  private roomIdsFromState(value?: string): number[] {
    if (!value) return [];
    try {
      const parsed = JSON.parse(value) as { roomIds?: unknown[] };
      return Array.isArray(parsed.roomIds)
        ? parsed.roomIds.filter((id): id is number => typeof id === 'number')
        : [];
    } catch {
      return [];
    }
  }
}
