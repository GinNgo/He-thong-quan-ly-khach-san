import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { SelectModule } from 'primeng/select';
import {
  CheckoutPreview,
  CheckoutResult,
  NegativeAdjustmentType,
  PropertyCheckoutService,
  ServiceChargeType,
  SurchargeType,
} from '../../../core/services/property-checkout.service';
import { HotelServiceDTO } from '../../../core/services/hotel-service.service';

type AdjustmentMode = 'SURCHARGE' | 'NEGATIVE_ADJUSTMENT';
type BusyAction = 'PREVIEW' | 'SERVICE' | 'ADJUSTMENT' | 'OVERRIDE' | 'CHECKOUT' | null;

@Component({
  selector: 'app-reservation-checkout',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ButtonModule, InputNumberModule, SelectModule],
  templateUrl: './reservation-checkout.component.html',
  styleUrl: './reservation-checkout.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReservationCheckoutComponent implements OnChanges {
  private readonly checkoutService = inject(PropertyCheckoutService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly servicesState = signal<HotelServiceDTO[]>([]);

  @Input({ required: true }) reservationId!: number;
  @Input() set services(value: HotelServiceDTO[]) {
    this.servicesState.set(value ?? []);
  }
  @Output() completed = new EventEmitter<CheckoutResult>();
  @Output() closed = new EventEmitter<void>();

  readonly preview = signal<CheckoutPreview | null>(null);
  readonly busyAction = signal<BusyAction>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly checkoutOverrideId = signal<number | null>(null);

  readonly serviceForm = this.formBuilder.nonNullable.group({
    serviceId: [0, [Validators.required, Validators.min(1)]],
    chargeType: ['SERVICE' as ServiceChargeType, Validators.required],
    quantity: [1, [Validators.required, Validators.min(0.01)]],
  });

  readonly adjustmentForm = this.formBuilder.nonNullable.group({
    mode: ['SURCHARGE' as AdjustmentMode, Validators.required],
    surchargeType: ['OTHER' as SurchargeType, Validators.required],
    negativeType: ['SERVICE_RECOVERY' as NegativeAdjustmentType, Validators.required],
    description: ['', [Validators.required, Validators.maxLength(500)]],
    amount: [0, [Validators.required, Validators.min(1)]],
  });

  readonly overrideForm = this.formBuilder.nonNullable.group({
    reason: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(500)]],
  });

  readonly serviceOptions = computed(() =>
    this.servicesState()
      .filter((service) => service.id && service.status !== 'INACTIVE')
      .map((service) => ({
        label: `${service.nameVi} - ${this.formatVnd(service.price)}`,
        value: service.id as number,
      })),
  );

  readonly canCheckout = computed(() => {
    const current = this.preview();
    return Boolean(current?.checkoutAllowed || this.checkoutOverrideId());
  });

  readonly needsDebtOverride = computed(
    () => this.preview()?.settlementState === 'OUTSTANDING' && !this.checkoutOverrideId(),
  );

  readonly isOverpaid = computed(() => this.preview()?.settlementState === 'OVERPAID');

  readonly serviceChargeTypes: Array<{ label: string; value: ServiceChargeType }> = [
    { label: 'Dịch vụ', value: 'SERVICE' },
    { label: 'Minibar', value: 'MINIBAR' },
  ];

  readonly surchargeTypes: Array<{ label: string; value: SurchargeType }> = [
    { label: 'Nhận phòng sớm', value: 'EARLY_CHECK_IN' },
    { label: 'Trả phòng muộn', value: 'LATE_CHECK_OUT' },
    { label: 'Thêm khách', value: 'EXTRA_GUEST' },
    { label: 'Hư hỏng', value: 'DAMAGE' },
    { label: 'Vệ sinh đặc biệt', value: 'CLEANING' },
    { label: 'Mất chìa khóa', value: 'LOST_KEY' },
    { label: 'Khác', value: 'OTHER' },
  ];

  readonly negativeAdjustmentTypes: Array<{ label: string; value: NegativeAdjustmentType }> = [
    { label: 'Khắc phục dịch vụ', value: 'SERVICE_RECOVERY' },
    { label: 'Hỗ trợ thiện chí', value: 'GOODWILL' },
    { label: 'Điều chỉnh giá', value: 'PRICE_CORRECTION' },
    { label: 'Giảm giá thủ công', value: 'MANUAL_DISCOUNT' },
    { label: 'Khác', value: 'OTHER' },
  ];

  readonly adjustmentModes: Array<{ label: string; value: AdjustmentMode }> = [
    { label: 'Phụ thu', value: 'SURCHARGE' },
    { label: 'Điều chỉnh giảm', value: 'NEGATIVE_ADJUSTMENT' },
  ];

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['reservationId'] && this.reservationId > 0) {
      this.checkoutOverrideId.set(null);
      this.loadPreview();
    }
  }

  loadPreview(): void {
    if (!this.reservationId || this.busyAction()) return;
    this.beginAction('PREVIEW');
    this.checkoutService.preview(this.reservationId).subscribe({
      next: (preview) => {
        this.preview.set(preview);
        this.finishAction();
      },
      error: (error: unknown) => this.failAction(error, 'Không thể tải quyết toán hiện tại.'),
    });
  }

  addService(): void {
    if (this.serviceForm.invalid || this.busyAction()) {
      this.serviceForm.markAllAsTouched();
      return;
    }
    this.beginAction('SERVICE');
    this.checkoutService.addServiceCharge(this.reservationId, this.serviceForm.getRawValue()).subscribe({
      next: () => {
        this.successMessage.set('Đã thêm dịch vụ theo giá cấu hình của hệ thống.');
        this.serviceForm.patchValue({ serviceId: 0, quantity: 1 });
        this.refreshAfterMutation();
      },
      error: (error: unknown) => this.failAction(error, 'Không thể thêm dịch vụ.'),
    });
  }

  addAdjustment(): void {
    if (this.adjustmentForm.invalid || this.busyAction()) {
      this.adjustmentForm.markAllAsTouched();
      return;
    }
    const value = this.adjustmentForm.getRawValue();
    this.beginAction('ADJUSTMENT');
    const request$ = value.mode === 'NEGATIVE_ADJUSTMENT'
      ? this.checkoutService.addNegativeAdjustment(this.reservationId, {
          type: value.negativeType,
          description: value.description,
          amount: value.amount,
        })
      : this.checkoutService.addSurcharge(this.reservationId, {
          type: value.surchargeType,
          description: value.description,
          amount: value.amount,
        });

    request$.subscribe({
      next: () => {
        this.successMessage.set(
          value.mode === 'NEGATIVE_ADJUSTMENT'
            ? 'Đã ghi nhận điều chỉnh giảm có kiểm soát.'
            : 'Đã thêm phụ thu vào folio.',
        );
        this.adjustmentForm.patchValue({ description: '', amount: 0 });
        this.refreshAfterMutation();
      },
      error: (error: unknown) => this.failAction(error, 'Không thể cập nhật phụ thu.'),
    });
  }

  authorizeDebtOverride(): void {
    if (this.overrideForm.invalid || this.busyAction()) {
      this.overrideForm.markAllAsTouched();
      return;
    }
    this.beginAction('OVERRIDE');
    this.checkoutService
      .authorizeDebtOverride(this.reservationId, this.overrideForm.getRawValue().reason)
      .subscribe({
        next: (result) => {
          this.checkoutOverrideId.set(result.overrideId);
          this.preview.set(result.preview);
          this.successMessage.set('Đã cấp quyền trả phòng còn công nợ cho lần thao tác này.');
          this.finishAction();
        },
        error: (error: unknown) => this.failAction(error, 'Không thể cấp quyền công nợ.'),
      });
  }

  checkout(): void {
    if (!this.canCheckout() || this.busyAction()) return;
    this.beginAction('CHECKOUT');
    this.checkoutService
      .checkout(this.reservationId, this.checkoutOverrideId() ?? undefined)
      .subscribe({
        next: (result) => {
          this.successMessage.set(`Đã chốt hóa đơn ${result.invoiceNumber}.`);
          this.finishAction();
          this.completed.emit(result);
        },
        error: (error: unknown) => this.failAction(error, 'Không thể hoàn tất trả phòng.'),
      });
  }

  close(): void {
    if (!this.busyAction()) this.closed.emit();
  }

  trackLine(index: number, line: { sourceType: string; sourceId: number | null }): string {
    return `${line.sourceType}-${line.sourceId ?? index}`;
  }

  sumAmounts(first: number | string, second: number | string): number {
    return Number(first) + Number(second);
  }

  isNegative(value: number | string): boolean {
    return Number(value) < 0;
  }

  formatVnd(value: number | string): string {
    const amount = typeof value === 'number' ? value : Number(value);
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND',
      maximumFractionDigits: 0,
    }).format(Number.isFinite(amount) ? amount : 0);
  }

  private refreshAfterMutation(): void {
    this.busyAction.set(null);
    this.loadPreview();
  }

  private beginAction(action: Exclude<BusyAction, null>): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.busyAction.set(action);
  }

  private finishAction(): void {
    this.busyAction.set(null);
  }

  private failAction(error: unknown, fallback: string): void {
    this.errorMessage.set(this.extractErrorMessage(error) || fallback);
    this.busyAction.set(null);
  }

  private extractErrorMessage(error: unknown): string | null {
    if (!error || typeof error !== 'object') return null;
    const candidate = error as { error?: { message?: unknown }; message?: unknown };
    if (typeof candidate.error?.message === 'string') return candidate.error.message;
    return typeof candidate.message === 'string' ? candidate.message : null;
  }
}
