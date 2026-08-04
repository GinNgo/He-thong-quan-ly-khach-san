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
});
