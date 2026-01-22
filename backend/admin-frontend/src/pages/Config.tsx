import { useState } from 'react'
import {
  Typography,
  Card,
  Tabs,
  Switch,
  Form,
  Button,
  message,
  Table,
  Input,
  InputNumber,
  Space,
  Select,
  Modal,
  Tag,
} from 'antd'
import { SaveOutlined, EditOutlined } from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { configApi } from '../api/config'

const { Title } = Typography
const { TextArea } = Input
const { Option } = Select

export default function Config() {
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState('search-mode')
  const [editingPrompt, setEditingPrompt] = useState<any>(null)
  const [promptForm] = Form.useForm()
  const [matchingForm] = Form.useForm()

  // 검색 모드 조회
  const { data: searchModeData } = useQuery({
    queryKey: ['config', 'search-mode'],
    queryFn: () => configApi.getSearchMode(),
  })

  // LLM 프롬프트 조회
  const { data: promptsData, isLoading: promptsLoading } = useQuery({
    queryKey: ['config', 'llm', 'prompts'],
    queryFn: () => configApi.getLlmPrompts(),
  })

  // LLM 매칭 가중치 조회
  const { data: matchingData, isLoading: matchingLoading } = useQuery({
    queryKey: ['config', 'llm', 'matching'],
    queryFn: () => configApi.getLlmMatching(),
  })

  // 프리셋 목록 조회
  const { data: presetsData } = useQuery({
    queryKey: ['config', 'llm', 'matching', 'preset'],
    queryFn: () => configApi.getLlmMatchingPresets(),
  })

  // 검색 모드 저장
  const saveSearchModeMutation = useMutation({
    mutationFn: (config: { useMeilisearch: boolean; fallbackToDb: boolean }) =>
      configApi.setSearchMode(config),
    onSuccess: () => {
      message.success('검색 모드가 저장되었습니다')
      queryClient.invalidateQueries({ queryKey: ['config', 'search-mode'] })
    },
    onError: (error: any) => {
      message.error('저장 실패: ' + (error.response?.data?.message || error.message))
    },
  })

  // 프롬프트 수정
  const updatePromptMutation = useMutation({
    mutationFn: ({ type, data }: { type: string; data: { prompt_text: string; is_active: boolean } }) =>
      configApi.updateLlmPrompt(type, data),
    onSuccess: () => {
      message.success('프롬프트가 저장되었습니다')
      queryClient.invalidateQueries({ queryKey: ['config', 'llm', 'prompts'] })
      setEditingPrompt(null)
      promptForm.resetFields()
    },
    onError: (error: any) => {
      message.error('저장 실패: ' + (error.response?.data?.message || error.message))
    },
  })

  // 가중치 수정
  const updateMatchingMutation = useMutation({
    mutationFn: (key: string, value: number, description?: string) =>
      configApi.updateLlmMatching(key, { config_value: value, description }),
    onSuccess: () => {
      message.success('가중치가 저장되었습니다')
      queryClient.invalidateQueries({ queryKey: ['config', 'llm', 'matching'] })
    },
    onError: (error: any) => {
      message.error('저장 실패: ' + (error.response?.data?.message || error.message))
    },
  })

  // 가중치 일괄 수정
  const updateMatchingBatchMutation = useMutation({
    mutationFn: (configs: Record<string, number>) =>
      configApi.updateLlmMatchingBatch(configs),
    onSuccess: () => {
      message.success('가중치가 일괄 저장되었습니다')
      queryClient.invalidateQueries({ queryKey: ['config', 'llm', 'matching'] })
    },
    onError: (error: any) => {
      message.error('저장 실패: ' + (error.response?.data?.message || error.message))
    },
  })

  // 프리셋 적용
  const applyPresetMutation = useMutation({
    mutationFn: (presetName: string) => configApi.applyLlmMatchingPreset(presetName),
    onSuccess: () => {
      message.success('프리셋이 적용되었습니다')
      queryClient.invalidateQueries({ queryKey: ['config', 'llm', 'matching'] })
    },
    onError: (error: any) => {
      message.error('적용 실패: ' + (error.response?.data?.message || error.message))
    },
  })

  const handleSaveSearchMode = (values: any) => {
    saveSearchModeMutation.mutate(values)
  }

  const handleEditPrompt = (prompt: any) => {
    setEditingPrompt(prompt)
    promptForm.setFieldsValue({
      prompt_text: prompt.prompt_text,
      is_active: prompt.is_active,
    })
  }

  const handleSavePrompt = (values: any) => {
    if (editingPrompt) {
      updatePromptMutation.mutate({
        type: editingPrompt.prompt_type,
        data: values,
      })
    }
  }

  const handleSaveMatching = (values: Record<string, number>) => {
    updateMatchingBatchMutation.mutate(values)
  }

  const handleApplyPreset = (presetName: string) => {
    applyPresetMutation.mutate(presetName)
  }

  const searchMode = searchModeData?.data?.data || { useMeilisearch: true, fallbackToDb: true }
  const prompts = promptsData?.data?.data || []
  const matchingConfigs = matchingData?.data?.data || []
  const presets = presetsData?.data?.data || []

  const promptColumns = [
    { title: '타입', dataIndex: 'prompt_type', key: 'prompt_type', width: 150 },
    {
      title: '프롬프트',
      dataIndex: 'prompt_text',
      key: 'prompt_text',
      ellipsis: true,
      render: (text: string) => (
        <div style={{ maxWidth: 500, whiteSpace: 'pre-wrap' }}>{text}</div>
      ),
    },
    {
      title: '활성화',
      dataIndex: 'is_active',
      key: 'is_active',
      width: 100,
      render: (active: boolean) => (
        <Tag color={active ? 'green' : 'red'}>{active ? '활성' : '비활성'}</Tag>
      ),
    },
    {
      title: '수정일',
      dataIndex: 'updated_at',
      key: 'updated_at',
      width: 180,
    },
    {
      title: '작업',
      key: 'action',
      width: 100,
      render: (_: any, record: any) => (
        <Button
          type="link"
          icon={<EditOutlined />}
          onClick={() => handleEditPrompt(record)}
        >
          수정
        </Button>
      ),
    },
  ]

  // 가중치를 그룹별로 분류
  const groupedMatching = matchingConfigs.reduce((acc: any, config: any) => {
    const group = config.config_key.split('.')[0]
    if (!acc[group]) {
      acc[group] = []
    }
    acc[group].push(config)
    return acc
  }, {})

  return (
    <div>
      <Title level={2}>설정 관리</Title>

      <Card>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            {
              key: 'search-mode',
              label: '검색 모드',
              children: (
                <Form
                  initialValues={searchMode}
                  onFinish={handleSaveSearchMode}
                  layout="vertical"
                  style={{ maxWidth: 600 }}
                >
                  <Form.Item
                    name="useMeilisearch"
                    label="Meilisearch 사용"
                    valuePropName="checked"
                  >
                    <Switch />
                  </Form.Item>
                  <Form.Item
                    name="fallbackToDb"
                    label="DB 폴백 사용"
                    valuePropName="checked"
                  >
                    <Switch />
                  </Form.Item>
                  <Form.Item>
                    <Button type="primary" htmlType="submit" icon={<SaveOutlined />}>
                      저장
                    </Button>
                  </Form.Item>
                </Form>
              ),
            },
            {
              key: 'llm-prompts',
              label: 'LLM 프롬프트',
              children: (
                <Space direction="vertical" size="large" style={{ width: '100%' }}>
                  <Table
                    columns={promptColumns}
                    dataSource={prompts}
                    rowKey="id"
                    loading={promptsLoading}
                    pagination={false}
                  />
                  <Modal
                    title="프롬프트 수정"
                    open={!!editingPrompt}
                    onCancel={() => {
                      setEditingPrompt(null)
                      promptForm.resetFields()
                    }}
                    footer={null}
                    width={800}
                  >
                    <Form
                      form={promptForm}
                      layout="vertical"
                      onFinish={handleSavePrompt}
                    >
                      <Form.Item
                        name="prompt_text"
                        label="프롬프트 내용"
                        rules={[{ required: true, message: '프롬프트 내용을 입력하세요' }]}
                      >
                        <TextArea rows={10} />
                      </Form.Item>
                      <Form.Item
                        name="is_active"
                        label="활성화"
                        valuePropName="checked"
                      >
                        <Switch />
                      </Form.Item>
                      <Form.Item>
                        <Button type="primary" htmlType="submit" icon={<SaveOutlined />}>
                          저장
                        </Button>
                      </Form.Item>
                    </Form>
                  </Modal>
                </Space>
              ),
            },
            {
              key: 'llm-matching',
              label: 'LLM 매칭 가중치',
              children: (
                <Space direction="vertical" size="large" style={{ width: '100%' }}>
                  <Card size="small">
                    <Space>
                      <span>프리셋 적용:</span>
                      {presets.map((preset: string) => (
                        <Button
                          key={preset}
                          size="small"
                          onClick={() => handleApplyPreset(preset)}
                          loading={applyPresetMutation.isPending}
                        >
                          {preset}
                        </Button>
                      ))}
                    </Space>
                  </Card>
                  <Form
                    form={matchingForm}
                    layout="vertical"
                    onFinish={handleSaveMatching}
                    initialValues={matchingConfigs.reduce((acc: any, config: any) => {
                      acc[config.config_key] = config.config_value
                      return acc
                    }, {})}
                  >
                    {Object.entries(groupedMatching).map(([group, configs]: [string, any]) => (
                      <Card key={group} title={group} size="small" style={{ marginBottom: 16 }}>
                        {(configs as any[]).map((config: any) => (
                          <Form.Item
                            key={config.config_key}
                            name={config.config_key}
                            label={
                              <span>
                                {config.config_key.split('.').pop()}
                                <br />
                                <small style={{ color: '#999' }}>{config.description}</small>
                              </span>
                            }
                            rules={[
                              { required: true, message: '값을 입력하세요' },
                              { type: 'number', min: 0, max: 1, message: '0~1 사이의 값을 입력하세요' },
                            ]}
                          >
                            <InputNumber
                              min={0}
                              max={1}
                              step={0.01}
                              style={{ width: '100%' }}
                            />
                          </Form.Item>
                        ))}
                      </Card>
                    ))}
                    <Form.Item>
                      <Button type="primary" htmlType="submit" icon={<SaveOutlined />}>
                        일괄 저장
                      </Button>
                    </Form.Item>
                  </Form>
                </Space>
              ),
            },
          ]}
        />
      </Card>
    </div>
  )
}
