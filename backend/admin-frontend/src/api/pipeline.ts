import { apiClient } from './client'

export interface PipelineResult {
  crawl?: string
  merge?: {
    mergedCount: number
    durationMs: number
  }
  codeMapping?: {
    mappedCount: number
    durationMs: number
  }
  masterMerge?: {
    mergedCount: number
    durationMs: number
  }
  totalDurationMs: number
  bizDate: string
}

export const pipelineApi = {
  runFullPipeline: (bizDate?: string, skipCrawl: boolean = false) => {
    const params = new URLSearchParams()
    if (bizDate) params.append('bizDate', bizDate)
    params.append('skipCrawl', String(skipCrawl))
    return apiClient.post<PipelineResult>(`/admin/pipeline/full?${params}`)
  },

  runWorkflow: (bizDate?: string) => {
    const params = bizDate ? `?bizDate=${bizDate}` : ''
    return apiClient.post<string>(`/admin/pipeline/workflow${params}`)
  },

  runMergeOnly: (bizDate?: string) => {
    const params = bizDate ? `?bizDate=${bizDate}` : ''
    return apiClient.post<{ mergedCount: number; durationMs: number; bizDate: string }>(
      `/admin/pipeline/merge-only${params}`
    )
  },

  runCodeMappingOnly: (scope: 'TODAY' | 'FULL' = 'TODAY') => {
    return apiClient.post<{ mappedCount: number; durationMs: number; scope: string }>(
      `/admin/pipeline/code-mapping-only?scope=${scope}`
    )
  },

  runMasterMergeOnly: (bizDate?: string) => {
    const params = bizDate ? `?bizDate=${bizDate}` : ''
    return apiClient.post<{ mergedCount: number; durationMs: number; bizDate: string }>(
      `/admin/pipeline/master-merge-only${params}`
    )
  },
}
