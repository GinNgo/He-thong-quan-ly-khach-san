import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SubscriptionService, SubscriptionPlan, AccountSubscription } from '../../../core/services/subscription.service';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { ToastModule } from 'primeng/toast';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';

@Component({
  selector: 'app-subscription-plans',
  standalone: true,
  imports: [CommonModule, ButtonModule, CardModule, ToastModule, TableModule, TagModule],
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
  private subscriptionService = inject(SubscriptionService);
  private messageService = inject(MessageService);

  plans: SubscriptionPlan[] = [];
  mySubscriptions: AccountSubscription[] = [];

  ngOnInit() {
    this.loadPlans();
    this.loadMySubscriptions();
  }

  loadPlans() {
    this.subscriptionService.getPlans().subscribe({
      next: (data) => this.plans = data,
      error: (err) => this.messageService.add({ severity: 'error', summary: 'Lỗi', detail: 'Không thể tải danh sách gói.' })
    });
  }

  loadMySubscriptions() {
    this.subscriptionService.getMySubscriptions().subscribe({
      next: (data) => this.mySubscriptions = data,
      error: (err) => console.error(err)
    });
  }

  purchase(plan: SubscriptionPlan) {
    // Implement purchase logic via Payment API
    this.messageService.add({ severity: 'info', summary: 'Chức năng', detail: `Đang chuyển hướng thanh toán cho gói ${plan.nameVi}...` });
  }

  isCurrentPlan(plan: SubscriptionPlan): boolean {
    return this.mySubscriptions.some(sub => sub.plan.id === plan.id && sub.status === 'ACTIVE');
  }
}
