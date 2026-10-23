import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { FooterComponent } from '../footer/footer.component';
import { HeaderComponent } from '../header/header.component';

@Component({
  selector: 'app-main-layout',
  imports: [RouterOutlet, HeaderComponent, FooterComponent],
  template: `
    <!-- Botón y no enlace #ancla: con <base href="/"> un href="#..." navegaría a la raíz y perdería la URL -->
    <button type="button" class="skip-link" (click)="main.focus()">Saltar al contenido</button>
    <app-header />
    <main #main class="main" tabindex="-1">
      <router-outlet />
    </main>
    <app-footer />
  `,
  styleUrl: './main-layout.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MainLayoutComponent {}
