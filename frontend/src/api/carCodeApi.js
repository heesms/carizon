import axios from 'axios';

const apiClient = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

export const carCodeApi = {
  // Get all makers
  getMakers: async () => {
    const response = await apiClient.get('/code/makers');
    return response.data;
  },

  // Get model groups with optional maker filter
  getModelGroups: async (maker) => {
    const params = maker ? { maker } : {};
    const response = await apiClient.get('/code/model-groups', { params });
    return response.data;
  },

  // Get models with optional filters
  getModels: async (maker, modelGroup) => {
    const params = {};
    if (maker) params.maker = maker;
    if (modelGroup) params.modelGroup = modelGroup;
    const response = await apiClient.get('/code/models', { params });
    return response.data;
  },

  // Get trims with optional filters
  getTrims: async (maker, modelGroup, model) => {
    const params = {};
    if (maker) params.maker = maker;
    if (modelGroup) params.modelGroup = modelGroup;
    if (model) params.model = model;
    const response = await apiClient.get('/code/trims', { params });
    return response.data;
  },

  // Get grades with optional filters
  getGrades: async (maker, modelGroup, model, trim) => {
    const params = {};
    if (maker) params.maker = maker;
    if (modelGroup) params.modelGroup = modelGroup;
    if (model) params.model = model;
    if (trim) params.trim = trim;
    const response = await apiClient.get('/code/grades', { params });
    return response.data;
  },

  // Get representative model image
  getModelImage: async (modelCode) => {
    const response = await apiClient.get('/code/model-image', {
      params: { modelCode }
    });
    return response.data;
  },

  // Get all model images
  getModelImages: async (modelCode) => {
    const response = await apiClient.get('/code/model-images', {
      params: { modelCode }
    });
    return response.data;
  },
};

export default apiClient;
