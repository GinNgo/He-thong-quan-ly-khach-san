import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { LocationSuggestion } from '../../../../../core/services/client-api.service';
import { PopularDestinationsComponent } from './popular-destinations.component';

describe('PopularDestinationsComponent', () => {
  let fixture: ComponentFixture<PopularDestinationsComponent>;
  let component: PopularDestinationsComponent;

  const destination: LocationSuggestion = {
    type: 'PROVINCE',
    id: 79,
    name: 'Ho Chi Minh City',
    displayName: 'Ho Chi Minh City',
    propertyCount: 12,
    imageUrl: '/assets/destinations/hcm.webp',
    imageAltText: 'Skyline by the river',
    imageProvenance: 'BUNDLED_DESTINATION:79',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PopularDestinationsComponent],
      providers: [provideRouter([]), provideNoopAnimations()],
    }).compileComponents();

    fixture = TestBed.createComponent(PopularDestinationsComponent);
    component = fixture.componentInstance;
  });

  it('renders the API image source, alt text, provenance and destination id', () => {
    fixture.componentRef.setInput('destinations', [destination]);
    fixture.detectChanges();

    const card = fixture.nativeElement.querySelector('[data-destination-id="79"]') as HTMLElement;
    const image = card.querySelector('img') as HTMLImageElement;

    expect(image.getAttribute('src')).toBe(destination.imageUrl);
    expect(image.alt).toBe(destination.imageAltText);
    expect(image.dataset['imageProvenance']).toBe(destination.imageProvenance);
  });

  it('uses the bundled fallback once while retaining the API provenance', () => {
    fixture.componentRef.setInput('destinations', [destination]);
    fixture.detectChanges();
    const image = fixture.nativeElement.querySelector('img[data-image-provenance]') as HTMLImageElement;

    image.dispatchEvent(new Event('error'));
    fixture.detectChanges();
    const fallbackSource = image.src;

    expect(fallbackSource).toContain('/assets/fallbacks/destination-default.webp');
    expect(image.dataset['imageProvenance']).toBe(destination.imageProvenance);
    expect(image.dataset['imageFallback']).toBe('true');

    image.dispatchEvent(new Event('error'));
    expect(image.src).toBe(fallbackSource);
  });

  it('renders separate error and valid-empty states and emits retry from the error action', () => {
    const retry = vi.fn();
    component.retry.subscribe(retry);
    fixture.componentRef.setInput('error', true);
    fixture.detectChanges();

    const alert = fixture.nativeElement.querySelector('[role="alert"]') as HTMLElement;
    expect(alert.textContent).toContain('Không thể tải điểm đến');
    (alert.querySelector('button') as HTMLButtonElement).click();
    expect(retry).toHaveBeenCalledOnce();

    fixture.componentRef.setInput('error', false);
    fixture.detectChanges();
    const empty = fixture.nativeElement.querySelector('[role="status"]') as HTMLElement;
    expect(empty.textContent).toContain('Chưa có điểm đến phổ biến');
    expect(empty.querySelector('button')).toBeNull();
  });
});
