import { Component, inject, Input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { PermissionService, FunctionCode } from '../../core/services/permission.service';

@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.css',
})
export class Sidebar {
  @Input() isCollapsed = false;
  permissionService = inject(PermissionService);
  FunctionCode = FunctionCode;
}
