<<<<<<< HEAD
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PropertyResultCardComponent } from './property-result-card';

describe('PropertyResultCardComponent image fallback', () => {
  let fixture: ComponentFixture<PropertyResultCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [PropertyResultCardComponent] }).compileComponents();
    fixture = TestBed.createComponent(PropertyResultCardComponent);
  });

  it('treats blank API image values as missing', () => {
    fixture.componentRef.setInput('property', {
      id: 91,
      name: 'Blank Image Hotel',
      propertyType: 'HOTEL',
      thumbnailUrl: '   ',
      mainImageUrl: '\t',
      mainImage: '',
      addressLine: '91 Test Street',
    });
    fixture.detectChanges();

    const image = fixture.nativeElement.querySelector('img') as HTMLImageElement;
    expect(image.getAttribute('src')).toBe('/assets/fallbacks/hotel-default.webp');
    expect(image.alt).toBe('Blank Image Hotel');
  });

  it('renders a reviewed zero score instead of treating it as unrated', () => {
    fixture.componentRef.setInput('property', property({ reviewScore: 0, reviewCount: 3 }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.review b')?.textContent.trim()).toBe('0.0');
    expect(fixture.nativeElement.querySelector('.unrated')).toBeNull();
  });

  it.each([
    [{ reviewScore: null, reviewCount: 3 }, 'null score'],
    [{ reviewScore: 8, reviewCount: 0 }, 'zero review count'],
  ])('renders the unrated state for %s', (override, _label) => {
    fixture.componentRef.setInput('property', property(override));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.review')).toBeNull();
    expect(fixture.nativeElement.querySelector('.unrated')).not.toBeNull();
  });

  it('exposes the authoritative displayed nightly price for range assertions', () => {
    fixture.componentRef.setInput('property', property({
      pricing: {
        nightlyPrice: 500000,
        discountedPrice: 500000,
        numberOfNights: 2,
        taxAmount: 150000,
        feeAmount: 0,
        totalAmount: 1150000,
=======
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { of, Subject } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LocaleService } from '../../../../core/i18n/locale.service';
import { Hotel } from '../../../../core/services/client-api.service';
import { PropertyResultCardComponent } from './property-result-card';
import { AuthService } from '../../../../core/services/auth';
import { FavoriteService } from '../../../../core/services/favorite.service';

describe('PropertyResultCardComponent', () => {
  let fixture: ComponentFixture<PropertyResultCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PropertyResultCardComponent],
      providers: [
        { provide: LocaleService, useValue: { locale: signal<'vi' | 'en'>('en') } },
        provideRouter([]),
        { provide: AuthService, useValue: { logout$: new Subject<void>(), isLoggedIn: () => false } },
        { provide: FavoriteService, useValue: { favorites: signal([]), ensureLoaded: () => of([]), isFavorite: () => false, add: vi.fn(), remove: vi.fn() } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PropertyResultCardComponent);
  });

  it('renders canonical original/final pricing and typed sponsored disclosure', () => {
    fixture.componentRef.setInput('property', hotel({
      sponsoredPlacement: {
        placementId: 77,
        placementKind: 'SPONSORED',
        disclosureVi: '\u0110\u01b0\u1ee3c t\u00e0i tr\u1ee3',
        disclosureEn: 'Sponsored',
        endsAt: '2026-08-04T00:00:00Z',
      },
    }));
    fixture.detectChanges();

    const disclosure = fixture.nativeElement.querySelector('[data-sponsored="true"]') as HTMLElement;
    expect(disclosure).toBeTruthy();
    expect(disclosure.textContent).toContain('Sponsored');
    expect(fixture.nativeElement.querySelector('.nightly-price-label del')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.nightly-price-label strong').textContent).toContain('450,000');
    expect(fixture.nativeElement.querySelector('.promotion-proof').textContent).toContain('Gold');
  });

  it('does not render sponsored or discount markup without backend disclosure and quote data', () => {
    fixture.componentRef.setInput('property', hotel({
      sponsoredPlacement: undefined,
      quote: undefined,
      pricing: {
        nightlyPrice: 500000,
        discountedNightlyPrice: 500000,
        discountedPrice: 500000,
        numberOfNights: 1,
        roomQuantity: 1,
        subtotal: 500000,
        taxAmount: 60000,
        feeAmount: 15000,
        totalAmount: 575000,
>>>>>>> codex/ui-functional-audit-polish
        currency: 'VND',
      },
    }));
    fixture.detectChanges();

<<<<<<< HEAD
    const nightlyPrice = fixture.nativeElement.querySelector('[data-nightly-price]') as HTMLElement;
    expect(nightlyPrice.getAttribute('data-price-value')).toBe('500000');
    expect(nightlyPrice.textContent).toContain('500.000');
    expect(fixture.nativeElement.querySelector('[data-pricing-unavailable]')).toBeNull();
  });

  it('renders unavailable without a synthetic zero nightly price when pricing is null', () => {
    fixture.componentRef.setInput('property', property({ pricing: undefined }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-nightly-price]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-pricing-unavailable]')).not.toBeNull();
  });

  it.each([
    [3, '[data-availability-count]', '3', 'positive authoritative count'],
    [0, '[data-availability-unavailable]', null, 'sold out'],
    [undefined, '[data-availability-missing]', null, 'missing projection'],
  ])('renders availability state for %s (%s)', (availableRoomCount, selector, rawValue, _label) => {
    fixture.componentRef.setInput('property', property({ availableRoomCount }));
    fixture.detectChanges();

    const state = fixture.nativeElement.querySelector(selector) as HTMLElement;
    expect(state).not.toBeNull();
    if (rawValue !== null) expect(state.getAttribute('data-availability-value')).toBe(rawValue);
  });
});

function property(override: Record<string, unknown> = {}) {
  return {
    id: 91,
    name: 'Review Contract Hotel',
    addressLine: '91 Test Street',
    starRating: 4,
    latitude: 10,
    longitude: 106,
    ...override,
  };
}
=======
    expect(fixture.nativeElement.querySelector('[data-sponsored]')).toBeNull();
    expect(fixture.nativeElement.querySelector('.nightly-price-label del')).toBeNull();
    expect(fixture.nativeElement.querySelector('.promotion-proof')).toBeNull();
  });

  function hotel(overrides: Partial<Hotel> = {}): Hotel {
    return {
      id: 501,
      name: 'LuxeStay Riverside',
      addressLine: '1 River Road',
      starRating: 4,
      latitude: 10.7,
      longitude: 106.7,
      propertyType: 'HOTEL',
      provinceName: 'Ho Chi Minh City',
      reviewScore: 8.8,
      reviewCount: 30,
      availableRoomCount: 2,
      lowestRoomType: { id: 901, name: 'Deluxe', maxGuests: 2 },
      pricing: {
        nightlyPrice: 500000,
        discountedNightlyPrice: 450000,
        discountedPrice: 450000,
        numberOfNights: 1,
        roomQuantity: 1,
        subtotal: 500000,
        taxAmount: 60000,
        feeAmount: 15000,
        totalAmount: 525000,
        currency: 'VND',
      },
      quote: {
        quoteId: 'quote-501',
        expiresAt: '2026-08-04T00:00:00Z',
        propertyId: 501,
        roomTypeId: 901,
        nightlyPrice: 500000,
        numberOfNights: 1,
        roomQuantity: 1,
        baseSubtotal: 500000,
        taxAmount: 60000,
        feeAmount: 15000,
        taxesAndFees: 75000,
        appliedPromotions: [{
          campaignId: 71,
          code: 'MEMBER10',
          applicationType: 'AUTOMATIC',
          nameVi: 'Gi\u00e1 th\u00e0nh vi\u00ean',
          nameEn: 'Member price',
          discountAmount: 50000,
        }],
        memberBenefit: {
          eligible: true,
          tierCode: 'GOLD',
          tierNameVi: 'V\u00e0ng',
          tierNameEn: 'Gold',
        },
        totalDiscount: 50000,
        finalTotal: 525000,
        currency: 'VND',
      },
      ...overrides,
    };
  }
});
>>>>>>> codex/ui-functional-audit-polish
