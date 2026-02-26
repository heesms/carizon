/**
 * 배치 관리 API
 */

import apiClient from './client';

export interface BatchJob {
  job_id: string;
  job_name: string;
  job_type: string;
  description: string;
  cron_expression: string;
  is_active: boolean;
}

export interface BatchJobExecution {
  execution_id: number;
  job_id: string;
  job_name: string;
  status: string;
  started_at: string;
  ended_at?: string;
  duration_ms?: number;
  processed_items: number;
  success_items: number;
  failed_items: number;
  error_message?: string;
}

export interface BatchWorkflow {
  workflow_id: string;
  workflow_name: string;
  description: string;
  job_sequence: string;
  is_active: boolean;
  cron_expression: string;
}

export const batchApi = {
  // 작업 정의
  getJobDefinitions: () =>
    apiClient.get<{ success: boolean; data: BatchJob[] }>('/admin/batch/jobs'),
  
  executeJob: (jobId: string, config?: Record<string, any>) =>
    apiClient.post<{ success: boolean; data: string }>(
      `/admin/batch/jobs/${jobId}/execute`,
      config || {}
    ),
  
  getJobExecutions: (jobId: string, limit = 20) =>
    apiClient.get<{ success: boolean; data: BatchJobExecution[] }>(
      `/admin/batch/jobs/${jobId}/executions`,
      { params: { limit } }
    ),

  // 워크플로우
  getWorkflowDefinitions: () =>
    apiClient.get<{ success: boolean; data: BatchWorkflow[] }>('/admin/batch/workflows'),
  
  executeWorkflow: (workflowId: string, config?: Record<string, any>) =>
    apiClient.post<{ success: boolean; data: string }>(
      `/admin/batch/workflows/${workflowId}/execute`,
      config || {}
    ),
  
  getWorkflowExecutions: (workflowId: string, limit = 20) =>
    apiClient.get<{ success: boolean; data: any[] }>(
      `/admin/batch/workflows/${workflowId}/executions`,
      { params: { limit } }
    ),
};
