import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AmenityService } from '../../../core/services/amenity.service';
import { AmenityAssignmentComponent } from './amenity-assignment.component';

describe('AmenityAssignmentComponent', () => {
  let fixture: ComponentFixture<AmenityAssignmentComponent>;
  let component: AmenityAssignmentComponent;
  const wifi = { id: 1, code: 'WIFI', nameVi: 'Wi-Fi miễn phí', category: 'INTERNET', sortOrder: 1, status: 'ACTIVE' as const };
  const pool = { id: 2, code: 'POOL', nameVi: 'Hồ bơi', category: 'WELLNESS', sortOrder: 2, status: 'ACTIVE' as const };
  let api: any;

  beforeEach(async () => {
    api = {
      publicCatalog: vi.fn(() => of([wifi, pool])),
      managementCatalog: vi.fn(() => of([wifi, pool])),
      assignments: vi.fn(() => of([wifi])),
      replaceAssignments: vi.fn(() => of([wifi, pool])),
      createCatalogEntry: vi.fn(() => of(pool)),
      deactivateCatalogEntry: vi.fn(() => of({ ...pool, status: 'INACTIVE' }))
    };
    await TestBed.configureTestingModule({
      imports: [AmenityAssignmentComponent],
      providers: [{ provide: AmenityService, useValue: api }]
    }).compileComponents();
    fixture = TestBed.createComponent(AmenityAssignmentComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('scope', 'property');
    fixture.componentRef.setInput('entityId', 7);
    fixture.componentRef.setInput('editable', true);
    fixture.detectChanges();
  });

  it('loads localized catalog and saves exact selected ids', () => {
    expect(component.selectedIds).toEqual([1]);
    component.toggle(2, true);
    component.save();
    expect(api.replaceAssignments).toHaveBeenCalledWith('property', 7, [1, 2]);
    expect(component.success).toContain('Da luu');
  });

  it('allows system admins to create catalog entries', () => {
    fixture.componentRef.setInput('catalogEditable', true);
    fixture.detectChanges();
    component.draft = { code: 'SPA', nameVi: 'Spa', nameEn: 'Spa', category: 'WELLNESS', sortOrder: 9 };
    component.createCatalogEntry();
    expect(api.createCatalogEntry).toHaveBeenCalledWith(expect.objectContaining({ code: 'SPA', category: 'WELLNESS' }));
  });
});
