import { ChangeDetectorRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NavigationEnd, Router } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { RoleService } from '@app/core/services/role.service';
import { PermissionService } from '@app/core/services/permission.service';
import { RoleManagementComponent } from './role-management.component';

describe('RoleManagementComponent', () => {
  const routerEvents = new Subject<NavigationEnd>();
  const roleService = {
    getRoles: vi.fn(() => of([])),
    createRole: vi.fn(() => of({ id: 20, code: 'NIGHT_AUDITOR', name: 'Night auditor', status: 'ACTIVE' })),
    updateRole: vi.fn(() => of({ id: 20, code: 'NIGHT_AUDITOR', name: 'Night auditor', status: 'ACTIVE' })),
    deleteRole: vi.fn(() => of(void 0)),
    reactivateRole: vi.fn(() => of({ id: 20, code: 'NIGHT_AUDITOR', name: 'Night auditor', status: 'ACTIVE' }))
  };
  const messages = { add: vi.fn() };
  const confirmation = { confirm: vi.fn((options: { accept?: () => void }) => options.accept?.()) };
  const router = { events: routerEvents.asObservable(), navigate: vi.fn() };
  const cdr = { detectChanges: vi.fn() };
  const permissions = { hasPermission: vi.fn(() => true) };

  let component: RoleManagementComponent;

  beforeEach(() => {
    vi.clearAllMocks();
    roleService.getRoles.mockReturnValue(of([]));
    roleService.createRole.mockReturnValue(of({ id: 20, code: 'NIGHT_AUDITOR', name: 'Night auditor', status: 'ACTIVE' }));
    roleService.updateRole.mockReturnValue(of({ id: 20, code: 'NIGHT_AUDITOR', name: 'Night auditor', status: 'ACTIVE' }));
    roleService.deleteRole.mockReturnValue(of(void 0));
    roleService.reactivateRole.mockReturnValue(of({ id: 20, code: 'NIGHT_AUDITOR', name: 'Night auditor', status: 'ACTIVE' }));

    TestBed.configureTestingModule({
      providers: [
        { provide: RoleService, useValue: roleService },
        { provide: PermissionService, useValue: permissions },
        { provide: MessageService, useValue: messages },
        { provide: ConfirmationService, useValue: confirmation },
        { provide: Router, useValue: router },
        { provide: ChangeDetectorRef, useValue: cdr }
      ]
    });
    component = TestBed.runInInjectionContext(() => new RoleManagementComponent());
  });

  afterEach(() => {
    component.ngOnDestroy();
    TestBed.resetTestingModule();
  });

  it('creates a normalized role without client-owned lifecycle fields', () => {
    component.roleDialogMode = 'create';
    component.displayDialog = true;
    component.roleForm = {
      id: 0,
      code: ' night_auditor ',
      name: ' Night auditor ',
      description: ' Night shift ',
      status: 'INACTIVE',
      systemRole: true
    };

    component.saveRole();

    expect(roleService.createRole).toHaveBeenCalledWith({
      code: 'NIGHT_AUDITOR',
      name: 'Night auditor',
      description: 'Night shift'
    });
    expect(component.displayDialog).toBe(false);
    expect(component.saving).toBe(false);
  });

  it('shows the assignment conflict returned by soft deactivation', () => {
    roleService.deleteRole.mockReturnValue(throwError(() => ({
      error: { message: 'Không thể ngừng sử dụng vai trò đang được gán cho người dùng.' }
    })));
    const role = { id: 20, code: 'NIGHT_AUDITOR', name: 'Night auditor', status: 'ACTIVE' as const, systemRole: false };

    component.deleteRole(role);

    expect(confirmation.confirm).toHaveBeenCalled();
    expect(roleService.deleteRole).toHaveBeenCalledWith(20);
    expect(messages.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'error',
      detail: 'Không thể ngừng sử dụng vai trò đang được gán cho người dùng.'
    }));
    expect(component.saving).toBe(false);
  });

  it('reactivates an inactive custom role and reloads the catalog', () => {
    const role = { id: 20, code: 'NIGHT_AUDITOR', name: 'Night auditor', status: 'INACTIVE' as const, systemRole: false };

    component.reactivateRole(role);

    expect(roleService.reactivateRole).toHaveBeenCalledWith(20);
    expect(roleService.getRoles).toHaveBeenCalled();
    expect(messages.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'success',
      detail: 'Đã kích hoạt lại vai trò.'
    }));
  });

  it('blocks lifecycle actions when the matching action permission is absent', () => {
    component.canDelete = false;
    component.canUpdate = false;
    const active = { id: 20, code: 'NIGHT_AUDITOR', name: 'Night auditor', status: 'ACTIVE' as const, systemRole: false };
    const inactive = { ...active, status: 'INACTIVE' as const };

    component.deleteRole(active);
    component.reactivateRole(inactive);

    expect(roleService.deleteRole).not.toHaveBeenCalled();
    expect(roleService.reactivateRole).not.toHaveBeenCalled();
    expect(messages.add).toHaveBeenCalledTimes(2);
  });
});
