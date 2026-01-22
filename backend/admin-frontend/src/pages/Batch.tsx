import { useState } from 'react'
import {
  Typography,
  Card,
  Tabs,
  Table,
  Button,
  Space,
  Tag,
  message,
  Modal,
  Form,
  Input,
} from 'antd'
import { PlayCircleOutlined, HistoryOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { batchApi, BatchJob, BatchJobExecution, BatchWorkflow } from '../api/batch'
import dayjs from 'dayjs'

const { Title } = Typography
const { TextArea } = Input

export default function Batch() {
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState('jobs')
  const [selectedJob, setSelectedJob] = useState<string | null>(null)
  const [selectedWorkflow, setSelectedWorkflow] = useState<string | null>(null)
  const [configForm] = Form.useForm()

  // 작업 정의 조회
  const { data: jobsData, isLoading: jobsLoading } = useQuery({
    queryKey: ['batch', 'jobs'],
    queryFn: () => batchApi.getJobDefinitions(),
  })

  // 워크플로우 정의 조회
  const { data: workflowsData, isLoading: workflowsLoading } = useQuery({
    queryKey: ['batch', 'workflows'],
    queryFn: () => batchApi.getWorkflowDefinitions(),
  })

  // 작업 실행 이력 조회
  const { data: jobExecutionsData, isLoading: executionsLoading } = useQuery({
    queryKey: ['batch', 'job-executions', selectedJob],
    queryFn: () => batchApi.getJobExecutions(selectedJob!, 20),
    enabled: !!selectedJob,
  })

  // 작업 실행
  const executeJobMutation = useMutation({
    mutationFn: ({ jobId, config }: { jobId: string; config?: Record<string, any> }) =>
      batchApi.executeJob(jobId, config),
    onSuccess: () => {
      message.success('작업이 시작되었습니다')
      queryClient.invalidateQueries({ queryKey: ['batch'] })
    },
    onError: (error: any) => {
      message.error('작업 실행 실패: ' + (error.response?.data?.message || error.message))
    },
  })

  // 워크플로우 실행
  const executeWorkflowMutation = useMutation({
    mutationFn: ({ workflowId, config }: { workflowId: string; config?: Record<string, any> }) =>
      batchApi.executeWorkflow(workflowId, config),
    onSuccess: () => {
      message.success('워크플로우가 시작되었습니다')
      queryClient.invalidateQueries({ queryKey: ['batch'] })
    },
    onError: (error: any) => {
      message.error('워크플로우 실행 실패: ' + (error.response?.data?.message || error.message))
    },
  })

  const handleExecuteJob = (jobId: string) => {
    configForm.resetFields()
    setSelectedJob(jobId)
    Modal.confirm({
      title: '작업 실행',
      content: (
        <Form form={configForm} layout="vertical">
          <Form.Item name="config" label="설정 (JSON)">
            <TextArea rows={4} placeholder='{"bizDate": "2024-01-01", "limit": 1000}' />
          </Form.Item>
        </Form>
      ),
      onOk: () => {
        const values = configForm.getFieldsValue()
        let config: Record<string, any> = {}
        if (values.config) {
          try {
            config = JSON.parse(values.config)
          } catch (e) {
            message.error('JSON 형식이 올바르지 않습니다')
            return Promise.reject()
          }
        }
        executeJobMutation.mutate({ jobId, config })
      },
    })
  }

  const handleExecuteWorkflow = (workflowId: string) => {
    executeWorkflowMutation.mutate({ workflowId })
  }

  const jobs = jobsData?.data?.data || []
  const workflows = workflowsData?.data?.data || []
  const executions = jobExecutionsData?.data?.data || []

  const jobColumns = [
    { title: '작업 ID', dataIndex: 'job_id', key: 'job_id', width: 150 },
    { title: '작업명', dataIndex: 'job_name', key: 'job_name', width: 200 },
    { title: '타입', dataIndex: 'job_type', key: 'job_type', width: 120 },
    { title: '설명', dataIndex: 'description', key: 'description', ellipsis: true },
    {
      title: '스케줄',
      dataIndex: 'cron_expression',
      key: 'cron_expression',
      width: 150,
    },
    {
      title: '상태',
      dataIndex: 'is_active',
      key: 'is_active',
      width: 100,
      render: (active: boolean) => (
        <Tag color={active ? 'green' : 'red'}>{active ? '활성' : '비활성'}</Tag>
      ),
    },
    {
      title: '작업',
      key: 'action',
      width: 150,
      render: (_: any, record: BatchJob) => (
        <Space>
          <Button
            type="primary"
            size="small"
            icon={<PlayCircleOutlined />}
            onClick={() => handleExecuteJob(record.job_id)}
            loading={executeJobMutation.isPending}
          >
            실행
          </Button>
          <Button
            size="small"
            icon={<HistoryOutlined />}
            onClick={() => setSelectedJob(record.job_id)}
          >
            이력
          </Button>
        </Space>
      ),
    },
  ]

  const workflowColumns = [
    { title: '워크플로우 ID', dataIndex: 'workflow_id', key: 'workflow_id', width: 150 },
    { title: '워크플로우명', dataIndex: 'workflow_name', key: 'workflow_name', width: 200 },
    { title: '설명', dataIndex: 'description', key: 'description', ellipsis: true },
    {
      title: '스케줄',
      dataIndex: 'cron_expression',
      key: 'cron_expression',
      width: 150,
    },
    {
      title: '상태',
      dataIndex: 'is_active',
      key: 'is_active',
      width: 100,
      render: (active: boolean) => (
        <Tag color={active ? 'green' : 'red'}>{active ? '활성' : '비활성'}</Tag>
      ),
    },
    {
      title: '작업',
      key: 'action',
      width: 150,
      render: (_: any, record: BatchWorkflow) => (
        <Button
          type="primary"
          size="small"
          icon={<PlayCircleOutlined />}
          onClick={() => handleExecuteWorkflow(record.workflow_id)}
          loading={executeWorkflowMutation.isPending}
        >
          실행
        </Button>
      ),
    },
  ]

  const executionColumns = [
    { title: '실행 ID', dataIndex: 'execution_id', key: 'execution_id', width: 100 },
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
            : status === 'FAILED'
            ? 'red'
            : 'default'
        return <Tag color={color}>{status}</Tag>
      },
    },
    {
      title: '처리 건수',
      key: 'items',
      width: 150,
      render: (_: any, record: BatchJobExecution) => (
        <span>
          {record.processed_items || 0} / {record.success_items || 0} / {record.failed_items || 0}
        </span>
      ),
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
      title: '소요 시간',
      dataIndex: 'duration_ms',
      key: 'duration_ms',
      width: 120,
      render: (ms: number) => (ms ? `${(ms / 1000).toFixed(1)}초` : '-'),
    },
    {
      title: '에러',
      dataIndex: 'error_message',
      key: 'error_message',
      ellipsis: true,
    },
  ]

  return (
    <div>
      <Title level={2}>배치 관리</Title>

      <Card>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            {
              key: 'jobs',
              label: '작업 관리',
              children: (
                <Space direction="vertical" size="large" style={{ width: '100%' }}>
                  <Table
                    columns={jobColumns}
                    dataSource={jobs}
                    rowKey="job_id"
                    loading={jobsLoading}
                    pagination={false}
                  />
                  {selectedJob && (
                    <Card title={`작업 실행 이력: ${selectedJob}`} size="small">
                      <Table
                        columns={executionColumns}
                        dataSource={executions}
                        rowKey="execution_id"
                        loading={executionsLoading}
                        pagination={{ pageSize: 10 }}
                        size="small"
                      />
                    </Card>
                  )}
                </Space>
              ),
            },
            {
              key: 'workflows',
              label: '워크플로우 관리',
              children: (
                <Table
                  columns={workflowColumns}
                  dataSource={workflows}
                  rowKey="workflow_id"
                  loading={workflowsLoading}
                  pagination={false}
                />
              ),
            },
          ]}
        />
      </Card>
    </div>
  )
}
