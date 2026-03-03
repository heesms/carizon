import { useState } from 'react'
import {
  Typography,
  Card,
  Tabs,
  Table,
  Input,
  Button,
  Space,
  Form,
} from 'antd'
import { SearchOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { dataApi } from '../../api/admin/data'

const { Title } = Typography

export default function Data() {
  const [activeTab, setActiveTab] = useState('car-master')
  const [carMasterParams, setCarMasterParams] = useState({
    carId: undefined as number | undefined,
    carNo: undefined as string | undefined,
    makerCode: undefined as string | undefined,
    modelCode: undefined as string | undefined,
    page: 0,
    size: 20,
  })
  const [platformCarParams, setPlatformCarParams] = useState({
    platformCarId: undefined as number | undefined,
    platformName: undefined as string | undefined,
    carId: undefined as number | undefined,
    carNo: undefined as string | undefined,
    page: 0,
    size: 20,
  })

  const { data: carMasterData, isLoading: carMasterLoading } = useQuery({
    queryKey: ['data', 'car-master', carMasterParams],
    queryFn: () => dataApi.getCarMaster(carMasterParams),
  })

  const { data: platformCarData, isLoading: platformCarLoading } = useQuery({
    queryKey: ['data', 'platform-car', platformCarParams],
    queryFn: () => dataApi.getPlatformCar(platformCarParams),
  })

  const carMasterColumns = [
    { title: '차량 ID', dataIndex: 'car_id', key: 'car_id', width: 100 },
    { title: '차량번호', dataIndex: 'car_no', key: 'car_no', width: 150 },
    { title: '제조사 코드', dataIndex: 'maker_code', key: 'maker_code', width: 120 },
    { title: '모델 코드', dataIndex: 'model_code', key: 'model_code', width: 120 },
    { title: '제조사명', dataIndex: 'maker_name', key: 'maker_name', width: 120 },
    { title: '모델명', dataIndex: 'model_name', key: 'model_name', width: 150 },
    { title: '연식', dataIndex: 'year', key: 'year', width: 80 },
    { title: '연료', dataIndex: 'fuel', key: 'fuel', width: 80 },
    { title: '변속기', dataIndex: 'transmission', key: 'transmission', width: 100 },
    { title: '차종', dataIndex: 'body_type', key: 'body_type', width: 100 },
    { title: '가격', dataIndex: 'price', key: 'price', width: 120, render: (v: number) => v?.toLocaleString() },
    { title: '주행거리', dataIndex: 'km', key: 'km', width: 120, render: (v: number) => v?.toLocaleString() + 'km' },
    { title: '생성일', dataIndex: 'created_at', key: 'created_at', width: 180 },
    { title: '수정일', dataIndex: 'updated_at', key: 'updated_at', width: 180 },
  ]

  const platformCarColumns = [
    { title: '플랫폼 차량 ID', dataIndex: 'platform_car_id', key: 'platform_car_id', width: 120 },
    { title: '플랫폼', dataIndex: 'platform_name', key: 'platform_name', width: 100 },
    { title: '플랫폼 키', dataIndex: 'platform_car_key', key: 'platform_car_key', width: 150 },
    { title: '차량번호', dataIndex: 'car_no', key: 'car_no', width: 150 },
    { title: '차량 ID', dataIndex: 'car_id', key: 'car_id', width: 100 },
    { title: '제조사 코드', dataIndex: 'maker_code', key: 'maker_code', width: 120 },
    { title: '모델 코드', dataIndex: 'model_code', key: 'model_code', width: 120 },
    { title: '제조사명', dataIndex: 'maker_name', key: 'maker_name', width: 120 },
    { title: '모델명', dataIndex: 'model_name', key: 'model_name', width: 150 },
    { title: '가격', dataIndex: 'price', key: 'price', width: 120, render: (v: number) => v?.toLocaleString() },
    { title: '주행거리', dataIndex: 'km', key: 'km', width: 120, render: (v: number) => v?.toLocaleString() + 'km' },
    { title: '상태', dataIndex: 'status', key: 'status', width: 80 },
    { title: '연료', dataIndex: 'fuel', key: 'fuel', width: 80 },
    { title: '변속기', dataIndex: 'transmission', key: 'transmission', width: 100 },
    { title: '차종', dataIndex: 'body_type', key: 'body_type', width: 100 },
    { title: '지역', dataIndex: 'region', key: 'region', width: 100 },
    { title: '생성일', dataIndex: 'created_at', key: 'created_at', width: 180 },
    { title: '수정일', dataIndex: 'updated_at', key: 'updated_at', width: 180 },
    { title: '최종 확인일', dataIndex: 'last_seen_date', key: 'last_seen_date', width: 120 },
  ]

  const handleCarMasterSearch = (values: any) => {
    setCarMasterParams({
      ...carMasterParams,
      ...values,
      carId: values.carId ? Number(values.carId) : undefined,
      page: 0,
    })
  }

  const handlePlatformCarSearch = (values: any) => {
    setPlatformCarParams({
      ...platformCarParams,
      ...values,
      platformCarId: values.platformCarId ? Number(values.platformCarId) : undefined,
      carId: values.carId ? Number(values.carId) : undefined,
      page: 0,
    })
  }

  const carMasterItems = carMasterData?.data?.data?.items || []
  const carMasterTotal = carMasterData?.data?.data?.total || 0
  const platformCarItems = platformCarData?.data?.data?.items || []
  const platformCarTotal = platformCarData?.data?.data?.total || 0

  return (
    <div>
      <Title level={2}>데이터 조회</Title>
      <Card>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            {
              key: 'car-master',
              label: '차량 마스터',
              children: (
                <Space direction="vertical" size="large" style={{ width: '100%' }}>
                  <Card size="small">
                    <Form layout="inline" onFinish={handleCarMasterSearch}>
                      <Form.Item name="carId" label="차량 ID">
                        <Input placeholder="차량 ID" style={{ width: 150 }} />
                      </Form.Item>
                      <Form.Item name="carNo" label="차량번호">
                        <Input placeholder="차량번호" style={{ width: 150 }} />
                      </Form.Item>
                      <Form.Item name="makerCode" label="제조사 코드">
                        <Input placeholder="제조사 코드" style={{ width: 150 }} />
                      </Form.Item>
                      <Form.Item name="modelCode" label="모델 코드">
                        <Input placeholder="모델 코드" style={{ width: 150 }} />
                      </Form.Item>
                      <Form.Item>
                        <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>검색</Button>
                      </Form.Item>
                    </Form>
                  </Card>
                  <Table
                    columns={carMasterColumns}
                    dataSource={carMasterItems}
                    rowKey="car_id"
                    loading={carMasterLoading}
                    pagination={{
                      current: carMasterParams.page + 1,
                      pageSize: carMasterParams.size,
                      total: carMasterTotal,
                      onChange: (page, size) => setCarMasterParams({ ...carMasterParams, page: page - 1, size }),
                    }}
                    scroll={{ x: 1500 }}
                  />
                </Space>
              ),
            },
            {
              key: 'platform-car',
              label: '플랫폼 차량',
              children: (
                <Space direction="vertical" size="large" style={{ width: '100%' }}>
                  <Card size="small">
                    <Form layout="inline" onFinish={handlePlatformCarSearch}>
                      <Form.Item name="platformCarId" label="플랫폼 차량 ID">
                        <Input placeholder="플랫폼 차량 ID" style={{ width: 150 }} />
                      </Form.Item>
                      <Form.Item name="platformName" label="플랫폼명">
                        <Input placeholder="플랫폼명" style={{ width: 150 }} />
                      </Form.Item>
                      <Form.Item name="carId" label="차량 ID">
                        <Input placeholder="차량 ID" style={{ width: 150 }} />
                      </Form.Item>
                      <Form.Item name="carNo" label="차량번호">
                        <Input placeholder="차량번호" style={{ width: 150 }} />
                      </Form.Item>
                      <Form.Item>
                        <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>검색</Button>
                      </Form.Item>
                    </Form>
                  </Card>
                  <Table
                    columns={platformCarColumns}
                    dataSource={platformCarItems}
                    rowKey="platform_car_id"
                    loading={platformCarLoading}
                    pagination={{
                      current: platformCarParams.page + 1,
                      pageSize: platformCarParams.size,
                      total: platformCarTotal,
                      onChange: (page, size) => setPlatformCarParams({ ...platformCarParams, page: page - 1, size }),
                    }}
                    scroll={{ x: 1800 }}
                  />
                </Space>
              ),
            },
          ]}
        />
      </Card>
    </div>
  )
}
