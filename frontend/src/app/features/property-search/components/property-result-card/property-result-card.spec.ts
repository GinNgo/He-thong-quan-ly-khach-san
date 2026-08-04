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
