import axios from 'axios';

const apiClient = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

export const carsApi = {
  // Get list of cars with pagination and filters
  getCars: async (params = {}) => {
    const response = await apiClient.get('/cars', { params });
    return response.data;
  },

  // Get car detail
  getCarDetail: async (carId) => {
    const response = await apiClient.get(`/cars/${carId}`);
    return response.data;
  },

  // Get price history
  getPriceHistory: async (carId, platformCarId = null) => {
    const params = platformCarId ? { platformCarId } : {};
    const response = await apiClient.get(`/cars/${carId}/price-history`, { params });
    return response.data;
  },
};

export default apiClient;
