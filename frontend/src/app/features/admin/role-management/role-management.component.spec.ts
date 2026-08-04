import { ChangeDetectorRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NavigationEnd, Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { RoleService } from '@app/core/services/role.service';
import { PermissionService } from '@app/core/services/permission.service';
import { RoleManagementComponent } from './role-management.component';

describe('RoleManagementComponent', () => {
  const routerEvents = new Subject<NavigationEnd>();
  const roleService = {
    getRoles: vi.fn(() => of([])),
    createRole: vi.fn(() => of({ id: 20, version: 0, code: 'NIGHT_AUDITOR', name: 'Night auditor', status: 'ACTIVE' })),
    updateRole: vi.fn(() => of({ id: 20, version: 1, code: 'NIGHT_AUDITOR', name: 'Night auditor', status: 'ACTIVE' })),
    deleteRole: vi.fn(() => of(void 0)),
    reactivateRole: vi.fn(() => of({ id: 20, version: 2, code: 'NIGHT_AUDITOR', name: 'Night auditor', status: 'ACTIVE' }))
  };
  const messages = { add: vi.fn() };
  const router = { events: routerEvents.asObservable(), navigate: vi.fn(), url: '/admin/roles' };
  const cdr = { detectChanges: vi.fn() };
  const permissions = { hasPermission: vi.fn(() => true) };

  let component: RoleManagementComponent;

  beforeEach(() => {
    vi.clearAllMocks();
    roleService.getRoles.mockReturnValue(of([]));
    roleService.createRole.mockReturnValue(of({ id: 20, version: 0, code: 'NIGHT_AUDITOR', name: 'Night auditor', status: 'ACTIVE' }));
    roleService.updateRole.mockReturnValue(of({ id: 20, version: 1, code: 'NIGHT_AUDITOR', name: 'Night auditor', status: 'ACTIVE' }));
    roleService.deleteRole.mockReturnValue(of(void 0));
    roleService.reactivateRole.mockReturnValue(of({ id: 20, version: 2, code: 'NIGHT_AUDITOR', name: 'Night auditor', status: 'ACTIVE' }));

    TestBed.configureTestingModule({
      providers: [
        { provide: RoleService, useValue: roleService },
        { provide: PermissionService, useValue: permissions },
        { provide: MessageService, useValue: messages },
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
      version: 0,
      code: ' night_auditor ',
      name: ' Night auditor ',
      description: ' Night shift ',
      status: 'INACTIVE',
      systemRole: true
    };
    component.changeReason = 'Night coverage';

    component.saveRole();

    expect(roleService.createRole).toHaveBeenCalledWith({
      code: 'NIGHT_AUDITOR',
      name: 'Night auditor',
      description: 'Night shift',
      reason: 'Night coverage'
    });
    expect(component.displayDialog).toBe(false);
    expect(component.saving).toBe(false);
  });

  it('submits versioned role updates with an explicit audit reason', () => {
    const role = {
      id: 20,
      version: 4,
      code: 'NIGHT_AUDITOR',
      name: 'Night auditor',
      description: 'Night shift',
      status: 'ACTIVE' as const,
      systemRole: false,
    };
    component.editRole(role);
    component.roleForm.name = 'Night operations';
    component.changeReason = 'Approved catalog update';

    component.saveRole();

    expect(roleService.updateRole).toHaveBeenCalledWith(20, {
      code: 'NIGHT_AUDITOR',
      name: 'Night operations',
      description: 'Night shift',
      expectedVersion: 4,
      reason: 'Approved catalog update',
    });
  });

  it('shows the assignment conflict returned by soft deactivation', () => {
    roleService.deleteRole.mockReturnValue(throwError(() => ({
      error: { message: 'Không thể ngừng sử dụng vai trò đang được gán cho người dùng.' }
    })));
    const role = { id: 20, version: 3, code: 'NIGHT_AUDITOR', name: 'Night auditor', status: 'ACTIVE' as const, systemRole: false };

    component.deleteRole(role);
    component.lifecycleReason = 'Role retired';
    component.submitLifecycle();

    expect(roleService.deleteRole).toHaveBeenCalledWith(20, { expectedVersion: 3, reason: 'Role retired' });
    expect(messages.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'error',
      detail: 'Không thể ngừng sử dụng vai trò đang được gán cho người dùng.'
    }));
    expect(component.saving).toBe(false);
  });

  it('reactivates an inactive custom role and reloads the catalog', () => {
    const role = { id: 20, version: 4, code: 'NIGHT_AUDITOR', name: 'Night auditor', status: 'INACTIVE' as const, systemRole: false };

    component.reactivateRole(role);
    component.lifecycleReason = 'Role restored';
    component.submitLifecycle();

    expect(roleService.reactivateRole).toHaveBeenCalledWith(20, { expectedVersion: 4, reason: 'Role restored' });
    expect(roleService.getRoles).toHaveBeenCalled();
    expect(messages.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'success',
      detail: 'Đã kích hoạt lại vai trò.'
    }));
  });

  it('blocks lifecycle actions when the matching action permission is absent', () => {
    component.canDelete = false;
    component.canUpdate = false;
    const active = { id: 20, version: 3, code: 'NIGHT_AUDITOR', name: 'Night auditor', status: 'ACTIVE' as const, systemRole: false };
    const inactive = { ...active, status: 'INACTIVE' as const };

    component.deleteRole(active);
    component.reactivateRole(inactive);

    expect(roleService.deleteRole).not.toHaveBeenCalled();
    expect(roleService.reactivateRole).not.toHaveBeenCalled();
    expect(messages.add).toHaveBeenCalledTimes(2);
  });

  it('blocks seeded-role edit and lifecycle actions even when flags are stale', () => {
    const staleSeed = {
      id: 7,
      version: 2,
      code: 'RECEPTIONIST',
      name: 'Receptionist',
      status: 'INACTIVE' as const,
      systemRole: false,
      roleType: 'CUSTOM' as const
    };

    component.editRole(staleSeed);
    component.deleteRole({ ...staleSeed, status: 'ACTIVE' });
    component.reactivateRole(staleSeed);

    expect(component.displayDialog).toBe(false);
    expect(roleService.updateRole).not.toHaveBeenCalled();
    expect(roleService.deleteRole).not.toHaveBeenCalled();
    expect(roleService.reactivateRole).not.toHaveBeenCalled();
    expect(messages.add).toHaveBeenCalledWith(expect.objectContaining({
      severity: 'warn',
      summary: 'Vai trò hệ thống'
    }));
  });

  it('opens targeted role audit history', () => {
    const role = { id: 20, version: 4, code: 'NIGHT_AUDITOR', name: 'Night auditor', status: 'ACTIVE' as const };

    component.openHistory(role);

    expect(router.navigate).toHaveBeenCalledWith(['/admin/audit-log'], {
      queryParams: { domain: 'ROLE', aggregateType: 'ROLE', aggregateId: 20 },
    });
  });
});
