import { Card, Statistic, Row, Col, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { dashboardApi } from '../api/dashboard'

const { Title } = Typography

export default function Dashboard() {
  const { data, isLoading } = useQuery({
    queryKey: ['dashboard', 'stats'],
    queryFn: () => dashboardApi.getStats(),
  })

  if (isLoading) {
    return <div>로딩 중...</div>
  }

  const stats = data?.data?.data || {}
  
  // 숫자 값 처리 (null, undefined, -1 등을 0으로 변환)
  const carMasterCount = stats.carMasterCount != null && stats.carMasterCount >= 0 ? stats.carMasterCount : 0
  const platformCarCount = stats.platformCarCount != null && stats.platformCarCount >= 0 ? stats.platformCarCount : 0
  const embeddingCount = stats.embeddingCount != null && stats.embeddingCount >= 0 ? stats.embeddingCount : 0
  const meilisearchCount = stats.meilisearchCount != null && stats.meilisearchCount >= 0 ? stats.meilisearchCount : 0

  return (
    <div>
      <Title level={2}>대시보드</Title>
      <Row gutter={16} style={{ marginTop: 24 }}>
        <Col span={6}>
          <Card>
            <Statistic
              title="전체 차량 수"
              value={carMasterCount}
              valueStyle={{ color: '#3f8600' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="플랫폼 차량 수"
              value={platformCarCount}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="임베딩된 차량"
              value={embeddingCount}
              valueStyle={{ color: '#722ed1' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="인덱싱된 차량"
              value={meilisearchCount}
              valueStyle={{ color: '#fa8c16' }}
            />
          </Card>
        </Col>
      </Row>
    </div>
  )
}
