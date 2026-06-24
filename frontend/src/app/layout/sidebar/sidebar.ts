import { Component, OnInit, inject, Input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { HttpClient } from '@angular/common/http';

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
  
  menuItems: AppModuleDto[] = [];

  ngOnInit() {
    this.http.get<AppModuleDto[]>('http://localhost:8080/api/auth/my-menu').subscribe(res => {
      this.menuItems = res;
    });
  }
}
