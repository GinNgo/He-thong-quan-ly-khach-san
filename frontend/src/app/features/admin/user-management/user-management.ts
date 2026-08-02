import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { SharedModule } from '@app/shared/shared.module';
import { StaffAssignment, UserService, User } from '@app/core/services/user';
import { RoleService, Role } from '@app/core/services/role.service';
import { ClientApiService, Hotel } from '@app/core/services/client-api.service';
import { ActivatedRoute } from '@angular/router';
import { MessageService } from 'primeng/api';
import { finalize, timeout } from 'rxjs/operators';

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
  hotels: Hotel[] = [];
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

  private userService = inject(UserService);
  private roleService = inject(RoleService);
  private hotelService = inject(ClientApiService);
  private route = inject(ActivatedRoute);
  private messageService = inject(MessageService);
  private cdr = inject(ChangeDetectorRef);

  ngOnInit(): void {
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
    this.loadHotels();
  }

  loadUsers(): void {
    this.loading = true;
    this.errorMessage = '';
    this.users = [];

    this.userService.getUsers().pipe(
      timeout(10000),
      finalize(() => {
        this.loading = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: (data) => {
        if (this.userType === 'CUSTOMER') {
          this.users = data.filter(u => u.roles && u.roles.some((r: any) => r.code === 'CUSTOMER'));
        } else {
          this.users = data.filter(u => !u.roles || !u.roles.some((r: any) => r.code === 'CUSTOMER'));
        }
      },
      error: (error) => {
        this.errorMessage = error?.error?.message || 'Không thể tải danh sách người dùng.';
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail: this.errorMessage });
      }
    });
  }

  loadRoles(): void {
    this.roleService.getRoles().pipe(timeout(10000)).subscribe({
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
    this.hotelService.searchHotels({}).pipe(timeout(10000)).subscribe({
      next: (data: any) => {
        this.hotels = data.content || [];
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
      username: user.username,
      email: user.email,
      password: '',
      fullName: (user as any).fullName || '',
      phone: (user as any).phone || '',
      status: user.status,
      roleIds: user.roles ? user.roles.map((r: any) => r.id) : [],
      hotelId: activeAssignment?.hotelId ?? user.hotel?.id ?? null
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

    const request = this.userDialogMode === 'create'
      ? this.userService.createUser(payload)
      : this.userService.updateUser(this.userForm.id, payload);

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
      ? this.userService.deactivateStaff(this.lifecycleUser.id, { hotelId: this.lifecycleHotelId, reason })
      : this.userService.reactivateStaff(this.lifecycleUser.id, { hotelId: this.lifecycleHotelId, reason });

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

  private createEmptyForm(): any {
    return {
      id: null,
      username: '',
      email: '',
      password: '',
      fullName: '',
      phone: '',
      status: 'ACTIVE',
      roleIds: [],
      hotelId: null
    };
  }
}
