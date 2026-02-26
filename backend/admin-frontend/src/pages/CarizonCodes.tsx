import { useState } from 'react'
import {
  Typography,
  Card,
  Table,
  Button,
  Space,
  Select,
  Modal,
  Descriptions,
  Tag,
  message,
} from 'antd'
import { SearchOutlined, EyeOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { carizonCodeApi, Maker, ModelGroup, Model, Trim, Grade, CodeMapping } from '../api/carizonCode'

const { Title } = Typography
const { Option } = Select

export default function CarizonCodes() {
  const [selectedMaker, setSelectedMaker] = useState<string | undefined>()
  const [selectedModelGroup, setSelectedModelGroup] = useState<string | undefined>()
  const [selectedModel, setSelectedModel] = useState<string | undefined>()
  const [selectedTrim, setSelectedTrim] = useState<string | undefined>()
  const [selectedCode, setSelectedCode] = useState<{
    type: 'maker' | 'modelGroup' | 'model' | 'trim' | 'grade'
    makerCode?: string
    modelGroupCode?: string
    modelCode?: string
    trimCode?: string
    gradeCode?: string
  } | null>(null)

  // 제조사 조회
  const { data: makers } = useQuery({
    queryKey: ['carizon-codes', 'makers'],
    queryFn: () => carizonCodeApi.getMakers(),
  })

  // 모델 그룹 조회
  const { data: modelGroups } = useQuery({
    queryKey: ['carizon-codes', 'model-groups', selectedMaker],
    queryFn: () => carizonCodeApi.getModelGroups(selectedMaker!),
    enabled: !!selectedMaker,
  })

  // 모델 조회
  const { data: models } = useQuery({
    queryKey: ['carizon-codes', 'models', selectedMaker, selectedModelGroup],
    queryFn: () => carizonCodeApi.getModels(selectedMaker!, selectedModelGroup!),
    enabled: !!selectedMaker && !!selectedModelGroup,
  })

  // 트림 조회
  const { data: trims } = useQuery({
    queryKey: ['carizon-codes', 'trims', selectedMaker, selectedModelGroup, selectedModel],
    queryFn: () => carizonCodeApi.getTrims(selectedMaker!, selectedModelGroup!, selectedModel!),
    enabled: !!selectedMaker && !!selectedModelGroup && !!selectedModel,
  })

  // 등급 조회
  const { data: grades } = useQuery({
    queryKey: [
      'carizon-codes',
      'grades',
      selectedMaker,
      selectedModelGroup,
      selectedModel,
      selectedTrim,
    ],
    queryFn: () =>
      carizonCodeApi.getGrades(selectedMaker!, selectedModelGroup!, selectedModel!, selectedTrim!),
    enabled: !!selectedMaker && !!selectedModelGroup && !!selectedModel && !!selectedTrim,
  })

  // 매핑 정보 조회
  const { data: mappings } = useQuery({
    queryKey: ['carizon-codes', 'mappings', selectedCode],
    queryFn: () => carizonCodeApi.getMappings({
      makerCode: selectedCode?.makerCode,
      modelGroupCode: selectedCode?.modelGroupCode,
      modelCode: selectedCode?.modelCode,
      trimCode: selectedCode?.trimCode,
      gradeCode: selectedCode?.gradeCode,
    }),
    enabled: !!selectedCode,
  })

  const handleCodeClick = (
    type: 'maker' | 'modelGroup' | 'model' | 'trim' | 'grade',
    code: any
  ) => {
    setSelectedCode({
      type,
      makerCode: code.maker_code || selectedMaker,
      modelGroupCode: code.model_group_code || selectedModelGroup,
      modelCode: code.model_code || selectedModel,
      trimCode: code.trim_code || selectedTrim,
      gradeCode: type === 'grade' ? code.grade_code : undefined,
    })
  }

  const makerColumns = [
    {
      title: '제조사 코드',
      dataIndex: 'maker_code',
      key: 'maker_code',
    },
    {
      title: '제조사 이름',
      dataIndex: 'maker_name',
      key: 'maker_name',
    },
    {
      title: '국가',
      dataIndex: 'country_code',
      key: 'country_code',
    },
    {
      title: '작업',
      key: 'actions',
      render: (_: any, record: Maker) => (
        <Button
          type="link"
          icon={<EyeOutlined />}
          onClick={() => handleCodeClick('maker', record)}
        >
          매핑 보기
        </Button>
      ),
    },
  ]

  const modelGroupColumns = [
    {
      title: '모델 그룹 코드',
      dataIndex: 'model_group_code',
      key: 'model_group_code',
    },
    {
      title: '모델 그룹 이름',
      dataIndex: 'model_group_name',
      key: 'model_group_name',
    },
    {
      title: '작업',
      key: 'actions',
      render: (_: any, record: ModelGroup) => (
        <Button
          type="link"
          icon={<EyeOutlined />}
          onClick={() => handleCodeClick('modelGroup', record)}
        >
          매핑 보기
        </Button>
      ),
    },
  ]

  const modelColumns = [
    {
      title: '모델 코드',
      dataIndex: 'model_code',
      key: 'model_code',
    },
    {
      title: '모델 이름',
      dataIndex: 'model_name',
      key: 'model_name',
    },
    {
      title: '연식',
      key: 'year',
      render: (_: any, record: Model) =>
        `${record.from_year || ''} ~ ${record.to_year || ''}`,
    },
    {
      title: '작업',
      key: 'actions',
      render: (_: any, record: Model) => (
        <Button
          type="link"
          icon={<EyeOutlined />}
          onClick={() => handleCodeClick('model', record)}
        >
          매핑 보기
        </Button>
      ),
    },
  ]

  const trimColumns = [
    {
      title: '트림 코드',
      dataIndex: 'trim_code',
      key: 'trim_code',
    },
    {
      title: '트림 이름',
      dataIndex: 'trim_name',
      key: 'trim_name',
    },
    {
      title: '작업',
      key: 'actions',
      render: (_: any, record: Trim) => (
        <Button
          type="link"
          icon={<EyeOutlined />}
          onClick={() => handleCodeClick('trim', record)}
        >
          매핑 보기
        </Button>
      ),
    },
  ]

  const gradeColumns = [
    {
      title: '등급 코드',
      dataIndex: 'grade_code',
      key: 'grade_code',
    },
    {
      title: '등급 이름',
      dataIndex: 'grade_name',
      key: 'grade_name',
    },
    {
      title: '작업',
      key: 'actions',
      render: (_: any, record: Grade) => (
        <Button
          type="link"
          icon={<EyeOutlined />}
          onClick={() => handleCodeClick('grade', record)}
        >
          매핑 보기
        </Button>
      ),
    },
  ]

  const mappingColumns = [
    {
      title: '플랫폼',
      dataIndex: 'platform_name',
      key: 'platform_name',
    },
    {
      title: '플랫폼 코드',
      key: 'platform_codes',
      render: (_: any, record: CodeMapping) => (
        <div style={{ fontSize: '12px' }}>
          <div>Maker: {record.p_maker_code || '-'}</div>
          <div>Model: {record.p_model_code || '-'}</div>
        </div>
      ),
    },
    {
      title: '플랫폼 이름',
      key: 'platform_names',
      render: (_: any, record: CodeMapping) => (
        <div style={{ fontSize: '12px' }}>
          <div>{record.p_maker_name_norm || '-'}</div>
          <div>{record.p_model_name_norm || '-'}</div>
        </div>
      ),
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
    },
    {
      title: '상태',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => {
        const colors: Record<string, string> = {
          AUTO: 'green',
          REVIEW: 'orange',
          LOCKED: 'purple',
        }
        return <Tag color={colors[status]}>{status}</Tag>
      },
    },
  ]

  return (
    <div>
      <Title level={2}>카리즌 코드집</Title>

      <Card style={{ marginBottom: 16 }}>
        <Space>
          <Select
            placeholder="제조사 선택"
            allowClear
            style={{ width: 200 }}
            value={selectedMaker}
            onChange={(value) => {
              setSelectedMaker(value)
              setSelectedModelGroup(undefined)
              setSelectedModel(undefined)
              setSelectedTrim(undefined)
            }}
          >
            {makers?.data?.map((maker) => (
              <Option key={maker.maker_code} value={maker.maker_code}>
                {maker.maker_name} ({maker.maker_code})
              </Option>
            ))}
          </Select>
          {selectedMaker && (
            <Select
              placeholder="모델 그룹 선택"
              allowClear
              style={{ width: 200 }}
              value={selectedModelGroup}
              onChange={(value) => {
                setSelectedModelGroup(value)
                setSelectedModel(undefined)
                setSelectedTrim(undefined)
              }}
            >
              {modelGroups?.data?.map((group) => (
                <Option key={group.model_group_code} value={group.model_group_code}>
                  {group.model_group_name} ({group.model_group_code})
                </Option>
              ))}
            </Select>
          )}
          {selectedModelGroup && (
            <Select
              placeholder="모델 선택"
              allowClear
              style={{ width: 200 }}
              value={selectedModel}
              onChange={(value) => {
                setSelectedModel(value)
                setSelectedTrim(undefined)
              }}
            >
              {models?.data?.map((model) => (
                <Option key={model.model_code} value={model.model_code}>
                  {model.model_name} ({model.model_code})
                </Option>
              ))}
            </Select>
          )}
          {selectedModel && (
            <Select
              placeholder="트림 선택"
              allowClear
              style={{ width: 200 }}
              value={selectedTrim}
              onChange={setSelectedTrim}
            >
              {trims?.data?.map((trim) => (
                <Option key={trim.trim_code} value={trim.trim_code}>
                  {trim.trim_name} ({trim.trim_code})
                </Option>
              ))}
            </Select>
          )}
        </Space>
      </Card>

      {!selectedMaker && (
        <Card title="제조사 목록">
          <Table
            columns={makerColumns}
            dataSource={makers?.data || []}
            rowKey="maker_code"
            pagination={{ pageSize: 20 }}
          />
        </Card>
      )}

      {selectedMaker && !selectedModelGroup && (
        <Card title="모델 그룹 목록">
          <Table
            columns={modelGroupColumns}
            dataSource={modelGroups?.data || []}
            rowKey="model_group_code"
            pagination={{ pageSize: 20 }}
          />
        </Card>
      )}

      {selectedModelGroup && !selectedModel && (
        <Card title="모델 목록">
          <Table
            columns={modelColumns}
            dataSource={models?.data || []}
            rowKey="model_code"
            pagination={{ pageSize: 20 }}
          />
        </Card>
      )}

      {selectedModel && !selectedTrim && (
        <Card title="트림 목록">
          <Table
            columns={trimColumns}
            dataSource={trims?.data || []}
            rowKey="trim_code"
            pagination={{ pageSize: 20 }}
          />
        </Card>
      )}

      {selectedTrim && (
        <Card title="등급 목록">
          <Table
            columns={gradeColumns}
            dataSource={grades?.data || []}
            rowKey="grade_code"
            pagination={{ pageSize: 20 }}
          />
        </Card>
      )}

      {/* 매핑 정보 모달 */}
      <Modal
        title="플랫폼 매핑 정보"
        open={!!selectedCode}
        onCancel={() => setSelectedCode(null)}
        footer={null}
        width={800}
      >
        {mappings?.data && mappings.data.length > 0 ? (
          <Table
            columns={mappingColumns}
            dataSource={mappings.data}
            rowKey={(record) =>
              `${record.platform_name}-${record.p_maker_code}-${record.p_model_code}`
            }
            pagination={{ pageSize: 10 }}
          />
        ) : (
          <div>매핑된 플랫폼 코드가 없습니다.</div>
        )}
      </Modal>
    </div>
  )
}
