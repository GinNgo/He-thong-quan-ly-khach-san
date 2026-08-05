import {
  AfterViewInit,
  DestroyRef,
  Directive,
  ElementRef,
  HostBinding,
  Input,
  OnChanges,
  SimpleChanges,
  inject,
} from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs/operators';

@Directive({
  selector: '[appRouteFocusTarget]',
  standalone: true,
})
export class RouteFocusTargetDirective {
  private readonly element = inject(ElementRef<HTMLElement>);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private skipNextNavigation = true;

  @HostBinding('attr.tabindex') readonly tabindex = '-1';

  constructor() {
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => {
        if (this.skipNextNavigation) {
          this.skipNextNavigation = false;
          return;
        }
        this.scheduleFocus();
      });
  }

  private scheduleFocus(): void {
    queueMicrotask(() => this.element.nativeElement.focus({ preventScroll: true }));
  }
}

@Directive({
  selector: '[appFocusOnError]',
  standalone: true,
})
export class FocusOnErrorDirective implements AfterViewInit, OnChanges {
  private readonly element = inject(ElementRef<HTMLElement>);

  @Input() appFocusOnError = false;
  @HostBinding('attr.tabindex') readonly tabindex = '-1';

  ngAfterViewInit(): void {
    if (this.appFocusOnError) this.scheduleFocus();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['appFocusOnError']?.currentValue === true) this.scheduleFocus();
  }

  private scheduleFocus(): void {
    queueMicrotask(() => this.element.nativeElement.focus({ preventScroll: true }));
  }
}
