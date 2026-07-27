import { ChangeDetectorRef, Component, EventEmitter, OnInit, inject, Input, Output } from '@angular/core';
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
  @Output() navigated = new EventEmitter<void>();

  private http = inject(HttpClient);
  private cdr = inject(ChangeDetectorRef);

  menuItems: AppModuleDto[] = [];
  isLoading = true;
  errorMessage = '';

  ngOnInit(): void {
    this.loadMenu();
  }

  loadMenu(): void {
    this.isLoading = true;
    this.errorMessage = '';

    this.http.get<AppModuleDto[]>(`${environment.apiUrl}/auth/my-menu`).subscribe({
      next: (res) => {
        this.menuItems = this.deduplicateMenu(res);
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.menuItems = [];
        this.isLoading = false;
        this.errorMessage = 'Không thể tải menu theo quyền.';
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

  onNavigate(): void {
    this.navigated.emit();
  }
}
