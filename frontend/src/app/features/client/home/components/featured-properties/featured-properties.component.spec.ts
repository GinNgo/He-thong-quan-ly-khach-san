import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';

import { FeaturedPropertiesComponent } from './featured-properties.component';

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
  });
});
