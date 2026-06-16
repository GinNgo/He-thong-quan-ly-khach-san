import { Component, signal, ChangeDetectionStrategy } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AiAssistant } from './features/ai-assistant/ai-assistant';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, AiAssistant],
  templateUrl: './app.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './app.css',
})
export class App {
  protected readonly title = signal('frontend');
}
