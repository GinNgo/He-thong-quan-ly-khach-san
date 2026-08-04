import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, convertToParamMap } from '@angular/router';
import { MessageService } from 'primeng/api';
import { Observable, of, throwError } from 'rxjs';
import { SubscriptionService } from '../services/subscription.service';
import { FeatureGuard } from './feature.guard';

describe('FeatureGuard', () => {
  const api = { getPropertyFeatures: vi.fn() };
  const router = { navigate: vi.fn() };
  const messages = { add: vi.fn() };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({ providers: [
      FeatureGuard,
      { provide: SubscriptionService, useValue: api },
      { provide: Router, useValue: router },
      { provide: MessageService, useValue: messages },
    ] });
  });

  it('fails closed when no property is selected', () => {
    const result = TestBed.inject(FeatureGuard).canActivate(route(undefined), {} as RouterStateSnapshot);
    expect(result).toBe(false);
    expect(api.getPropertyFeatures).not.toHaveBeenCalled();
  });

  it('checks the selected property feature map and routes upgrades with property scope', () => {
    api.getPropertyFeatures.mockReturnValue(of({ MAX_ROOMS: 50 }));
    (TestBed.inject(FeatureGuard).canActivate(route(17, 'AI_CHAT'), {} as RouterStateSnapshot) as Observable<boolean>)
      .subscribe(result => expect(result).toBe(false));
    expect(api.getPropertyFeatures).toHaveBeenCalledWith(17);
    expect(router.navigate).toHaveBeenCalledWith(['/management/billing'], { queryParams: { propertyId: 17 } });
  });

  it.each([0, -2, null, undefined])('fails closed for a non-authorizing feature limit: %s', limit => {
    api.getPropertyFeatures.mockReturnValue(of({ AI_CHAT: limit }));
    (TestBed.inject(FeatureGuard).canActivate(route(17, 'AI_CHAT'), {} as RouterStateSnapshot) as Observable<boolean>)
      .subscribe(result => expect(result).toBe(false));
    expect(router.navigate).toHaveBeenCalledWith(['/management/billing'], { queryParams: { propertyId: 17 } });
  });

  it('does not redirect unavailable entitlement reads to login', () => {
    api.getPropertyFeatures.mockReturnValue(throwError(() => new Error('unavailable')));
    (TestBed.inject(FeatureGuard).canActivate(route(17), {} as RouterStateSnapshot) as Observable<boolean>)
      .subscribe(result => expect(result).toBe(false));
    expect(router.navigate).not.toHaveBeenCalledWith(['/auth/login']);
    expect(messages.add).toHaveBeenCalledWith(expect.objectContaining({ summary: 'Không thể xác minh gói' }));
  });
});

function route(propertyId?: number, feature = 'MAX_ROOMS'): ActivatedRouteSnapshot {
  return { data: { requiredFeature: feature }, queryParamMap: convertToParamMap(propertyId ? { propertyId } : {}) } as unknown as ActivatedRouteSnapshot;
}
