import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ImageFallbackService } from '../../../../core/services/image-fallback.service';
import { PropertyResultCardComponent } from './property-result-card';

describe('PropertyResultCardComponent', () => {
  let fixture: ComponentFixture<PropertyResultCardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PropertyResultCardComponent],
      providers: [{ provide: ImageFallbackService, useValue: { property: () => '/fallback.webp', replace: vi.fn() } }]
    }).compileComponents();
    fixture = TestBed.createComponent(PropertyResultCardComponent);
  });

  it('renders localized amenities returned by canonical search', () => {
    fixture.componentRef.setInput('property', {
      id: 7, name: 'LuxeStay', addressLine: 'Đà Nẵng', starRating: 4,
      amenities: ['Wi-Fi miễn phí', 'Hồ bơi'], availableRoomCount: 2
    });
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent || '';
    expect(text).toContain('Wi-Fi miễn phí');
    expect(text).toContain('Hồ bơi');
  });
});
