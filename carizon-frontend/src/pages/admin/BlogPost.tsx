import { useState } from 'react'
import {
  Typography,
  Card,
  Form,
  Input,
  Button,
  message,
  Space,
  InputNumber,
  Select,
  Alert,
  Spin,
} from 'antd'
import { SendOutlined, FileTextOutlined } from '@ant-design/icons'
import { useMutation } from '@tanstack/react-query'
import { blogPostApi } from '../../api/admin/blogPost'

const { Title } = Typography
const { Option } = Select

export default function BlogPost() {
  const [form] = Form.useForm()
  const [postResult, setPostResult] = useState<any>(null)

  const postMutation = useMutation({
    mutationFn: (params: { modelCode: string; limit: number; status: string }) =>
      blogPostApi.postToWordPress(params.modelCode, params.limit, params.status),
    onSuccess: (data) => {
      message.success('WordPress 포스팅이 성공적으로 생성되었습니다!')
      setPostResult(data)
      form.resetFields()
    },
    onError: (error: any) => {
      message.error('포스팅 생성 실패: ' + (error.response?.data?.message || error.message))
      setPostResult(null)
    },
  })

  const previewMutation = useMutation({
    mutationFn: (params: { modelCode: string; limit: number }) =>
      blogPostApi.generatePostContent(params.modelCode, params.limit),
    onSuccess: (data) => {
      setPostResult({ ...data, preview: true })
    },
    onError: (error: any) => {
      message.error('포스팅 내용 생성 실패: ' + (error.response?.data?.message || error.message))
      setPostResult(null)
    },
  })

  const handlePost = (values: any) => {
    postMutation.mutate({ modelCode: values.modelCode, limit: values.limit || 10, status: values.status || 'draft' })
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Title level={2}>블로그 포스팅 관리</Title>

      <Card title="WordPress 포스팅 생성">
        <Alert
          message="모델코드 입력"
          description="car_master의 표준 MODEL_CODE를 입력하세요. Best 매물을 자동으로 선정하여 WordPress에 포스팅합니다."
          type="info"
          showIcon
          style={{ marginBottom: 24 }}
        />

        <Form form={form} layout="vertical" onFinish={handlePost} initialValues={{ limit: 10, status: 'draft' }}>
          <Form.Item name="modelCode" label="모델 코드 (MODEL_CODE)" rules={[{ required: true, message: '모델 코드를 입력하세요' }]} extra="예: KIA_CORANDO_SPORT_2023">
            <Input placeholder="모델 코드 입력" allowClear />
          </Form.Item>
          <Form.Item name="limit" label="Best 매물 개수" extra="상위 몇 개의 매물을 선정할지 설정합니다">
            <InputNumber min={1} max={20} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="status" label="포스팅 상태" extra="draft: 초안, publish: 즉시 발행">
            <Select style={{ width: '100%' }}>
              <Option value="draft">초안 (Draft)</Option>
              <Option value="publish">발행 (Publish)</Option>
              <Option value="pending">검토 대기 (Pending)</Option>
            </Select>
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SendOutlined />} loading={postMutation.isPending} size="large">
                WordPress에 포스팅
              </Button>
              <Button
                icon={<FileTextOutlined />}
                onClick={() => {
                  form.validateFields(['modelCode', 'limit']).then((values) => {
                    previewMutation.mutate({ modelCode: values.modelCode, limit: values.limit || 10 })
                  })
                }}
                loading={previewMutation.isPending}
                size="large"
              >
                미리보기
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>

      {postResult && (
        <Card title={postResult.preview ? '포스팅 내용 미리보기' : '포스팅 결과'}>
          {postResult.preview ? (
            <Space direction="vertical" style={{ width: '100%' }}>
              <Alert message="제목" description={postResult.title} type="info" showIcon />
              <div
                style={{ border: '1px solid #d9d9d9', borderRadius: '4px', padding: '16px', backgroundColor: '#fafafa', maxHeight: '500px', overflow: 'auto' }}
                dangerouslySetInnerHTML={{ __html: postResult.content }}
              />
              <Alert message={`선정된 매물: ${postResult.carCount}개`} type="success" showIcon />
            </Space>
          ) : (
            <Alert
              message="포스팅 생성 성공!"
              description={
                <div>
                  <p><strong>포스팅 ID:</strong> {postResult.postId}</p>
                  <p><strong>제목:</strong> {postResult.title}</p>
                  <p><strong>상태:</strong> {postResult.status}</p>
                  <p><strong>선정된 매물:</strong> {postResult.carCount}개</p>
                </div>
              }
              type="success"
              showIcon
            />
          )}
        </Card>
      )}

      {postMutation.isPending && (
        <Card>
          <Spin size="large" tip="WordPress 포스팅 생성 중..." />
        </Card>
      )}
    </Space>
  )
}
