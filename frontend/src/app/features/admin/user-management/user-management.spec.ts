import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { RoleService } from '@app/core/services/role.service';
import { User, UserService } from '@app/core/services/user';
import { UserManagement } from './user-management';

describe('UserManagement staff lifecycle', () => {
  const staff: User = {
    id: 42,
    username: 'staff-42',
    email: 'staff42@example.com',
    fullName: 'Nguyen Staff',
    roles: [{ id: 3, code: 'RECEPTIONIST', name: 'Le tan' }],
    status: 'INACTIVE',
    createdAt: '2026-01-01T00:00:00',
    staffAssignments: [
      { id: 1, hotelId: 10, hotelName: 'LuxeStay Da Nang', status: 'ACTIVE' },
      {
        id: 2,
        hotelId: 11,
        hotelName: 'LuxeStay Hue',
        status: 'INACTIVE',
        statusReason: 'Previous contract ended',
      },
    ],
  };

  let userService: {
    getUsers: ReturnType<typeof vi.fn>;
    getStaff: ReturnType<typeof vi.fn>;
    getStaffProperties: ReturnType<typeof vi.fn>;
    getStaffRoles: ReturnType<typeof vi.fn>;
    createUser: ReturnType<typeof vi.fn>;
    createStaff: ReturnType<typeof vi.fn>;
    updateStaff: ReturnType<typeof vi.fn>;
    updateUser: ReturnType<typeof vi.fn>;
    deactivateStaff: ReturnType<typeof vi.fn>;
    reactivateStaff: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    userService = {
      getUsers: vi.fn(() => of([staff])),
      getStaff: vi.fn(() => of([staff])),
      getStaffProperties: vi.fn(() => of([
        { id: 10, name: 'LuxeStay Da Nang' },
        { id: 11, name: 'LuxeStay Hue' },
      ])),
      getStaffRoles: vi.fn(() => of([
        { id: 3, code: 'RECEPTIONIST', name: 'Le tan' },
      ])),
      createUser: vi.fn(() => of(staff)),
      createStaff: vi.fn(() => of(staff)),
      updateStaff: vi.fn(() => of(staff)),
      updateUser: vi.fn(() => of(staff)),
      deactivateStaff: vi.fn(() => of(staff)),
      reactivateStaff: vi.fn(() => of(staff)),
    };

    await TestBed.configureTestingModule({
      imports: [UserManagement],
      providers: [
        { provide: UserService, useValue: userService },
        { provide: RoleService, useValue: { getRoles: () => of([]) } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { data: { userType: 'STAFF' } }, data: of({ userType: 'STAFF' }) },
        },
      ],
    }).compileComponents();
  });

  it('renders active and historical staff assignments without a destructive delete action', async () => {
    const fixture = TestBed.createComponent(UserManagement);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent).toContain('LuxeStay Da Nang · Đang làm');
    expect(element.textContent).toContain('LuxeStay Hue · Đã nghỉ');
    expect(element.querySelector('[title="Ngừng quyền truy cập"]')).not.toBeNull();
    expect(element.querySelector('[title="Tuyển lại"]')).not.toBeNull();
    expect(element.querySelector('[title="Xóa"]')).toBeNull();
  });

  it('requires a reason and calls the rehire endpoint for the selected historical property', () => {
    const fixture = TestBed.createComponent(UserManagement);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.openLifecycle(staff, 'reactivate');
    expect(component.lifecycleHotelId).toBe(11);
    component.lifecycleReason = 'New seasonal contract';
    component.submitLifecycle();

    expect(userService.reactivateStaff).toHaveBeenCalledWith(42, {
      hotelId: 11,
      reason: 'New seasonal contract',
    });
  });

  it('loads the tenant-scoped staff and property options instead of public hotel search data', () => {
    const fixture = TestBed.createComponent(UserManagement);
    fixture.detectChanges();

    expect(userService.getStaff).toHaveBeenCalledTimes(1);
    expect(userService.getStaffProperties).toHaveBeenCalledTimes(1);
    expect(userService.getStaffRoles).toHaveBeenCalledTimes(1);
    expect(userService.getUsers).not.toHaveBeenCalled();
    expect(fixture.componentInstance.hotels.map(hotel => hotel.id)).toEqual([10, 11]);
  });

  it('does not submit a staff account with an invalid initial password', () => {
    const fixture = TestBed.createComponent(UserManagement);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.openNew();
    component.userForm = {
      ...component.userForm,
      username: 'new-staff',
      email: 'new-staff@example.test',
      password: 'short',
      fullName: 'New Staff',
      roleIds: [3],
      hotelId: 10,
    };
    component.saveUser();

    expect(userService.createStaff).not.toHaveBeenCalled();
    expect(component.saving).toBe(false);
  });

  it('submits staff creation through the dedicated validated endpoint', () => {
    const fixture = TestBed.createComponent(UserManagement);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.openNew();
    component.userForm = {
      ...component.userForm,
      username: '  new-staff  ',
      email: '  NEW-STAFF@example.test  ',
      password: 'StrongPass1',
      fullName: '  New Staff  ',
      phone: ' 0901000000 ',
      roleIds: [3],
      hotelId: 10,
    };
    component.saveUser();

    expect(userService.createStaff).toHaveBeenCalledWith({
      username: 'new-staff',
      email: 'NEW-STAFF@example.test',
      password: 'StrongPass1',
      fullName: 'New Staff',
      phone: '0901000000',
      roleIds: [3],
      hotelId: 10,
    });
    expect(userService.createUser).not.toHaveBeenCalled();
  });

  it('requires a reason before moving staff to another property', () => {
    const fixture = TestBed.createComponent(UserManagement);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.editUser(staff);
    component.userForm.hotelId = 11;
    component.userForm.assignmentReason = '';
    component.saveUser();

    expect(userService.updateStaff).not.toHaveBeenCalled();
  });

  it('submits a validated property move through the dedicated staff endpoint', () => {
    const fixture = TestBed.createComponent(UserManagement);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.editUser(staff);
    component.userForm.hotelId = 11;
    component.userForm.assignmentReason = 'Transfer to Hue property';
    component.userForm.password = '';
    component.saveUser();

    expect(userService.updateStaff).toHaveBeenCalledWith(42, {
      fullName: 'Nguyen Staff',
      phone: null,
      password: null,
      roleIds: [3],
      hotelId: 11,
      assignmentReason: 'Transfer to Hue property',
    });
    expect(userService.updateUser).not.toHaveBeenCalled();
  });
});
