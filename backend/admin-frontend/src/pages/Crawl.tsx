import { useState } from 'react'
import {
  Typography,
  Card,
  Button,
  Table,
  Space,
  Tag,
  message,
  Select,
  DatePicker,
  Divider,
} from 'antd'
import { PlayCircleOutlined, MergeCellsOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { crawlApi, CrawlRun } from '../api/crawl'
import dayjs from 'dayjs'

const { Title } = Typography
const { Option } = Select

const PLATFORMS = [
  { value: 'encar', label: '엔카' },
  { value: 'kcar', label: 'KCar' },
  { value: 'cha', label: '차차차' },
  { value: 'chutcha', label: '첫차' },
  { value: 'charancha', label: '차란차' },
  { value: 'tcar', label: '티카' },
]

export default function Crawl() {
  const queryClient = useQueryClient()
  const [selectedPlatform, setSelectedPlatform] = useState<string>('')

  // 크롤링 현황 조회
  const { data: runsData, isLoading } = useQuery({
    queryKey: ['crawl', 'runs', selectedPlatform],
    queryFn: () => crawlApi.getRuns(20, selectedPlatform || undefined),
  })

  // 전체 크롤링 실행
  const runAllMutation = useMutation({
    mutationFn: () => crawlApi.runAll(),
    onSuccess: () => {
      message.success('전체 크롤링이 시작되었습니다')
      queryClient.invalidateQueries({ queryKey: ['crawl'] })
    },
    onError: (error: any) => {
      message.error('크롤링 실행 실패: ' + (error.response?.data?.message || error.message))
    },
  })

  // 플랫폼별 크롤링 실행
  const runPlatformMutation = useMutation({
    mutationFn: (platform: string) => crawlApi.runPlatform(platform),
    onSuccess: (_, platform) => {
      message.success(`${platform} 크롤링이 시작되었습니다`)
      queryClient.invalidateQueries({ queryKey: ['crawl'] })
    },
    onError: (error: any) => {
      message.error('크롤링 실행 실패: ' + (error.response?.data?.message || error.message))
    },
  })

  // 전체 머지 실행
  const runMergeMutation = useMutation({
    mutationFn: (bizDate?: string) => crawlApi.runMerge(bizDate),
    onSuccess: () => {
      message.success('데이터 머지가 시작되었습니다')
      queryClient.invalidateQueries({ queryKey: ['crawl'] })
    },
    onError: (error: any) => {
      message.error('머지 실행 실패: ' + (error.response?.data?.message || error.message))
    },
  })

  // 플랫폼별 머지 실행
  const runMergePlatformMutation = useMutation({
    mutationFn: ({ platform, bizDate }: { platform: string; bizDate?: string }) =>
      crawlApi.runMergePlatform(platform, bizDate),
    onSuccess: (_, { platform }) => {
      message.success(`${platform} 머지가 시작되었습니다`)
      queryClient.invalidateQueries({ queryKey: ['crawl'] })
    },
    onError: (error: any) => {
      message.error('머지 실행 실패: ' + (error.response?.data?.message || error.message))
    },
  })

  const handleRunAll = () => {
    runAllMutation.mutate()
  }

  const handleRunPlatform = (platform: string) => {
    runPlatformMutation.mutate(platform)
  }

  const handleMergeAll = () => {
    runMergeMutation.mutate()
  }

  const handleMergePlatform = (platform: string) => {
    runMergePlatformMutation.mutate({ platform })
  }

  const runs = runsData?.data?.data || []

  const columns = [
    {
      title: '실행 ID',
      dataIndex: 'run_id',
      key: 'run_id',
      width: 200,
    },
    {
      title: '플랫폼',
      dataIndex: 'source',
      key: 'source',
      width: 120,
      render: (source: string) => (
        <Tag color="blue">{PLATFORMS.find(p => p.value === source)?.label || source}</Tag>
      ),
    },
    {
      title: '상태',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => {
        const color =
          status === 'SUCCESS'
            ? 'green'
            : status === 'RUNNING'
            ? 'processing'
            : status === 'FAIL'
            ? 'red'
            : 'default'
        return <Tag color={color}>{status}</Tag>
      },
    },
    {
      title: '아이템 수',
      dataIndex: 'total_items',
      key: 'total_items',
      width: 100,
    },
    {
      title: '시작 시간',
      dataIndex: 'started_at',
      key: 'started_at',
      width: 180,
      render: (time: string) => (time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    {
      title: '종료 시간',
      dataIndex: 'ended_at',
      key: 'ended_at',
      width: 180,
      render: (time: string) => (time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
    {
      title: '메시지',
      dataIndex: 'message',
      key: 'message',
      ellipsis: true,
    },
  ]

  return (
    <div>
      <Title level={2}>크롤링 관리</Title>

      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        {/* 크롤링 실행 */}
        <Card title="크롤링 실행">
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Space>
              <Button
                type="primary"
                icon={<PlayCircleOutlined />}
                onClick={handleRunAll}
                loading={runAllMutation.isPending}
              >
                전체 크롤링 실행
              </Button>
            </Space>
            <Divider orientation="left">플랫폼별 크롤링</Divider>
            <Space wrap>
              {PLATFORMS.map(platform => (
                <Button
                  key={platform.value}
                  onClick={() => handleRunPlatform(platform.value)}
                  loading={runPlatformMutation.isPending}
                >
                  {platform.label}
                </Button>
              ))}
            </Space>
          </Space>
        </Card>

        {/* 데이터 머지 */}
        <Card title="데이터 머지">
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Space>
              <Button
                type="primary"
                icon={<MergeCellsOutlined />}
                onClick={handleMergeAll}
                loading={runMergeMutation.isPending}
              >
                전체 머지 실행
              </Button>
            </Space>
            <Divider orientation="left">플랫폼별 머지</Divider>
            <Space wrap>
              {PLATFORMS.map(platform => (
                <Button
                  key={platform.value}
                  onClick={() => handleMergePlatform(platform.value)}
                  loading={runMergePlatformMutation.isPending}
                >
                  {platform.label} 머지
                </Button>
              ))}
            </Space>
          </Space>
        </Card>

        {/* 크롤링 현황 */}
        <Card
          title="크롤링 현황"
          extra={
            <Select
              placeholder="플랫폼 필터"
              allowClear
              style={{ width: 150 }}
              value={selectedPlatform}
              onChange={setSelectedPlatform}
            >
              {PLATFORMS.map(platform => (
                <Option key={platform.value} value={platform.value}>
                  {platform.label}
                </Option>
              ))}
            </Select>
          }
        >
          <Table
            columns={columns}
            dataSource={runs}
            rowKey="run_id"
            loading={isLoading}
            pagination={{ pageSize: 20 }}
          />
        </Card>
      </Space>
    </div>
  )
}
