import { useState } from 'react'
import { Layout, Menu, Card, Typography, Space } from 'antd'
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
} from '@ant-design/icons'
import Dashboard from './pages/Dashboard'
import Crawl from './pages/Crawl'
import Data from './pages/Data'
import Embedding from './pages/Embedding'
import Search from './pages/Search'
import Config from './pages/Config'
import Batch from './pages/Batch'
import BlogPost from './pages/BlogPost'
import CodeMapping from './pages/CodeMapping'
import CarizonCodes from './pages/CarizonCodes'
import Pipeline from './pages/Pipeline'

const { Header, Sider, Content } = Layout
const { Title } = Typography

type MenuItem = {
  key: string
  icon: React.ReactNode
  label: string
  component: React.ReactNode
}

function App() {
  const [selectedKey, setSelectedKey] = useState('dashboard')

  const menuItems: MenuItem[] = [
    {
      key: 'dashboard',
      icon: <DashboardOutlined />,
      label: '대시보드',
      component: <Dashboard />,
    },
    {
      key: 'crawl',
      icon: <CloudDownloadOutlined />,
      label: '크롤링 관리',
      component: <Crawl />,
    },
    {
      key: 'data',
      icon: <DatabaseOutlined />,
      label: '데이터 조회',
      component: <Data />,
    },
    {
      key: 'embedding',
      icon: <RobotOutlined />,
      label: '임베딩 관리',
      component: <Embedding />,
    },
    {
      key: 'search',
      icon: <SearchOutlined />,
      label: '검색 관리',
      component: <Search />,
    },
    {
      key: 'config',
      icon: <SettingOutlined />,
      label: '설정 관리',
      component: <Config />,
    },
    {
      key: 'batch',
      icon: <ThunderboltOutlined />,
      label: '배치 관리',
      component: <Batch />,
    },
    {
      key: 'blog-post',
      icon: <FileTextOutlined />,
      label: '블로그 포스팅',
      component: <BlogPost />,
    },
    {
      key: 'code-mapping',
      icon: <LinkOutlined />,
      label: '코드 매핑 관리',
      component: <CodeMapping />,
    },
    {
      key: 'carizon-codes',
      icon: <DatabaseOutlined />,
      label: '카리즌 코드집',
      component: <CarizonCodes />,
    },
    {
      key: 'pipeline',
      icon: <ThunderboltOutlined />,
      label: '파이프라인 실행',
      component: <Pipeline />,
    },
  ]

  const currentItem = menuItems.find(item => item.key === selectedKey) || menuItems[0]

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ background: '#001529', padding: '0 24px', display: 'flex', alignItems: 'center' }}>
        <img src="/logo.svg" alt="Carizon" style={{ height: '32px', marginRight: '12px' }} />
        <Title level={3} style={{ color: '#fff', margin: 0 }}>
          Admin
        </Title>
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

export default App
