import { SearchFilterSidebarComponent } from './search-filter-sidebar';

describe('SearchFilterSidebarComponent', () => {
  it('formats Vietnamese currency without US grouping', () => {
    const component = new SearchFilterSidebarComponent();
    expect(component.formatVnd(10000000)).toBe('10.000.000 ₫');
    expect(component.formatVnd(0)).toBe('0 ₫');
  });

  it('emits validated filters only when applied', () => {
    const component = new SearchFilterSidebarComponent();
    const emit = vi.spyOn(component.filtersChanged, 'emit');
    component.priceRange = [-100, 1500000];
    component.selectedPropertyTypes = ['hotel', 'HOTEL', 'INVALID'];
    component.selectedStars = [4, 5, 5, 0];
    component.selectedReviewScore = 8;
    component.applyFilters();
    expect(emit).toHaveBeenCalledWith(expect.objectContaining({
      minPrice: 0, maxPrice: 1500000, propertyTypes: ['HOTEL'], starRatings: [5, 4], minReviewScore: 8
    }));
  });

  it('canonicalizes duplicate route state while retaining a zero review threshold', () => {
    const component = new SearchFilterSidebarComponent();
    component.initialState = {
      propertyTypes: ['resort', 'HOTEL', 'RESORT', 'UNKNOWN'],
      starRatings: [3, 5, 3, 8],
      minReviewScore: 0,
    };

    component.ngOnChanges();

    expect(component.selectedPropertyTypes).toEqual(['HOTEL', 'RESORT']);
    expect(component.selectedStars).toEqual([5, 3]);
    expect(component.selectedReviewScore).toBe(0);
  });
});
