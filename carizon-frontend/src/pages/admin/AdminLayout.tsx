import { useState } from 'react'
import { Layout, Menu, Typography, Button } from 'antd'
import {
  DashboardOutlined,
  CloudDownloadOutlined,
  DatabaseOutlined,
  RobotOutlined,
  SearchOutlined,
  SettingOutlined,
  ThunderboltOutlined,
  FileTextOutlined,
  LinkOutlined,
  LogoutOutlined,
} from '@ant-design/icons'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import Dashboard from './Dashboard'
import Crawl from './Crawl'
import Data from './Data'
import Embedding from './Embedding'
import AdminSearch from './AdminSearch'
import Config from './Config'
import Batch from './Batch'
import BlogPost from './BlogPost'
import CodeMapping from './CodeMapping'
import CarizonCodes from './CarizonCodes'
import Pipeline from './Pipeline'
import apiClient from '../../api/admin/client'

const { Header, Sider, Content } = Layout
const { Title } = Typography

const queryClient = new QueryClient()

type MenuItem = {
  key: string
  icon: React.ReactNode
  label: string
  component: React.ReactNode
}

function AdminApp() {
  const [selectedKey, setSelectedKey] = useState('dashboard')

  const menuItems: MenuItem[] = [
    { key: 'dashboard', icon: <DashboardOutlined />, label: '대시보드', component: <Dashboard /> },
    { key: 'crawl', icon: <CloudDownloadOutlined />, label: '크롤링 관리', component: <Crawl /> },
    { key: 'data', icon: <DatabaseOutlined />, label: '데이터 조회', component: <Data /> },
    { key: 'embedding', icon: <RobotOutlined />, label: '임베딩 관리', component: <Embedding /> },
    { key: 'search', icon: <SearchOutlined />, label: '검색 관리', component: <AdminSearch /> },
    { key: 'config', icon: <SettingOutlined />, label: '설정 관리', component: <Config /> },
    { key: 'batch', icon: <ThunderboltOutlined />, label: '배치 관리', component: <Batch /> },
    { key: 'blog-post', icon: <FileTextOutlined />, label: '블로그 포스팅', component: <BlogPost /> },
    { key: 'code-mapping', icon: <LinkOutlined />, label: '코드 매핑 관리', component: <CodeMapping /> },
    { key: 'carizon-codes', icon: <DatabaseOutlined />, label: '카리즌 코드집', component: <CarizonCodes /> },
    { key: 'pipeline', icon: <ThunderboltOutlined />, label: '파이프라인 실행', component: <Pipeline /> },
  ]

  const currentItem = menuItems.find(item => item.key === selectedKey) || menuItems[0]

  const handleLogout = async () => {
    try {
      await apiClient.post('/admin/auth/logout')
    } catch {
      // 무시
    }
    sessionStorage.removeItem('adminAuth')
    window.location.href = '/admin/login'
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ background: '#001529', padding: '0 24px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <Title level={3} style={{ color: '#fff', margin: 0 }}>
            Carizon Admin
          </Title>
        </div>
        <Button
          type="text"
          icon={<LogoutOutlined />}
          style={{ color: '#fff' }}
          onClick={handleLogout}
        >
          로그아웃
        </Button>
      </Header>
      <Layout>
        <Sider width={200} style={{ background: '#fff' }}>
          <Menu
            mode="inline"
            selectedKeys={[selectedKey]}
            style={{ height: '100%', borderRight: 0 }}
            onClick={({ key }) => setSelectedKey(key)}
          >
            {menuItems.map(item => (
              <Menu.Item key={item.key} icon={item.icon}>
                {item.label}
              </Menu.Item>
            ))}
          </Menu>
        </Sider>
        <Layout style={{ padding: '24px' }}>
          <Content>
            {currentItem.component}
          </Content>
        </Layout>
      </Layout>
    </Layout>
  )
}

export default function AdminLayout() {
  return (
    <QueryClientProvider client={queryClient}>
      <AdminApp />
    </QueryClientProvider>
  )
}
