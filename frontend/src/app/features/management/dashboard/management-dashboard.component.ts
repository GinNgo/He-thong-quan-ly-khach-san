import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ManagedProperty, ManagementApiService, ManagementContext } from '../../../core/services/management-api.service';
import { ManagementPropertyContextService } from '../../../core/services/management-property-context.service';
import { FeedbackStateComponent } from '../../../shared/components/feedback-state/feedback-state.component';

@Component({
  selector: 'app-management-dashboard', standalone: true, imports: [CommonModule, RouterLink, FeedbackStateComponent],
  templateUrl: './management-dashboard.component.html', styleUrl: './management-dashboard.component.css'
})
export class ManagementDashboardComponent implements OnInit {
  private api = inject(ManagementApiService);
  private cdr = inject(ChangeDetectorRef);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private propertyContext = inject(ManagementPropertyContextService);
  context?: ManagementContext;
  selectedPropertyId?: number;
  loading = true;
  error = '';
  private requestSequence = 0;

  ngOnInit(): void {
    this.propertyContext.propertyId$.subscribe(propertyId => this.load(propertyId));
    const propertyId = Number(this.route.snapshot.queryParamMap.get('propertyId'));
    if (Number.isInteger(propertyId) && propertyId > 0) this.propertyContext.select(propertyId);
  }
  load(propertyId?: number): void {
    const requestSequence = ++this.requestSequence;
    this.loading = true;
    this.error = '';
    this.api.context(propertyId).subscribe({
      next: context => { if (requestSequence !== this.requestSequence) return; this.context = context; this.selectedPropertyId = context.activePropertyId; this.loading = false; this.cdr.markForCheck(); },
      error: error => { if (requestSequence !== this.requestSequence) return; this.error = error?.error?.message || 'Không thể tải tổng quan.'; this.loading = false; this.cdr.markForCheck(); }
    });
  }
  selectProperty(): void {
    if (!this.selectedPropertyId) return;
    this.propertyContext.select(this.selectedPropertyId);
    void this.router.navigate([], { queryParams: { propertyId: this.selectedPropertyId }, queryParamsHandling: 'merge' });
  }
  get activeProperty(): ManagedProperty | undefined { return this.context?.properties.find(property => property.id === this.selectedPropertyId); }
  get activePropertyOperational(): boolean { return this.context?.activePropertyOperational ?? this.activeProperty?.operational ?? false; }
  value(name: keyof NonNullable<ManagementContext['dashboard']>): number { const value = this.context?.dashboard?.[name]; return typeof value === 'number' ? value : 0; }
  limit(name: string): string { const value = this.context?.limits?.[name]; return value === -1 ? 'Không giới hạn' : String(value ?? 0); }
  generatedAtLabel(): string { return this.context?.generatedAt ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'medium' }).format(new Date(this.context.generatedAt)) : 'Không có dữ liệu'; }
}
