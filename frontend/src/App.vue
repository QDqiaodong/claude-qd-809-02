<script setup>
import { computed, onMounted, provide, ref, watch } from 'vue'
import { routes } from './router'
import { getDict, getOverview } from './api'

/** 顶部血型 chips —— 全站主导航维度 */
const BLOOD_TYPES = ['A+', 'A-', 'B+', 'B-', 'O+', 'O-', 'AB+', 'AB-']
const bloodType = ref('A+')
provide('bloodType', bloodType)
provide('goMenu', go)

const menus = [
  { key: 'collection', seq: '01', title: '采血位状态盘', sub: '献血者与采血登记' },
  { key: 'lab', seq: '02', title: '标本检验轨道', sub: '待检/检测中/已放行' },
  { key: 'preparation', seq: '03', title: '成分制备', sub: '红细胞/血浆/血小板/冷沉淀' },
  { key: 'inventory', seq: '04', title: '血型×成分库存', sub: '二维矩阵与出入库' },
  { key: 'transfusion', seq: '05', title: '交叉配血发血', sub: '医院申请与出库' },
  { key: 'reactions', seq: '06', title: '输血不良反应追溯台', sub: '反应上报·同源隔离·质量复核' }
]

const current = ref('collection')
const overview = ref({})
const overviewTimer = ref(null)

const activeRoute = computed(() => routes.find((r) => r.name === current.value) || routes[0])
const activeComponent = computed(
  () => activeRoute.value.component || activeRoute.value.components?.default
)
const activeMenu = computed(() => menus.find((m) => m.key === current.value) || menus[0])

function go(key) {
  current.value = key
  const route = routes.find((r) => r.name === key)
  if (route && window.history && window.history.replaceState) {
    window.history.replaceState({}, '', route.path)
  }
}

async function loadOverview() {
  try {
    const res = await getOverview()
    overview.value = res.data || {}
  } catch (e) {
    overview.value = {}
  }
}

onMounted(() => {
  loadOverview()
  const hit = routes.find((r) => r.path === window.location.pathname)
  if (hit && hit.name) current.value = hit.name
  getDict().catch(() => {})
  overviewTimer.value = window.setInterval(loadOverview, 30000)
})

watch(bloodType, () => loadOverview())
</script>

<template>
  <div class="bc-shell">
    <!-- 顶部品牌栏 + 血型 chips 主导航 -->
    <header class="bc-topbar">
      <div class="bc-topbar-main">
        <div class="bc-brand-mark">血站</div>
        <div>
          <div class="bc-brand-title">血站 · 采血供血与血液库存管理</div>
          <div class="bc-brand-sub">采血 → 检验 → 制备 → 库存 → 发血 全流程闭环</div>
        </div>
        <div class="bc-topbar-kpis">
          <div class="bc-topbar-kpi">
            <b>{{ overview.donorCount ?? '-' }}</b><span>在册献血者</span>
          </div>
          <div class="bc-topbar-kpi">
            <b>{{ overview.todayCollectionCount ?? '-' }}</b><span>今日采血人次</span>
          </div>
          <div class="bc-topbar-kpi">
            <b>{{ overview.pendingSampleCount ?? '-' }}</b><span>待检标本</span>
          </div>
          <div class="bc-topbar-kpi">
            <b>{{ overview.availableBagCount ?? '-' }}</b><span>可用血袋</span>
          </div>
          <div class="bc-topbar-kpi">
            <b>{{ overview.lowStockCells ?? '-' }}</b><span>低库存格</span>
          </div>
        </div>
      </div>

      <div class="bc-chips">
        <span class="bc-chips-label">血型主导航</span>
        <div
          v-for="bt in BLOOD_TYPES"
          :key="bt"
          class="bc-chip"
          :class="{ active: bloodType === bt }"
          @click="bloodType = bt"
        >
          {{ bt }}<i class="dot"></i>
        </div>
        <div class="bc-chip" :class="{ active: bloodType === '' }" @click="bloodType = ''">全部血型</div>
      </div>
    </header>

    <div class="bc-body">
      <!-- 左侧二级功能菜单 -->
      <aside class="bc-side">
        <div class="bc-side-label">血 液 生 命 周 期</div>
        <div
          v-for="menu in menus"
          :key="menu.key"
          class="bc-menu-item"
          :class="{ active: menu.key === current }"
          @click="go(menu.key)"
        >
          <div class="bc-menu-seq">{{ menu.seq }}</div>
          <div>
            <div class="bc-menu-title">{{ menu.title }}</div>
            <div class="bc-menu-sub">{{ menu.sub }}</div>
          </div>
        </div>
        <div class="bc-side-foot">
          当前血型：<b>{{ bloodType || '全部' }}</b><br />
          左侧菜单与内容区均随血型过滤<br />
          检验放行自动生成制备单（领域事件）
        </div>
      </aside>

      <!-- 内容区 -->
      <div class="bc-content">
        <div class="bc-panel">
          <div class="bc-panel-head">
            <div>
              <div class="bc-panel-title">{{ activeMenu.title }}</div>
              <div class="bc-panel-sub">{{ activeMenu.sub }} · 当前血型 {{ bloodType || '全部' }}</div>
            </div>
            <div class="spacer"></div>
            <el-tag type="danger" effect="dark" v-if="bloodType">{{ bloodType }} 型血液</el-tag>
          </div>
        </div>
        <component :is="activeComponent" :key="current" />
      </div>
    </div>
  </div>
</template>
