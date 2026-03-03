import { apiClient } from './client'

export interface BlogPostResponse {
  postId?: number
  title: string
  content: string
  status?: string
  carCount?: number
  message?: string
}

export const blogPostApi = {
  postToWordPress: async (
    modelCode: string,
    limit: number = 10,
    status: string = 'draft'
  ): Promise<BlogPostResponse> => {
    const response = await apiClient.post(
      `/admin/recommendation/weekly-best/model/${modelCode}/post-to-wordpress`,
      null,
      { params: { limit, status } }
    )
    return response.data.data
  },

  generatePostContent: async (
    modelCode: string,
    limit: number = 10
  ): Promise<BlogPostResponse> => {
    const response = await apiClient.post(
      `/admin/recommendation/weekly-best/model/${modelCode}/generate`,
      null,
      { params: { limit } }
    )
    return response.data.data
  },
}
