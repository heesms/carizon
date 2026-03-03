/**
 * 검색 관리 API
 */

import apiClient from './client';

export interface SearchTestParams {
  q?: string;
  makerCode?: string;
  priceMin?: number;
  priceMax?: number;
  sort?: string;
  page?: number;
  size?: number;
}

export const searchApi = {
  reindexAll: () => apiClient.post('/admin/search/reindex'),
  incrementalIndex: (since?: string) =>
    apiClient.post('/admin/search/incremental', null, {
      params: since ? { since } : {},
    }),
  batchIndex: (limit = 1000) =>
    apiClient.post('/admin/search/batch', null, {
      params: { limit },
    }),

  testSearch: (params: SearchTestParams) =>
    apiClient.post<{ success: boolean; data: any }>('/admin/search/test', null, {
      params,
    }),
};
