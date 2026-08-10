import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { AuthService } from '../../../core/services/auth';
import { AccountSubscription, SubscriptionPlan, SubscriptionService } from '../../../core/services/subscription.service';
import { finalize, timeout } from 'rxjs';

@Component({
  selector: 'app-subscription-plans',
  standalone: true,
  imports: [CommonModule, ButtonModule, CardModule, ToastModule, TableModule, TagModule],
  providers: [MessageService],
  templateUrl: './subscription-plans.html',
  styles: [`
    .plan-card { height: 100%; display: flex; flex-direction: column; }
    .plan-price { margin: 1rem 0; color: var(--hotel-primary); font-size: 2rem; font-weight: 700; }
    .plan-features { flex-grow: 1; text-align: left; }
    .feature-item { display: flex; align-items: center; gap: .5rem; margin: .5rem 0; }
    .feature-item i { color: var(--hotel-success); }
  `]
})
export class SubscriptionPlansComponent implements OnInit {
  private subscriptionService = inject(SubscriptionService);
  private messageService = inject(MessageService);
  private authService = inject(AuthService);
  private cdr = inject(ChangeDetectorRef);
  private destroyRef = inject(DestroyRef);

  plans: SubscriptionPlan[] = [];
  mySubscriptions: AccountSubscription[] = [];
  loading = true;
  errorMessage = '';

  get isSystemAdministrator(): boolean {
    return this.authService.getRoles().some(role => role === 'SUPER_ADMIN' || role === 'ADMIN');
  }

  ngOnInit(): void {
    this.loadPlans();
    if (!this.isSystemAdministrator) this.loadMySubscriptions();
  }

  loadPlans(): void {
    this.loading = true;
    this.errorMessage = '';
    const request = this.isSystemAdministrator
      ? this.subscriptionService.getAdminPlans()
      : this.subscriptionService.getPlans();
    request.pipe(
      timeout(15_000),
      finalize(() => {
        this.loading = false;
        this.cdr.detectChanges();
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: data => {
        this.plans = data.map(plan => ({ ...plan, features: plan.features ?? [] }));
      },
      error: () => {
        this.errorMessage = 'Không thể tải danh sách gói dịch vụ.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail: this.errorMessage });
      }
    });
  }

  loadMySubscriptions(): void {
    this.subscriptionService.getMySubscriptions()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: data => { this.mySubscriptions = data; this.cdr.detectChanges(); },
        error: () => { this.mySubscriptions = []; this.cdr.detectChanges(); }
      });
  }

  isCurrentPlan(plan: SubscriptionPlan): boolean {
    return this.mySubscriptions.some(sub => sub.plan.id === plan.id && sub.status === 'ACTIVE');
  }

  billingLabel(plan: SubscriptionPlan): string {
    if (plan.isLifetime || plan.billingType === 'ONCE') return 'Vĩnh viễn';
    return plan.billingType === 'YEARLY' ? 'Năm' : 'Tháng';
  }
}
