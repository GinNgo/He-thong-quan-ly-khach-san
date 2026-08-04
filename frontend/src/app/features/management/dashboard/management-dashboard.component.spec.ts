import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject } from 'rxjs';
import { ManagementApiService, ManagementContext } from '../../../core/services/management-api.service';
import { ManagementDashboardComponent } from './management-dashboard.component';
import { PropertyGalleryService } from '../../../core/services/property-gallery.service';
import { AmenityService } from '../../../core/services/amenity.service';

const galleryApi = { list: vi.fn(() => of([])) };
const amenityApi = { publicCatalog: vi.fn(() => of([])), assignments: vi.fn(() => of([])), replaceAssignments: vi.fn(() => of([])) };

describe('ManagementDashboardComponent', () => {
  it('submits an owner-scoped profile edit with a reason', async () => {
    const context: ManagementContext = {
      properties: [{
        id: 3, code: 'OWNER-3', nameVi: 'Old name', propertyType: 'HOTEL', addressLine: 'Old address',
        provinceId: 1, wardId: 2,
        approvalStatus: 'APPROVED', operationStatus: 'ACTIVE', operational: true, isDemo: false
      }],
      activePropertyId: 3,
      activePropertyOperational: true,
      planCode: 'STANDARD', subscriptionStatus: 'ACTIVE', lifetime: false,
      limits: {}, usage: {}, upgradeRequired: false, dashboard: {}
    };
    const api = {
      context: vi.fn(() => of(context)),
      provinces: vi.fn(() => of([{ id: 1, nameVi: 'Đà Nẵng', locationType: 'PROVINCE' }])),
      wards: vi.fn(() => of([{ id: 2, nameVi: 'Hải Châu', locationType: 'WARD' }])),
      updateProperty: vi.fn(() => of({ id: 3 }))
    };
    await TestBed.configureTestingModule({
      imports: [ManagementDashboardComponent],
      providers: [
        provideRouter([]),
        { provide: ManagementApiService, useValue: api },
        { provide: PropertyGalleryService, useValue: galleryApi },
        { provide: AmenityService, useValue: amenityApi }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(ManagementDashboardComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.openProfileEditor();
    expect(api.provinces).toHaveBeenCalled();
    expect(api.wards).toHaveBeenCalledWith(1);
    component.profileDraft = {
      nameVi: 'New owner name',
      propertyType: 'HOTEL',
      addressLine: '3 Tenant Safe Street',
      provinceId: 1,
      wardId: 2,
      reason: 'Correct public profile'
    };
    component.saveProfile();

    expect(api.updateProperty).toHaveBeenCalledWith(3, {
      profile: expect.objectContaining({
        nameVi: 'New owner name',
        propertyType: 'HOTEL',
        addressLine: '3 Tenant Safe Street',
        provinceId: 1,
        wardId: 2
      }),
      reason: 'Correct public profile'
    });
    expect(component.profileEditing).toBe(false);
  });

  it('renders loaded context in zoneless mode', async () => {
    const context$ = new Subject<ManagementContext>();

    await TestBed.configureTestingModule({
      imports: [ManagementDashboardComponent],
      providers: [
        provideRouter([]),
        { provide: ManagementApiService, useValue: { context: () => context$ } },
        { provide: PropertyGalleryService, useValue: galleryApi },
        { provide: AmenityService, useValue: amenityApi },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ManagementDashboardComponent);
    fixture.detectChanges();

    context$.next({
      properties: [{ id: 1, code: 'HOTEL-1', nameVi: 'LuxeStay Hà Nội', propertyType: 'HOTEL', addressLine: 'Hà Nội', provinceId: 1, wardId: 2, approvalStatus: 'APPROVED', operationStatus: 'ACTIVE', isDemo: false }],
      activePropertyId: 1,
      planCode: 'STANDARD',
      subscriptionStatus: 'ACTIVE',
      subscriptionSource: 'PLATFORM',
      lifetime: false,
      limits: { MAX_ROOMS: 50, MAX_PROPERTIES: 1 },
      usage: { rooms: 9, properties: 1 },
      upgradeRequired: false,
      dashboard: { availableRooms: 6, occupiedRooms: 3 },
    });
    await fixture.whenStable();

    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent).not.toContain('Đang tải tổng quan...');
    expect(element.textContent).toContain('LuxeStay Hà Nội');
    expect(element.textContent).toContain('STANDARD');
    expect(element.textContent).toContain('Entitlement source: PLATFORM');
  });

  it('shows approval guidance instead of operational metrics for a pending property', async () => {
    const context$ = new Subject<ManagementContext>();
    await TestBed.configureTestingModule({
      imports: [ManagementDashboardComponent],
      providers: [
        provideRouter([]),
        { provide: ManagementApiService, useValue: { context: () => context$ } },
        { provide: PropertyGalleryService, useValue: galleryApi },
        { provide: AmenityService, useValue: amenityApi },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ManagementDashboardComponent);
    fixture.detectChanges();
    context$.next({
      properties: [{
        id: 2,
        code: 'PENDING-2',
        nameVi: 'Cơ sở mới',
        propertyType: 'HOTEL',
        addressLine: 'Huế',
        provinceId: 1,
        wardId: 2,
        approvalStatus: 'PENDING_APPROVAL',
        operationStatus: 'INACTIVE',
        operational: false,
        isDemo: false,
      }],
      activePropertyId: 2,
      activePropertyOperational: false,
      planCode: 'NO_PLAN',
      subscriptionStatus: 'NONE',
      subscriptionSource: 'NONE',
      lifetime: false,
      limits: {},
      usage: { properties: 1 },
      upgradeRequired: true,
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent || '';
    expect(text).toContain('Chưa thể vận hành');
    expect(text).toContain('PENDING_APPROVAL');
    expect(text).not.toContain('Phòng trống');
  });
});
