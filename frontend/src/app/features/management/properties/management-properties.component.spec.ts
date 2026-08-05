import { TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';
import { ManagementApiService, ManagedProperty } from '../../../core/services/management-api.service';
import { PropertyService } from '../../../core/services/property.service';
import { ManagementPropertiesComponent } from './management-properties.component';

describe('ManagementPropertiesComponent', () => {
  it('renders a distinct profile surface and submits only editable fields', async () => {
    const properties$ = new Subject<ManagedProperty[]>();
    const update$ = new Subject<ManagedProperty>();
    const updateProperty = vi.fn((_id: number, _body: Record<string, unknown>) => update$);
    await TestBed.configureTestingModule({
      imports: [ManagementPropertiesComponent],
      providers: [
        { provide: ManagementApiService, useValue: { properties: () => properties$, updateProperty } },
        { provide: PropertyService, useValue: { getProvinces: () => of([{ id: 1, nameVi: 'Ha Noi' }]), getWards: () => of([{ id: 2, nameVi: 'Ba Dinh' }]) } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ManagementPropertiesComponent);
    fixture.detectChanges();
    const property: ManagedProperty = {
      id: 10, code: 'HN-10', nameVi: 'LuxeStay Ha Noi', propertyType: 'HOTEL', address: 'Old', addressLine: 'Old',
      provinceId: 1, wardId: 2, approvalStatus: 'APPROVED', operationStatus: 'ACTIVE', operational: true, isDemo: false,
    };
    properties$.next([property]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Hồ sơ cơ sở');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Đối soát số phòng');
    fixture.componentInstance.form.patchValue({ nameVi: 'LuxeStay Capital', address: 'New address' });
    fixture.componentInstance.save();
    const body = updateProperty.mock.calls[0][1] as Record<string, unknown>;
    const profile = body['profile'] as Record<string, unknown>;
    expect(profile['nameVi']).toBe('LuxeStay Capital');
    expect(body['reason']).toBe('Property profile update');
    expect(profile['approvalStatus']).toBeUndefined();
    expect(profile['operationStatus']).toBeUndefined();
    expect(profile['isDemo']).toBeUndefined();

    update$.next({ ...property, nameVi: 'LuxeStay Capital', address: 'New address', addressLine: 'New address' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Đã lưu hồ sơ cơ sở.');
  });
});
