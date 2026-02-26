import { useState } from 'react'
import {
  Typography,
  Card,
  Button,
  Space,
  Form,
  Input,
  Select,
  InputNumber,
  message,
  Table,
  Divider,
} from 'antd'
import {
  ReloadOutlined,
  PlayCircleOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { searchApi, SearchTestParams } from '../api/search'

const { Title } = Typography
const { Option } = Select

export default function Search() {
  const queryClient = useQueryClient()
  const [searchForm] = Form.useForm()
  const [testResult, setTestResult] = useState<any>(null)

  // 전체 재인덱싱
  const reindexMutation = useMutation({
    mutationFn: () => searchApi.reindexAll(),
    onSuccess: () => {
      message.success('전체 재인덱싱이 시작되었습니다')
      queryClient.invalidateQueries({ queryKey: ['search'] })
    },
    onError: (error: any) => {
      message.error('재인덱싱 실패: ' + (error.response?.data?.message || error.message))
    },
  })

  // 증분 인덱싱
  const incrementalIndexMutation = useMutation({
    mutationFn: (since?: string) => searchApi.incrementalIndex(since),
    onSuccess: () => {
      message.success('증분 인덱싱이 시작되었습니다')
      queryClient.invalidateQueries({ queryKey: ['search'] })
    },
    onError: (error: any) => {
      message.error('인덱싱 실패: ' + (error.response?.data?.message || error.message))
    },
  })

  // 배치 인덱싱
  const batchIndexMutation = useMutation({
    mutationFn: (limit: number) => searchApi.batchIndex(limit),
    onSuccess: () => {
      message.success('배치 인덱싱이 시작되었습니다')
      queryClient.invalidateQueries({ queryKey: ['search'] })
    },
    onError: (error: any) => {
      message.error('인덱싱 실패: ' + (error.response?.data?.message || error.message))
    },
  })

  // 검색 테스트
  const testSearchMutation = useMutation({
    mutationFn: (params: SearchTestParams) => searchApi.testSearch(params),
    onSuccess: (response) => {
      setTestResult(response.data.data)
      message.success('검색 테스트 완료')
    },
    onError: (error: any) => {
      message.error('검색 테스트 실패: ' + (error.response?.data?.message || error.message))
    },
  })

  const handleTestSearch = (values: any) => {
    testSearchMutation.mutate({
      q: values.q,
      makerCode: values.makerCode,
      priceMin: values.priceMin,
      priceMax: values.priceMax,
      sort: values.sort,
      page: values.page || 0,
      size: values.size || 20,
    })
  }

  const handleIncrementalIndex = () => {
    incrementalIndexMutation.mutate()
  }

  const handleBatchIndex = (limit: number = 1000) => {
    batchIndexMutation.mutate(limit)
  }

  const testColumns = [
    { title: '차량 ID', dataIndex: 'car_id', key: 'car_id', width: 100 },
    { title: '차량번호', dataIndex: 'car_no', key: 'car_no', width: 150 },
    { title: '제조사명', dataIndex: 'maker_name', key: 'maker_name', width: 120 },
    { title: '모델명', dataIndex: 'model_name', key: 'model_name', width: 150 },
    { title: '가격', dataIndex: 'price', key: 'price', width: 120, render: (v: number) => v?.toLocaleString() },
    { title: '주행거리', dataIndex: 'km', key: 'km', width: 120, render: (v: number) => v?.toLocaleString() + 'km' },
    { title: '연식', dataIndex: 'year', key: 'year', width: 80 },
  ]

  return (
    <div>
      <Title level={2}>검색 관리</Title>

      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        {/* 인덱싱 실행 */}
        <Card title="인덱싱 실행">
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Space>
              <Button
                type="primary"
                danger
                icon={<ReloadOutlined />}
                onClick={() => reindexMutation.mutate()}
                loading={reindexMutation.isPending}
              >
                전체 재인덱싱
              </Button>
              <Button
                icon={<PlayCircleOutlined />}
                onClick={handleIncrementalIndex}
                loading={incrementalIndexMutation.isPending}
              >
                증분 인덱싱 (최근 1시간)
              </Button>
              <Button
                onClick={() => handleBatchIndex(1000)}
                loading={batchIndexMutation.isPending}
              >
                배치 인덱싱 (1000건)
              </Button>
            </Space>
          </Space>
        </Card>

        {/* 검색 테스트 */}
        <Card title="검색 테스트">
          <Form
            form={searchForm}
            layout="inline"
            onFinish={handleTestSearch}
            style={{ marginBottom: 16 }}
          >
            <Form.Item name="q" label="검색어">
              <Input placeholder="검색어" style={{ width: 200 }} />
            </Form.Item>
            <Form.Item name="makerCode" label="제조사 코드">
              <Input placeholder="제조사 코드" style={{ width: 150 }} />
            </Form.Item>
            <Form.Item name="priceMin" label="최소 가격">
              <InputNumber placeholder="최소 가격" style={{ width: 150 }} />
            </Form.Item>
            <Form.Item name="priceMax" label="최대 가격">
              <InputNumber placeholder="최대 가격" style={{ width: 150 }} />
            </Form.Item>
            <Form.Item name="sort" label="정렬">
              <Select placeholder="정렬" style={{ width: 150 }}>
                <Option value="LOW_PRICE">낮은 가격순</Option>
                <Option value="LOW_KM">낮은 주행거리순</Option>
                <Option value="NEW_YEAR">최신 연식순</Option>
              </Select>
            </Form.Item>
            <Form.Item name="page" label="페이지">
              <InputNumber min={0} defaultValue={0} style={{ width: 100 }} />
            </Form.Item>
            <Form.Item name="size" label="크기">
              <InputNumber min={1} max={100} defaultValue={20} style={{ width: 100 }} />
            </Form.Item>
            <Form.Item>
              <Button
                type="primary"
                htmlType="submit"
                icon={<SearchOutlined />}
                loading={testSearchMutation.isPending}
              >
                검색 테스트
              </Button>
            </Form.Item>
          </Form>

          {testResult && (
            <>
              <Divider />
              <Space direction="vertical" style={{ width: '100%' }}>
                <div>
                  <strong>검색 결과:</strong> 총 {testResult.total || 0}건
                </div>
                <Table
                  columns={testColumns}
                  dataSource={testResult.items || []}
                  rowKey="car_id"
                  pagination={false}
                  scroll={{ x: 800 }}
                />
              </Space>
            </>
          )}
        </Card>
      </Space>
    </div>
  )
}
