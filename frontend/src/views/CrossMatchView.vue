<script setup>
import { computed, inject, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createRequest,
  getMatches,
  getRequests,
  issueBlood,
  recoveryMatch,
  setMatchResult
} from '../api'

const bloodType = inject('bloodType')
const goMenu = inject('goMenu', null)
const requests = ref([])
const matches = ref([])
const loading = ref(false)
const activeId = ref(null)

const active = computed(() => matches.value.find((m) => m.id === activeId.value) || null)

/** 出库是否可用：不相合永久禁用 */
const canIssue = computed(() => {
  const m = active.value
  if (!m) return false
  return !m.issued && m.result === 'COMPATIBLE' && !m.blockReason
})

async function select(row) {
  activeId.value = row.id
}

async function chooseResult(result) {
  const m = active.value
  if (!m || m.issued) return
  try {
    const res = await setMatchResult(m.id, result, m.operator || '姜妍')
    ElMessage.success(res.message || '配血结果已记录')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function doIssue() {
  const m = active.value
  if (!m) return
  try {
    const res = await issueBlood(m.id, '姜妍')
    ElMessage.success(res.message || '发血出库成功')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function doRecovery() {
  const m = active.value
  if (!m) return
  try {
    const res = await recoveryMatch(m.id)
    ElMessage.success(res.message || '回收核对完成')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function goReaction() {
  const m = active.value
  if (!m || !m.issued) return
  if (goMenu) goMenu('reactions')
  ElMessage.info(`可在追溯台针对发血单 ${m.matchNo} 上报不良反应`)
}

// 新建用血申请
const reqVisible = ref(false)
const reqForm = ref({
  hospital: '市第一人民医院',
  patientName: '',
  patientBloodType: 'A+',
  component: 'RBC',
  unitsU: 2,
  urgency: 'URGENT',
  remark: ''
})

function openRequest() {
  reqForm.value.patientBloodType = bloodType.value || 'A+'
  reqVisible.value = true
}

async function submitRequest() {
  try {
    const res = await createRequest(reqForm.value)
    ElMessage.success(res.message || '申请已登记')
    reqVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function load() {
  loading.value = true
  try {
    const [r, m] = await Promise.all([getRequests(bloodType.value), getMatches(bloodType.value)])
    requests.value = r.data || []
    matches.value = m.data || []
    if (!activeId.value && matches.value.length) {
      activeId.value = matches.value[0].id
    }
    if (activeId.value && !matches.value.some((x) => x.id === activeId.value)) {
      activeId.value = matches.value.length ? matches.value[0].id : null
    }
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
      左栏供血者 / 袋号 / 血型 / 成分 / 效期，右栏受血者 / 医院 / 申请 U 数 / 紧急程度，中间三态判定配血结果；
      <b>不相合时出库按钮永久禁用</b>。
    </div>

    <div class="bc-panel">
      <div class="bc-panel-head">
        <div class="bc-panel-title">医院用血申请</div>
        <div class="spacer"></div>
        <el-button size="small" type="primary" @click="openRequest">登记用血申请</el-button>
      </div>
      <el-table :data="requests" size="small" stripe highlight-current-row @row-click="select">
        <el-table-column prop="requestNo" label="申请单号" width="130" />
        <el-table-column prop="hospital" label="医院" min-width="150" />
        <el-table-column prop="patientName" label="患者" width="80" />
        <el-table-column prop="patientBloodType" label="血型" width="64" />
        <el-table-column prop="componentName" label="成分" width="100" />
        <el-table-column prop="unitsU" label="申请 U" width="80" />
        <el-table-column prop="issuedU" label="已发 U" width="80" />
        <el-table-column label="紧急程度" width="100">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.urgency === 'CRITICAL' ? 'danger' : row.urgency === 'URGENT' ? 'warning' : 'info'"
            >
              {{ row.urgencyName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'DONE' ? 'success' : 'primary'">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="140" />
      </el-table>
    </div>

    <div class="bc-panel">
      <div class="bc-panel-head">
        <div class="bc-panel-title">配血记录</div>
        <div class="spacer"></div>
        <span class="bc-panel-sub">点一行查看对照卡</span>
      </div>
      <el-table :data="matches" size="small" stripe highlight-current-row @row-click="select">
        <el-table-column prop="matchNo" label="配血单号" width="130" />
        <el-table-column prop="bagNo" label="供血袋号" width="120" />
        <el-table-column prop="recipient" label="受血者" width="90" />
        <el-table-column prop="bloodType" label="血型" width="64" />
        <el-table-column prop="componentName" label="成分" width="100" />
        <el-table-column prop="unitsU" label="U" width="56" />
        <el-table-column label="配血结果" width="100">
          <template #default="{ row }">
            <el-tag
              size="small"
              effect="dark"
              :type="row.result === 'COMPATIBLE' ? 'success' : row.result === 'INCOMPATIBLE' ? 'danger' : 'warning'"
            >
              {{ row.resultName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="出库" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.issued" size="small" type="success">已出库</el-tag>
            <span v-else>--</span>
          </template>
        </el-table-column>
        <el-table-column label="回收核对" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.recoveryChecked" size="small" type="info">已核对</el-tag>
            <span v-else>--</span>
          </template>
        </el-table-column>
        <el-table-column prop="blockReason" label="出库拦截原因" min-width="180" />
      </el-table>
    </div>

    <!-- 交叉配血对照卡 -->
    <div class="bc-cross" v-if="active">
      <div class="bc-cross-col supply">
        <div class="bc-cross-title">供血侧</div>
        <div class="bc-cross-row"><span class="k">供血者</span><span class="v">{{ active.donorName || '--' }}</span></div>
        <div class="bc-cross-row"><span class="k">袋号</span><span class="v">{{ active.bagNo }}</span></div>
        <div class="bc-cross-row">
          <span class="k">血型</span>
          <span class="v"><el-tag type="danger" effect="dark">{{ active.bloodType }}</el-tag></span>
        </div>
        <div class="bc-cross-row"><span class="k">成分</span><span class="v">{{ active.componentName }}</span></div>
        <div class="bc-cross-row">
          <span class="k">效期</span>
          <span class="v">{{ active.expiryDate }} <span v-if="active.daysToExpiry !== null">({{ active.daysToExpiry }} 天)</span></span>
        </div>
        <div class="bc-cross-row">
          <span class="k">检验</span>
          <span class="v">
            <el-tag size="small" :type="active.sampleStatus === 'RELEASED' ? 'success' : 'danger'">
              {{ active.sampleStatusName }}
            </el-tag>
          </span>
        </div>
      </div>

      <div class="bc-match-mid">
        <div style="font-size: 12px; color: #8d6e63; margin-bottom: 8px">配血结果三态</div>
        <button
          class="bc-match-btn"
          :class="{ 'active-compatible': active.result === 'COMPATIBLE' }"
          :disabled="active.issued"
          @click="chooseResult('COMPATIBLE')"
        >
          相合
        </button>
        <button
          class="bc-match-btn"
          :class="{ 'active-suspect': active.result === 'SUSPECT' }"
          :disabled="active.issued"
          @click="chooseResult('SUSPECT')"
        >
          可疑
        </button>
        <button
          class="bc-match-btn"
          :class="{ 'active-incompatible': active.result === 'INCOMPATIBLE' }"
          :disabled="active.issued"
          @click="chooseResult('INCOMPATIBLE')"
        >
          不相合
        </button>

        <div v-if="active.blockReason" class="bc-block-bar">{{ active.blockReason }}</div>

        <div style="margin-top: 12px; width: 100%">
          <el-button
            type="primary"
            style="width: 100%"
            :disabled="!canIssue"
            @click="doIssue"
          >
            发血出库
          </el-button>
          <el-button
            style="width: 100%; margin-top: 8px"
            :disabled="!active.issued || active.recoveryChecked"
            @click="doRecovery"
          >
            血袋回收核对
          </el-button>
          <el-button
            type="danger"
            plain
            style="width: 100%; margin-top: 8px"
            :disabled="!active.issued"
            @click="goReaction"
          >
            上报输血不良反应
          </el-button>
        </div>
      </div>

      <div class="bc-cross-col demand">
        <div class="bc-cross-title">受血侧</div>
        <div class="bc-cross-row"><span class="k">受血者</span><span class="v">{{ active.recipient }}</span></div>
        <div class="bc-cross-row"><span class="k">医院</span><span class="v">{{ active.hospital }}</span></div>
        <div class="bc-cross-row"><span class="k">申请单号</span><span class="v">{{ active.requestNo }}</span></div>
        <div class="bc-cross-row"><span class="k">申请 U 数</span><span class="v">{{ active.unitsU }} U</span></div>
        <div class="bc-cross-row">
          <span class="k">紧急程度</span>
          <span class="v">
            <el-tag
              size="small"
              :type="active.urgency === 'CRITICAL' ? 'danger' : active.urgency === 'URGENT' ? 'warning' : 'info'"
            >
              {{ active.urgencyName }}
            </el-tag>
          </span>
        </div>
        <div class="bc-cross-row">
          <span class="k">发血时间</span>
          <span class="v">{{ active.issueTime ? String(active.issueTime).replace('T', ' ').slice(0, 16) : '未出库' }}</span>
        </div>
      </div>
    </div>
    <div v-else class="bc-panel">
      <div class="bc-empty">该血型暂无配血记录</div>
    </div>

    <!-- 登记用血申请 -->
    <el-dialog v-model="reqVisible" title="登记医院用血申请" width="480px">
      <el-form label-width="96px">
        <el-form-item label="医院">
          <el-input v-model="reqForm.hospital" />
        </el-form-item>
        <el-form-item label="患者姓名">
          <el-input v-model="reqForm.patientName" placeholder="例如 韩东" />
        </el-form-item>
        <el-form-item label="患者血型">
          <el-select v-model="reqForm.patientBloodType" style="width: 100%">
            <el-option v-for="b in ['A+', 'A-', 'B+', 'B-', 'O+', 'O-', 'AB+', 'AB-']" :key="b" :label="b" :value="b" />
          </el-select>
        </el-form-item>
        <el-form-item label="申请成分">
          <el-select v-model="reqForm.component" style="width: 100%">
            <el-option label="全血" value="WHOLE" />
            <el-option label="红细胞悬液" value="RBC" />
            <el-option label="血浆" value="PLASMA" />
            <el-option label="血小板" value="PLATELET" />
          </el-select>
        </el-form-item>
        <el-form-item label="申请 U 数">
          <el-input-number v-model="reqForm.unitsU" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="紧急程度">
          <el-radio-group v-model="reqForm.urgency">
            <el-radio value="ROUTINE">常规</el-radio>
            <el-radio value="URGENT">紧急</el-radio>
            <el-radio value="CRITICAL">特急</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="reqForm.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reqVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRequest">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>
