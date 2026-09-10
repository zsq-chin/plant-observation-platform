import type { RouteRecordRaw } from 'vue-router'

const studentRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/student/Login.vue'),
    meta: { title: '登录', noAuth: true },
  },
  {
    path: '/student',
    component: () => import('@/layout/StudentLayout.vue'),
    meta: { title: '学生端', roles: ['student'] },
    redirect: '/student/home',
    children: [
      {
        path: 'home',
        name: 'StudentHome',
        component: () => import('@/views/student/Home.vue'),
        meta: { title: '首页' },
      },
      {
        path: 'todos',
        name: 'TodoList',
        component: () => import('@/views/student/TodoList.vue'),
        meta: { title: '我的待办' },
      },
      {
        path: 'observations',
        name: 'MyObservations',
        component: () => import('@/views/student/MyObservations.vue'),
        meta: { title: '我的植物观察' },
      },
      {
        path: 'observations/create',
        name: 'ObservationCreate',
        component: () => import('@/views/student/ObservationEdit.vue'),
        meta: { title: '新增植物观察' },
      },
      {
        path: 'observations/edit/:id',
        name: 'ObservationEdit',
        component: () => import('@/views/student/ObservationEdit.vue'),
        meta: { title: '编辑植物观察' },
      },
      {
        path: 'ranking',
        name: 'MyRanking',
        component: () => import('@/views/student/MyRanking.vue'),
        meta: { title: '我的评分' },
      },
      {
        path: 'notify',
        name: 'StudentNotify',
        component: () => import('@/views/student/Notify.vue'),
        meta: { title: '消息通知' },
      },
    ],
  },
]

export default studentRoutes
