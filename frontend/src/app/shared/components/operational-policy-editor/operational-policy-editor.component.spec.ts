import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { OperationalPolicyService } from '../../../core/services/operational-policy.service';
import { OperationalPolicyEditorComponent } from './operational-policy-editor.component';

describe('OperationalPolicyEditorComponent', () => {
  let fixture: ComponentFixture<OperationalPolicyEditorComponent>;
  const api = {
    list: vi.fn(() => of([])),
    create: vi.fn((hotelId, request) => of({ id: 1, hotelId, version: 1, status: 'DRAFT', ...request })),
    update: vi.fn(),
    publish: vi.fn(() => of({ id: 1 }))
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [OperationalPolicyEditorComponent],
      providers: [{ provide: OperationalPolicyService, useValue: api }]
    }).compileComponents();
    fixture = TestBed.createComponent(OperationalPolicyEditorComponent);
    fixture.componentRef.setInput('propertyId', 7);
    fixture.detectChanges();
  });

  it('creates a localized draft for the selected property', () => {
    const component = fixture.componentInstance;
    component.draft = {
      effectiveFrom: '2026-08-10T14:00', checkInVi: 'Sau 14:00', checkOutVi: 'Trước 12:00',
      cancellationVi: 'Liên hệ cơ sở', childPolicyVi: 'Theo sức chứa', petPolicyVi: 'Không thú cưng',
      smokingPolicyVi: 'Không hút thuốc', houseRulesVi: 'Giữ yên lặng'
    };

    component.save();

    expect(api.create).toHaveBeenCalledWith(7, expect.objectContaining({
      effectiveFrom: '2026-08-10T14:00:00', houseRulesVi: 'Giữ yên lặng'
    }));
  });

  it('blocks incomplete localized content before calling the API', () => {
    fixture.componentInstance.draft.houseRulesVi = '';
    fixture.componentInstance.save();
    expect(api.create).not.toHaveBeenCalled();
    expect(fixture.componentInstance.error).toContain('đầy đủ');
  });
});
