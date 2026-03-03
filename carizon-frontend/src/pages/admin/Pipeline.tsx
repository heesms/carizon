import { useState } from 'react'
import {
  Typography,
  Card,
  Button,
  Space,
  message,
  Descriptions,
  Tag,
  Divider,
  DatePicker,
} from 'antd'
import { PlayCircleOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { useMutation } from '@tanstack/react-query'
import { pipelineApi } from '../../api/admin/pipeline'
import dayjs, { Dayjs } from 'dayjs'

const { Title } = Typography

export default function Pipeline() {
  const [bizDate, setBizDate] = useState<Dayjs | null>(dayjs())

  const fullPipelineMutation = useMutation({
    mutationFn: () => pipelineApi.runFullPipeline(bizDate?.format('YYYY-MM-DD'), true),
    onSuccess: () => { message.success('파이프라인 실행 완료') },
    onError: (error: any) => { message.error(`파이프라인 실행 실패: ${error.message || '알 수 없는 오류'}`) },
  })

  const workflowMutation = useMutation({
    mutationFn: () => pipelineApi.runWorkflow(bizDate?.format('YYYY-MM-DD')),
    onSuccess: (data) => { message.success(data.data || '워크플로우 실행 시작됨') },
    onError: (error: any) => { message.error(`워크플로우 실행 실패: ${error.message || '알 수 없는 오류'}`) },
  })

  const mergeOnlyMutation = useMutation({
    mutationFn: () => pipelineApi.runMergeOnly(bizDate?.format('YYYY-MM-DD')),
    onSuccess: (data) => { message.success(`머지 완료: ${data.data?.mergedCount}건 (${data.data?.durationMs}ms)`) },
    onError: (error: any) => { message.error(`머지 실행 실패: ${error.message || '알 수 없는 오류'}`) },
  })

  const codeMappingOnlyMutation = useMutation({
    mutationFn: () => pipelineApi.runCodeMappingOnly('TODAY'),
    onSuccess: (data) => { message.success(`코드 매핑 완료: ${data.data?.mappedCount}건 (${data.data?.durationMs}ms)`) },
    onError: (error: any) => { message.error(`코드 매핑 실행 실패: ${error.message || '알 수 없는 오류'}`) },
  })

  const masterMergeOnlyMutation = useMutation({
    mutationFn: () => pipelineApi.runMasterMergeOnly(bizDate?.format('YYYY-MM-DD')),
    onSuccess: (data) => { message.success(`car_master 머지 완료: ${data.data?.mergedCount}건 (${data.data?.durationMs}ms)`) },
    onError: (error: any) => { message.error(`car_master 머지 실행 실패: ${error.message || '알 수 없는 오류'}`) },
  })

  const result = fullPipelineMutation.data?.data

  return (
    <div>
      <Title level={2}>파이프라인 실행</Title>

      <Card style={{ marginBottom: 16 }}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Space>
            <span>기준일자:</span>
            <DatePicker value={bizDate} onChange={setBizDate} format="YYYY-MM-DD" />
          </Space>
          <Divider />
          <Space wrap>
            <Button type="primary" size="large" icon={<PlayCircleOutlined />} onClick={() => fullPipelineMutation.mutate()} loading={fullPipelineMutation.isPending}>
              전체 파이프라인 실행 (머지→코드매핑→car_master)
            </Button>
            <Button size="large" icon={<ThunderboltOutlined />} onClick={() => workflowMutation.mutate()} loading={workflowMutation.isPending}>
              워크플로우 실행 (크롤링 포함)
            </Button>
          </Space>
          <Divider />
          <Space wrap>
            <Button onClick={() => mergeOnlyMutation.mutate()} loading={mergeOnlyMutation.isPending}>머지만 실행 (raw_* → platform_car)</Button>
            <Button onClick={() => codeMappingOnlyMutation.mutate()} loading={codeMappingOnlyMutation.isPending}>코드 매핑만 실행 (platform_car → cz_code_map)</Button>
            <Button onClick={() => masterMergeOnlyMutation.mutate()} loading={masterMergeOnlyMutation.isPending}>car_master 머지만 실행 (→ car_master)</Button>
          </Space>
        </Space>
      </Card>

      {result && (
        <Card title="실행 결과">
          <Descriptions column={1} bordered>
            <Descriptions.Item label="기준일자">{result.bizDate}</Descriptions.Item>
            {result.merge && (
              <Descriptions.Item label="머지">
                <Space><Tag color="blue">{result.merge.mergedCount}건</Tag><span>{result.merge.durationMs}ms</span></Space>
              </Descriptions.Item>
            )}
            {result.codeMapping && (
              <Descriptions.Item label="코드 매핑">
                <Space><Tag color="green">{result.codeMapping.mappedCount}건</Tag><span>{result.codeMapping.durationMs}ms</span></Space>
              </Descriptions.Item>
            )}
            {result.masterMerge && (
              <Descriptions.Item label="car_master 머지">
                <Space><Tag color="purple">{result.masterMerge.mergedCount}건</Tag><span>{result.masterMerge.durationMs}ms</span></Space>
              </Descriptions.Item>
            )}
            <Descriptions.Item label="총 소요 시간"><Tag color="red">{result.totalDurationMs}ms</Tag></Descriptions.Item>
          </Descriptions>
        </Card>
      )}
    </div>
  )
}
