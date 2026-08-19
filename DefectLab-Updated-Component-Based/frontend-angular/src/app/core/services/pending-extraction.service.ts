import { Injectable } from '@angular/core';
import { Subscription, interval } from 'rxjs';
import { switchMap, takeWhile } from 'rxjs/operators';
import { DatasetFamily } from '../models/defectlab.model';
import { ToastService } from '../../shared/ui-toast/toast.service';
import { DefectLabApiService } from './defectlab-api.service';

interface PendingExtractionMarker {
  family: DatasetFamily;
  projectName: string;
  projectVersion: string;
  startedAt: number;
}

const STORAGE_KEY = 'defectlab.pendingExtraction';
const POLL_INTERVAL_MS = 5000;
const MAX_WAIT_MS = 15 * 60 * 1000;

/**
 * Source analysis runs synchronously on the backend: if the user refreshes
 * or navigates away before the request returns, the extraction keeps
 * running to completion server-side, but the original response has nowhere
 * to go. This remembers what was started (in localStorage, so it survives a
 * full page reload) and polls the dataset list afterward until a matching
 * dataset shows up, so the user still finds out.
 */
@Injectable({ providedIn: 'root' })
export class PendingExtractionService {
  private pollSubscription: Subscription | null = null;

  constructor(
    private readonly api: DefectLabApiService,
    private readonly toast: ToastService
  ) {
    const marker = this.readMarker();
    if (marker) {
      this.poll(marker);
    }
  }

  start(family: DatasetFamily, projectName: string, projectVersion: string): void {
    const marker: PendingExtractionMarker = {
      family, projectName: projectName.trim(), projectVersion, startedAt: Date.now()
    };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(marker));
    this.poll(marker);
  }

  clear(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.pollSubscription?.unsubscribe();
    this.pollSubscription = null;
  }

  private readMarker(): PendingExtractionMarker | null {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    try {
      const marker = JSON.parse(raw) as PendingExtractionMarker;
      if (!marker.startedAt || Date.now() - marker.startedAt > MAX_WAIT_MS) {
        localStorage.removeItem(STORAGE_KEY);
        return null;
      }
      return marker;
    } catch {
      localStorage.removeItem(STORAGE_KEY);
      return null;
    }
  }

  private poll(marker: PendingExtractionMarker): void {
    this.pollSubscription?.unsubscribe();
    this.pollSubscription = interval(POLL_INTERVAL_MS)
      .pipe(
        takeWhile(() => Date.now() - marker.startedAt <= MAX_WAIT_MS),
        switchMap(() => this.api.listDatasets())
      )
      .subscribe({
        next: datasets => {
          const match = datasets.find(dataset => this.matches(dataset, marker));
          if (match) {
            this.toast.success(`Background extraction finished: ${match.displayName}.`);
            this.clear();
          }
        },
        error: () => this.clear(),
        complete: () => this.clear()
      });
  }

  private matches(
    dataset: { datasetFamily: string; projectName: string; projectVersion: string; createdAt: string },
    marker: PendingExtractionMarker
  ): boolean {
    if (dataset.datasetFamily !== marker.family) return false;
    if (dataset.projectVersion !== marker.projectVersion) return false;
    if (new Date(dataset.createdAt).getTime() < marker.startedAt) return false;
    if (marker.projectName
        && dataset.projectName.trim().toLowerCase() !== marker.projectName.toLowerCase()) {
      return false;
    }
    return true;
  }
}
