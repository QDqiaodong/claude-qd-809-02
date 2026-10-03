<script setup>
import { computed, inject, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  collectBlood,
  finishCollect,
  getCollections,
  getDonors,
  getStations
} from '../api'

const bloodType = inject('bloodType')

const stations = ref([])
const donors = ref([])
const collections = ref([])
const loading = ref(false)

/** 采血位按圆环排布：12 个位均分 360° */
const ring = computed(() => {
  const list = stations.value
  const n = list.length || 1
  const radius = 200
  return list.map((s, i) => {
    const angle = (2 * Math.PI * i) / n - Math.PI / 2
    return {
      ...s,
      left: 260 + radius * Math.cos(angle),
      top: 260 + radius * Math.sin(angle)
    }
  })
})

const occupied = computed(() => stations.value.filter((s) => s.status === 'OCCUPIED').length)
const cleaning = computed(() => stations.value.filter((s) => s.status === 'CLEANING').length)
const idle = computed(() => stations.value.filter((s) => s.status === 'IDLE').length)

// ---------------- 采血登记弹窗 ----------------
const dialogVisible = ref(false)
const form = ref({ donorId: null, stationCode: '', volumeMl: 400, nurse: '沈青' })

const collectableDonors = computed(() =>
  donors.value.filter((d) => !bloodType.value || d.bloodType === bloodType.value)
)

function openDialog(station) {
  form.value = {
    donorId: null,
    stationCode: station.code,
    volumeMl: 400,
    nurse: station.nurse || '沈青'
  }
  dialogVisible.value = true
}

async function submitCollect() {
  if (!form.value.donorId) {
    ElMessage.warning('请选择献血者')
    return
  }
  try {
    const res = await collectBlood(form.value)
    ElMessage.success(res.message || '采血登记成功')
    dialogVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function finish(id) {
  try {
    const res = await finishCollect(id)
    ElMessage.success(res.message || '采血完成')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function load() {
  loading.value = true
  try {
    const [s, d, c] = await Promise.all([
      getStations(bloodType.value),
      getDonors(bloodType.value),
      getCollections(bloodType.value)
    ])
    stations.value = s.data || []
    donors.value = d.data || []
    collections.value = (c.data || []).slice(0, 12)
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
      <div class="bc-kpi"><b>{{ occupied }}</b><span>占用采血位</span></div>
      <div class="bc-kpi"><b>{{ cleaning }}</b><span>消毒中</span></div>
      <div class="bc-kpi"><b>{{ idle }}</b><span>空闲可登记</span></div>
      <div class="bc-kpi"><b>{{ donors.length }}</b><span>{{ bloodType || '全部' }} 型献血者</span></div>
    </div>

    <div class="bc-board">
      <!-- 圆环排布的采血位 -->
      <div class="bc-ring">
        <div class="bc-ring-center">
          <b>{{ occupied }}/{{ stations.length }}</b>
          <span>采血位占用</span>
        </div>
        <div
          v-for="s in ring"
          :key="s.code"
          class="bc-station"
          :class="s.status.toLowerCase()"
          :style="{ left: s.left + 'px', top: s.top + 'px' }"
          @click="openDialog(s)"
        >
          <div class="code">{{ s.code }}</div>
          <div class="who">{{ s.donorSurname || '空' }}</div>
          <div class="meta">{{ s.status === 'OCCUPIED' ? s.bloodType : s.statusName }}</div>
        </div>
      </div>

      <!-- 侧栏：献血者与今日采血 -->
      <div class="bc-board-side">
        <div class="bc-panel">
          <div class="bc-panel-head">
            <div class="bc-panel-title">{{ bloodType || '全部' }} 型献血者</div>
            <div class="spacer"></div>
            <span class="bc-panel-sub">点采血位圆牌登记采血</span>
          </div>
          <el-table :data="collectableDonors" size="small" height="248" stripe>
            <el-table-column prop="name" label="姓名" width="70" />
            <el-table-column prop="bloodType" label="血型" width="58" />
            <el-table-column prop="donationCount" label="既往次数" width="80" />
            <el-table-column prop="totalVolumeMl" label="累计 ml" width="80" />
            <el-table-column label="可献" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.collectable" type="success" size="small">可献</el-tag>
                <el-tag v-else type="danger" size="small">暂缓</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="blockReason" label="说明" min-width="150">
              <template #default="{ row }">
                <span style="font-size: 12px">{{ row.blockReason || `上限 ${row.maxVolumeMl} ml` }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="bc-panel">
          <div class="bc-panel-head">
            <div class="bc-panel-title">采血记录</div>
            <div class="spacer"></div>
            <span class="bc-panel-sub">完成采血后自动生成检验标本</span>
          </div>
          <el-table :data="collections" size="small" height="240" stripe>
            <el-table-column prop="collectNo" label="单号" width="120" />
            <el-table-column prop="donorName" label="献血者" width="80" />
            <el-table-column prop="stationCode" label="采血位" width="70" />
            <el-table-column prop="volumeMl" label="ml" width="60" />
            <el-table-column prop="nurse" label="护士" width="80" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'DONE' ? 'success' : 'warning'">
                  {{ row.statusName }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'COLLECTING'"
                  size="small"
                  type="primary"
                  @click="finish(row.id)"
                >
                  完成
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>

    <!-- 采血登记 -->
    <el-dialog v-model="dialogVisible" title="采血登记" width="440px">
      <el-form label-width="92px">
        <el-form-item label="采血位">
          <el-tag type="danger" effect="dark">{{ form.stationCode }}</el-tag>
        </el-form-item>
        <el-form-item label="献血者">
          <el-select v-model="form.donorId" filterable placeholder="选择献血者" style="width: 100%">
            <el-option
              v-for="d in collectableDonors"
              :key="d.id"
              :label="`${d.name} · ${d.bloodType} · 上限 ${d.maxVolumeMl}ml`"
              :value="d.id"
              :disabled="!d.collectable"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="采血量 ml">
          <el-radio-group v-model="form.volumeMl">
            <el-radio-button :value="200">200</el-radio-button>
            <el-radio-button :value="300">300</el-radio-button>
            <el-radio-button :value="400">400</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="采血护士">
          <el-select v-model="form.nurse" style="width: 100%">
            <el-option v-for="n in ['沈青', '韩梅', '崔颖']" :key="n" :label="n" :value="n" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCollect">登记并开始采血</el-button>
      </template>
    </el-dialog>
  </div>
</template>
