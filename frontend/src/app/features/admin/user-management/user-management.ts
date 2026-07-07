import { Component, inject, OnInit } from '@angular/core';
import { SharedModule } from '@app/shared/shared.module';
import { UserService, User } from '@app/core/services/user';
import { RoleService, Role } from '@app/core/services/role.service';
import { ClientApiService, Hotel } from '@app/core/services/client-api.service';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [SharedModule],
  templateUrl: './user-management.html',
  styleUrl: './user-management.css',
})
export class UserManagement implements OnInit {
  users: User[] = [];
  roles: Role[] = [];
  hotels: Hotel[] = [];
  loading = true;
  userType: 'STAFF' | 'CUSTOMER' = 'STAFF';

  displayDialog = false;
  userDialogMode: 'create' | 'edit' = 'create';
  userForm: any = {
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

  private userService = inject(UserService);
  private roleService = inject(RoleService);
  private hotelService = inject(ClientApiService);
  private route = inject(ActivatedRoute);

  ngOnInit(): void {
    this.route.data.subscribe(data => {
      this.userType = data['userType'] || 'STAFF';
      this.loadUsers();
    });
    this.loadRoles();
    this.loadHotels();
  }

  loadUsers(): void {
    this.loading = true;
    this.userService.getUsers().subscribe({
      next: (data) => {
        if (this.userType === 'CUSTOMER') {
          this.users = data.filter(u => u.roles && u.roles.some((r: any) => r.code === 'CUSTOMER'));
        } else {
          this.users = data.filter(u => !u.roles || !u.roles.some((r: any) => r.code === 'CUSTOMER'));
        }
        this.loading = false;
      },
      error: (err) => {
        console.error('Error fetching users', err);
        this.loading = false;
      }
    });
  }

  loadRoles(): void {
    this.roleService.getRoles().subscribe(data => this.roles = data);
  }

  loadHotels(): void {
    this.hotelService.searchHotels().subscribe(data => this.hotels = data);
  }

  openNew() {
    this.userForm = { id: null, username: '', email: '', password: '', fullName: '', phone: '', status: 'ACTIVE', roleIds: [], hotelId: null };
    this.userDialogMode = 'create';
    this.displayDialog = true;
  }

  editUser(user: User) {
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

  saveUser() {
    const payload = { ...this.userForm };
    
    // Automatically assign CUSTOMER role if userType is CUSTOMER
    if (this.userType === 'CUSTOMER') {
      const customerRole = this.roles.find(r => r.code === 'CUSTOMER');
      if (customerRole) {
        payload.roleIds = [customerRole.id];
      }
    }

    if (this.userDialogMode === 'create') {
      this.userService.createUser(payload).subscribe(() => {
        this.displayDialog = false;
        this.loadUsers();
      });
    } else {
      this.userService.updateUser(this.userForm.id, payload).subscribe(() => {
        this.displayDialog = false;
        this.loadUsers();
      });
    }
  }

  deleteUser(user: User) {
    if (confirm(`Bạn có chắc muốn xóa người dùng ${user.username}?`)) {
      this.userService.deleteUser(user.id).subscribe(() => {
        this.loadUsers();
      });
    }
  }

  getRolesString(roles: any[]): string {
    if (!roles) return '';
    return roles.map(r => r.name).join(', ');
  }
}

