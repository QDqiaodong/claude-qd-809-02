<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getIssues,
  getReactions,
  getReaction,
  getReactionTrace,
  reportReaction,
  reviewReaction
} from '../api'

const loading = ref(false)
const issues = ref([])
const events = ref([])
const activeEventId = ref(null)
const activeEvent = ref(null)
const statusFilter = ref('')

const activeIssue = ref(null)
const trace = ref(null)
const traceLoading = ref(false)

// ---------------- 上报弹窗 ----------------
const reportVisible = ref(false)
const reportForm = ref({
  matchId: null,
  symptoms: '',
  reactionTime: null,
  hospital: '',
  reporter: ''
})

function fmtDateTimeInput(d) {
  if (!d) return null
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function chooseIssue(row) {
  activeIssue.value = row
  reportForm.value = {
    matchId: row.id,
    symptoms: '',
    reactionTime: fmtDateTimeInput(new Date(row.issueTime || Date.now())),
    hospital: row.hospital || '',
    reporter: ''
  }
  traceLoading.value = true
  try {
    const res = await getReactionTrace(row.id)
    trace.value = res.data
  } catch (e) {
    trace.value = null
    ElMessage.error(e.message)
  } finally {
    traceLoading.value = false
  }
}

function openReport() {
  if (!activeIssue.value) {
    ElMessage.warning('请先在「已发血记录」中选择一条')
    return
  }
  reportVisible.value = true
}

async function submitReport() {
  if (!reportForm.value.symptoms || !reportForm.value.symptoms.trim()) {
    ElMessage.warning('请登记反应症状')
    return
  }
  if (!reportForm.value.reactionTime) {
    ElMessage.warning('请选择反应发生时间')
    return
  }
  try {
    const res = await reportReaction({
      matchId: reportForm.value.matchId,
      symptoms: reportForm.value.symptoms.trim(),
      reactionTime: reportForm.value.reactionTime.replace(' ', 'T'),
      hospital: reportForm.value.hospital,
      reporter: reportForm.value.reporter
    })
    reportVisible.value = false
    if (res.data?.duplicated) {
      ElMessageBox.alert(res.data.reportMessage || res.message, '该反应已登记（幂等）', {
        type: 'warning',
        confirmButtonText: '查看事件'
      }).then(() => openEvent(res.data.id))
    } else {
      ElMessage.success(res.message)
    }
    await loadEvents()
    if (res.data?.id) openEvent(res.data.id)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

// ---------------- 事件详情 / 复核 ----------------
async function openEvent(id) {
  try {
    const res = await getReaction(id)
    activeEventId.value = id
    activeEvent.value = res.data
    activeIssue.value = issues.value.find((i) => i.id === res.data.matchId) || null
  } catch (e) {
    ElMessage.error(e.message)
  }
}

const reviewVisible = ref(false)
const reviewForm = ref({ result: 'EXCLUDED', reviewer: '', comment: '' })

function openReview(result) {
  reviewForm.value = { result, reviewer: '', comment: '' }
  reviewVisible.value = true
}

async function submitReview() {
  if (!activeEvent.value) return
  if (!reviewForm.value.reviewer || !reviewForm.value.reviewer.trim()) {
    ElMessage.warning('请填写质量复核人')
    return
  }
  try {
    const res = await reviewReaction(activeEvent.value.id, {
      result: reviewForm.value.result,
      reviewer: reviewForm.value.reviewer.trim(),
      comment: reviewForm.value.comment
    })
    ElMessage.success(res.message)
    reviewVisible.value = false
    await loadEvents()
    openEvent(activeEvent.value.id)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

// ---------------- 加载 ----------------
async function loadIssues() {
  try {
    const res = await getIssues()
    issues.value = res.data || []
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function loadEvents() {
  loading.value = true
  try {
    const res = await getReactions(statusFilter.value)
    events.value = res.data || []
    if (activeEventId.value) {
      const stillThere = events.value.find((e) => e.id === activeEventId.value)
      if (stillThere) {
        const detail = await getReaction(activeEventId.value)
        activeEvent.value = detail.data
      }
    } else if (events.value.length) {
      openEvent(events.value[0].id)
    }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function filterStatus(s) {
  statusFilter.value = statusFilter.value === s ? '' : s
  loadEvents()
}

onMounted(async () => {
  await loadIssues()
  await loadEvents()
})

// ---------------- 展示辅助 ----------------
function fmtDT(v) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '--'
}

const sampleRows = computed(() => {
  const s = activeEvent.value?.trace?.sample
  if (!s) return []
  return [
    { k: '标本条码', v: s.barcode },
    { k: '血型', v: s.bloodType },
    { k: 'ALT (U/L)', v: s.altValue || '--' },
    { k: '血红蛋白 (g/L)', v: s.hbValue || '--' },
    { k: 'HBV 乙肝', v: s.hbvResult || '未检测' },
    { k: 'HCV 丙肝', v: s.hcvResult || '未检测' },
    { k: 'HIV', v: s.hivResult || '未检测' },
    { k: '梅毒', v: s.syphilisResult || '未检测' },
    { k: '复核人', v: s.reviewer || '--' },
    { k: '标本状态', v: s.statusName, highlight: s.status !== 'RELEASED' },
    { k: '检测时间', v: fmtDT(s.testedAt) },
    { k: '放行时间', v: fmtDT(s.releasedAt) }
  ]
})

function linkTagType(row) {
  if (row.linkStatus === 'QUARANTINED') return 'danger'
  if (row.linkStatus === 'KEPT') return 'warning'
  if (row.linkStatus === 'RELEASED') return 'success'
  if (row.linkStatus === 'SCRAPPED') return 'info'
  return ''
}

function bagStatusTagType(status) {
  if (status === 'QUARANTINED') return 'danger'
  if (status === 'ISSUED') return 'warning'
  if (status === 'SCRAPPED') return 'info'
  return 'success'
}

function ledgerTag(action) {
  return {
    REPORT: 'primary',
    ISOLATE: 'danger',
    RELEASE: 'success',
    SCRAP: 'info',
    REVIEW: 'warning',
    KEEP: 'warning'
  }[action] || ''
}
</script>

<template>
  <div v-loading="loading">
    <div class="bc-alert danger">
      医院就一条<b>已发血记录</b>上报输血不良反应后，系统立即对<b>同一采血来源仍未发出的全部成分血袋</b>建立同源隔离，
      后续配血与发血一律拒绝；已发出 / 已报废血袋只入追溯清单。质量复核可「排除关联」（条件释放）或「确认风险」（隔离转报废），
      同一血袋被多事件隔离时任一事件未结束不得释放。
    </div>

    <div class="bc-react-layout">
      <!-- 左栏：发血记录 + 事件列表 -->
      <div class="bc-react-left">
        <div class="bc-panel">
          <div class="bc-panel-head">
            <div class="bc-panel-title">已发血记录（上报入口）</div>
            <div class="spacer"></div>
            <el-button size="small" type="primary" :disabled="!activeIssue" @click="openReport">
              上报反应
            </el-button>
          </div>
          <el-table :data="issues" size="small" stripe highlight-current-row max-height="260"
            @row-click="chooseIssue">
          <el-table-column prop="matchNo" label="发血单号" width="110" />
            <el-table-column prop="bagNo" label="供血袋号" width="105" />
            <el-table-column prop="recipient" label="受血者" width="80" />
            <el-table-column prop="hospital" label="医院" min-width="120" show-overflow-tooltip />
            <el-table-column prop="bloodType" label="血型" width="56" />
            <el-table-column prop="componentName" label="成分" width="90" />
            <el-table-column label="发血时间" width="120">
              <template #default="{ row }">{{ fmtDT(row.issueTime) }}</template>
            </el-table-column>
          </el-table>
        </div>

        <div class="bc-panel">
          <div class="bc-panel-head">
            <div class="bc-panel-title">处置事件台账</div>
            <div class="spacer"></div>
            <el-radio-group size="small" v-model="statusFilter" @change="loadEvents">
              <el-radio-button label="">全部</el-radio-button>
              <el-radio-button label="OPEN">在办</el-radio-button>
              <el-radio-button label="EXCLUDED">排除关联</el-radio-button>
              <el-radio-button label="CONFIRMED">确认风险</el-radio-button>
            </el-radio-group>
          </div>
          <el-table :data="events" size="small" stripe highlight-current-row max-height="300"
            @row-click="(row) => openEvent(row.id)">
            <el-table-column prop="eventNo" label="事件单号" width="120" />
            <el-table-column prop="matchNo" label="发血单号" width="105" />
            <el-table-column prop="recipient" label="受血者" width="76" />
            <el-table-column prop="symptoms" label="症状" min-width="130" show-overflow-tooltip />
            <el-table-column label="隔离/释放/报废" width="130">
              <template #default="{ row }">
                <span class="bc-counts">
                  <b class="c-iso">{{ row.isolatedCount }}</b>/
                  <b class="c-rel">{{ row.releasedCount }}</b>/
                  <b class="c-scrap">{{ row.scrappedCount }}</b>
                </span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="92">
              <template #default="{ row }">
                <el-tag size="small"
                  :type="row.status === 'CONFIRMED' ? 'danger' : row.status === 'EXCLUDED' ? 'success' : 'warning'">
                  {{ row.statusName }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <!-- 右栏：来源链 / 同源清单 / 台账 -->
      <div class="bc-react-right">
        <div v-if="!activeEvent" class="bc-panel">
          <div class="bc-empty">请选择左侧处置事件查看完整来源链与隔离 / 释放 / 报废记录</div>
        </div>

        <template v-else>
          <!-- 事件头 -->
          <div class="bc-panel bc-event-head">
            <div>
              <div class="bc-panel-title">
                {{ activeEvent.eventNo }}
                <el-tag size="small" style="margin-left: 8px"
                  :type="activeEvent.status === 'CONFIRMED' ? 'danger' : activeEvent.status === 'EXCLUDED' ? 'success' : 'warning'">
                  {{ activeEvent.statusName }}
                </el-tag>
              </div>
              <div class="bc-event-meta">
                <span>受血者：<b>{{ activeEvent.recipient }}</b></span>
                <span>上报医院：<b>{{ activeEvent.hospital }}</b></span>
                <span>处置人：<b>{{ activeEvent.reporter }}</b></span>
                <span>反应时间：<b>{{ fmtDT(activeEvent.reactionTime) }}</b></span>
                <span>上报时间：<b>{{ fmtDT(activeEvent.reportTime) }}</b></span>
              </div>
              <div class="bc-event-symptom">症状：{{ activeEvent.symptoms }}</div>
            </div>
            <div class="spacer"></div>
            <div v-if="activeEvent.status === 'OPEN'" class="bc-review-btns">
              <el-button type="success" @click="openReview('EXCLUDED')">排除关联</el-button>
              <el-button type="danger" @click="openReview('CONFIRMED')">确认风险</el-button>
            </div>
            <div v-else class="bc-review-result">
              <div>复核结论：<b>{{ activeEvent.reviewResultName }}</b></div>
              <div>复核人：{{ activeEvent.reviewer }} · {{ fmtDT(activeEvent.reviewedAt) }}</div>
              <div v-if="activeEvent.reviewComment">意见：{{ activeEvent.reviewComment }}</div>
            </div>
          </div>

          <!-- 来源链 -->
          <div class="bc-panel">
            <div class="bc-panel-head">
              <div class="bc-panel-title">来源链反查</div>
              <div class="bc-panel-sub">发血记录 → 供血血袋 → 制备批次 → 采血记录 → 检验标本</div>
            </div>
            <div v-if="!activeEvent.trace" class="bc-empty">来源链数据缺失</div>
            <div v-else class="bc-chain">
              <div class="bc-chain-node node-issue">
                <div class="node-tag">发血记录</div>
                <div class="node-no">{{ activeEvent.trace.issue.matchNo }}</div>
                <div class="node-line">医院：{{ activeEvent.trace.issue.hospital }}</div>
                <div class="node-line">受血者：{{ activeEvent.trace.issue.recipient }}</div>
                <div class="node-line">袋号：{{ activeEvent.trace.issue.bagNo }}</div>
                <div class="node-line">成分：{{ activeEvent.trace.issue.componentName }} {{ activeEvent.trace.issue.unitsU }}U</div>
                <div class="node-line">发血人：{{ activeEvent.trace.issue.issueOperator }} · {{ fmtDT(activeEvent.trace.issue.issueTime) }}</div>
              </div>
              <div class="bc-chain-arrow">→</div>
              <div class="bc-chain-node node-bag">
                <div class="node-tag">供血血袋</div>
                <template v-if="activeEvent.trace.bag">
                  <div class="node-no">{{ activeEvent.trace.bag.bagNo }}</div>
                  <div class="node-line">{{ activeEvent.trace.bag.bloodType }} · {{ activeEvent.trace.bag.componentName }} · {{ activeEvent.trace.bag.volumeMl }}ml</div>
                  <div class="node-line">献血者：{{ activeEvent.trace.bag.donorName || '--' }}</div>
                  <div class="node-line">采集：{{ activeEvent.trace.bag.collectDate }} / 效期：{{ activeEvent.trace.bag.expiryDate }}</div>
                  <div class="node-line">
                    状态：
                    <el-tag size="small" :type="bagStatusTagType(activeEvent.trace.bag.status)">
                      {{ activeEvent.trace.bag.statusName }}
                    </el-tag>
                  </div>
                </template>
                <div v-else class="node-line">无关联血袋</div>
              </div>
              <div class="bc-chain-arrow">→</div>
              <div class="bc-chain-node node-batch">
                <div class="node-tag">制备批次</div>
                <template v-if="activeEvent.trace.batch">
                  <div class="node-no">{{ activeEvent.trace.batch.batchNo }}</div>
                  <div class="node-line">全血来源：{{ activeEvent.trace.batch.collectNo }}</div>
                  <div class="node-line">
                    红 {{ activeEvent.trace.batch.rbcBags }} / 浆 {{ activeEvent.trace.batch.plasmaBags }}
                    / 小板 {{ activeEvent.trace.batch.plateletBags }} / 冷沉 {{ activeEvent.trace.batch.cryoBags }}
                  </div>
                  <div class="node-line">制备人：{{ activeEvent.trace.batch.operator }}</div>
                  <div class="node-line">时间：{{ fmtDT(activeEvent.trace.batch.preparedTime) }}</div>
                </template>
                <div v-else class="node-line">无制备批次</div>
              </div>
              <div class="bc-chain-arrow">→</div>
              <div class="bc-chain-node node-collect">
                <div class="node-tag">采血记录</div>
                <template v-if="activeEvent.trace.collection">
                  <div class="node-no">{{ activeEvent.trace.collection.collectNo }}</div>
                  <div class="node-line">
                    献血者：{{ activeEvent.trace.collection.donorName }}
                    （{{ activeEvent.trace.collection.donorNo }}）
                  </div>
                  <div class="node-line">
                    {{ activeEvent.trace.collection.donorGender }} · {{ activeEvent.trace.collection.donorAge }} 岁
                    · {{ activeEvent.trace.collection.bloodType }}
                  </div>
                  <div class="node-line">采血位：{{ activeEvent.trace.collection.stationCode }} · {{ activeEvent.trace.collection.volumeMl }}ml</div>
                  <div class="node-line">护士：{{ activeEvent.trace.collection.nurse }} · {{ activeEvent.trace.collection.collectDate }}</div>
                </template>
                <div v-else class="node-line">无采血记录</div>
              </div>
              <div class="bc-chain-arrow">→</div>
              <div class="bc-chain-node node-sample">
                <div class="node-tag">检验标本</div>
                <template v-if="activeEvent.trace.sample">
                  <div class="node-no">{{ activeEvent.trace.sample.barcode }}</div>
                  <div class="node-line">
                    状态：
                    <el-tag size="small" :type="activeEvent.trace.sample.status === 'RELEASED' ? 'success' : 'danger'">
                      {{ activeEvent.trace.sample.statusName }}
                    </el-tag>
                  </div>
                  <div class="node-line">
                    HBV {{ activeEvent.trace.sample.hbvResult || '--' }} ·
                    HCV {{ activeEvent.trace.sample.hcvResult || '--' }}
                  </div>
                  <div class="node-line">
                    HIV {{ activeEvent.trace.sample.hivResult || '--' }} ·
                    梅毒 {{ activeEvent.trace.sample.syphilisResult || '--' }}
                  </div>
                  <div class="node-line">ALT {{ activeEvent.trace.sample.altValue || '--' }} · Hb {{ activeEvent.trace.sample.hbValue || '--' }}</div>
                </template>
                <div v-else class="node-line">无检验标本</div>
              </div>
            </div>
          </div>

          <!-- 同源血袋清单 -->
          <div class="bc-panel">
            <div class="bc-panel-head">
              <div class="bc-panel-title">同源血袋处置清单</div>
              <div class="bc-panel-sub">
                在库血袋已即时隔离；已发出 / 已报废只追溯不改写
              </div>
            </div>
            <el-table :data="activeEvent.links" size="small" stripe>
              <el-table-column prop="bagNo" label="袋号" width="110" />
              <el-table-column prop="bloodType" label="血型" width="58" />
              <el-table-column prop="componentName" label="成分" width="92" />
              <el-table-column label="效期" width="100">
                <template #default="{ row }">
                  {{ row.expiryDate || '--' }}
                  <span v-if="row.daysToExpiry !== null && row.daysToExpiry <= 0" class="c-expired">（已过期）</span>
                </template>
              </el-table-column>
              <el-table-column label="血袋当前状态" width="100">
                <template #default="{ row }">
                  <el-tag size="small" :type="bagStatusTagType(row.bagStatus)">{{ row.bagStatusName }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="关联类型" width="92">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.linkType === 'ISOLATED' ? 'danger' : 'info'" effect="plain">
                    {{ row.linkTypeName }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="处置状态" width="92">
                <template #default="{ row }">
                  <el-tag size="small" :type="linkTagType(row)">{{ row.linkStatusName }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="note" label="说明" min-width="200" show-overflow-tooltip />
            </el-table>
          </div>

          <!-- 逐步台账 -->
          <div class="bc-panel">
            <div class="bc-panel-head">
              <div class="bc-panel-title">隔离 / 释放 / 报废逐步台账</div>
              <div class="bc-panel-sub">按时间顺序留痕，刷新或服务重启后结果一致</div>
            </div>
            <el-timeline class="bc-ledger">
              <el-timeline-item v-for="item in activeEvent.ledger" :key="item.id"
                :timestamp="fmtDT(item.actionTime) + ' · ' + (item.operator || '--')"
                :type="ledgerTag(item.action)" placement="top">
                <el-tag size="small" :type="ledgerTag(item.action)" style="margin-right: 8px">
                  {{ item.actionName }}
                </el-tag>
                <b v-if="item.bagNo">{{ item.bagNo }}：</b>{{ item.detail }}
              </el-timeline-item>
            </el-timeline>
          </div>
        </template>
      </div>
    </div>

    <!-- 上报弹窗：先展示来源链 -->
    <el-dialog v-model="reportVisible" title="上报输血不良反应" width="860px" top="6vh">
      <div v-if="trace" class="bc-report-chain">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px"
          :title="`针对发血记录 ${trace.issue.matchNo}（${trace.issue.hospital} · 受血者 ${trace.issue.recipient} · 供血袋 ${trace.issue.bagNo}）上报`" />
        <div class="bc-chain bc-chain-mini">
          <div class="bc-chain-node node-issue">
            <div class="node-tag">发血</div>
            <div class="node-no">{{ trace.issue.matchNo }}</div>
            <div class="node-line">{{ fmtDT(trace.issue.issueTime) }} · {{ trace.issue.issueOperator }}</div>
          </div>
          <div class="bc-chain-arrow">→</div>
          <div class="bc-chain-node node-bag">
            <div class="node-tag">供血血袋</div>
            <div class="node-no">{{ trace.bag?.bagNo || '--' }}</div>
            <div class="node-line">{{ trace.bag?.bloodType }} {{ trace.bag?.componentName }}</div>
            <div class="node-line">
              <el-tag size="small" :type="bagStatusTagType(trace.bag?.status)">{{ trace.bag?.statusName }}</el-tag>
            </div>
          </div>
          <div class="bc-chain-arrow">→</div>
          <div class="bc-chain-node node-batch">
            <div class="node-tag">制备批次</div>
            <div class="node-no">{{ trace.batch?.batchNo || '无' }}</div>
            <div class="node-line">{{ trace.batch?.collectNo || '' }}</div>
          </div>
          <div class="bc-chain-arrow">→</div>
          <div class="bc-chain-node node-collect">
            <div class="node-tag">采血记录</div>
            <div class="node-no">{{ trace.collection?.collectNo || '无' }}</div>
            <div class="node-line">{{ trace.collection?.donorName }} · {{ trace.collection?.stationCode }}</div>
          </div>
          <div class="bc-chain-arrow">→</div>
          <div class="bc-chain-node node-sample">
            <div class="node-tag">检验标本</div>
            <div class="node-no">{{ trace.sample?.barcode || '无' }}</div>
            <div class="node-line">
              <el-tag size="small" :type="trace.sample?.status === 'RELEASED' ? 'success' : 'danger'">
                {{ trace.sample?.statusName || '无标本' }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>

      <el-form label-width="96px" style="margin-top: 8px">
        <el-form-item label="反应症状" required>
          <el-input v-model="reportForm.symptoms" type="textarea" :rows="2"
            placeholder="例如：输注约 20 分钟后出现畏寒、发热，体温 38.9℃，伴腰部酸胀" />
        </el-form-item>
        <el-form-item label="发生时间" required>
          <el-date-picker v-model="reportForm.reactionTime" type="datetime"
            format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm"
            placeholder="选择反应发生时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="上报医院" required>
          <el-input v-model="reportForm.hospital" placeholder="医院名称" />
        </el-form-item>
        <el-form-item label="处置人" required>
          <el-input v-model="reportForm.reporter" placeholder="医院处置 / 上报人姓名" />
        </el-form-item>
      </el-form>
      <el-alert type="warning" :closable="false" show-icon
        title="提交后立即隔离同一采血来源仍未发出的全部成分血袋；相同反应时间与症状的重复上报只形成一宗事件。" />
      <template #footer>
        <el-button @click="reportVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReport">提交上报并隔离同源血袋</el-button>
      </template>
    </el-dialog>

    <!-- 复核弹窗 -->
    <el-dialog v-model="reviewVisible"
      :title="reviewForm.result === 'CONFIRMED' ? '质量复核：确认风险' : '质量复核：排除关联'"
      width="520px">
      <el-alert v-if="reviewForm.result === 'CONFIRMED'" type="error" :closable="false" show-icon
        title="确认风险后，全部隔离血袋将转为报废并扣减库存，处置链保留；操作不可撤销。"
        style="margin-bottom: 12px" />
      <el-alert v-else type="success" :closable="false" show-icon
        title="排除关联后，仅释放仍满足原检验放行、未过期且未被其他在办事件隔离的血袋；不满足条件的血袋保持隔离。"
        style="margin-bottom: 12px" />
      <el-form label-width="88px">
        <el-form-item label="质量复核人" required>
          <el-input v-model="reviewForm.reviewer" placeholder="复核人姓名" />
        </el-form-item>
        <el-form-item label="复核意见">
          <el-input v-model="reviewForm.comment" type="textarea" :rows="3"
            :placeholder="reviewForm.result === 'CONFIRMED' ? '例如：回溯判定为同源细菌污染，整批报废' : '例如：判定为患者自身药物热，与血液无关'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button :type="reviewForm.result === 'CONFIRMED' ? 'danger' : 'success'" @click="submitReview">
          提交复核结论
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.bc-react-layout {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}
.bc-react-left {
  flex: 1 1 42%;
  min-width: 380px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.bc-react-right {
  flex: 1 1 58%;
  min-width: 460px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.bc-event-head {
  display: flex;
  align-items: flex-start;
}
.bc-event-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 12.5px;
  color: var(--bc-sub);
  margin: 6px 0;
}
.bc-event-meta b {
  color: var(--bc-ink);
}
.bc-event-symptom {
  font-size: 13px;
  background: #fdf0f4;
  border-left: 3px solid var(--bc-primary);
  padding: 6px 10px;
  border-radius: 0 8px 8px 0;
}
.bc-review-btns {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.bc-review-result {
  font-size: 12.5px;
  color: var(--bc-sub);
  text-align: right;
  line-height: 1.8;
}
.bc-counts b {
  font-size: 13px;
}
.c-iso {
  color: #c62828;
}
.c-rel {
  color: #2e7d32;
}
.c-scrap {
  color: #757575;
}
.c-expired {
  color: #c62828;
}
.bc-chain {
  display: flex;
  align-items: stretch;
  gap: 6px;
  overflow-x: auto;
  padding: 6px 2px 10px;
}
.bc-chain-mini .bc-chain-node {
  min-width: 130px;
}
.bc-chain-node {
  flex: 1 0 150px;
  background: #fff;
  border: 1px solid var(--bc-line);
  border-top: 4px solid var(--bc-primary);
  border-radius: 10px;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.7;
}
.node-issue { border-top-color: #1565c0; }
.node-bag { border-top-color: #880e4f; }
.node-batch { border-top-color: #6a1b9a; }
.node-collect { border-top-color: #2e7d32; }
.node-sample { border-top-color: #ef6c00; }
.node-tag {
  font-size: 11px;
  color: var(--bc-sub);
}
.node-no {
  font-size: 13.5px;
  font-weight: 700;
  margin-bottom: 2px;
}
.node-line {
  color: #5d4037;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.bc-chain-arrow {
  display: flex;
  align-items: center;
  color: var(--bc-primary);
  font-weight: 700;
}
.bc-ledger {
  padding: 12px 8px 0 20px;
}
.bc-report-chain {
  margin-bottom: 8px;
}
</style>
