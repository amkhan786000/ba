import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

/** The choices: fathers' professions the students gave, and the lowest / highest family income. */
export interface PreferenceOptions { father_professions: string[]; income_min: number | null; income_max: number | null }

/** What kind of student a sponsor would like (as sent to the server). */
export interface SponsorPreferences { collegeType: string; fatherProfessions: string[]; incomeMin: number | null; incomeMax: number | null }

export function emptyPreferences(): SponsorPreferences {
  return { collegeType: 'ANY', fatherProfessions: [], incomeMin: null, incomeMax: null };
}

/** From the server's pref_* fields (Edit Sponsor) or an application's fields. */
export function preferencesFrom(r: Record<string, unknown> | null | undefined, prefix = 'pref_'): SponsorPreferences {
  if (!r) return emptyPreferences();
  const num = (v: unknown) => (v === null || v === undefined || v === '' ? null : Number(v));
  return {
    collegeType: (r[prefix + 'college_type'] as string) || 'ANY',
    fatherProfessions: [...((r[prefix + 'father_professions'] as string[]) ?? [])],
    incomeMin: num(r[prefix + 'income_min']),
    incomeMax: num(r[prefix + 'income_max'])
  };
}

/** College type, fathers' professions (several) and a family income range: the kind of student a sponsor wants. */
@Component({
  selector: 'app-sponsor-preferences',
  standalone: true,
  imports: [CommonModule, FormsModule],
  styles: [`
    .professions { max-height: 180px; overflow-y: auto; border: 1px solid #dee2e6; border-radius: 4px; padding: 6px 10px; }
    .chosen .badge { font-size: 12px; margin: 0 4px 4px 0; }
  `],
  template: `
    <div class="row">
      <div class="col-md-4 form-group">
        <label [for]="id + 'ct'">College type</label>
        <select [id]="id + 'ct'" class="form-control" [(ngModel)]="value.collegeType" (ngModelChange)="changed()" [disabled]="disabled">
          <option value="ANY">Doesn't matter</option>
          <option value="GOVT">Govt</option>
          <option value="PRIVATE">Private</option>
        </select>
      </div>
      <div class="col-md-8 form-group">
        <label>Family income (yearly, ₹)</label>
        <div class="d-flex align-items-center">
          <input type="number" min="0" class="form-control" [(ngModel)]="value.incomeMin" (ngModelChange)="changed()" [disabled]="disabled"
                 [placeholder]="options?.income_min != null ? 'Min, e.g. ' + options!.income_min : 'Min'" aria-label="Minimum income">
          <span class="mx-2">to</span>
          <input type="number" min="0" class="form-control" [(ngModel)]="value.incomeMax" (ngModelChange)="changed()" [disabled]="disabled"
                 [placeholder]="options?.income_max != null ? 'Max, e.g. ' + options!.income_max : 'Max'" aria-label="Maximum income">
        </div>
        <small class="text-muted" *ngIf="options?.income_min != null">Students' family incomes range from
          ₹{{ options!.income_min | number }} to ₹{{ options!.income_max | number }}. Leave empty for any.</small>
      </div>
      <div class="col-12 form-group">
        <label>Father's profession <span class="text-muted small">(choose any number; none = any)</span></label>
        <div class="chosen mb-1" *ngIf="value.fatherProfessions.length">
          <span class="badge badge-primary" *ngFor="let p of value.fatherProfessions">{{ p }}
            <a *ngIf="!disabled" href="" class="text-white ml-1" (click)="$event.preventDefault(); toggle(p)" aria-label="Remove">&times;</a></span>
        </div>
        <input *ngIf="!disabled && allProfessions.length > 8" class="form-control form-control-sm mb-1" [(ngModel)]="filter" placeholder="Search professions">
        <div class="professions" *ngIf="!disabled">
          <div class="custom-control custom-checkbox" *ngFor="let p of shownProfessions; let i = index">
            <input type="checkbox" class="custom-control-input" [id]="id + 'p' + i" [checked]="has(p)" (change)="toggle(p)">
            <label class="custom-control-label" [for]="id + 'p' + i">{{ p }}</label>
          </div>
          <span *ngIf="!shownProfessions.length" class="text-muted small">No professions found.</span>
        </div>
      </div>
    </div>
  `
})
export class SponsorPreferencesComponent {
  private static seq = 0;
  readonly id = 'sp' + SponsorPreferencesComponent.seq++;

  @Input() value: SponsorPreferences = emptyPreferences();
  @Output() valueChange = new EventEmitter<SponsorPreferences>();
  @Input() options: PreferenceOptions | null = null;
  @Input() disabled = false;
  filter = '';

  /** The students' professions, plus any chosen earlier that no student has now. */
  get allProfessions(): string[] {
    const list = [...(this.options?.father_professions ?? [])];
    for (const p of this.value.fatherProfessions) if (!list.some((x) => x.toLowerCase() === p.toLowerCase())) list.push(p);
    return list;
  }

  get shownProfessions(): string[] {
    const f = this.filter.trim().toLowerCase();
    return f ? this.allProfessions.filter((p) => p.toLowerCase().includes(f)) : this.allProfessions;
  }

  has(p: string): boolean { return this.value.fatherProfessions.some((x) => x.toLowerCase() === p.toLowerCase()); }

  toggle(p: string): void {
    const list = this.value.fatherProfessions;
    this.value.fatherProfessions = this.has(p) ? list.filter((x) => x.toLowerCase() !== p.toLowerCase()) : [...list, p];
    this.changed();
  }

  changed(): void { this.valueChange.emit(this.value); }
}
