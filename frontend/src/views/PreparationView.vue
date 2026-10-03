<script setup>
import { computed, inject, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getPreparations, getPrepSummary } from '../api'

const bloodType = inject('bloodType')
const batches = ref([])
const summary = ref([])
const loading = ref(false)

const totalBags = computed(() => batches.value.reduce((sum, b) => sum + (b.totalBags || 0), 0))
const totalVolume = computed(() => batches.value.reduce((sum, b) => sum + (b.totalVolumeMl || 0), 0))

async function load() {
  loading.value = true
  try {
    const [b, s] = await Promise.all([getPreparations(bloodType.value), getPrepSummary()])
    batches.value = b.data || []
    summary.value = (s.data || []).filter((x) => !bloodType.value || x.bloodType === bloodType.value)
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
    <div class="bc-kpis" style="margin-bottom: 14px">
      <div class="bc-kpi"><b>{{ batches.length }}</b><span>制备批次</span></div>
      <div class="bc-kpi"><b>{{ totalBags }}</b><span>产出袋数</span></div>
      <div class="bc-kpi"><b>{{ totalVolume }}</b><span>产出容量 ml</span></div>
      <div class="bc-kpi">
        <b>{{ summary.length }}</b><span>覆盖血型</span>
      </div>
    </div>

    <div class="bc-panel">
      <div class="bc-panel-head">
        <div class="bc-panel-title">按血型汇总制备成果</div>
        <div class="spacer"></div>
        <span class="bc-panel-sub">JPQL 聚合：批次数 / 总袋数 / 总容量</span>
      </div>
      <el-table :data="summary" size="small" stripe>
        <el-table-column prop="bloodType" label="血型" width="90" />
        <el-table-column prop="batchCount" label="批次数" width="90" />
        <el-table-column prop="totalBags" label="总袋数" width="90" />
        <el-table-column prop="totalVolumeMl" label="总容量 ml" width="110" />
        <el-table-column label="最近制备时间" min-width="180">
          <template #default="{ row }">
            {{ row.lastPreparedTime ? String(row.lastPreparedTime).replace('T', ' ') : '--' }}
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="bc-panel">
      <div class="bc-panel-head">
        <div class="bc-panel-title">成分制备批次明细</div>
        <div class="spacer"></div>
        <span class="bc-panel-sub">全血 → 红细胞悬液 / 血浆 / 血小板 / 冷沉淀</span>
      </div>
      <el-table :data="batches" size="small" stripe>
        <el-table-column prop="batchNo" label="批号" width="130" />
        <el-table-column prop="collectNo" label="全血来源" width="120" />
        <el-table-column prop="bloodType" label="血型" width="64" />
        <el-table-column label="红细胞悬液" width="120">
          <template #default="{ row }">
            <el-tag size="small" type="danger">{{ row.rbcBags }} 袋</el-tag>
            <span style="margin-left: 6px">{{ row.rbcVolumeMl }} ml</span>
          </template>
        </el-table-column>
        <el-table-column label="血浆" width="120">
          <template #default="{ row }">
            <el-tag size="small" type="warning">{{ row.plasmaBags }} 袋</el-tag>
            <span style="margin-left: 6px">{{ row.plasmaVolumeMl }} ml</span>
          </template>
        </el-table-column>
        <el-table-column label="血小板" width="120">
          <template #default="{ row }">
            <el-tag size="small" type="primary">{{ row.plateletBags }} 袋</el-tag>
            <span style="margin-left: 6px">{{ row.plateletVolumeMl }} ml</span>
          </template>
        </el-table-column>
        <el-table-column label="冷沉淀" width="120">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.cryoBags }} 袋</el-tag>
            <span style="margin-left: 6px">{{ row.cryoVolumeMl }} ml</span>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="制备人" width="120" />
        <el-table-column label="制备时间" min-width="160">
          <template #default="{ row }">
            {{ row.preparedTime ? String(row.preparedTime).replace('T', ' ').slice(0, 16) : '--' }}
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!batches.length" class="bc-empty">该血型暂无制备批次，可到「标本检验轨道」放行标本自动生成</div>
    </div>
  </div>
</template>
