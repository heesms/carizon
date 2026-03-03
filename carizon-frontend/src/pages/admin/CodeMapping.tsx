import { useState } from 'react'
import {
  Typography,
  Card,
  Table,
  Button,
  Space,
  Select,
  Modal,
  Form,
  Input,
  message,
  Statistic,
  Row,
  Col,
  Tag,
  Popconfirm,
  Tabs,
} from 'antd'
import {
  ReloadOutlined,
  EditOutlined,
  CheckCircleOutlined,
  LockOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { codeMappingApi, CodeMapping as CodeMappingType } from '../../api/admin/codeMapping'
import { forcedMappingApi, ForcedMapping } from '../../api/admin/forcedMapping'

const { Title } = Typography

const PLATFORM_OPTIONS = [
  { value: 'ENCAR', label: 'ENCAR' },
  { value: 'KCAR', label: 'KCAR' },
  { value: 'CHACHACHA', label: 'CHACHACHA' },
  { value: 'CHUTCHA', label: 'CHUTCHA' },
  { value: 'CHARANCHA', label: 'CHARANCHA' },
  { value: 'TCAR', label: 'TCAR' },
]

const STATUS_OPTIONS = [
  { value: 'AUTO', label: 'AUTO' },
  { value: 'REVIEW', label: 'REVIEW' },
  { value: 'LOCKED', label: 'LOCKED' },
]

function buildColumns(
  onEdit: (r: CodeMappingType) => void,
  onLock: (r: CodeMappingType) => void,
) {
  return [
    { title: '플랫폼', dataIndex: 'platform_name', key: 'platform_name', width: 110 },
    {
      title: '상태',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (s: string) => {
        const colors: Record<string, string> = { AUTO: 'green', REVIEW: 'orange', LOCKED: 'purple' }
        return <Tag color={colors[s] || 'default'}>{s}</Tag>
      },
    },
    {
      title: '플랫폼 코드',
      key: 'p_codes',
      width: 200,
      render: (_: any, r: CodeMappingType) => (
        <div style={{ fontSize: 12 }}>
          <div>Maker: {r.p_maker_code || '-'}</div>
          <div>Group: {r.p_model_group_code || '-'}</div>
          <div>Model: {r.p_model_code || '-'}</div>
          <div>Trim: {r.p_trim_code || '-'}</div>
          <div>Grade: {r.p_grade_code || '-'}</div>
        </div>
      ),
    },
    {
      title: '플랫폼 이름',
      key: 'p_names',
      width: 200,
      render: (_: any, r: CodeMappingType) => (
        <div style={{ fontSize: 12 }}>
          <div>{r.p_maker_name_norm || '-'}</div>
          <div>{r.p_model_group_name_norm || '-'}</div>
          <div>{r.p_model_name_norm || '-'}</div>
          <div>{r.p_trim_name_norm || '-'}</div>
          <div>{r.p_grade_name_norm || '-'}</div>
        </div>
      ),
    },
    {
      title: '표준 코드',
      key: 'cz_codes',
      width: 200,
      render: (_: any, r: CodeMappingType) => (
        <div style={{ fontSize: 12 }}>
          <div>Maker: {r.maker_code || '-'}</div>
          <div>Group: {r.model_group_code || '-'}</div>
          <div>Model: {r.model_code || '-'}</div>
          <div>Trim: {r.trim_code || '-'}</div>
          <div>Grade: {r.grade_code || '-'}</div>
        </div>
      ),
    },
    {
      title: '신뢰도',
      dataIndex: 'confidence_score',
      key: 'confidence_score',
      width: 100,
      render: (score: number) => (
        <Tag color={score >= 0.93 ? 'green' : score >= 0.85 ? 'orange' : 'red'}>
          {(score * 100).toFixed(1)}%
        </Tag>
      ),
      sorter: (a: CodeMappingType, b: CodeMappingType) => a.confidence_score - b.confidence_score,
    },
    {
      title: '매칭 사유',
      dataIndex: 'match_reason',
      key: 'match_reason',
      width: 120,
      render: (reason: string) => {
        const colors: Record<string, string> = { PLATE_EQUAL: 'green', HIER_TEXT: 'blue', MANUAL: 'purple' }
        return <Tag color={colors[reason] || 'default'}>{reason}</Tag>
      },
    },
    {
      title: '작업',
      key: 'actions',
      fixed: 'right' as const,
      width: 150,
      render: (_: any, r: CodeMappingType) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => onEdit(r)} size="small">수정</Button>
          <Popconfirm title="이 매핑을 고정하시겠습니까?" onConfirm={() => onLock(r)}>
            <Button type="link" icon={<LockOutlined />} size="small" danger>고정</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]
}

export default function CodeMapping() {
  const [editingMapping, setEditingMapping] = useState<CodeMappingType | null>(null)
  const [form] = Form.useForm()
  const [autoMapPlatform, setAutoMapPlatform] = useState<string | undefined>()
  const queryClient = useQueryClient()

  const { data: stats } = useQuery({
    queryKey: ['code-mapping', 'stats'],
    queryFn: () => codeMappingApi.getStats(),
  })

  const updateMutation = useMutation({
    mutationFn: ({ platformName, data }: { platformName: string; data: any }) =>
      codeMappingApi.updateMapping(platformName, data),
    onSuccess: () => {
      message.success('코드 매핑이 수정되었습니다.')
      setEditingMapping(null)
      form.resetFields()
      queryClient.invalidateQueries({ queryKey: ['code-mapping'] })
    },
    onError: (error: any) => {
      message.error(`수정 실패: ${error.message || '알 수 없는 오류'}`)
    },
  })

  const autoMappingMutation = useMutation({
    mutationFn: ({ platformName, scope }: { platformName: string; scope: 'TODAY' | 'FULL' }) =>
      codeMappingApi.runAutoMapping(platformName, scope),
    onSuccess: (data) => {
      const result = (data as any).data?.data
      message.success(`${result?.platform} 매핑 완료: ${result?.mappedCount}건`)
      queryClient.invalidateQueries({ queryKey: ['code-mapping'] })
    },
    onError: (error: any) => {
      message.error(`자동 매핑 실패: ${error.message || '알 수 없는 오류'}`)
    },
  })

  const handleEdit = (record: CodeMappingType) => {
    setEditingMapping(record)
    form.setFieldsValue({
      maker_code: record.maker_code,
      model_group_code: record.model_group_code,
      model_code: record.model_code,
      trim_code: record.trim_code,
      grade_code: record.grade_code,
      status: record.status === 'LOCKED' ? 'LOCKED' : 'AUTO',
    })
  }

  const handleSave = () => {
    if (!editingMapping) return
    form.validateFields().then((values: any) => {
      updateMutation.mutate({
        platformName: editingMapping.platform_name,
        data: {
          p_maker_code: editingMapping.p_maker_code,
          p_model_group_code: editingMapping.p_model_group_code,
          p_model_code: editingMapping.p_model_code,
          p_trim_code: editingMapping.p_trim_code,
          p_grade_code: editingMapping.p_grade_code,
          ...values,
        },
      })
    })
  }

  const handleLock = (record: CodeMappingType) => {
    updateMutation.mutate({
      platformName: record.platform_name,
      data: {
        p_maker_code: record.p_maker_code,
        p_model_group_code: record.p_model_group_code,
        p_model_code: record.p_model_code,
        p_trim_code: record.p_trim_code,
        p_grade_code: record.p_grade_code,
        maker_code: record.maker_code,
        model_group_code: record.model_group_code,
        model_code: record.model_code,
        trim_code: record.trim_code,
        grade_code: record.grade_code,
        status: 'LOCKED',
      },
    })
  }

  const statusStats: Array<{ status: string; count: number }> = (stats as any)?.data?.data?.statusStats || []
  const totalCount = statusStats.reduce((s: number, x: any) => s + (x.count || 0), 0)

  return (
    <div>
      <Title level={2}>코드 매핑 관리</Title>

      {/* 통계 */}
      <Card style={{ marginBottom: 16 }}>
        <Row gutter={16}>
          <Col span={6}>
            <Statistic
              title="AUTO 상태"
              value={statusStats.find(s => s.status === 'AUTO')?.count || 0}
              prefix={<CheckCircleOutlined style={{ color: '#52c41a' }} />}
            />
          </Col>
          <Col span={6}>
            <Statistic
              title="REVIEW 상태"
              value={statusStats.find(s => s.status === 'REVIEW')?.count || 0}
              prefix={<CheckCircleOutlined style={{ color: '#faad14' }} />}
            />
          </Col>
          <Col span={6}>
            <Statistic
              title="LOCKED 상태"
              value={statusStats.find(s => s.status === 'LOCKED')?.count || 0}
              prefix={<LockOutlined style={{ color: '#722ed1' }} />}
            />
          </Col>
          <Col span={6}>
            <Statistic title="총 매핑 수" value={totalCount} />
          </Col>
        </Row>
      </Card>

      {/* 자동 매핑 */}
      <Card title="자동 매핑 실행" style={{ marginBottom: 16 }}>
        <Space wrap>
          <Select
            placeholder="플랫폼 선택"
            allowClear
            style={{ width: 160 }}
            options={PLATFORM_OPTIONS}
            value={autoMapPlatform}
            onChange={setAutoMapPlatform}
          />
          <Button
            type="primary"
            loading={autoMappingMutation.isPending}
            onClick={() => {
              if (!autoMapPlatform) { message.warning('플랫폼을 선택해주세요.'); return }
              autoMappingMutation.mutate({ platformName: autoMapPlatform, scope: 'TODAY' })
            }}
          >
            오늘 데이터 자동 매핑
          </Button>
          <Button
            loading={autoMappingMutation.isPending}
            onClick={() => {
              if (!autoMapPlatform) { message.warning('플랫폼을 선택해주세요.'); return }
              autoMappingMutation.mutate({ platformName: autoMapPlatform, scope: 'FULL' })
            }}
          >
            전체 자동 매핑
          </Button>
          <Button
            icon={<ReloadOutlined />}
            onClick={() => queryClient.invalidateQueries({ queryKey: ['code-mapping'] })}
          >
            통계 새로고침
          </Button>
        </Space>
      </Card>

      <Tabs
        items={[
          {
            key: 'review',
            label: 'REVIEW 매핑',
            children: <ReviewTab onEdit={handleEdit} onLock={handleLock} />,
          },
          {
            key: 'search',
            label: '전체 조회',
            children: <SearchTab onEdit={handleEdit} onLock={handleLock} />,
          },
          {
            key: 'forced',
            label: '강제 매핑',
            children: <ForcedMappingTab />,
          },
        ]}
      />

      {/* 수정 모달 */}
      <Modal
        title="코드 매핑 수정"
        open={!!editingMapping}
        onOk={handleSave}
        onCancel={() => { setEditingMapping(null); form.resetFields() }}
        confirmLoading={updateMutation.isPending}
        width={600}
      >
        {editingMapping && (
          <Form form={form} layout="vertical">
            <Form.Item label="플랫폼">
              <Input value={editingMapping.platform_name} disabled />
            </Form.Item>
            <Form.Item label="플랫폼 이름">
              <Input value={editingMapping.p_model_name_norm || ''} disabled />
            </Form.Item>
            <Row gutter={12}>
              <Col span={12}>
                <Form.Item label="표준 Maker 코드" name="maker_code" rules={[{ required: true }]}>
                  <Input placeholder="예: KIA" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item label="표준 Model Group 코드" name="model_group_code">
                  <Input placeholder="예: SUV" />
                </Form.Item>
              </Col>
            </Row>
            <Row gutter={12}>
              <Col span={12}>
                <Form.Item label="표준 Model 코드" name="model_code" rules={[{ required: true }]}>
                  <Input placeholder="예: KORANDO" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item label="표준 Trim 코드" name="trim_code">
                  <Input placeholder="예: SPORT" />
                </Form.Item>
              </Col>
            </Row>
            <Row gutter={12}>
              <Col span={12}>
                <Form.Item label="표준 Grade 코드" name="grade_code">
                  <Input placeholder="예: PREMIUM" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item label="상태" name="status" rules={[{ required: true }]}>
                  <Select options={[
                    { value: 'AUTO', label: 'AUTO (자동 업데이트 허용)' },
                    { value: 'LOCKED', label: 'LOCKED (수동 고정)' },
                  ]} />
                </Form.Item>
              </Col>
            </Row>
          </Form>
        )}
      </Modal>
    </div>
  )
}

// ─── REVIEW 탭 ────────────────────────────────────────────────────────────────

function ReviewTab({
  onEdit,
  onLock,
}: {
  onEdit: (r: CodeMappingType) => void
  onLock: (r: CodeMappingType) => void
}) {
  const [platform, setPlatform] = useState<string | undefined>()
  const [keyword, setKeyword] = useState('')
  const [appliedKeyword, setAppliedKeyword] = useState('')
  const [page, setPage] = useState(0)
  const size = 50

  const { data, isLoading } = useQuery({
    queryKey: ['code-mapping', 'review', platform, appliedKeyword, page],
    queryFn: () =>
      codeMappingApi.searchMappings({
        status: 'REVIEW',
        platform,
        keyword: appliedKeyword || undefined,
        page,
        size,
      }),
  })

  const applySearch = () => {
    setAppliedKeyword(keyword)
    setPage(0)
  }

  const items = (data as any)?.data?.data?.items || []
  const total = (data as any)?.data?.data?.total || 0
  const columns = buildColumns(onEdit, onLock)

  return (
    <Card>
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          placeholder="플랫폼"
          allowClear
          style={{ width: 160 }}
          options={PLATFORM_OPTIONS}
          value={platform}
          onChange={v => { setPlatform(v); setPage(0) }}
        />
        <Input
          placeholder="차량명/모델명 검색"
          style={{ width: 220 }}
          value={keyword}
          onChange={e => setKeyword(e.target.value)}
          onPressEnter={applySearch}
          allowClear
          onClear={() => { setAppliedKeyword(''); setPage(0) }}
        />
        <Button type="primary" icon={<SearchOutlined />} onClick={applySearch}>
          조회
        </Button>
      </Space>
      <Table
        columns={columns}
        dataSource={items}
        loading={isLoading}
        rowKey={(r: CodeMappingType) =>
          `${r.platform_name}-${r.p_maker_code}-${r.p_model_code}-${r.p_trim_code}`
        }
        pagination={{
          current: page + 1,
          pageSize: size,
          total,
          onChange: p => setPage(p - 1),
          showSizeChanger: false,
          showTotal: t => `총 ${t}건`,
        }}
        scroll={{ x: 1200 }}
      />
    </Card>
  )
}

// ─── 전체 조회 탭 ─────────────────────────────────────────────────────────────

function SearchTab({
  onEdit,
  onLock,
}: {
  onEdit: (r: CodeMappingType) => void
  onLock: (r: CodeMappingType) => void
}) {
  const [platform, setPlatform] = useState<string | undefined>()
  const [status, setStatus] = useState<string | undefined>()
  const [keyword, setKeyword] = useState('')
  const [appliedParams, setAppliedParams] = useState<{
    platform?: string
    status?: string
    keyword?: string
    page: number
  }>({ page: 0 })
  const size = 50

  const { data, isLoading } = useQuery({
    queryKey: ['code-mapping', 'search', appliedParams, size],
    queryFn: () => codeMappingApi.searchMappings({ ...appliedParams, size }),
  })

  const applySearch = () => {
    setAppliedParams({
      platform,
      status,
      keyword: keyword || undefined,
      page: 0,
    })
  }

  const items = (data as any)?.data?.data?.items || []
  const total = (data as any)?.data?.data?.total || 0
  const columns = buildColumns(onEdit, onLock)

  return (
    <Card>
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          placeholder="플랫폼"
          allowClear
          style={{ width: 160 }}
          options={PLATFORM_OPTIONS}
          value={platform}
          onChange={setPlatform}
        />
        <Select
          placeholder="상태"
          allowClear
          style={{ width: 130 }}
          options={STATUS_OPTIONS}
          value={status}
          onChange={setStatus}
        />
        <Input
          placeholder="차량명/모델명 검색"
          style={{ width: 220 }}
          value={keyword}
          onChange={e => setKeyword(e.target.value)}
          onPressEnter={applySearch}
          allowClear
        />
        <Button type="primary" icon={<SearchOutlined />} onClick={applySearch}>
          조회
        </Button>
      </Space>
      <Table
        columns={columns}
        dataSource={items}
        loading={isLoading}
        rowKey={(r: CodeMappingType) =>
          `${r.platform_name}-${r.p_maker_code}-${r.p_model_code}-${r.p_trim_code}-${r.p_grade_code}`
        }
        pagination={{
          current: appliedParams.page + 1,
          pageSize: size,
          total,
          onChange: p => setAppliedParams(prev => ({ ...prev, page: p - 1 })),
          showSizeChanger: false,
          showTotal: t => `총 ${t}건`,
        }}
        scroll={{ x: 1200 }}
      />
    </Card>
  )
}

// ─── 강제 매핑 탭 ─────────────────────────────────────────────────────────────

function ForcedMappingTab() {
  const [platform, setPlatform] = useState<string | undefined>()
  const [keyword, setKeyword] = useState('')
  const [appliedKeyword, setAppliedKeyword] = useState('')
  const [showModal, setShowModal] = useState(false)
  const [editingRecord, setEditingRecord] = useState<ForcedMapping | null>(null)
  const [form] = Form.useForm()
  const queryClient = useQueryClient()

  const { data: forcedMappings, isLoading } = useQuery({
    queryKey: ['forced-mapping', platform, appliedKeyword],
    queryFn: () => forcedMappingApi.getForcedMappings(platform, appliedKeyword || undefined),
  })

  const createMutation = useMutation({
    mutationFn: (data: Partial<ForcedMapping>) => forcedMappingApi.createForcedMapping(data),
    onSuccess: () => {
      message.success('강제 매핑이 추가되었습니다.')
      setShowModal(false)
      form.resetFields()
      queryClient.invalidateQueries({ queryKey: ['forced-mapping'] })
    },
    onError: (error: any) => {
      message.error(`추가 실패: ${error.message || '알 수 없는 오류'}`)
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ platformName, data }: { platformName: string; data: Partial<ForcedMapping> }) =>
      forcedMappingApi.updateForcedMapping(platformName, data),
    onSuccess: () => {
      message.success('강제 매핑이 수정되었습니다.')
      setShowModal(false)
      setEditingRecord(null)
      form.resetFields()
      queryClient.invalidateQueries({ queryKey: ['forced-mapping'] })
    },
    onError: (error: any) => {
      message.error(`수정 실패: ${error.message || '알 수 없는 오류'}`)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (record: ForcedMapping) =>
      forcedMappingApi.deleteForcedMapping(record.platform_name, {
        pMakerCode: record.p_maker_code,
        pModelGroupCode: record.p_model_group_code,
        pModelCode: record.p_model_code,
        pTrimCode: record.p_trim_code,
        pGradeCode: record.p_grade_code,
      }),
    onSuccess: () => {
      message.success('강제 매핑이 삭제되었습니다.')
      queryClient.invalidateQueries({ queryKey: ['forced-mapping'] })
    },
    onError: (error: any) => {
      message.error(`삭제 실패: ${error.message || '알 수 없는 오류'}`)
    },
  })

  const openCreate = () => {
    setEditingRecord(null)
    form.resetFields()
    setShowModal(true)
  }

  const openEdit = (record: ForcedMapping) => {
    setEditingRecord(record)
    form.setFieldsValue({
      platform_name: record.platform_name,
      p_maker_code: record.p_maker_code,
      p_model_group_code: record.p_model_group_code,
      p_model_code: record.p_model_code,
      p_trim_code: record.p_trim_code,
      p_grade_code: record.p_grade_code,
      maker_code: record.maker_code,
      model_group_code: record.model_group_code,
      model_code: record.model_code,
      trim_code: record.trim_code,
      grade_code: record.grade_code,
    })
    setShowModal(true)
  }

  const handleSubmit = () => {
    form.validateFields().then((values: any) => {
      if (editingRecord) {
        updateMutation.mutate({
          platformName: editingRecord.platform_name,
          data: {
            p_maker_code: editingRecord.p_maker_code,
            p_model_group_code: editingRecord.p_model_group_code,
            p_model_code: editingRecord.p_model_code,
            p_trim_code: editingRecord.p_trim_code,
            p_grade_code: editingRecord.p_grade_code,
            maker_code: values.maker_code,
            model_group_code: values.model_group_code,
            model_code: values.model_code,
            trim_code: values.trim_code,
            grade_code: values.grade_code,
          },
        })
      } else {
        createMutation.mutate(values)
      }
    })
  }

  const applySearch = () => {
    setAppliedKeyword(keyword)
  }

  const forcedData: ForcedMapping[] = (forcedMappings as any)?.data?.data || []

  const forcedColumns = [
    { title: '플랫폼', dataIndex: 'platform_name', key: 'platform_name', width: 110 },
    { title: 'Depth', dataIndex: 'depth', key: 'depth', width: 70 },
    {
      title: '플랫폼 코드',
      key: 'p_codes',
      width: 220,
      render: (_: any, r: ForcedMapping) => (
        <div style={{ fontSize: 12 }}>
          <div>Maker: {r.p_maker_code || '-'}</div>
          <div>Group: {r.p_model_group_code || '-'}</div>
          <div>Model: {r.p_model_code || '-'}</div>
          <div>Trim: {r.p_trim_code || '-'}</div>
          <div>Grade: {r.p_grade_code || '-'}</div>
        </div>
      ),
    },
    {
      title: '카리즌 코드',
      key: 'cz_codes',
      width: 220,
      render: (_: any, r: ForcedMapping) => (
        <div style={{ fontSize: 12 }}>
          <div>Maker: {r.maker_code}</div>
          <div>Group: {r.model_group_code || '-'}</div>
          <div>Model: {r.model_code}</div>
          <div>Trim: {r.trim_code || '-'}</div>
          <div>Grade: {r.grade_code || '-'}</div>
        </div>
      ),
    },
    {
      title: '작업',
      key: 'actions',
      fixed: 'right' as const,
      width: 130,
      render: (_: any, r: ForcedMapping) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} size="small" onClick={() => openEdit(r)}>수정</Button>
          <Popconfirm title="이 강제 매핑을 삭제하시겠습니까?" onConfirm={() => deleteMutation.mutate(r)}>
            <Button type="link" danger size="small">삭제</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Card style={{ marginBottom: 16 }}>
        <Space wrap>
          <Select
            placeholder="플랫폼"
            allowClear
            style={{ width: 160 }}
            options={PLATFORM_OPTIONS}
            value={platform}
            onChange={v => { setPlatform(v) }}
          />
          <Input
            placeholder="코드 키워드 검색 (maker/model)"
            style={{ width: 240 }}
            value={keyword}
            onChange={e => setKeyword(e.target.value)}
            onPressEnter={applySearch}
            allowClear
            onClear={() => setAppliedKeyword('')}
          />
          <Button type="primary" icon={<SearchOutlined />} onClick={applySearch}>
            조회
          </Button>
          <Button icon={<PlusOutlined />} type="primary" ghost onClick={openCreate}>
            강제 매핑 추가
          </Button>
        </Space>
      </Card>

      <Card>
        <Table
          columns={forcedColumns}
          dataSource={forcedData}
          loading={isLoading}
          rowKey={(r: ForcedMapping) =>
            `${r.platform_name}-${r.p_maker_code}-${r.p_model_group_code}-${r.p_model_code}`
          }
          pagination={{ pageSize: 20, showTotal: t => `총 ${t}건` }}
          scroll={{ x: 800 }}
        />
      </Card>

      <Modal
        title={editingRecord ? '강제 매핑 수정' : '강제 매핑 추가'}
        open={showModal}
        onOk={handleSubmit}
        onCancel={() => { setShowModal(false); setEditingRecord(null); form.resetFields() }}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        width={640}
      >
        <Form form={form} layout="vertical">
          <Form.Item label="플랫폼" name="platform_name" rules={[{ required: true, message: '플랫폼을 선택해주세요.' }]}>
            <Select disabled={!!editingRecord} options={PLATFORM_OPTIONS} placeholder="플랫폼 선택" />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="플랫폼 Maker 코드" name="p_maker_code">
                <Input placeholder="예: KIA" disabled={!!editingRecord} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="카리즌 Maker 코드" name="maker_code" rules={[{ required: true, message: '필수' }]}>
                <Input placeholder="예: KIA" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="플랫폼 Model Group 코드" name="p_model_group_code">
                <Input placeholder="예: SUV" disabled={!!editingRecord} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="카리즌 Model Group 코드" name="model_group_code">
                <Input placeholder="예: SUV" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="플랫폼 Model 코드" name="p_model_code">
                <Input placeholder="예: KORANDO_SPORT" disabled={!!editingRecord} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="카리즌 Model 코드" name="model_code" rules={[{ required: true, message: '필수' }]}>
                <Input placeholder="예: KORANDO" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="플랫폼 Trim 코드" name="p_trim_code">
                <Input placeholder="예: SPORT" disabled={!!editingRecord} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="카리즌 Trim 코드" name="trim_code">
                <Input placeholder="예: SPORT" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="플랫폼 Grade 코드" name="p_grade_code">
                <Input placeholder="예: PREMIUM" disabled={!!editingRecord} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="카리즌 Grade 코드" name="grade_code">
                <Input placeholder="예: PREMIUM" />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </div>
  )
}
