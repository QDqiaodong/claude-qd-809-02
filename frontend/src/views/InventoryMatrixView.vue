<script setup>
import { computed, inject, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { adjustInventory, getCellBags, getMatrix, getScraps, scrapExpired } from '../api'

const bloodType = inject('bloodType')
const matrix = ref({ bloodTypes: [], components: [], cells: [], legend: [] })
const scraps = ref([])
const loading = ref(false)

// 右侧滑出抽屉
const drawer = ref(false)
const currentCell = ref(null)
const bags = ref([])

const cellMap = computed(() => {
  const map = {}
  for (const c of matrix.value.cells) {
    map[c.bloodType + '|' + c.component] = c
  }
  return map
})

function cellOf(bt, comp) {
  return (
    cellMap.value[bt + '|' + comp] || {
      bloodType: bt,
      component: comp,
      bagCount: 0,
      unitCount: 0,
      nearestExpiry: null,
      level: 'OK'
    }
  )
}

async function openCell(bt, comp) {
  currentCell.value = { bloodType: bt, component: comp }
  try {
    const res = await getCellBags(bt, comp)
    bags.value = res.data || []
  } catch (e) {
    ElMessage.error(e.message)
    bags.value = []
  }
  drawer.value = true
}

// 出入库
const adjustForm = ref({ bags: 1, volumeMl: 200, mode: 'IN', operator: '库存岗', remark: '' })

async function doAdjust(mode) {
  if (!currentCell.value) return
  adjustForm.value.mode = mode
  try {
    const res = await adjustInventory({
      bloodType: currentCell.value.bloodType,
      component: currentCell.value.component,
      bags: adjustForm.value.bags,
      volumeMl: adjustForm.value.volumeMl,
      mode,
      operator: adjustForm.value.operator,
      remark: adjustForm.value.remark || (mode === 'IN' ? '手工入库' : '手工出库')
    })
    ElMessage.success(res.message || '库存已调整')
    const r = await getCellBags(currentCell.value.bloodType, currentCell.value.component)
    bags.value = r.data || []
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function doScrapExpired() {
  try {
    const res = await scrapExpired()
    ElMessage.success(res.message || '过期扫描完成')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function load() {
  loading.value = true
  try {
    const [m, s] = await Promise.all([getMatrix(bloodType.value), getScraps(bloodType.value)])
    matrix.value = m.data || matrix.value
    scraps.value = (s.data || []).slice(0, 10)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(bloodType, load)
</script>

<template>
  <div v-loading="loading">
    <div class="bc-alert">
      行 = 8 种血型，列 = 4 种成分；格子按「袋数 / 预警线 / 最近效期」染色。点格子右侧滑出血袋明细并可出入库。
    </div>

    <div class="bc-panel">
      <div class="bc-panel-head">
        <div class="bc-panel-title">血型 × 成分 库存矩阵</div>
        <div class="spacer"></div>
        <el-button size="small" type="danger" @click="doScrapExpired">过期扫描（自动生成报废记录）</el-button>
        <el-button size="small" @click="load">刷新</el-button>
      </div>

      <div class="bc-matrix-wrap">
        <table class="bc-matrix">
          <thead>
            <tr>
              <th class="row-head">血型</th>
              <th v-for="c in matrix.components" :key="c.code">{{ c.name }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="bt in matrix.bloodTypes" :key="bt">
              <th class="row-head">{{ bt }}</th>
              <td
                v-for="c in matrix.components"
                :key="c.code"
                class="cell"
                :class="'level-' + cellOf(bt, c.code).level"
                @click="openCell(bt, c.code)"
              >
                <div class="bags">{{ cellOf(bt, c.code).bagCount }} 袋</div>
                <div class="unit">{{ cellOf(bt, c.code).unitCount }} U</div>
                <div class="exp">
                  {{ cellOf(bt, c.code).nearestExpiry || '无库存' }}
                  <span v-if="cellOf(bt, c.code).daysToExpiry !== null">
                    ({{ cellOf(bt, c.code).daysToExpiry }}天)
                  </span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 色带图例 -->
      <div class="bc-legend">
        <span>色带图例：</span>
        <div v-for="l in matrix.legend" :key="l.level" class="item">
          <i class="swatch" :style="{ background: l.color }"></i>
          <span>{{ l.name }} · {{ l.desc }}</span>
        </div>
      </div>
    </div>

    <div class="bc-panel">
      <div class="bc-panel-head">
        <div class="bc-panel-title">报废记录</div>
        <div class="spacer"></div>
        <span class="bc-panel-sub">过期 / 破损 / 检验不合格</span>
      </div>
      <el-table :data="scraps" size="small" stripe>
        <el-table-column prop="scrapNo" label="报废单号" width="140" />
        <el-table-column prop="bagNo" label="袋号" width="120" />
        <el-table-column prop="bloodType" label="血型" width="64" />
        <el-table-column prop="componentName" label="成分" width="110" />
        <el-table-column prop="volumeMl" label="容量 ml" width="90" />
        <el-table-column label="原因" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.reason === 'EXPIRED' ? 'danger' : 'warning'">
              {{ row.reasonName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="130" />
        <el-table-column prop="remark" label="说明" min-width="180" />
      </el-table>
    </div>

    <!-- 格子明细抽屉 -->
    <el-drawer
      v-model="drawer"
      :title="currentCell ? `${currentCell.bloodType} · ${currentCell.component} 血袋明细` : '血袋明细'"
      size="560px"
      direction="rtl"
    >
      <div v-if="currentCell">
        <div class="bc-alert">
          袋数 {{ cellOf(currentCell.bloodType, currentCell.component).bagCount }} ·
          库存 {{ cellOf(currentCell.bloodType, currentCell.component).unitCount }} U ·
          预警线 {{ cellOf(currentCell.bloodType, currentCell.component).warningLine }} 袋
        </div>
        <el-table :data="bags" size="small" stripe height="360">
          <el-table-column prop="bagNo" label="袋号" width="130" />
          <el-table-column prop="componentName" label="成分" width="100" />
          <el-table-column prop="volumeMl" label="容量 ml" width="80" />
          <el-table-column prop="units" label="U" width="50" />
          <el-table-column prop="donorName" label="献血者" width="90" />
          <el-table-column label="效期" width="120">
            <template #default="{ row }">
              {{ row.expiryDate }}
              <el-tag v-if="row.daysToExpiry !== null && row.daysToExpiry <= 7" size="small" type="danger">
                {{ row.daysToExpiry }}天
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'AVAILABLE' ? 'success' : 'info'">
                {{ row.statusName }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

        <div class="bc-panel" style="margin-top: 14px">
          <div class="bc-panel-head">
            <div class="bc-panel-title">出入库</div>
          </div>
          <el-form inline>
            <el-form-item label="袋数">
              <el-input-number v-model="adjustForm.bags" :min="1" :max="20" />
            </el-form-item>
            <el-form-item label="单袋 ml">
              <el-input-number v-model="adjustForm.volumeMl" :min="30" :max="400" :step="10" />
            </el-form-item>
            <el-form-item label="操作人">
              <el-input v-model="adjustForm.operator" style="width: 120px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="doAdjust('IN')">入库</el-button>
              <el-button type="danger" @click="doAdjust('OUT')">出库</el-button>
            </el-form-item>
          </el-form>
        </div>
      </div>
    </el-drawer>
  </div>
</template>
