import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../core/services/api.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { RccCenter } from './admin-rcc-centers.component';

/** Port of templates/admin/edit_rcc_center.html (add + edit). */
@Component({
  selector: 'app-admin-rcc-center-edit',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">{{ isEdit ? 'Edit' : 'Add' }} RCC Center</h4></div></div></div>
    <div class="row">
      <div class="col-12 col-md-8 offset-md-2">
        <app-alerts [(error)]="error"></app-alerts>
        <div class="card">
          <div class="card-body">
            <form #f="ngForm" (ngSubmit)="save()">
              <div class="form-group">
                <label for="center_name">Center Name</label>
                <input type="text" class="form-control" id="center_name" name="centerName" [(ngModel)]="center.centerName" required />
              </div>
              <div class="form-group">
                <label for="incharge_name">Incharge Name</label>
                <input type="text" class="form-control" id="incharge_name" name="inchargeName" [(ngModel)]="center.inchargeName" required />
              </div>
              <div class="row">
                <div class="col-md-6">
                  <div class="form-group">
                    <label for="contact_number">Contact Number</label>
                    <input type="text" class="form-control" id="contact_number" name="contactNumber" [(ngModel)]="center.contactNumber" required />
                  </div>
                </div>
                <div class="col-md-6">
                  <div class="form-group">
                    <label for="location">Location</label>
                    <input type="text" class="form-control" id="location" name="location" [(ngModel)]="center.location" required />
                  </div>
                </div>
              </div>
              <div class="d-flex justify-content-between">
                <a [routerLink]="['/', section, 'rcc-centers']" class="btn btn-light btn-lg">Back</a>
                <button type="submit" class="btn btn-primary btn-lg waves-effect waves-light" [disabled]="f.invalid || saving">
                  {{ isEdit ? 'Update Center' : 'Save Center' }}
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AdminRccCenterEditComponent implements OnInit {
  /** Area this page is shown in ('admin' or 'office'); set from route data, defaults to admin. */
  @Input() set section(v: string | undefined) { this._section = v || 'admin'; }
  get section(): string { return this._section; }
  private _section = 'admin';
  /** From the route parameter :id (absent on /admin/rcc-centers/new). */
  @Input() id?: string;

  center: RccCenter = { centerName: '', inchargeName: '', contactNumber: '', location: '' };
  saving = false;
  error = '';

  constructor(private api: ApiService, private router: Router) {}

  get isEdit(): boolean { return !!this.id; }

  ngOnInit(): void {
    if (!this.id) return;
    this.api.get<RccCenter[]>('/admin/rcc-centers').subscribe({
      next: (list) => {
        const found = list.find((c) => String(c.rccCenterId) === this.id);
        if (found) this.center = { ...found };
        else this.error = 'RCC Center not found.';
      },
      error: (e) => (this.error = errorText(e, 'Could not load the RCC center.'))
    });
  }

  save(): void {
    this.saving = true;
    this.api.post<RccCenter>('/admin/rcc-centers', this.center).subscribe({
      next: () => this.router.navigate(['/', this.section, 'rcc-centers']),
      error: (e) => { this.saving = false; this.error = errorText(e, 'Could not save the RCC center.'); }
    });
  }
}
