import { createRouter, createWebHistory } from 'vue-router'

// 1. 导入基础视图
import HomeView from '../views/HomeView.vue'
import DashboardView from '../views/DashboardView.vue'

// 2. 导入 Project 和 Model 视图
import ProjectListView from '../views/ProjectListView.vue'
import ModelView from '../views/ModelView.vue'

// 3. 导入 Constraint 和 Trade 视图
import ConstraintView from '../views/ConstraintView.vue'
import TradeView from '../views/TradeView.vue'
import SettingView from '../views/SettingView.vue'
import DisplayView from '../views/DisplayView.vue'
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView
    },
    // 【修改点】Dashboard 路由增加动态参数 :projectId
    {
      path: '/dashboard/:projectId',
      name: 'dashboard',
      component: DashboardView,
      props: true
    },
    // 项目列表路由
    {
      path: '/projects',
      name: 'projects',
      component: ProjectListView
    },
    // Model 编辑器路由
    {
      path: '/model/:projectId',
      name: 'model',
      component: ModelView,
      props: true
    },
    // Constraints 路由
    {
      path: '/constraints/:projectId',
      name: 'constraints',
      component: ConstraintView,
      props: true
    },
    // Trade 路由
    {
      path: '/trade/:projectId',
      name: 'trade',
      component: TradeView, 
      props: true
    },
    {
      path: '/settings/:projectId',
      name: 'settings',
      component: SettingView,
      props: true
    },
    {
    path: '/display/:projectId',
    name: 'display',
    component: DisplayView,
    props: true
    }
  ]
})

export default router