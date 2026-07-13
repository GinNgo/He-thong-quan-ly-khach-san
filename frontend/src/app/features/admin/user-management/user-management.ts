import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { SharedModule } from '@app/shared/shared.module';
import { UserService, User } from '@app/core/services/user';
import { RoleService, Role } from '@app/core/services/role.service';
import { ClientApiService, Hotel } from '@app/core/services/client-api.service';
import { ActivatedRoute } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { finalize, timeout } from 'rxjs/operators';

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [SharedModule],
  providers: [ConfirmationService, MessageService],
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

  private userService = inject(UserService);
  private roleService = inject(RoleService);
  private hotelService = inject(ClientApiService);
  private route = inject(ActivatedRoute);
  private confirmationService = inject(ConfirmationService);
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
    this.userForm = {
      id: user.id,
      username: user.username,
      email: user.email,
      password: '',
      fullName: (user as any).fullName || '',
      phone: (user as any).phone || '',
      status: user.status,
      roleIds: user.roles ? user.roles.map((r: any) => r.id) : [],
      hotelId: (user as any).hotel ? (user as any).hotel.id : null
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

  deleteUser(user: User): void {
    this.confirmationService.confirm({
      message: `Bạn có chắc muốn xóa người dùng ${user.username}?`,
      header: 'Xác nhận xóa',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.saving = true;
        this.userService.deleteUser(user.id!).pipe(
          timeout(10000),
          finalize(() => {
            this.saving = false;
          })
        ).subscribe({
          next: () => {
            this.messageService.add({ severity: 'success', summary: 'Thành công', detail: 'Đã xóa người dùng.' });
            this.loadUsers();
          },
          error: (error) => {
            const detail = error?.error?.message || 'Không thể xóa người dùng.';
            this.messageService.add({ severity: 'error', summary: 'Lỗi', detail });
          }
        });
      }
    });
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
