import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

/**
 * "Session year" dropdown for screens that list students: All years (''), each session year the server reports, and
 * "Not set" ('none'). Bind [(value)] and reload on (valueChange); the value goes to the API as sessionYear.
 */
@Component({
  selector: 'app-session-year-select',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <select class="form-control form-control-sm w-auto" [ngModel]="value" (ngModelChange)="valueChange.emit($event)" aria-label="Session year" title="Session year">
      <option value="">All session years</option>
      <option *ngFor="let y of years" [value]="'' + y">Session {{ y }}</option>
      <option value="none">Session year not set</option>
    </select>
  `
})
export class SessionYearSelectComponent {
  /** The session years there are, newest first (the API's "years"). */
  @Input() years: number[] | null | undefined = [];
  @Input() value = '';
  @Output() valueChange = new EventEmitter<string>();
}
