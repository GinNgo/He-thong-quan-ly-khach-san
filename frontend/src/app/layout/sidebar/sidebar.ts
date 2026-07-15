import { ChangeDetectorRef, Component, OnInit, inject, Input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

export interface AppFunctionDto {
  id: number;
  code: string;
  name: string;
  url: string;
  icon: string;
}

export interface AppModuleDto {
  id: number;
  code: string;
  name: string;
  functions: AppFunctionDto[];
}

@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.css',
})
export class Sidebar implements OnInit {
  @Input() isCollapsed = false;
  http = inject(HttpClient);
  cdr = inject(ChangeDetectorRef);
  
  menuItems: AppModuleDto[] = [];

  ngOnInit() {
    this.http.get<AppModuleDto[]>(`${environment.apiUrl}/auth/my-menu`).subscribe({
      next: (res) => {
        this.menuItems = this.deduplicateMenu(res);
        this.cdr.detectChanges();
      },
      error: () => {
        this.menuItems = this.getFallbackMenu();
        this.cdr.detectChanges();
      }
    });
  }

  private deduplicateMenu(modules: AppModuleDto[]): AppModuleDto[] {
    const seenCodes = new Set<string>();
    const seenRoutes = new Set<string>();
    return modules.map(module => ({
      ...module,
      functions: (module.functions || []).filter(func => {
        if (!func.url || seenCodes.has(func.code) || seenRoutes.has(func.url)) return false;
        seenCodes.add(func.code);
        seenRoutes.add(func.url);
        return true;
      })
    })).filter(module => module.functions.length > 0);
  }

  private getFallbackMenu(): AppModuleDto[] {
    return [
      {
        id: 1,
        code: 'SYSTEM',
        name: 'Tổng quan',
        functions: [
          { id: 1, code: 'DASHBOARD', name: 'Bảng điều khiển', url: '/admin/dashboard', icon: 'pi pi-chart-bar' },
          { id: 2, code: 'PROFILE', name: 'Hồ sơ', url: '/admin/profile', icon: 'pi pi-user' },
        ],
      },
    ];
  }
}
