import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/services/api.service';
import { AuthService } from '../core/services/auth.service';
import { AlertsComponent, errorText } from '../shared/alerts/alerts.component';
import { AppDatePipe } from '../shared/app-date';

type Row = Record<string, any>;
const CATEGORIES: Record<string, string> = { BOOK: 'Books', UNIFORM: 'Uniform', STATIONERY: 'Stationery', DEVICE: 'Laptop / device', BICYCLE: 'Bicycle', OTHER: 'Other' };
const KINDS: Record<string, string> = { SCHOLAR: 'Scholarship', COACHING: 'Coaching', REB: 'Rahbar-e-Banaat' };

/**
 * Community > Materials: items and their stock (received from donors), and hand-outs to scholarship students,
 * coaching students and REB girls. Give one item to many students at once; stock can't go below zero.
 */
@Component({
  selector: 'app-materials',
  standalone: true,
  imports: [CommonModule, FormsModule, AlertsComponent, AppDatePipe],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Books & Materials</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>
    <ul class="nav nav-pills mb-3">
      <li class="nav-item"><a href="" class="nav-link" [class.active]="tab === 'items'" (click)="$event.preventDefault(); tab = 'items'; loadItems()">Items & stock</a></li>
      <li class="nav-item"><a href="" class="nav-link" [class.active]="tab === 'given'" (click)="$event.preventDefault(); tab = 'given'; loadGiven()">Handed out</a></li>
    </ul>

    <div class="card" *ngIf="tab === 'items'"><div class="card-body">
      <div class="d-flex mb-2" style="gap: 8px"><h4 class="header-title mb-0 mr-auto">Items</h4>
        <button *ngIf="canEdit" class="btn btn-sm btn-success" (click)="openGive(null)" [disabled]="!items.length"><i class="mdi mdi-gift-outline"></i> Hand out</button>
        <button *ngIf="canEdit && office" class="btn btn-sm btn-primary" (click)="openItem(null)"><i class="mdi mdi-plus"></i> New item</button></div>
      <div class="table-responsive"><table class="table table-sm mb-0">
        <thead class="thead-light"><tr><th>Item</th><th>Kind</th><th class="text-right">Received</th><th class="text-right">Handed out</th><th class="text-right">In stock</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!items.length"><td colspan="6" class="text-center text-muted">No items yet{{ office ? ': add one with "New item".' : '.' }}</td></tr>
          <tr *ngFor="let i of items" [class.text-muted]="!i['active']">
            <td><strong>{{ i['name'] }}</strong> <span *ngIf="!i['active']" class="badge badge-light border">Inactive</span><div class="small text-muted">{{ i['notes'] }}</div></td>
            <td>{{ categories[i['category']] }}</td>
            <td class="text-right">{{ i['received'] }}</td><td class="text-right">{{ i['given'] }}</td>
            <td class="text-right"><strong [class.text-danger]="i['in_stock'] === 0">{{ i['in_stock'] }}</strong> <span class="small text-muted">{{ i['unit'] }}</span></td>
            <td class="text-right text-nowrap">
              <button class="btn btn-xs btn-outline-secondary" (click)="openStock(i)">Stock</button>
              <button *ngIf="canEdit && i['active'] && i['in_stock'] > 0" class="btn btn-xs btn-success ml-1" (click)="openGive(i)">Hand out</button>
              <ng-container *ngIf="canEdit && office">
                <button class="btn btn-xs btn-outline-primary ml-1" (click)="openItem(i)">Edit</button>
                <button class="btn btn-xs btn-outline-danger ml-1" (click)="deleteItem(i)"><i class="mdi mdi-delete"></i></button></ng-container>
            </td>
          </tr>
        </tbody>
      </table></div>
    </div></div>

    <div class="card" *ngIf="tab === 'given'"><div class="card-body">
      <div class="d-flex flex-wrap mb-2" style="gap: 8px">
        <h4 class="header-title mb-0 mr-auto">Handed out</h4>
        <select class="form-control form-control-sm w-auto" [(ngModel)]="givenItem" (ngModelChange)="loadGiven()" aria-label="Item">
          <option [ngValue]="null">All items</option><option *ngFor="let i of items" [ngValue]="i['id']">{{ i['name'] }}</option></select>
        <select class="form-control form-control-sm w-auto" [(ngModel)]="givenKind" (ngModelChange)="loadGiven()" aria-label="Students">
          <option value="">All students</option><option *ngFor="let k of kindList" [value]="k.key">{{ k.label }}</option></select>
        <input class="form-control form-control-sm w-auto" placeholder="Search name, ID, place" [(ngModel)]="q" (ngModelChange)="loadGiven()">
      </div>
      <div class="table-responsive"><table class="table table-sm mb-0">
        <thead class="thead-light"><tr><th>Date</th><th>Item</th><th class="text-right">Qty</th><th>Student</th><th>Note</th><th></th></tr></thead>
        <tbody>
          <tr *ngIf="!given.length"><td colspan="6" class="text-center text-muted">Nothing handed out yet.</td></tr>
          <tr *ngFor="let d of given">
            <td>{{ d['given_on'] | appDate }}</td><td>{{ d['item'] }}</td><td class="text-right">{{ d['quantity'] }}</td>
            <td>{{ d['recipient'] }} <span class="small text-muted">{{ d['recipient_code'] }}</span>
              <div class="small text-muted">{{ d['recipient_label'] }}{{ d['recipient_place'] ? ' · ' + d['recipient_place'] : '' }}</div></td>
            <td class="small">{{ d['note'] }}</td>
            <td class="text-right"><button *ngIf="canEdit" class="btn btn-xs btn-light" (click)="undo(d)" title="Recorded by mistake: put it back in stock"><i class="mdi mdi-undo"></i></button></td>
          </tr>
        </tbody>
      </table></div>
    </div></div>

    <!-- item -->
    <div *ngIf="itemForm" class="modal fade show d-block" tabindex="-1" (click)="itemForm = null">
      <div class="modal-dialog" (click)="$event.stopPropagation()"><div class="modal-content">
        <div class="modal-header"><h5 class="modal-title">{{ itemForm['id'] ? 'Edit item' : 'New item' }}</h5>
          <button type="button" class="close" (click)="itemForm = null" aria-label="Close">&times;</button></div>
        <div class="modal-body">
          <div *ngIf="formError" class="alert alert-danger">{{ formError }}</div>
          <div class="form-group"><label>Name *</label><input class="form-control" maxlength="150" [(ngModel)]="itemForm['name']" placeholder="e.g. Class 10 Science textbook"></div>
          <div class="row">
            <div class="col-7 form-group"><label>Kind</label><select class="form-control" [(ngModel)]="itemForm['category']">
              <option *ngFor="let c of categoryList" [value]="c.key">{{ c.label }}</option></select></div>
            <div class="col-5 form-group"><label>Unit</label><input class="form-control" maxlength="20" [(ngModel)]="itemForm['unit']" placeholder="e.g. set"></div>
          </div>
          <div class="form-group"><label>Notes</label><input class="form-control" maxlength="500" [(ngModel)]="itemForm['notes']"></div>
          <div class="custom-control custom-checkbox"><input type="checkbox" class="custom-control-input" id="itemActive" [(ngModel)]="itemForm['active']">
            <label class="custom-control-label" for="itemActive">Active</label></div>
        </div>
        <div class="modal-footer"><button class="btn btn-light" (click)="itemForm = null">Cancel</button>
          <button class="btn btn-primary" (click)="saveItem()" [disabled]="busy">Save</button></div>
      </div></div>
    </div>

    <!-- stock -->
    <div *ngIf="stock" class="modal fade show d-block" tabindex="-1" (click)="stock = null">
      <div class="modal-dialog modal-lg modal-dialog-scrollable" (click)="$event.stopPropagation()"><div class="modal-content">
        <div class="modal-header"><h5 class="modal-title">Stock: {{ stock.item['name'] }} <small class="text-muted">{{ stock.item['in_stock'] }} in stock</small></h5>
          <button type="button" class="close" (click)="stock = null" aria-label="Close">&times;</button></div>
        <div class="modal-body">
          <div *ngIf="formError" class="alert alert-danger">{{ formError }}</div>
          <div *ngIf="canEdit && office" class="border rounded p-2 mb-3">
            <div class="row">
              <div class="col-md-2 form-group"><label class="small">How many *</label><input type="number" min="1" class="form-control form-control-sm" [(ngModel)]="receipt['quantity']"></div>
              <div class="col-md-3 form-group"><label class="small">Received on</label><input type="date" class="form-control form-control-sm" [max]="today" [(ngModel)]="receipt['receivedOn']"></div>
              <div class="col-md-3 form-group"><label class="small">From (donor)</label><input class="form-control form-control-sm" maxlength="150" [(ngModel)]="receipt['donorName']"></div>
              <div class="col-md-4 form-group"><label class="small">Note</label><input class="form-control form-control-sm" maxlength="500" [(ngModel)]="receipt['note']"></div>
            </div>
            <button class="btn btn-sm btn-primary" (click)="addStock()" [disabled]="busy || !receipt['quantity']">Add to stock</button>
          </div>
          <p *ngIf="!stock.rows.length" class="text-muted mb-0">No stock received yet.</p>
          <table class="table table-sm mb-0" *ngIf="stock.rows.length">
            <thead class="thead-light"><tr><th>Received</th><th class="text-right">How many</th><th>From</th><th>Note</th><th></th></tr></thead>
            <tbody><tr *ngFor="let r of stock.rows">
              <td>{{ r['received_on'] | appDate }}</td><td class="text-right">{{ r['quantity'] }}</td><td>{{ r['donor'] || '-' }}</td><td class="small">{{ r['note'] }}</td>
              <td class="text-right"><button *ngIf="canEdit && office" class="btn btn-xs btn-light" (click)="removeStock(r)" title="Remove (entered by mistake)"><i class="mdi mdi-close"></i></button></td>
            </tr></tbody>
          </table>
        </div>
      </div></div>
    </div>

    <!-- hand out -->
    <div *ngIf="give" class="modal fade show d-block" tabindex="-1" (click)="give = null">
      <div class="modal-dialog modal-lg modal-dialog-scrollable" (click)="$event.stopPropagation()"><div class="modal-content">
        <div class="modal-header"><h5 class="modal-title">Hand out</h5>
          <button type="button" class="close" (click)="give = null" aria-label="Close">&times;</button></div>
        <div class="modal-body">
          <div *ngIf="formError" class="alert alert-danger">{{ formError }}</div>
          <div class="row">
            <div class="col-md-6 form-group"><label>Item *</label><select class="form-control" [(ngModel)]="give['itemId']">
              <option [ngValue]="null">-- Choose --</option>
              <option *ngFor="let i of activeItems" [ngValue]="i['id']">{{ i['name'] }} ({{ i['in_stock'] }} in stock)</option></select></div>
            <div class="col-md-2 form-group"><label>Each gets</label><input type="number" min="1" class="form-control" [(ngModel)]="give['quantity']"></div>
            <div class="col-md-4 form-group"><label>Date</label><input type="date" class="form-control" [max]="today" [(ngModel)]="give['givenOn']"></div>
            <div class="col-12 form-group"><label>Note</label><input class="form-control" maxlength="500" [(ngModel)]="give['note']" placeholder="e.g. At the annual ceremony"></div>
          </div>
          <label>Who gets it * <span class="small text-muted">({{ picked.length }} chosen)</span></label>
          <input class="form-control" placeholder="Type a student's name, ID or admission no." [(ngModel)]="who" (ngModelChange)="search()">
          <div class="list-group mt-1" *ngIf="hits.length">
            <a href="" class="list-group-item list-group-item-action py-1" *ngFor="let h of hits" (click)="$event.preventDefault(); pick(h)">
              {{ h['name'] }} <span class="small text-muted">{{ h['code'] }} · {{ h['label'] }}{{ h['place'] ? ' · ' + h['place'] : '' }}</span></a></div>
          <div class="mt-2">
            <span *ngFor="let p of picked; let i = index" class="badge badge-primary p-2 mr-1 mb-1">{{ p['name'] }}
              <a href="" class="text-white ml-1" (click)="$event.preventDefault(); picked.splice(i, 1)" aria-label="Remove">&times;</a></span>
          </div>
        </div>
        <div class="modal-footer"><button class="btn btn-light" (click)="give = null">Cancel</button>
          <button class="btn btn-success" (click)="saveGive()" [disabled]="busy || !give['itemId'] || !picked.length">Hand out to {{ picked.length }}</button></div>
      </div></div>
    </div>
  `
})
export class MaterialsComponent implements OnInit {
  readonly today = new Date().toISOString().slice(0, 10);
  readonly categories = CATEGORIES;
  readonly categoryList = Object.entries(CATEGORIES).map(([key, label]) => ({ key, label }));
  readonly kindList = Object.entries(KINDS).map(([key, label]) => ({ key, label }));
  tab: 'items' | 'given' = 'items';
  items: Row[] = [];
  given: Row[] = [];
  givenItem: number | null = null;
  givenKind = '';
  q = '';
  itemForm: Row | null = null;
  stock: { item: Row; rows: Row[] } | null = null;
  receipt: Row = {};
  give: Row | null = null;
  picked: Row[] = [];
  who = '';
  hits: Row[] = [];
  formError = '';
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('MATERIALS', 'EDIT'); }
  /** Items and stock are kept by the office, not by a chapter / centre lead. */
  get office(): boolean { const u = this.auth.currentUser(); return !u?.scope || u.scope === 'ALL'; }
  get activeItems(): Row[] { return this.items.filter((i) => i['active'] && i['in_stock'] > 0); }

  ngOnInit(): void { this.loadItems(); }

  loadItems(): void { this.api.get<Row[]>('/admin/materials/items').subscribe({ next: (r) => (this.items = r), error: (e) => (this.error = errorText(e, 'Could not load.')) }); }

  loadGiven(): void {
    this.api.get<Row[]>('/admin/materials/given', { itemId: this.givenItem, kind: this.givenKind, q: this.q }).subscribe({
      next: (r) => (this.given = r), error: (e) => (this.error = errorText(e, 'Could not load.'))
    });
  }

  openItem(i: Row | null): void {
    this.formError = '';
    this.itemForm = i ? { id: i['id'], name: i['name'], category: i['category'], unit: i['unit'], notes: i['notes'], active: i['active'] }
      : { category: 'BOOK', active: true };
  }

  saveItem(): void {
    const f = this.itemForm!;
    this.busy = true;
    const req = f['id'] ? this.api.put(`/admin/materials/items/${f['id']}`, f) : this.api.post('/admin/materials/items', f);
    req.subscribe({
      next: () => { this.busy = false; this.itemForm = null; this.message = 'Item saved.'; this.loadItems(); },
      error: (e) => { this.busy = false; this.formError = errorText(e, 'Could not save.'); }
    });
  }

  deleteItem(i: Row): void {
    if (!confirm(`Delete ${i['name']}?`)) return;
    this.api.delete(`/admin/materials/items/${i['id']}`).subscribe({ next: () => this.loadItems(), error: (e) => (this.error = errorText(e, 'Could not delete.')) });
  }

  openStock(i: Row): void {
    this.formError = '';
    this.receipt = { itemId: i['id'], receivedOn: this.today };
    this.api.get<Row[]>(`/admin/materials/items/${i['id']}/stock`).subscribe({
      next: (rows) => (this.stock = { item: i, rows }), error: (e) => (this.error = errorText(e, 'Could not load.'))
    });
  }

  private refreshStock(): void {
    const id = this.stock!.item['id'];
    this.api.get<Row[]>('/admin/materials/items').subscribe((r) => {
      this.items = r;
      const item = r.find((x) => x['id'] === id);
      if (item) this.openStock(item);
    });
  }

  addStock(): void {
    this.busy = true;
    this.api.post<{ message: string }>('/admin/materials/stock', this.receipt).subscribe({
      next: (m) => { this.busy = false; this.message = m.message; this.refreshStock(); },
      error: (e) => { this.busy = false; this.formError = errorText(e, 'Could not add.'); }
    });
  }

  removeStock(r: Row): void {
    if (!confirm('Remove this stock entry?')) return;
    this.api.delete(`/admin/materials/stock/${r['id']}`).subscribe({ next: () => this.refreshStock(), error: (e) => (this.formError = errorText(e, 'Could not remove.')) });
  }

  openGive(i: Row | null): void {
    this.formError = '';
    this.picked = [];
    this.who = '';
    this.hits = [];
    this.give = { itemId: i ? i['id'] : null, quantity: 1, givenOn: this.today, note: '' };
  }

  search(): void {
    if (this.who.trim().length < 2) { this.hits = []; return; }
    this.api.get<Row[]>('/admin/materials/recipients', { q: this.who }).subscribe({ next: (h) => (this.hits = h) });
  }

  pick(h: Row): void {
    if (!this.picked.some((p) => p['kind'] === h['kind'] && p['id'] === h['id'])) this.picked.push(h);
    this.who = '';
    this.hits = [];
  }

  saveGive(): void {
    this.busy = true;
    this.api.post<{ message: string }>('/admin/materials/given', { ...this.give, recipients: this.picked.map((p) => ({ kind: p['kind'], id: p['id'] })) }).subscribe({
      next: (m) => { this.busy = false; this.give = null; this.message = m.message; this.loadItems(); if (this.tab === 'given') this.loadGiven(); },
      error: (e) => { this.busy = false; this.formError = errorText(e, 'Could not hand out.'); }
    });
  }

  undo(d: Row): void {
    if (!confirm(`Remove this hand-out (${d['quantity']} ${d['item']} to ${d['recipient']})? It goes back into stock.`)) return;
    this.api.delete<{ message: string }>(`/admin/materials/given/${d['id']}`).subscribe({
      next: (m) => { this.message = m.message; this.loadGiven(); this.loadItems(); }, error: (e) => (this.error = errorText(e, 'Could not remove.'))
    });
  }
}
