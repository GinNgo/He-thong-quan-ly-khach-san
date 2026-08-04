import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { distinctUntilChanged, finalize, map } from 'rxjs';
import {
  ManagementApiService,
  ManagementContext,
} from '../../../core/services/management-api.service';
import {
  PropertyRevenueReportFilters,
  RevenueBasis,
  RevenueBreakdown,
  RevenueExportFormat,
  RevenueReportResult,
  RevenueReportService,
  RevenueTransactionRow,
} from '../../../core/services/revenue-report.service';
import { FeedbackStateComponent } from '../../../shared/components/feedback-state/feedback-state.component';

@Component({
  selector: 'app-property-revenue',
  standalone: true,
  imports: [CommonModule, FormsModule, FeedbackStateComponent],
  templateUrl: './property-revenue.component.html',
  styleUrls: ['./property-revenue.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PropertyRevenueComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly managementApi = inject(ManagementApiService);
  private readonly reportService = inject(RevenueReportService);
  private readonly destroyRef = inject(DestroyRef);

  readonly report = signal<RevenueReportResult | null>(null);
  readonly context = signal<ManagementContext | null>(null);
  readonly propertyId = signal<number | null>(null);
  readonly loading = signal(false);
  readonly contextLoading = signal(true);
  readonly errorMessage = signal('');
  readonly exportLoading = signal<RevenueExportFormat | null>(null);
  readonly page = signal(0);
  readonly pageSize = 50;

  fromDate = this.monthStart();
  toDate = this.inputDate(new Date());
  basis: RevenueBasis = 'NET';
  provider = '';
  method = '';
  transactionType = '';
  roomType = '';

  ngOnInit(): void {
    this.route.queryParamMap
      .pipe(
        map((params) => Number(params.get('propertyId'))),
        map((value) => Number.isInteger(value) && value > 0 ? value : null),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((propertyId) => {
        this.propertyId.set(propertyId);
        this.loadContext(propertyId ?? undefined);
        if (propertyId) this.loadReport(propertyId);
      });
  }

  loadContext(propertyId?: number): void {
    this.contextLoading.set(true);
    this.managementApi.context(propertyId)
      .pipe(
        finalize(() => this.contextLoading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (context) => {
          this.context.set(context);
          if (!this.propertyId() && context.activePropertyId) {
            this.propertyId.set(context.activePropertyId);
            this.loadReport(context.activePropertyId);
          }
        },
        error: () => this.context.set(null),
      });
  }

  applyFilters(): void {
    const propertyId = this.propertyId();
    this.errorMessage.set('');
    if (!propertyId) {
      this.errorMessage.set('Hãy chọn một cơ sở trước khi xem báo cáo.');
      return;
    }
    if (!this.fromDate || !this.toDate || this.fromDate > this.toDate) {
      this.errorMessage.set('Ngày bắt đầu phải nằm trước hoặc trùng ngày kết thúc.');
      return;
    }
    this.loadReport(propertyId);
  }

  resetFilters(): void {
    this.fromDate = this.monthStart();
    this.toDate = this.inputDate(new Date());
    this.basis = 'NET';
    this.provider = '';
    this.method = '';
    this.transactionType = '';
    this.roomType = '';
    this.applyFilters();
  }

  breakdownsForChart(): RevenueBreakdown[] {
    const breakdowns = this.report()?.breakdowns ?? [];
    const roomBreakdowns = breakdowns.filter((item) => item.dimension === 'ROOM_TYPE');
    return (roomBreakdowns.length
      ? roomBreakdowns
      : breakdowns.filter((item) => item.dimension === 'TRANSACTION_TYPE'))
      .slice(0, 8);
  }

  chartTitle(): string {
    return this.report()?.breakdowns.some((item) => item.dimension === 'ROOM_TYPE')
      ? 'Doanh thu theo loại phòng'
      : 'Doanh thu theo loại giao dịch';
  }

  chartValue(item: RevenueBreakdown): number {
    if (this.basis === 'CASH_COLLECTED') return item.grossRevenue - item.refunds;
    return item.netRevenue;
  }

  chartWidth(item: RevenueBreakdown): number {
    const values = this.breakdownsForChart().map((entry) => Math.max(this.chartValue(entry), 0));
    const max = Math.max(...values, 1);
    return Math.max(Math.min((Math.max(this.chartValue(item), 0) / max) * 100, 100), 4);
  }

  providerOptions(): string[] {
    return this.unique(this.report()?.rows.map((row) => row.provider).filter(this.isPresent));
  }

  methodOptions(): string[] {
    return this.unique(this.report()?.rows.map((row) => row.method).filter(this.isPresent));
  }

  transactionTypeOptions(): string[] {
    return this.unique(this.report()?.rows.map((row) => row.transactionType));
  }

  roomTypeOptions(): string[] {
    return this.unique(this.report()?.breakdowns
      .filter((item) => item.dimension === 'ROOM_TYPE')
      .map((item) => item.code));
  }

  visibleRows(): RevenueTransactionRow[] {
    const start = this.page() * this.pageSize;
    return (this.report()?.rows ?? []).slice(start, start + this.pageSize);
  }

  totalPages(): number { return Math.max(1, Math.ceil((this.report()?.rows.length ?? 0) / this.pageSize)); }
  previousPage(): void { this.page.update(value => Math.max(0, value - 1)); }
  nextPage(): void { this.page.update(value => Math.min(this.totalPages() - 1, value + 1)); }

  export(format: RevenueExportFormat): void {
    const propertyId = this.propertyId();
    if (!propertyId || this.exportLoading()) return;
    this.exportLoading.set(format);
    this.reportService.exportPropertyRevenue(this.filters(propertyId), format)
      .pipe(finalize(() => this.exportLoading.set(null)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: blob => {
          const url = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = `property-revenue-${propertyId}-${this.fromDate}-${this.toDate}.${format.toLowerCase()}`;
          anchor.click();
          URL.revokeObjectURL(url);
        },
        error: (error: HttpErrorResponse) => this.errorMessage.set(this.errorMessageFrom(error)),
      });
  }

  propertyName(): string {
    const id = this.propertyId();
    return this.context()?.properties.find((property) => property.id === id)?.nameVi
      || `Cơ sở #${id ?? '—'}`;
  }

  formatMoney(value: number): string {
    return new Intl.NumberFormat('vi-VN', {
      style: 'currency',
      currency: 'VND',
      maximumFractionDigits: 0,
    }).format(value);
  }

  formatDateTime(value: string): string {
    return new Intl.DateTimeFormat('vi-VN', {
      dateStyle: 'short',
      timeStyle: 'short',
    }).format(new Date(value));
  }

  statusLabel(status: RevenueTransactionRow['reconciliationStatus']): string {
    if (status === 'MISMATCH') return 'Sai lệch';
    if (status === 'UNRECONCILED') return 'Chưa đối soát';
    return 'Đã đối soát';
  }

  trackBreakdown(_: number, item: RevenueBreakdown): string {
    return `${item.dimension}-${item.code}`;
  }

  trackRow(_: number, row: RevenueTransactionRow): string {
    return row.publicId;
  }

  private loadReport(propertyId: number): void {
    this.loading.set(true);
    this.errorMessage.set('');
    const filters = this.filters(propertyId);
    this.page.set(0);

    this.reportService.getPropertyRevenue(filters)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (report) => this.report.set(report),
        error: (error: HttpErrorResponse) => {
          this.report.set(null);
          this.errorMessage.set(this.errorMessageFrom(error));
        },
      });
  }

  private filters(propertyId: number): PropertyRevenueReportFilters {
    return {
      from: this.fromDate,
      to: this.toDate,
      basis: this.basis,
      propertyId,
      provider: this.provider || undefined,
      method: this.method || undefined,
      transactionType: this.transactionType || undefined,
      roomType: this.roomType || undefined,
    };
  }

  private errorMessageFrom(error: HttpErrorResponse): string {
    const body = error.error as { message?: string } | null;
    return body?.message || 'Không thể tải báo cáo doanh thu. Vui lòng thử lại.';
  }

  private monthStart(): string {
    const date = new Date();
    return this.inputDate(new Date(date.getFullYear(), date.getMonth(), 1));
  }

  private inputDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private unique(values: Array<string | undefined> | undefined): string[] {
    return [...new Set((values ?? []).filter(this.isPresent))].sort();
  }

  private isPresent(value: string | undefined): value is string {
    return Boolean(value);
  }
}
