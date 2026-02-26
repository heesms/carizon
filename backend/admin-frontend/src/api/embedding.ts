/**
 * 임베딩 관리 API
 */

import apiClient from './client';

export interface EmbeddingStatus {
  collection: {
    id: string;
    name: string;
    count: number;
    metadata?: any;
  };
  samples: Array<{
    id: string;
    metadata: Record<string, any>;
    document: string;
  }>;
}

export const embeddingApi = {
  // 임베딩 실행
  embedAll: () => apiClient.post('/admin/embedding/all'),
  embedCar: (carId: number) => apiClient.post(`/admin/embedding/car/${carId}`),
  embedRange: (fromCarId: number, toCarId: number) =>
    apiClient.post('/admin/embedding/range', null, {
      params: { fromCarId, toCarId },
    }),
  incrementalEmbed: (since?: string) =>
    apiClient.post('/admin/embedding/incremental', null, {
      params: since ? { since } : {},
    }),
  
  // 상태 조회
  getStatus: () =>
    apiClient.get<{ success: boolean; data: EmbeddingStatus }>('/admin/embedding/status'),
  
  getSamples: (limit = 10) =>
    apiClient.get<{ success: boolean; data: any[] }>('/admin/embedding/samples', {
      params: { limit },
    }),
};
