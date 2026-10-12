import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { AlertsComponent, errorText } from '../../shared/alerts/alerts.component';
import { AppDatePipe } from '../../shared/app-date';

interface Photo {
  id: number; title: string; caption?: string | null; album?: string | null; takenOn?: string | null;
  displayOrder: number; published: boolean; url: string; createdAt: string;
}

const MAX_BYTES = 10 * 1024 * 1024;
const ACCEPT = 'image/jpeg,image/png,image/gif,image/webp';

/** Admin > Website > Gallery: upload photos with a title, and edit / hide / delete them. */
@Component({
  selector: 'app-website-gallery',
  standalone: true,
  imports: [AppDatePipe, CommonModule, FormsModule, AlertsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Gallery</h4></div></div></div>
    <app-alerts [(message)]="message" [(error)]="error"></app-alerts>

    <div class="card" *ngIf="canEdit">
      <div class="card-body">
        <h5 class="mt-0">Upload a photo</h5>
        <div class="row">
          <div class="col-12 form-group">
            <label for="g-file">Image file * (JPEG, PNG, GIF or WebP, up to 10 MB)</label>
            <input #fileInput id="g-file" type="file" class="form-control-file" [accept]="accept" (change)="pick($event)">
            <img *ngIf="preview" [src]="preview" alt="Selected photo" class="d-block mt-2 rounded border" style="max-height: 160px; max-width: 100%">
          </div>
          <div class="col-md-6 form-group"><label for="g-title">Title *</label><input id="g-title" class="form-control" [(ngModel)]="title" maxlength="200"></div>
          <div class="col-md-6 form-group"><label for="g-album">Album</label>
            <input id="g-album" class="form-control" [(ngModel)]="album" list="g-albums" maxlength="80" placeholder="e.g. Rahbar Coaching Centres">
            <datalist id="g-albums"><option *ngFor="let a of albums" [value]="a"></option></datalist>
          </div>
          <div class="col-md-6 form-group"><label for="g-date">Date taken</label><input id="g-date" type="date" class="form-control" [(ngModel)]="takenOn"></div>
          <div class="col-12 form-group"><label for="g-caption">Caption</label><textarea id="g-caption" class="form-control" rows="2" [(ngModel)]="caption" maxlength="1000"></textarea></div>
          <div class="col-12 form-group">
            <div class="custom-control custom-checkbox">
              <input type="checkbox" class="custom-control-input" id="g-pub" [(ngModel)]="published">
              <label class="custom-control-label" for="g-pub">Show in the website's gallery</label>
            </div>
          </div>
        </div>
        <button type="button" class="btn btn-primary" (click)="upload()" [disabled]="!file || !title.trim() || busy">{{ busy ? 'Uploading…' : 'Upload' }}</button>
      </div>
    </div>

    <div class="card">
      <div class="card-body">
        <div class="row mb-3">
          <div class="col-12 col-md-6"><input class="form-control" placeholder="Search by title or album" aria-label="Search photos" [(ngModel)]="q"></div>
          <div class="col-12 col-md-6 text-md-right text-muted pt-2">{{ visible.length }} photo(s)</div>
        </div>
        <p *ngIf="loading" class="text-center"><span class="spinner-border spinner-border-sm"></span></p>
        <p *ngIf="!loading && !visible.length" class="text-muted text-center">No photos yet.</p>
        <div class="row">
          <div class="col-6 col-md-4 col-xl-3 mb-3" *ngFor="let g of visible; trackBy: byId">
            <div class="border rounded h-100 d-flex flex-column">
              <a [href]="g.url" target="_blank" rel="noopener"><img [src]="g.url" [alt]="g.title" loading="lazy" class="w-100 rounded-top" style="aspect-ratio: 4 / 3; object-fit: cover"></a>
              <div class="p-2 small flex-grow-1">
                <ng-container *ngIf="editing?.id === g.id; else show">
                  <input class="form-control form-control-sm mb-1" [(ngModel)]="editing!.title" aria-label="Title" maxlength="200">
                  <input class="form-control form-control-sm mb-1" [(ngModel)]="editing!.album" aria-label="Album" placeholder="Album" list="g-albums">
                  <textarea class="form-control form-control-sm mb-1" rows="2" [(ngModel)]="editing!.caption" aria-label="Caption" placeholder="Caption"></textarea>
                  <input type="date" class="form-control form-control-sm mb-1" [(ngModel)]="editing!.takenOn" aria-label="Date taken">
                  <input type="number" class="form-control form-control-sm mb-1" [(ngModel)]="editing!.displayOrder" aria-label="Display order" title="Lower numbers show first">
                  <button type="button" class="btn btn-xs btn-primary mr-1" (click)="save(editing!)">Save</button>
                  <button type="button" class="btn btn-xs btn-light" (click)="editing = null">Cancel</button>
                </ng-container>
                <ng-template #show>
                  <strong class="d-block">{{ g.title }}</strong>
                  <span class="text-muted d-block">{{ g.album || 'No album' }} · {{ g.createdAt | appDate }}</span>
                  <span class="badge" [ngClass]="g.published ? 'badge-success' : 'badge-light border'">{{ g.published ? 'In the gallery' : 'Hidden' }}</span>
                  <div class="mt-2" *ngIf="canEdit">
                    <button type="button" class="btn btn-xs btn-outline-primary mr-1 mb-1" (click)="startEdit(g)">Edit</button>
                    <button type="button" class="btn btn-xs btn-outline-secondary mr-1 mb-1" (click)="toggleShown(g)">{{ g.published ? 'Hide' : 'Show' }}</button>
                    <button type="button" class="btn btn-xs btn-outline-secondary mr-1 mb-1" (click)="copy(g)" title="Copy the photo for a blog post">Copy link</button>
                    <button type="button" class="btn btn-xs btn-outline-danger mb-1" (click)="remove(g)">Delete</button>
                  </div>
                </ng-template>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class WebsiteGalleryComponent implements OnInit {
  @ViewChild('fileInput') private fileInput?: ElementRef<HTMLInputElement>;
  readonly accept = ACCEPT;
  photos: Photo[] = [];
  title = '';
  album = '';
  caption = '';
  takenOn = '';
  published = true;
  file: File | null = null;
  preview: string | null = null;
  editing: Photo | null = null;
  q = '';
  loading = false;
  busy = false;
  message = '';
  error = '';

  constructor(private api: ApiService, private auth: AuthService) {}

  get canEdit(): boolean { return this.auth.can('WEBSITE_GALLERY', 'EDIT'); }
  get albums(): string[] { return [...new Set(this.photos.map((g) => g.album).filter((a): a is string => !!a))].sort(); }
  get visible(): Photo[] {
    const t = this.q.trim().toLowerCase();
    return t ? this.photos.filter((g) => `${g.title} ${g.album ?? ''}`.toLowerCase().includes(t)) : this.photos;
  }

  byId(_: number, g: Photo): number { return g.id; }

  startEdit(g: Photo): void { this.editing = { ...g }; }

  toggleShown(g: Photo): void { this.save({ ...g, published: !g.published }); }

  ngOnInit(): void { this.load(); }

  private load(): void {
    this.loading = true;
    this.api.get<Photo[]>('/admin/website/gallery').subscribe({
      next: (p) => { this.loading = false; this.photos = p; },
      error: (e) => { this.loading = false; this.error = errorText(e, 'Could not load the photos.'); }
    });
  }

  pick(event: Event): void {
    const input = event.target as HTMLInputElement;
    const f = input.files?.[0] ?? null;
    this.error = '';
    if (this.preview) URL.revokeObjectURL(this.preview);
    this.preview = null;
    this.file = null;
    if (!f) return;
    if (!ACCEPT.split(',').includes(f.type)) { this.error = 'Please choose a JPEG, PNG, GIF or WebP image.'; input.value = ''; return; }
    if (f.size > MAX_BYTES) { this.error = 'That image is larger than 10 MB. Please resize it first.'; input.value = ''; return; }
    this.file = f;
    this.preview = URL.createObjectURL(f);
    if (!this.title.trim()) this.title = f.name.replace(/\.[^.]+$/, '').replace(/[-_]+/g, ' ');
  }

  upload(): void {
    if (!this.file || !this.title.trim()) return;
    const form = new FormData();
    form.append('file', this.file);
    form.append('title', this.title.trim());
    if (this.album.trim()) form.append('album', this.album.trim());
    if (this.caption.trim()) form.append('caption', this.caption.trim());
    if (this.takenOn) form.append('takenOn', this.takenOn);
    form.append('published', String(this.published));
    this.busy = true;
    this.api.post<Photo>('/admin/website/gallery', form).subscribe({
      next: (g) => {
        this.busy = false;
        this.message = `Uploaded “${g.title}”.`;
        this.title = this.caption = this.takenOn = '';
        this.file = null;
        if (this.preview) URL.revokeObjectURL(this.preview);
        this.preview = null;
        if (this.fileInput) this.fileInput.nativeElement.value = '';
        this.load();
      },
      error: (e) => { this.busy = false; this.error = errorText(e, 'Could not upload the photo.'); }
    });
  }

  save(g: Photo): void {
    const body = { title: g.title, caption: g.caption || null, album: g.album || null, takenOn: g.takenOn || null,
      displayOrder: Number(g.displayOrder) || 0, published: g.published };
    this.api.put<Photo>(`/admin/website/gallery/${g.id}`, body).subscribe({
      next: () => { this.editing = null; this.load(); },
      error: (e) => (this.error = errorText(e, 'Could not save.'))
    });
  }

  remove(g: Photo): void {
    if (!confirm(`Delete “${g.title}” permanently? Posts using this photo will lose it.`)) return;
    this.api.delete(`/admin/website/gallery/${g.id}`).subscribe({
      next: () => this.load(),
      error: (e) => (this.error = errorText(e, 'Could not delete.'))
    });
  }

  copy(g: Photo): void {
    const markdown = `![${g.title}](${g.url})`;
    navigator.clipboard?.writeText(markdown).then(
      () => (this.message = 'Copied. Paste it into a blog post or page to show this photo.'),
      () => (this.message = markdown)
    );
  }
}
