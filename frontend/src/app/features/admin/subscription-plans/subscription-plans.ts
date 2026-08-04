import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';

import {
  AccountSubscription,
  SubscriptionPlan,
  SubscriptionService,
} from '../../../core/services/subscription.service';
import { FeedbackStateComponent } from '../../../shared/components/feedback-state/feedback-state.component';

@Component({
  selector: 'app-subscription-plans',
  standalone: true,
  imports: [CommonModule, ButtonModule, CardModule, ToastModule, TableModule, TagModule, FeedbackStateComponent],
  providers: [MessageService],
  templateUrl: './subscription-plans.html',
  styles: [`
    .plan-card { height: 100%; display: flex; flex-direction: column; }
    .plan-price { font-size: 2rem; font-weight: bold; margin: 1rem 0; color: var(--primary-color); }
    .plan-features { flex-grow: 1; }
    .feature-item { margin: 0.5rem 0; display: flex; align-items: center; }
    .feature-item i { margin-right: 0.5rem; color: var(--green-500); }
  `]
})
export class SubscriptionPlansComponent implements OnInit {
  private readonly subscriptionService = inject(SubscriptionService);
  private readonly messageService = inject(MessageService);
  private readonly cdr = inject(ChangeDetectorRef);

  plans: SubscriptionPlan[] = [];
  mySubscriptions: AccountSubscription[] = [];
  plansLoading = false;
  plansError = '';
  subscriptionsLoading = false;
  subscriptionsError = '';

  ngOnInit(): void {
    this.loadPlans();
    this.loadMySubscriptions();
  }

  loadPlans(): void {
    this.plansLoading = true;
    this.plansError = '';
    this.subscriptionService.getPlans().subscribe({
      next: data => {
        this.plans = data;
        this.plansLoading = false;
        this.cdr.markForCheck();
      },
      error: error => {
        this.plansLoading = false;
        this.plansError = error?.error?.message || 'Unable to load subscription plans.';
        this.messageService.add({ severity: 'error', summary: 'Error', detail: this.plansError });
        this.cdr.markForCheck();
      }
    });
  }

  loadMySubscriptions(): void {
    this.subscriptionsLoading = true;
    this.subscriptionsError = '';
    this.subscriptionService.getMySubscriptions().subscribe({
      next: data => {
        this.mySubscriptions = data;
        this.subscriptionsLoading = false;
        this.cdr.markForCheck();
      },
      error: error => {
        this.subscriptionsLoading = false;
        this.subscriptionsError = error?.error?.message || 'Unable to load current subscriptions.';
        this.cdr.markForCheck();
      }
    });
  }

  purchase(plan: SubscriptionPlan): void {
    this.messageService.add({
      severity: 'info',
      summary: 'Payment',
      detail: `Opening payment for ${plan.nameVi}...`,
    });
  }

  isCurrentPlan(plan: SubscriptionPlan): boolean {
    return this.mySubscriptions.some(subscription =>
      subscription.plan.id === plan.id && subscription.status === 'ACTIVE');
  }
}
