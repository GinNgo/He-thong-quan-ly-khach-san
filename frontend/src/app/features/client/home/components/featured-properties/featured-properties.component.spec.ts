<<<<<<< HEAD
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Hotel } from '../../../../../core/services/client-api.service';
import { FeaturedPropertiesComponent } from './featured-properties.component';

describe('FeaturedPropertiesComponent', () => {
  let fixture: ComponentFixture<FeaturedPropertiesComponent>;
  let component: FeaturedPropertiesComponent;

  const property: Hotel = {
    id: 42,
    name: 'Riverside Hotel',
    addressLine: '42 River Road',
    starRating: 4,
    latitude: 10.1,
    longitude: 106.1,
    propertyType: 'HOTEL',
    thumbnailUrl: '/media/properties/42-primary.webp',
    mainImageUrl: '/media/properties/42-catalog.webp',
    imageAltText: 'Riverside Hotel exterior',
    imageProvenance: 'PROPERTY_MEDIA',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FeaturedPropertiesComponent],
      providers: [provideRouter([]), provideNoopAnimations()],
    }).compileComponents();

    fixture = TestBed.createComponent(FeaturedPropertiesComponent);
    component = fixture.componentInstance;
  });

  it('renders the backend-selected image before legacy sources with API alt and provenance', () => {
    fixture.componentRef.setInput('properties', [property]);
    fixture.detectChanges();

    const card = fixture.nativeElement.querySelector('[data-property-id="42"]') as HTMLElement;
    const image = card.querySelector('img') as HTMLImageElement;

    expect(image.getAttribute('src')).toBe(property.thumbnailUrl);
    expect(image.alt).toBe(property.imageAltText);
    expect(image.dataset['imageProvenance']).toBe(property.imageProvenance);
  });

  it('uses the property-type fallback once while retaining the API provenance', () => {
    fixture.componentRef.setInput('properties', [property]);
    fixture.detectChanges();
    const image = fixture.nativeElement.querySelector('img[data-image-provenance]') as HTMLImageElement;

    image.dispatchEvent(new Event('error'));
    fixture.detectChanges();
    const fallbackSource = image.src;

    expect(fallbackSource).toContain('/assets/fallbacks/hotel-default.webp');
    expect(image.dataset['imageProvenance']).toBe(property.imageProvenance);
    expect(image.dataset['imageFallback']).toBe('true');

    image.dispatchEvent(new Event('error'));
    expect(image.src).toBe(fallbackSource);
  });

  it('uses the type fallback immediately when no API image is available', () => {
    fixture.componentRef.setInput('properties', [{
      ...property,
      thumbnailUrl: '   ',
      mainImageUrl: '\t',
      mainImage: '',
    }]);
    fixture.detectChanges();

    const image = fixture.nativeElement.querySelector('img[data-image-provenance]') as HTMLImageElement;
    expect(image.getAttribute('src')).toBe('/assets/fallbacks/hotel-default.webp');
    expect(image.dataset['imageProvenance']).toBe('FRONTEND_FALLBACK');
  });

  it('renders separate error and valid-empty states and emits retry from the error action', () => {
    const retry = vi.fn();
    component.retry.subscribe(retry);
    fixture.componentRef.setInput('error', true);
    fixture.detectChanges();

    const alert = fixture.nativeElement.querySelector('[role="alert"]') as HTMLElement;
    expect(alert.textContent).toContain('Không thể tải cơ sở nổi bật');
    (alert.querySelector('button') as HTMLButtonElement).click();
    expect(retry).toHaveBeenCalledOnce();

    fixture.componentRef.setInput('error', false);
    fixture.detectChanges();
    const empty = fixture.nativeElement.querySelector('[role="status"]') as HTMLElement;
    expect(empty.textContent).toContain('Chưa có cơ sở nổi bật');
    expect(empty.querySelector('button')).toBeNull();
=======
import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';

import { FeaturedPropertiesComponent } from './featured-properties.component';
import { HomeSearchStateService } from '../../services/home-search-state.service';

describe('FeaturedPropertiesComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FeaturedPropertiesComponent],
      providers: [provideRouter([])]
    }).compileComponents();
  });

  it('renders a recoverable empty state when no properties are available', () => {
    const fixture = TestBed.createComponent(FeaturedPropertiesComponent);
    fixture.componentInstance.loading = false;
    fixture.componentInstance.properties = [];
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.empty-state')?.textContent).toContain('Chưa có cơ sở phù hợp');
    expect(fixture.nativeElement.querySelector('.view-all')).toBeTruthy();
  });

  it('announces a property data error separately from an empty result', () => {
    const fixture = TestBed.createComponent(FeaturedPropertiesComponent);
    fixture.componentInstance.loading = false;
    fixture.componentInstance.error = true;
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.error-state')?.textContent).toContain('Chưa thể tải cơ sở nổi bật');
>>>>>>> codex/ui-functional-audit-polish
  });

  it('clears stale location and property type filters before opening all stays', () => {
    const fixture = TestBed.createComponent(FeaturedPropertiesComponent);
    const state = TestBed.inject(HomeSearchStateService);
    state.restoreLocation({ keyword: '', displayName: 'Tất cả chỗ nghỉ', selectedSuggestionType: 'PROVINCE', provinceId: 10133, wardId: null });
    state.updatePropertyTypes(['MOTEL']);
    fixture.componentInstance.viewAll();

    expect(state.state().provinceId).toBeNull();
    expect(state.state().propertyTypes).toEqual([]);
  });
});
