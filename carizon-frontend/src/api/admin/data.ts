/**
 * 데이터 조회 API
 */

import apiClient from './client';

export interface CarMaster {
  car_id: number;
  car_no: string;
  maker_code: string;
  model_code: string;
  maker_name: string;
  model_name: string;
  year: number;
  fuel: string;
  transmission: string;
  body_type: string;
  price: number;
  km: number;
  created_at: string;
  updated_at: string;
}

export interface PlatformCar {
  platform_car_id: number;
  platform_name: string;
  platform_car_key: string;
  car_no: string;
  car_id: number;
  maker_code: string;
  model_code: string;
  maker_name: string;
  model_name: string;
  price: number;
  km: number;
  status: string;
  fuel: string;
  transmission: string;
  body_type: string;
  region: string;
  created_at: string;
  updated_at: string;
  last_seen_date: string;
}

export interface DataResponse<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export const dataApi = {
  getCarMaster: (params?: {
    carId?: number;
    carNo?: string;
    makerCode?: string;
    modelCode?: string;
    page?: number;
    size?: number;
  }) =>
    apiClient.get<{ success: boolean; data: DataResponse<CarMaster> }>(
      '/admin/data/car-master',
      { params }
    ),

  getPlatformCar: (params?: {
    platformCarId?: number;
    platformName?: string;
    carId?: number;
    carNo?: string;
    page?: number;
    size?: number;
  }) =>
    apiClient.get<{ success: boolean; data: DataResponse<PlatformCar> }>(
      '/admin/data/platform-car',
      { params }
    ),
};
