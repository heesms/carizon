import { useState } from 'react'
import {
  Typography,
  Card,
  Button,
  Space,
  Statistic,
  Table,
  Input,
  Form,
  message,
  DatePicker,
  Divider,
} from 'antd'
import {
  PlayCircleOutlined,
  ReloadOutlined,
  DatabaseOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { embeddingApi } from '../api/embedding'
import dayjs from 'dayjs'

const { Title } = Typography

export default function Embedding() {
  const queryClient = useQueryClient()
  const [rangeForm] = Form.useForm()
  const [singleForm] = Form.useForm()

  // 임베딩 현황 조회
  const { data: statusData, isLoading: statusLoading } = useQuery({
    queryKey: ['embedding', 'status'],
    queryFn: () => embeddingApi.getStatus(),
  })

  // 샘플 조회
  const { data: samplesData, isLoading: samplesLoading } = useQuery({
    queryKey: ['embedding', 'samples'],
    queryFn: () => embeddingApi.getSamples(10),
  })

  // 전체 임베딩
  const embedAllMutation = useMutation({
    mutationFn: () => embeddingApi.embedAll(),
    onSuccess: () => {
      message.success('전체 임베딩이 시작되었습니다')
      queryClient.invalidateQueries({ queryKey: ['embedding'] })
    },
    onError: (error: any) => {
      message.error('임베딩 실행 실패: ' + (error.response?.data?.message || error.message))
    },
  })

  // 증분 임베딩
  const incrementalEmbedMutation = useMutation({
    mutationFn: (since?: string) => embeddingApi.incrementalEmbed(since),
    onSuccess: () => {
      message.success('증분 임베딩이 시작되었습니다')
      queryClient.invalidateQueries({ queryKey: ['embedding'] })
    },
    onError: (error: any) => {
      message.error('임베딩 실행 실패: ' + (error.response?.data?.message || error.message))
    },
  })

  // 단일 차량 임베딩
  const embedCarMutation = useMutation({
    mutationFn: (carId: number) => embeddingApi.embedCar(carId),
    onSuccess: () => {
      message.success('차량 임베딩이 완료되었습니다')
      queryClient.invalidateQueries({ queryKey: ['embedding'] })
    },
    onError: (error: any) => {
      message.error('임베딩 실행 실패: ' + (error.response?.data?.message || error.message))
    },
  })

  // 범위 임베딩
  const embedRangeMutation = useMutation({
    mutationFn: ({ fromCarId, toCarId }: { fromCarId: number; toCarId: number }) =>
      embeddingApi.embedRange(fromCarId, toCarId),
    onSuccess: () => {
      message.success('범위 임베딩이 시작되었습니다')
      queryClient.invalidateQueries({ queryKey: ['embedding'] })
      rangeForm.resetFields()
    },
    onError: (error: any) => {
      message.error('임베딩 실행 실패: ' + (error.response?.data?.message || error.message))
    },
  })

  const handleIncrementalEmbed = () => {
    incrementalEmbedMutation.mutate()
  }

  const handleEmbedCar = (values: { carId: number }) => {
    embedCarMutation.mutate(values.carId)
  }

  const handleEmbedRange = (values: { fromCarId: number; toCarId: number }) => {
    embedRangeMutation.mutate(values)
  }

  const status = statusData?.data?.data
  const samples = samplesData?.data?.data || []

  const sampleColumns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 200,
    },
    {
      title: '문서',
      dataIndex: 'document',
      key: 'document',
      ellipsis: true,
    },
    {
      title: '메타데이터',
      dataIndex: 'metadata',
      key: 'metadata',
      render: (metadata: Record<string, any>) => (
        <pre style={{ margin: 0, fontSize: '12px' }}>
          {JSON.stringify(metadata, null, 2)}
        </pre>
      ),
    },
  ]

  return (
    <div>
      <Title level={2}>임베딩 관리</Title>

      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        {/* 임베딩 현황 */}
        <Card title="임베딩 현황" loading={statusLoading}>
          <Space size="large">
            <Statistic
              title="임베딩된 차량 수"
              value={status?.collection?.count || 0}
              prefix={<DatabaseOutlined />}
            />
            <Statistic
              title="컬렉션명"
              value={status?.collection?.name || '-'}
            />
          </Space>
        </Card>

        {/* 임베딩 실행 */}
        <Card title="임베딩 실행">
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Space>
              <Button
                type="primary"
                icon={<PlayCircleOutlined />}
                onClick={() => embedAllMutation.mutate()}
                loading={embedAllMutation.isPending}
                danger
              >
                전체 임베딩 실행
              </Button>
              <Button
                icon={<ReloadOutlined />}
                onClick={handleIncrementalEmbed}
                loading={incrementalEmbedMutation.isPending}
              >
                증분 임베딩 (최근 1시간)
              </Button>
            </Space>

            <Divider orientation="left">단일 차량 임베딩</Divider>
            <Form
              form={singleForm}
              layout="inline"
              onFinish={handleEmbedCar}
              style={{ marginBottom: 16 }}
            >
              <Form.Item
                name="carId"
                label="차량 ID"
                rules={[{ required: true, message: '차량 ID를 입력하세요' }]}
              >
                <Input type="number" placeholder="차량 ID" style={{ width: 150 }} />
              </Form.Item>
              <Form.Item>
                <Button
                  type="primary"
                  htmlType="submit"
                  loading={embedCarMutation.isPending}
                >
                  임베딩 실행
                </Button>
              </Form.Item>
            </Form>

            <Divider orientation="left">범위 임베딩</Divider>
            <Form
              form={rangeForm}
              layout="inline"
              onFinish={handleEmbedRange}
            >
              <Form.Item
                name="fromCarId"
                label="시작 차량 ID"
                rules={[{ required: true, message: '시작 차량 ID를 입력하세요' }]}
              >
                <Input type="number" placeholder="시작 ID" style={{ width: 150 }} />
              </Form.Item>
              <Form.Item
                name="toCarId"
                label="종료 차량 ID"
                rules={[{ required: true, message: '종료 차량 ID를 입력하세요' }]}
              >
                <Input type="number" placeholder="종료 ID" style={{ width: 150 }} />
              </Form.Item>
              <Form.Item>
                <Button
                  type="primary"
                  htmlType="submit"
                  loading={embedRangeMutation.isPending}
                >
                  범위 임베딩 실행
                </Button>
              </Form.Item>
            </Form>
          </Space>
        </Card>

        {/* 샘플 조회 */}
        <Card title="임베딩 샘플" loading={samplesLoading}>
          <Table
            columns={sampleColumns}
            dataSource={samples}
            rowKey="id"
            pagination={false}
            size="small"
          />
        </Card>
      </Space>
    </div>
  )
}
