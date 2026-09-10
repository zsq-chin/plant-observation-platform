import type { RouteRecordRaw } from 'vue-router'

const publicRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layout/PublicLayout.vue'),
    meta: { noAuth: true },
    redirect: '/plant',
    children: [
      {
        path: 'plant',
        name: 'PublicPlantHome',
        component: () => import('@/views/public/PlantHome.vue'),
        meta: { title: '全国植物观察与交流平台' },
      },
      {
        path: 'plant/gallery',
        name: 'PlantGallery',
        component: () => import('@/views/public/PlantGallery.vue'),
        meta: { title: '植物观察展廊' },
      },
      {
        path: 'plant/observations/:id',
        name: 'PlantObservationDetail',
        component: () => import('@/views/public/PlantObservationDetail.vue'),
        meta: { title: '植物观察详情' },
      },
      {
        path: 'plant/map',
        name: 'ChinaPlantMap',
        component: () => import('@/views/public/ChinaPlantMap.vue'),
        meta: { title: '全国植物地图' },
      },
      {
        path: 'plant/species',
        name: 'PlantSpeciesLibrary',
        component: () => import('@/views/public/PlantSpecies.vue'),
        meta: { title: '植物物种库' },
      },
      {
        path: 'ranking',
        name: 'PublicRanking',
        component: () => import('@/views/public/Ranking.vue'),
        meta: { title: 'Ranking' },
      },
    ],
  },
]

export default publicRoutes
