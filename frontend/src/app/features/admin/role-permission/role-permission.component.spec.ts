import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { AppFunction, RoleService } from '../../../core/services/role.service';
import { RolePermissionComponent } from './role-permission.component';

describe('RolePermissionComponent', () => {
  let component: RolePermissionComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RolePermissionComponent],
      providers: [
        {
          provide: RoleService,
          useValue: {
            getRoles: () => of([]),
            getRolePermissionsTree: () => of([]),
            updateRolePermissions: () => of(1)
          }
        },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: { get: () => null } } }
        }
      ]
    });
    component = TestBed.createComponent(RolePermissionComponent).componentInstance;
  });

  it('adds VIEW when a dependent action is enabled', () => {
    const func = functionFixture(0, 127);

    component.togglePermission(func, 64, true);

    expect(func.actionMask).toBe(65);
  });

  it('clears dependent actions when VIEW is disabled', () => {
    const func = functionFixture(71, 127);

    component.togglePermission(func, 1, false);

    expect(func.actionMask).toBe(0);
  });

  it('does not grant an unsupported action', () => {
    const func = functionFixture(1, 1);

    component.togglePermission(func, 64, true);

    expect(func.actionMask).toBe(1);
  });

  function functionFixture(actionMask: number, supportedActionMask: number): AppFunction {
    return {
      id: 1,
      moduleId: 1,
      code: 'RESERVATION',
      name: 'Đặt phòng',
      actionMask,
      supportedActionMask,
      active: true
    };
  }
});
