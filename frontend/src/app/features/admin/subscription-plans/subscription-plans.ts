import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import {
  PropertyAccountSubscription,
  PropertySubscriptionUsage,
  SubscriptionPlan,
  SubscriptionService,
} from '../../../core/services/subscription.service';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';

@Component({
  selector: 'app-subscription-plans',
  standalone: true,
  imports: [CommonModule, RouterLink, ButtonModule, CardModule, TagModule],
  templateUrl: './subscription-plans.html',
  styles: [`
    .plan-card{height:100%;display:flex;flex-direction:column}.plan-price{font-size:2rem;font-weight:bold;margin:1rem 0;color:var(--primary-color)}
    .plan-features{flex-grow:1}.feature-item{margin:.5rem 0;display:flex;align-items:center;gap:.5rem}.feature-item i{color:var(--green-500)}
    .state{margin:1rem auto;max-width:900px;padding:1rem;border-radius:.75rem;background:#fff}.error{color:#9f1239;background:#fff1f2}.source{font-size:.85rem;color:#475569}
  `]
})
export class SubscriptionPlansComponent implements OnInit {
  private readonly subscriptions = inject(SubscriptionService);
  private readonly route = inject(ActivatedRoute);

  plans: SubscriptionPlan[] = [];
  current: PropertyAccountSubscription | null = null;
  usage: PropertySubscriptionUsage | null = null;
  propertyId?: number;
  loadingCatalog = true;
  loadingProperty = false;
  catalogError = '';
  propertyError = '';

  ngOnInit(): void {
    const propertyId = Number(this.route.snapshot.queryParamMap.get('propertyId'));
    this.propertyId = Number.isInteger(propertyId) && propertyId > 0 ? propertyId : undefined;
    this.loadPlans();
    this.loadSelectedProperty();
  }

  loadPlans(): void {
    this.loadingCatalog = true;
    this.catalogError = '';
    this.subscriptions.getPlans().subscribe({
      next: plans => { this.plans = plans; this.loadingCatalog = false; },
      error: () => { this.plans = []; this.catalogError = 'Không thể tải catalog gói dịch vụ từ máy chủ.'; this.loadingCatalog = false; }
    });
  }

  loadSelectedProperty(): void {
    if (!this.propertyId) {
      this.current = null;
      this.usage = null;
      return;
    }
    this.loadingProperty = true;
    this.propertyError = '';
    forkJoin({
      current: this.subscriptions.getPropertySubscription(this.propertyId),
      usage: this.subscriptions.getPropertyUsage(this.propertyId),
    }).subscribe({
      next: result => { this.current = result.current; this.usage = result.usage; this.loadingProperty = false; },
      error: () => {
        this.current = null;
        this.usage = null;
        this.propertyError = 'Không thể tải subscription của cơ sở đã chọn. Kiểm tra quyền truy cập hoặc thử lại.';
        this.loadingProperty = false;
      }
    });
  }

  isCurrentPlan(plan: SubscriptionPlan): boolean {
    return Boolean(this.current?.platformAuthoritative && this.current.status === 'ACTIVE' && this.current.planId === plan.id);
  }

  featureLimit(limit: number | null): string {
    if (limit === -1) return 'Không giới hạn';
    return typeof limit === 'number' && Number.isInteger(limit) && limit >= 0 ? String(limit) : 'Không khả dụng';
  }
}
