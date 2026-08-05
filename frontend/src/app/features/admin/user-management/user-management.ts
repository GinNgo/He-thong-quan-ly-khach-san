import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { SharedModule } from '@app/shared/shared.module';
import { PropertyOption, StaffAssignment, StaffCreateRequest, StaffUpdateRequest, UserService, User } from '@app/core/services/user';
import { RoleService, Role } from '@app/core/services/role.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { finalize, timeout } from 'rxjs/operators';
import { ActionCode, FunctionCode, PermissionService } from '@app/core/services/permission.service';

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [SharedModule],
  providers: [MessageService],
  templateUrl: './user-management.html',
  styleUrl: './user-management.css',
})
export class UserManagement implements OnInit {
  users: User[] = [];
  roles: Role[] = [];
  hotels: PropertyOption[] = [];
  loading = true;
  saving = false;
  errorMessage = '';
  userType: 'STAFF' | 'CUSTOMER' = 'STAFF';

  displayDialog = false;
  userDialogMode: 'create' | 'edit' = 'create';
  userForm: any = this.createEmptyForm();
  lifecycleDialogVisible = false;
  lifecycleMode: 'deactivate' | 'reactivate' = 'deactivate';
  lifecycleUser: User | null = null;
  lifecycleHotelId: number | null = null;
  lifecycleReason = '';
  canViewAudit = false;

  private userService = inject(UserService);
  private roleService = inject(RoleService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private permissionService = inject(PermissionService);
  private messageService = inject(MessageService);
  private cdr = inject(ChangeDetectorRef);

  ngOnInit(): void {
    this.canViewAudit = this.permissionService.hasPermission(FunctionCode.AUDIT_LOG, ActionCode.VIEW);
    this.userType = this.route.snapshot.data['userType'] || 'STAFF';
    this.loadUsers();

    this.route.data.subscribe(data => {
      const nextUserType = data['userType'] || 'STAFF';
      if (nextUserType !== this.userType) {
        this.userType = nextUserType;
        this.loadUsers();
      }
    });

    this.loadRoles();
    if (this.userType === 'STAFF') {
      this.loadHotels();
    }
  }

  loadUsers(): void {
    this.loading = true;
    this.errorMessage = '';
    this.users = [];

    const request = this.userType === 'STAFF'
      ? this.userService.getStaff()
      : this.userService.getUsers();
    request.pipe(
      timeout(10000),
      finalize(() => {
        this.loading = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: (data) => {
        this.users = this.userType === 'CUSTOMER'
          ? data.filter(u => u.roles && u.roles.some((r: any) => r.code === 'CUSTOMER'))
          : data;
      },
      error: (error) => {
        this.errorMessage = error?.error?.message || 'Không thể tải danh sách người dùng.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail: this.errorMessage });
      }
    });
  }

  loadRoles(): void {
    const request = this.userType === 'STAFF'
      ? this.userService.getStaffRoles()
      : this.roleService.getRoles();
    request.pipe(timeout(10000)).subscribe({
      next: (data) => {
        this.roles = data;
      },
      error: (error) => {
        const detail = error?.error?.message || 'Không thể tải danh sách vai trò.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail });
      }
    });
  }

  loadHotels(): void {
    this.userService.getStaffProperties().pipe(timeout(10000)).subscribe({
      next: (data) => {
        this.hotels = data;
      },
      error: (error) => {
        const detail = error?.error?.message || 'Không thể tải danh sách cơ sở.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail });
      }
    });
  }

  openNew(): void {
    this.userForm = this.createEmptyForm();
    this.userDialogMode = 'create';
    this.displayDialog = true;
  }

  editUser(user: User): void {
    const activeAssignment = user.staffAssignments?.find(item => item.status === 'ACTIVE');
    this.userForm = {
      id: user.id,
      expectedVersion: user.version,
      username: user.username,
      email: user.email,
      password: '',
      fullName: (user as any).fullName || '',
      phone: (user as any).phone || '',
      status: user.status,
      roleIds: user.roles ? user.roles.map((r: any) => r.id) : [],
      hotelId: activeAssignment?.hotelId ?? user.hotel?.id ?? null,
      originalHotelId: activeAssignment?.hotelId ?? user.hotel?.id ?? null,
      assignmentReason: '',
      changeReason: '',
    };
    this.userDialogMode = 'edit';
    this.displayDialog = true;
  }

  saveUser(): void {
    if (this.saving) return;

    const payload = { ...this.userForm };
    if (this.userType === 'CUSTOMER') {
      const customerRole = this.roles.find(r => r.code === 'CUSTOMER');
      if (customerRole) {
        payload.roleIds = [customerRole.id];
      }
    }

    let request;
    if (this.userDialogMode === 'create' && this.userType === 'STAFF') {
      const validationMessage = this.staffCreateValidationMessage();
      if (validationMessage) {
        this.messageService.add({ severity: 'warn', summary: 'Invalid staff account', detail: validationMessage });
        return;
      }
      const staffRequest: StaffCreateRequest = {
        username: String(payload.username).trim(),
        email: String(payload.email).trim(),
        password: payload.password,
        fullName: String(payload.fullName).trim(),
        phone: String(payload.phone || '').trim() || null,
        roleIds: payload.roleIds,
        hotelId: payload.hotelId,
      };
      request = this.userService.createStaff(staffRequest);
    } else if (this.userDialogMode === 'edit' && this.userType === 'STAFF') {
      const validationMessage = this.staffUpdateValidationMessage();
      if (validationMessage) {
        this.messageService.add({ severity: 'warn', summary: 'Invalid staff update', detail: validationMessage });
        return;
      }
      const staffRequest: StaffUpdateRequest = {
        fullName: String(payload.fullName).trim(),
        phone: String(payload.phone || '').trim() || null,
        password: String(payload.password || '') || null,
        roleIds: payload.roleIds,
        hotelId: payload.hotelId,
        assignmentReason: String(payload.assignmentReason || '').trim() || null,
        expectedVersion: payload.expectedVersion,
        changeReason: String(payload.changeReason || '').trim(),
      };
      request = this.userService.updateStaff(this.userForm.id, staffRequest);
    } else {
      request = this.userDialogMode === 'create'
        ? this.userService.createUser(payload)
        : this.userService.updateUser(this.userForm.id, payload);
    }

    this.saving = true;
    request.pipe(
      timeout(10000),
      finalize(() => {
        this.saving = false;
      })
    ).subscribe({
      next: () => {
        this.displayDialog = false;
        this.messageService.add({ severity: 'success', summary: 'Thành công', detail: 'Đã lưu người dùng.' });
        this.loadUsers();
      },
      error: (error) => {
        const detail = error?.error?.message || 'Không thể lưu người dùng.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail });
      }
    });
  }

  private staffCreateValidationMessage(): string | null {
    if (!String(this.userForm.username || '').trim()) return 'Username is required.';
    if (!String(this.userForm.email || '').trim()) return 'Email is required.';
    if (!String(this.userForm.fullName || '').trim()) return 'Full name is required.';
    const password = String(this.userForm.password || '');
    if (password.length < 8 || password.length > 256) {
      return 'Initial password must be between 8 and 256 characters.';
    }
    if (!Array.isArray(this.userForm.roleIds) || this.userForm.roleIds.length === 0) {
      return 'Select at least one staff role.';
    }
    if (!this.userForm.hotelId) return 'Select a property.';
    return null;
  }

  private staffUpdateValidationMessage(): string | null {
    if (!String(this.userForm.fullName || '').trim()) return 'Full name is required.';
    const password = String(this.userForm.password || '');
    if (password && (password.length < 8 || password.length > 256)) {
      return 'A replacement password must be between 8 and 256 characters.';
    }
    if (!Array.isArray(this.userForm.roleIds) || this.userForm.roleIds.length === 0) {
      return 'Select at least one staff role.';
    }
    if (!this.userForm.hotelId) return 'Select a property.';
    if (String(this.userForm.changeReason || '').trim().length < 3) {
      return 'Enter a change reason of at least 3 characters.';
    }
    if (this.userForm.hotelId !== this.userForm.originalHotelId
        && String(this.userForm.assignmentReason || '').trim().length < 3) {
      return 'Enter a property move reason of at least 3 characters.';
    }
    return null;
  }

  openLifecycle(user: User, mode: 'deactivate' | 'reactivate'): void {
    const assignments = this.lifecycleAssignments(user, mode);
    if (!assignments.length) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Không có phân công phù hợp',
        detail: mode === 'deactivate'
          ? 'Nhân viên không còn phân công đang hoạt động.'
          : 'Không tìm thấy lịch sử phân công để tuyển lại.'
      });
      return;
    }
    this.lifecycleUser = user;
    this.lifecycleMode = mode;
    this.lifecycleHotelId = assignments[0].hotelId;
    this.lifecycleReason = '';
    this.lifecycleDialogVisible = true;
  }

  submitLifecycle(): void {
    if (this.saving || !this.lifecycleUser || !this.lifecycleHotelId) return;
    const reason = this.lifecycleReason.trim();
    if (reason.length < 3) {
      this.messageService.add({
        severity: 'warn', summary: 'Thiếu lý do', detail: 'Vui lòng nhập lý do ít nhất 3 ký tự.'
      });
      return;
    }

    const request = this.lifecycleMode === 'deactivate'
      ? this.userService.deactivateStaff(this.lifecycleUser.id, {
          hotelId: this.lifecycleHotelId,
          reason,
          expectedVersion: this.lifecycleUser.version
        })
      : this.userService.reactivateStaff(this.lifecycleUser.id, {
          hotelId: this.lifecycleHotelId,
          reason,
          expectedVersion: this.lifecycleUser.version
        });

    this.saving = true;
    request.pipe(
      timeout(10000),
      finalize(() => {
        this.saving = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: () => {
        this.lifecycleDialogVisible = false;
        this.messageService.add({
          severity: 'success',
          summary: 'Thành công',
          detail: this.lifecycleMode === 'deactivate'
            ? 'Đã ngừng quyền truy cập và giữ nguyên lịch sử nhân sự.'
            : 'Đã tuyển lại nhân viên với một kỳ phân công mới.'
        });
        this.loadUsers();
      },
      error: (error) => {
        const detail = error?.error?.message || 'Không thể cập nhật vòng đời nhân viên.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail });
      }
    });
  }

  lifecycleAssignments(user: User | null, mode: 'deactivate' | 'reactivate'): StaffAssignment[] {
    const expectedStatus = mode === 'deactivate' ? 'ACTIVE' : 'INACTIVE';
    return (user?.staffAssignments || []).filter(item => item.status === expectedStatus);
  }

  hasAssignment(user: User, status: 'ACTIVE' | 'INACTIVE'): boolean {
    return (user.staffAssignments || []).some(item => item.status === status);
  }

  assignmentLabel(user: User): string {
    const assignments = user.staffAssignments || [];
    if (!assignments.length) return user.hotel?.name || '-';
    return assignments
      .map(item => `${item.hotelName} · ${item.status === 'ACTIVE' ? 'Đang làm' : 'Đã nghỉ'}`)
      .join(', ');
  }

  getRolesString(roles: any[]): string {
    if (!roles) return '';
    return roles.map(r => r.name).join(', ');
  }

  openHistory(user: User): void {
    const base = this.router.url.startsWith('/management') ? '/management/audit-log' : '/admin/audit-log';
    this.router.navigate([base], {
      queryParams: { domain: 'STAFF', aggregateType: 'USER', aggregateId: user.id }
    });
  }

  private createEmptyForm(): any {
    return {
      id: null,
      expectedVersion: 0,
      username: '',
      email: '',
      password: '',
      fullName: '',
      phone: '',
      status: 'ACTIVE',
      roleIds: [],
      hotelId: null,
      originalHotelId: null,
      assignmentReason: '',
      changeReason: '',
    };
  }
}
