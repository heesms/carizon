import { apiClient } from './client'

export interface Maker {
  maker_code: string
  maker_name: string
  country_code?: string
  maker_order?: number
}

export interface ModelGroup {
  maker_code: string
  model_group_code: string
  model_group_name: string
  class_order?: number
  use_code?: string
  use_name?: string
}

export interface Model {
  maker_code: string
  model_group_code: string
  model_code: string
  model_name: string
  car_order?: number
  from_year?: string
  to_year?: string
}

export interface Trim {
  maker_code: string
  model_group_code: string
  model_code: string
  trim_code: string
  trim_name: string
  model_order?: number
}

export interface Grade {
  maker_code: string
  model_group_code: string
  model_code: string
  trim_code: string
  grade_code: string
  grade_name: string
  grade_order?: number
  model_grade_code?: string
}

export interface CodeMapping {
  platform_name: string
  p_maker_code?: string
  p_model_group_code?: string
  p_model_code?: string
  p_trim_code?: string
  p_grade_code?: string
  p_maker_name_norm?: string
  p_model_group_name_norm?: string
  p_model_name_norm?: string
  p_trim_name_norm?: string
  p_grade_name_norm?: string
  confidence_score: number
  match_reason: string
  status: string
  first_seen: string
  last_seen: string
  forced_depth?: number
}

export const carizonCodeApi = {
  getMakers: () => {
    return apiClient.get<Maker[]>('/admin/carizon-codes/makers')
  },

  getModelGroups: (makerCode: string) => {
    return apiClient.get<ModelGroup[]>(`/admin/carizon-codes/model-groups?makerCode=${makerCode}`)
  },

  getModels: (makerCode: string, modelGroupCode: string) => {
    return apiClient.get<Model[]>(
      `/admin/carizon-codes/models?makerCode=${makerCode}&modelGroupCode=${modelGroupCode}`
    )
  },

  getTrims: (makerCode: string, modelGroupCode: string, modelCode: string) => {
    return apiClient.get<Trim[]>(
      `/admin/carizon-codes/trims?makerCode=${makerCode}&modelGroupCode=${modelGroupCode}&modelCode=${modelCode}`
    )
  },

  getGrades: (
    makerCode: string,
    modelGroupCode: string,
    modelCode: string,
    trimCode: string
  ) => {
    return apiClient.get<Grade[]>(
      `/admin/carizon-codes/grades?makerCode=${makerCode}&modelGroupCode=${modelGroupCode}&modelCode=${modelCode}&trimCode=${trimCode}`
    )
  },

  getMappings: (params: {
    makerCode?: string
    modelGroupCode?: string
    modelCode?: string
    trimCode?: string
    gradeCode?: string
  }) => {
    const queryParams = new URLSearchParams()
    if (params.makerCode) queryParams.append('makerCode', params.makerCode)
    if (params.modelGroupCode) queryParams.append('modelGroupCode', params.modelGroupCode)
    if (params.modelCode) queryParams.append('modelCode', params.modelCode)
    if (params.trimCode) queryParams.append('trimCode', params.trimCode)
    if (params.gradeCode) queryParams.append('gradeCode', params.gradeCode)
    return apiClient.get<CodeMapping[]>(`/admin/carizon-codes/mappings?${queryParams}`)
  },
}
