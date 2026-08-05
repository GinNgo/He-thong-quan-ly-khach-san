import { Injectable, inject } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot, RouterStateSnapshot, Router } from '@angular/router';
import { SubscriptionService } from '../services/subscription.service';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { MessageService } from 'primeng/api';

@Injectable({
  providedIn: 'root'
})
export class FeatureGuard implements CanActivate {
  private subscriptionService = inject(SubscriptionService);
  private router = inject(Router);
  private messageService = inject(MessageService);

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> | Promise<boolean> | boolean {
    const requiredFeature = route.data['requiredFeature'] as string;
    
    if (!requiredFeature) {
      return true; // No feature required
    }

    const propertyId = Number(route.queryParamMap.get('propertyId'));
    if (!Number.isInteger(propertyId) || propertyId <= 0) {
      this.messageService.add({ severity: 'error', summary: 'Chọn cơ sở', detail: 'Chọn một cơ sở trước khi mở chức năng theo gói.' });
      return false;
    }

    return this.subscriptionService.getPropertyFeatures(propertyId).pipe(
      map(features => {
        const limit = features?.[requiredFeature];
        if (limit === -1 || (typeof limit === 'number' && limit > 0)) {
          return true;
        }
        
        this.messageService.add({
          severity: 'error',
          summary: 'Nâng cấp dịch vụ',
          detail: `Bạn cần nâng cấp gói để sử dụng chức năng này (${requiredFeature})`
        });
        
        this.router.navigate(['/management/billing'], { queryParams: { propertyId } });
        return false;
      }),
      catchError(() => {
        this.messageService.add({ severity: 'error', summary: 'Không thể xác minh gói', detail: 'Quyền tính năng của cơ sở chưa thể xác minh. Vui lòng thử lại.' });
        return of(false);
      })
    );
  }
}
