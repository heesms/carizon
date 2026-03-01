import { useState } from 'react'
import {
  Typography,
  Card,
  Table,
  Input,
  Button,
  Space,
  Select,
  Modal,
  Form,
  message,
  Statistic,
  Row,
  Col,
  Tag,
  Popconfirm,
} from 'antd'
import {
  SearchOutlined,
  ReloadOutlined,
  EditOutlined,
  CheckCircleOutlined,
  LockOutlined,
} from '@ant-design/icons'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { codeMappingApi, CodeMapping as CodeMappingType } from '../api/codeMapping'
import { forcedMappingApi, ForcedMapping } from '../api/forcedMapping'
import { Tabs } from 'antd'

const { Title } = Typography
const { Option } = Select

export default function CodeMapping() {
  const [platformFilter, setPlatformFilter] = useState<string | undefined>()
  const [page, setPage] = useState(0)
  const [size] = useState(50)
  const [editingMapping, setEditingMapping] = useState<CodeMappingType | null>(null)
  const [form] = Form.useForm()
  const queryClient = useQueryClient()

  // 통계 조회
  const { data: stats } = useQuery({
    queryKey: ['code-mapping', 'stats'],
    queryFn: () => codeMappingApi.getStats(),
  })

  // REVIEW 매핑 조회
  const { data: reviewData, isLoading } = useQuery({
    queryKey: ['code-mapping', 'review', platformFilter, page, size],
    queryFn: () => codeMappingApi.getReviewMappings({ platformName: platformFilter, page, size }),
  })

  // 매핑 수정
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

  // 자동 매핑 실행
  const autoMappingMutation = useMutation({
    mutationFn: ({ platformName, scope }: { platformName: string; scope: 'TODAY' | 'FULL' }) =>
      codeMappingApi.runAutoMapping(platformName, scope),
    onSuccess: (data) => {
      message.success(`${data.platform} 플랫폼 매핑 완료: ${data.mappedCount}건`)
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
      status: 'AUTO',
    })
  }

  const handleSave = () => {
    if (!editingMapping) return

    form.validateFields().then((values) => {
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

  const columns = [
    {
      title: '플랫폼',
      dataIndex: 'platform_name',
      key: 'platform_name',
      width: 100,
    },
    {
      title: '플랫폼 코드',
      key: 'platform_codes',
      render: (_: any, record: CodeMappingType) => (
        <div style={{ fontSize: '12px' }}>
          <div>Maker: {record.p_maker_code || '-'}</div>
          <div>Group: {record.p_model_group_code || '-'}</div>
          <div>Model: {record.p_model_code || '-'}</div>
          <div>Trim: {record.p_trim_code || '-'}</div>
          <div>Grade: {record.p_grade_code || '-'}</div>
        </div>
      ),
      width: 200,
    },
    {
      title: '플랫폼 이름',
      key: 'platform_names',
      render: (_: any, record: CodeMappingType) => (
        <div style={{ fontSize: '12px' }}>
          <div>{record.p_maker_name_norm || '-'}</div>
          <div>{record.p_model_group_name_norm || '-'}</div>
          <div>{record.p_model_name_norm || '-'}</div>
          <div>{record.p_trim_name_norm || '-'}</div>
          <div>{record.p_grade_name_norm || '-'}</div>
        </div>
      ),
      width: 200,
    },
    {
      title: '표준 코드',
      key: 'standard_codes',
      render: (_: any, record: CodeMappingType) => (
        <div style={{ fontSize: '12px' }}>
          <div>Maker: {record.maker_code || '-'}</div>
          <div>Group: {record.model_group_code || '-'}</div>
          <div>Model: {record.model_code || '-'}</div>
          <div>Trim: {record.trim_code || '-'}</div>
          <div>Grade: {record.grade_code || '-'}</div>
        </div>
      ),
      width: 200,
    },
    {
      title: '신뢰도',
      dataIndex: 'confidence_score',
      key: 'confidence_score',
      render: (score: number) => (
        <Tag color={score >= 0.93 ? 'green' : score >= 0.85 ? 'orange' : 'red'}>
          {(score * 100).toFixed(1)}%
        </Tag>
      ),
      width: 100,
      sorter: (a: CodeMappingType, b: CodeMappingType) => a.confidence_score - b.confidence_score,
    },
    {
      title: '매칭 사유',
      dataIndex: 'match_reason',
      key: 'match_reason',
      render: (reason: string) => {
        const colors: Record<string, string> = {
          PLATE_EQUAL: 'green',
          HIER_TEXT: 'blue',
          MANUAL: 'purple',
        }
        return <Tag color={colors[reason]}>{reason}</Tag>
      },
      width: 120,
    },
    {
      title: '작업',
      key: 'actions',
      fixed: 'right' as const,
      width: 150,
      render: (_: any, record: CodeMappingType) => (
        <Space>
          <Button
            type="link"
            icon={<EditOutlined />}
            onClick={() => handleEdit(record)}
            size="small"
          >
            수정
          </Button>
          <Popconfirm
            title="이 매핑을 고정하시겠습니까? (LOCKED 상태로 변경)"
            onConfirm={() => handleLock(record)}
          >
            <Button type="link" icon={<LockOutlined />} size="small" danger>
              고정
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Title level={2}>코드 매핑 관리</Title>

      {/* 통계 */}
      <Card style={{ marginBottom: 16 }}>
        <Row gutter={16}>
          <Col span={6}>
            <Statistic
              title="AUTO 상태"
              value={stats?.data?.statusStats?.find((s: any) => s.status === 'AUTO')?.count || 0}
              prefix={<CheckCircleOutlined style={{ color: '#52c41a' }} />}
            />
          </Col>
          <Col span={6}>
            <Statistic
              title="REVIEW 상태"
              value={stats?.data?.statusStats?.find((s: any) => s.status === 'REVIEW')?.count || 0}
              prefix={<CheckCircleOutlined style={{ color: '#faad14' }} />}
            />
          </Col>
          <Col span={6}>
            <Statistic
              title="LOCKED 상태"
              value={stats?.data?.statusStats?.find((s: any) => s.status === 'LOCKED')?.count || 0}
              prefix={<LockOutlined style={{ color: '#722ed1' }} />}
            />
          </Col>
          <Col span={6}>
            <Statistic
              title="총 매핑 수"
              value={
                stats?.data?.statusStats?.reduce((sum: number, s: any) => sum + s.count, 0) || 0
              }
            />
          </Col>
        </Row>
      </Card>

      {/* 필터 및 액션 */}
      <Card style={{ marginBottom: 16 }}>
        <Space>
          <Select
            placeholder="플랫폼 필터"
            allowClear
            style={{ width: 150 }}
            value={platformFilter}
            onChange={setPlatformFilter}
          >
            <Option value="ENCAR">ENCAR</Option>
            <Option value="CHACHACHA">CHACHACHA</Option>
            <Option value="CHUTCHA">CHUTCHA</Option>
            <Option value="KCAR">KCAR</Option>
          </Select>
          <Button
            icon={<ReloadOutlined />}
            onClick={() => queryClient.invalidateQueries({ queryKey: ['code-mapping'] })}
          >
            새로고침
          </Button>
          <Button
            type="primary"
            onClick={() => {
              if (platformFilter) {
                autoMappingMutation.mutate({ platformName: platformFilter, scope: 'TODAY' })
              } else {
                message.warning('플랫폼을 선택해주세요.')
              }
            }}
            loading={autoMappingMutation.isPending}
          >
            자동 매핑 실행
          </Button>
        </Space>
      </Card>

      {/* 탭: REVIEW 매핑 / 강제 매핑 */}
      <Tabs
        items={[
          {
            key: 'review',
            label: 'REVIEW 매핑',
            children: (
              <Card>
                <Table
                  columns={columns}
                  dataSource={reviewData?.data?.items || []}
                  loading={isLoading}
                  rowKey={(record) =>
                    `${record.platform_name}-${record.p_maker_code}-${record.p_model_code}`
                  }
                  pagination={{
                    current: page + 1,
                    pageSize: size,
                    total: reviewData?.data?.total || 0,
                    onChange: (newPage) => setPage(newPage - 1),
                    showSizeChanger: false,
                  }}
                  scroll={{ x: 1200 }}
                />
              </Card>
            ),
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
        onCancel={() => {
          setEditingMapping(null)
          form.resetFields()
        }}
        confirmLoading={updateMutation.isPending}
        width={600}
      >
        {editingMapping && (
          <Form form={form} layout="vertical">
            <Form.Item label="플랫폼">
              <Input value={editingMapping.platform_name} disabled />
            </Form.Item>
            <Form.Item label="플랫폼 코드">
              <Input
                value={`${editingMapping.p_maker_code || ''}/${editingMapping.p_model_code || ''}`}
                disabled
              />
            </Form.Item>
            <Form.Item label="플랫폼 이름">
              <Input value={editingMapping.p_model_name_norm || ''} disabled />
            </Form.Item>
            <Form.Item
              label="표준 Maker 코드"
              name="maker_code"
              rules={[{ required: true, message: 'Maker 코드를 입력해주세요.' }]}
            >
              <Input placeholder="예: KIA" />
            </Form.Item>
            <Form.Item label="표준 Model Group 코드" name="model_group_code">
              <Input placeholder="예: SUV" />
            </Form.Item>
            <Form.Item
              label="표준 Model 코드"
              name="model_code"
              rules={[{ required: true, message: 'Model 코드를 입력해주세요.' }]}
            >
              <Input placeholder="예: KORANDO" />
            </Form.Item>
            <Form.Item label="표준 Trim 코드" name="trim_code">
              <Input placeholder="예: SPORT" />
            </Form.Item>
            <Form.Item label="표준 Grade 코드" name="grade_code">
              <Input placeholder="예: PREMIUM" />
            </Form.Item>
            <Form.Item
              label="상태"
              name="status"
              rules={[{ required: true, message: '상태를 선택해주세요.' }]}
            >
              <Select>
                <Option value="AUTO">AUTO (자동 업데이트 허용)</Option>
                <Option value="LOCKED">LOCKED (수동 고정)</Option>
              </Select>
            </Form.Item>
          </Form>
        )}
      </Modal>
    </div>
  )
}

// 강제 매핑 탭 컴포넌트
function ForcedMappingTab() {
  const [platformFilter, setPlatformFilter] = useState<string | undefined>()
  const [editingMapping, setEditingMapping] = useState<ForcedMapping | null>(null)
  const [form] = Form.useForm()
  const queryClient = useQueryClient()

  const { data: forcedMappings, isLoading } = useQuery({
    queryKey: ['forced-mapping', platformFilter],
    queryFn: () => forcedMappingApi.getForcedMappings(platformFilter),
  })

  const createMutation = useMutation({
    mutationFn: (data: Partial<ForcedMapping>) => forcedMappingApi.createForcedMapping(data),
    onSuccess: () => {
      message.success('강제 매핑이 추가되었습니다.')
      setEditingMapping(null)
      form.resetFields()
      queryClient.invalidateQueries({ queryKey: ['forced-mapping'] })
    },
    onError: (error: any) => {
      message.error(`추가 실패: ${error.message || '알 수 없는 오류'}`)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (params: { platformName: string; pMakerCode?: string; pModelCode?: string }) =>
      forcedMappingApi.deleteForcedMapping(params.platformName, params),
    onSuccess: () => {
      message.success('강제 매핑이 삭제되었습니다.')
      queryClient.invalidateQueries({ queryKey: ['forced-mapping'] })
    },
    onError: (error: any) => {
      message.error(`삭제 실패: ${error.message || '알 수 없는 오류'}`)
    },
  })

  const forcedColumns = [
    {
      title: '플랫폼',
      dataIndex: 'platform_name',
      key: 'platform_name',
    },
    {
      title: 'Depth',
      dataIndex: 'depth',
      key: 'depth',
    },
    {
      title: '플랫폼 코드',
      key: 'platform_codes',
      render: (_: any, record: ForcedMapping) => (
        <div style={{ fontSize: '12px' }}>
          <div>Maker: {record.p_maker_code || '-'}</div>
          <div>Group: {record.p_model_group_code || '-'}</div>
          <div>Model: {record.p_model_code || '-'}</div>
          <div>Trim: {record.p_trim_code || '-'}</div>
          <div>Grade: {record.p_grade_code || '-'}</div>
        </div>
      ),
    },
    {
      title: '카리즌 코드',
      key: 'carizon_codes',
      render: (_: any, record: ForcedMapping) => (
        <div style={{ fontSize: '12px' }}>
          <div>Maker: {record.maker_code}</div>
          <div>Group: {record.model_group_code || '-'}</div>
          <div>Model: {record.model_code}</div>
          <div>Trim: {record.trim_code || '-'}</div>
          <div>Grade: {record.grade_code || '-'}</div>
        </div>
      ),
    },
    {
      title: '작업',
      key: 'actions',
      render: (_: any, record: ForcedMapping) => (
        <Popconfirm
          title="이 강제 매핑을 삭제하시겠습니까?"
          onConfirm={() =>
            deleteMutation.mutate({
              platformName: record.platform_name,
              pMakerCode: record.p_maker_code,
              pModelCode: record.p_model_code,
            })
          }
        >
          <Button type="link" danger size="small">
            삭제
          </Button>
        </Popconfirm>
      ),
    },
  ]

  return (
    <div>
      <Card style={{ marginBottom: 16 }}>
        <Space>
          <Select
            placeholder="플랫폼 필터"
            allowClear
            style={{ width: 150 }}
            value={platformFilter}
            onChange={setPlatformFilter}
          >
            <Option value="ENCAR">ENCAR</Option>
            <Option value="CHACHACHA">CHACHACHA</Option>
            <Option value="CHUTCHA">CHUTCHA</Option>
            <Option value="KCAR">KCAR</Option>
          </Select>
          <Button type="primary" onClick={() => setEditingMapping({} as ForcedMapping)}>
            강제 매핑 추가
          </Button>
        </Space>
      </Card>

      <Card>
        <Table
          columns={forcedColumns}
          dataSource={forcedMappings?.data || []}
          loading={isLoading}
          rowKey={(record) =>
            `${record.platform_name}-${record.p_maker_code}-${record.p_model_code}`
          }
          pagination={{ pageSize: 20 }}
        />
      </Card>

      <Modal
        title="강제 매핑 추가"
        open={!!editingMapping}
        onOk={() => {
          form.validateFields().then((values) => {
            createMutation.mutate({
              platform_name: values.platform_name,
              p_maker_code: values.p_maker_code,
              p_model_group_code: values.p_model_group_code,
              p_model_code: values.p_model_code,
              p_trim_code: values.p_trim_code,
              p_grade_code: values.p_grade_code,
              maker_code: values.maker_code,
              model_group_code: values.model_group_code,
              model_code: values.model_code,
              trim_code: values.trim_code,
              grade_code: values.grade_code,
            })
          })
        }}
        onCancel={() => {
          setEditingMapping(null)
          form.resetFields()
        }}
        confirmLoading={createMutation.isPending}
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label="플랫폼"
            name="platform_name"
            rules={[{ required: true, message: '플랫폼을 선택해주세요.' }]}
          >
            <Select>
              <Option value="ENCAR">ENCAR</Option>
              <Option value="CHACHACHA">CHACHACHA</Option>
              <Option value="CHUTCHA">CHUTCHA</Option>
              <Option value="KCAR">KCAR</Option>
            </Select>
          </Form.Item>
          <Form.Item label="플랫폼 Maker 코드" name="p_maker_code">
            <Input placeholder="예: KIA" />
          </Form.Item>
          <Form.Item label="플랫폼 Model 코드" name="p_model_code">
            <Input placeholder="예: KORANDO_SPORT" />
          </Form.Item>
          <Form.Item
            label="카리즌 Maker 코드"
            name="maker_code"
            rules={[{ required: true, message: 'Maker 코드를 입력해주세요.' }]}
          >
            <Input placeholder="예: KIA" />
          </Form.Item>
          <Form.Item
            label="카리즌 Model 코드"
            name="model_code"
            rules={[{ required: true, message: 'Model 코드를 입력해주세요.' }]}
          >
            <Input placeholder="예: KORANDO" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
