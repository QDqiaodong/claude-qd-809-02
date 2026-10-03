<script setup>
import { computed, inject, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getPreparations,
  getSamples,
  inputResult,
  moveSample,
  releaseSample,
  retestSample
} from '../api'

const bloodType = inject('bloodType')
const samples = ref([])
const preparations = ref([])
const loading = ref(false)
const draggingId = ref(null)
const dragOverLane = ref('')
const detail = ref(null)
const detailVisible = ref(false)

const lanes = [
  { key: 'PENDING', title: '待检', cls: 'pending', desc: '标本已贴码，等待上机' },
  { key: 'TESTING', title: '检测中', cls: 'testing', desc: 'ALT / 血红蛋白 / HBV / HCV / HIV / 梅毒' },
  { key: 'RELEASED', title: '已放行', cls: 'released', desc: '复核通过后自动生成成分制备单' }
]

const grouped = computed(() => ({
  PENDING: samples.value.filter((s) => s.status === 'PENDING'),
  TESTING: samples.value.filter((s) => s.status === 'TESTING'),
  RELEASED: samples.value.filter((s) => s.status === 'RELEASED'),
  SCRAPPED: samples.value.filter((s) => s.status === 'SCRAPPED')
}))

// 任意项目当前判定为阳性即红色（取各项目最后一次有效结果，后端扁平字段已是有效口径）
function positiveNow(s) {
  return s.hbvResult === 'POSITIVE' || s.hcvResult === 'POSITIVE'
    || s.hivResult === 'POSITIVE' || s.syphilisResult === 'POSITIVE'
}

// ---------------- HTML5 原生拖拽 ----------------
function onDragStart(sample, event) {
  draggingId.value = sample.id
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', String(sample.id))
}

function onDragEnd() {
  draggingId.value = null
  dragOverLane.value = ''
}

function onDragOver(lane, event) {
  event.preventDefault()
  event.dataTransfer.dropEffect = 'move'
  dragOverLane.value = lane
}

function onDragLeave() {
  dragOverLane.value = ''
}

async function onDrop(lane) {
  const id = draggingId.value
  dragOverLane.value = ''
  draggingId.value = null
  if (!id) return
  const sample = samples.value.find((s) => s.id === id)
  if (!sample || sample.status === lane) return
  await doMove(sample, lane)
}

async function doMove(sample, lane) {
  if (lane === 'RELEASED') {
    await openRelease(sample)
    return
  }
  try {
    const res = await moveSample(sample.id, lane)
    ElMessage.success(res.message || '状态已更新')
  } catch (e) {
    ElMessage.error(e.message)
    return
  }
  load()
}

// ---------------- 检验结果录入（初检） ----------------
const resultVisible = ref(false)
const resultForm = ref({ id: null, altValue: 22, hbValue: 135, hbvResult: 'NEGATIVE', hcvResult: 'NEGATIVE', hivResult: 'NEGATIVE', syphilisResult: 'NEGATIVE', operator: '林岚' })

function openResult(sample) {
  resultForm.value = {
    id: sample.id,
    altValue: 22,
    hbValue: 135,
    hbvResult: 'NEGATIVE',
    hcvResult: 'NEGATIVE',
    hivResult: 'NEGATIVE',
    syphilisResult: 'NEGATIVE',
    operator: sample.reviewer || '林岚'
  }
  resultVisible.value = true
}

async function submitResult() {
  try {
    const res = await inputResult(resultForm.value.id, resultForm.value)
    ElMessage.success(res.message || '结果已录入')
    resultVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

// ---------------- 复测（按项目发起一轮，原结果不覆盖） ----------------
const retestVisible = ref(false)
const retestForm = ref({ id: null, itemCode: 'ALT', newValue: 22, newResult: 'NEGATIVE', reason: '', operator: '林岚' })

const ITEM_OPTIONS = [
  { code: 'ALT', name: 'ALT（数值，U/L）', numeric: true },
  { code: 'HB', name: '血红蛋白（数值，g/L）', numeric: true },
  { code: 'HBV', name: 'HBV（阴阳性）', numeric: false },
  { code: 'HCV', name: 'HCV（阴阳性）', numeric: false },
  { code: 'HIV', name: 'HIV（阴阳性）', numeric: false },
  { code: 'SYPHILIS', name: '梅毒（阴阳性）', numeric: false }
]

const retestItem = computed(() => ITEM_OPTIONS.find((i) => i.code === retestForm.value.itemCode))

/** 已存在待复核轮的项目不能再发起（同一项目并发/重复只允许一轮在办） */
const pendingItemCodes = computed(() =>
  new Set((detail.value?.rounds || []).filter((r) => r.decision === 'PENDING').map((r) => r.itemCode)))

const retestable = computed(() =>
  detail.value && (detail.value.status === 'TESTING' || detail.value.status === 'SCRAPPED')
    && detail.value.items.every((i) => i.value !== '--' && i.conclusion !== '未检'))

function openRetest(sample) {
  detail.value = sample
  const firstAllowed = ITEM_OPTIONS.find((i) => !pendingItemCodes.value.has(i.code)) || ITEM_OPTIONS[0]
  retestForm.value = {
    id: sample.id,
    itemCode: firstAllowed.code,
    newValue: 22,
    newResult: 'NEGATIVE',
    reason: '',
    operator: sample.reviewer || '林岚'
  }
  retestVisible.value = true
}

async function submitRetest() {
  const payload = {
    itemCode: retestForm.value.itemCode,
    reason: retestForm.value.reason,
    operator: retestForm.value.operator
  }
  if (retestItem.value.numeric) {
    payload.newValue = retestForm.value.newValue
  } else {
    payload.newResult = retestForm.value.newResult
  }
  try {
    const res = await retestSample(retestForm.value.id, payload)
    ElMessage.success(res.message || '复测已记录')
    retestVisible.value = false
    detailVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function roundTagType(decision) {
  if (decision === 'ADOPTED') return 'success'
  if (decision === 'REJECTED') return 'info'
  return 'warning'
}

function itemRowClass({ row }) {
  return row.pending ? 'row-pending-retest' : ''
}

function roundRowClass({ row }) {
  if (row.current) return 'round-current'
  if (row.decision === 'REJECTED') return 'round-rejected'
  return ''
}

// ---------------- 放行（必须逐轮处置复测结果并填理由） ----------------
const releaseVisible = ref(false)
const releaseTarget = ref(null)
const releaseForm = ref({ reviewer: '林岚', decisions: [] })

const pendingRounds = computed(() =>
  (releaseTarget.value?.rounds || []).filter((r) => r.decision === 'PENDING'))

function openRelease(sample) {
  releaseTarget.value = sample
  releaseForm.value = {
    reviewer: sample.reviewer || '林岚',
    decisions: pendingRoundsOf(sample).map((r) => ({
      roundId: r.id,
      itemName: r.itemName,
      roundNo: r.roundNo,
      operator: r.operator,
      value: r.value + (r.unit || ''),
      conclusion: r.conclusion,
      decision: 'ADOPTED',
      reason: ''
    }))
  }
  releaseVisible.value = true
}

function pendingRoundsOf(sample) {
  return (sample.rounds || []).filter((r) => r.decision === 'PENDING')
}

async function submitRelease() {
  if (!releaseForm.value.reviewer || !releaseForm.value.reviewer.trim()) {
    ElMessage.error('放行前必须选择复核人')
    return
  }
  for (const d of releaseForm.value.decisions) {
    if (!d.reason || !d.reason.trim()) {
      ElMessage.error(`第 ${d.roundNo} 轮复测（${d.itemName}）必须填写${d.decision === 'ADOPTED' ? '采用' : '拒绝'}理由`)
      return
    }
  }
  const decisions = releaseForm.value.decisions.map((d) => ({
    roundId: d.roundId,
    decision: d.decision,
    reason: d.reason
  }))
  try {
    const res = await releaseSample(releaseTarget.value.id, releaseForm.value.reviewer.trim(), decisions)
    ElMessage.success(res.message || '已放行')
    releaseVisible.value = false
    detailVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

/** 明细抽屉里的放行按钮：有待复核轮走复核对话框，否则直接按原确认方式放行 */
async function releaseFromDetail() {
  const sample = detail.value
  detailVisible.value = false
  if (pendingRoundsOf(sample).length) {
    openRelease(sample)
    return
  }
  try {
    const { value } = await ElMessageBox.prompt(
      `标本 ${sample.barcode}（${sample.bloodType}）放入「已放行」将即时触发成分制备，请输入复核人：`,
      '选择复核人',
      { inputPlaceholder: '例如 林岚', inputValue: '林岚' }
    )
    const res = await releaseSample(sample.id, value, [])
    ElMessage.success(res.message || '已放行')
    await load()
  } catch (e) {
    if (e !== 'cancel' && e.message) ElMessage.error(e.message)
  }
}

function openDetail(sample) {
  detail.value = sample
  detailVisible.value = true
}

async function load() {
  loading.value = true
  try {
    const [s, p] = await Promise.all([getSamples(bloodType.value), getPreparations(bloodType.value)])
    samples.value = s.data || []
    preparations.value = (p.data || []).slice(0, 8)
    if (detail.value) {
      const fresh = samples.value.find((s) => s.id === detail.value.id)
      if (fresh) detail.value = fresh
    }
    if (releaseTarget.value) {
      const fresh = samples.value.find((s) => s.id === releaseTarget.value.id)
      if (fresh) releaseTarget.value = fresh
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
      三条轨道分别代表 <b>待检 / 检测中 / 已放行</b>：按住标本管拖动即可流转；落入「已放行」需选择复核人，并即时触发成分制备（领域事件）。
      检测中或因单项不合格报废的标本可在明细中<b>按项目发起复测</b>，原结果不覆盖、按轮次留痕，复核人放行时逐轮采用或拒绝。
    </div>

    <div
      v-for="lane in lanes"
      :key="lane.key"
      class="bc-lane"
      :class="lane.cls"
    >
      <div class="bc-lane-head">
        <span class="bc-lane-title">{{ lane.title }}</span>
        <span class="bc-lane-count">{{ grouped[lane.key].length }}</span>
        <span class="bc-panel-sub">{{ lane.desc }}</span>
      </div>
      <div
        class="bc-lane-body"
        :class="{ dragover: dragOverLane === lane.key }"
        @dragover="onDragOver(lane.key, $event)"
        @dragleave="onDragLeave"
        @drop="onDrop(lane.key)"
      >
        <div
          v-for="s in grouped[lane.key]"
          :key="s.id"
          class="bc-tube"
          :class="{ dragging: draggingId === s.id, positive: positiveNow(s) }"
          draggable="true"
          @dragstart="onDragStart(s, $event)"
          @dragend="onDragEnd"
          @click="openDetail(s)"
        >
          <div class="barcode"></div>
          <div class="info">
            <div class="code">
              {{ s.barcode }}
              <el-tag v-if="s.pendingRoundCount > 0" size="small" type="warning" effect="dark" round>
                待复核复测 {{ s.pendingRoundCount }}
              </el-tag>
            </div>
            <div class="who">
              {{ s.donorName }} · {{ s.reviewer ? '复核 ' + s.reviewer : '未复核' }}
              <span v-if="s.retestRoundCount > 0">· 已复测 {{ s.retestRoundCount }} 轮</span>
            </div>
          </div>
          <div class="blood">{{ s.bloodType }}</div>
        </div>
        <div v-if="!grouped[lane.key].length" class="bc-empty">拖动标本管到这里</div>
      </div>
    </div>

    <!-- 已报废标本（可复测救活；复测合格自动回到检测中） -->
    <div class="bc-lane scrapped">
      <div class="bc-lane-head">
        <span class="bc-lane-title">已报废</span>
        <span class="bc-lane-count">{{ grouped.SCRAPPED.length }}</span>
        <span class="bc-panel-sub">检验不合格（ALT 超标 / 血红蛋白偏低 / 传染病阳性）；点开可发起复测</span>
      </div>
      <div class="bc-lane-body">
        <div
          v-for="s in grouped.SCRAPPED"
          :key="s.id"
          class="bc-tube positive"
          :class="{ dragging: draggingId === s.id }"
          draggable="true"
          @dragstart="onDragStart(s, $event)"
          @dragend="onDragEnd"
          @click="openDetail(s)"
        >
          <div class="barcode"></div>
          <div class="info">
            <div class="code">
              {{ s.barcode }}
              <el-tag v-if="s.pendingRoundCount > 0" size="small" type="warning" effect="dark" round>
                待复核复测 {{ s.pendingRoundCount }}
              </el-tag>
            </div>
            <div class="who">
              {{ s.donorName }} · 判定不合格
              <span v-if="s.retestRoundCount > 0">· 已复测 {{ s.retestRoundCount }} 轮</span>
            </div>
          </div>
          <div class="blood">{{ s.bloodType }}</div>
        </div>
        <div v-if="!grouped.SCRAPPED.length" class="bc-empty">暂无报废标本</div>
      </div>
    </div>

    <div class="bc-panel">
      <div class="bc-panel-head">
        <div class="bc-panel-title">最近生成的成分制备批次</div>
        <div class="spacer"></div>
        <span class="bc-panel-sub">由标本放行事件自动写入</span>
      </div>
      <el-table :data="preparations" size="small" stripe>
        <el-table-column prop="batchNo" label="批号" width="130" />
        <el-table-column prop="collectNo" label="全血来源" width="120" />
        <el-table-column prop="bloodType" label="血型" width="60" />
        <el-table-column label="红细胞" width="110">
          <template #default="{ row }">{{ row.rbcBags }} 袋 / {{ row.rbcVolumeMl }} ml</template>
        </el-table-column>
        <el-table-column label="血浆" width="110">
          <template #default="{ row }">{{ row.plasmaBags }} 袋 / {{ row.plasmaVolumeMl }} ml</template>
        </el-table-column>
        <el-table-column label="血小板" width="110">
          <template #default="{ row }">{{ row.plateletBags }} 袋 / {{ row.plateletVolumeMl }} ml</template>
        </el-table-column>
        <el-table-column label="冷沉淀" width="110">
          <template #default="{ row }">{{ row.cryoBags }} 袋 / {{ row.cryoVolumeMl }} ml</template>
        </el-table-column>
        <el-table-column prop="operator" label="制备人" width="110" />
      </el-table>
    </div>

    <!-- 标本明细：当前判定 + 复测轮次 -->
    <el-drawer v-model="detailVisible" title="标本检验明细" size="560px">
      <div v-if="detail">
        <div class="bc-alert">
          {{ detail.barcode }} · {{ detail.donorName }} · {{ detail.bloodType }} ·
          <b>{{ detail.statusName }}</b>
          <span v-if="detail.pendingRoundCount > 0">
            ｜<el-tag size="small" type="warning">有 {{ detail.pendingRoundCount }} 轮复测待放行复核</el-tag>
          </span>
        </div>

        <div class="bc-panel-sub" style="margin: 10px 0 6px">各项目当前判定（最后一次有效结果）</div>
        <el-table :data="detail.items" size="small" :row-class-name="itemRowClass">
          <el-table-column prop="name" label="项目" width="100" />
          <el-table-column label="结果" width="120">
            <template #default="{ row }">
              {{ row.value }}{{ row.unit }}
              <el-tag v-if="row.retested" size="small" :type="row.pending ? 'warning' : 'success'" effect="plain">
                第 {{ row.roundNo }} 轮{{ row.pending ? '·待复核' : '' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="conclusion" label="判定" />
        </el-table>

        <template v-if="detail.rounds && detail.rounds.length">
          <div class="bc-panel-sub" style="margin: 16px 0 6px">复测轮次（初检为第 0 轮，原结果不覆盖）</div>
          <el-table :data="detail.rounds" size="small" stripe :row-class-name="roundRowClass">
            <el-table-column label="轮次" width="70">
              <template #default="{ row }">第{{ row.roundNo }}轮</template>
            </el-table-column>
            <el-table-column label="项目" width="86">
              <template #default="{ row }">{{ row.itemName }}</template>
            </el-table-column>
            <el-table-column label="新结果" width="96">
              <template #default="{ row }">{{ row.value }}{{ row.unit }}</template>
            </el-table-column>
            <el-table-column prop="conclusion" label="判定" width="70" />
            <el-table-column label="复核" width="150">
              <template #default="{ row }">
                <el-tag size="small" :type="roundTagType(row.decision)">
                  {{ row.decisionName }}<span v-if="row.current">·当前</span>
                </el-tag>
                <div v-if="row.reviewer" class="round-reviewer">{{ row.reviewer }}</div>
              </template>
            </el-table-column>
            <el-table-column label="原因 / 理由" min-width="160">
              <template #default="{ row }">
                <div>复测原因：{{ row.reason }}（{{ row.operator }}）</div>
                <div v-if="row.decisionReason" class="round-decision-reason">
                  复核理由：{{ row.decisionReason }}
                </div>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <div style="margin-top: 14px; display: flex; gap: 8px; flex-wrap: wrap">
          <el-button
            v-if="detail.status !== 'RELEASED' && detail.retestRoundCount === 0"
            type="primary"
            @click="openResult(detail)"
          >
            录入/重录初检结果
          </el-button>
          <el-tooltip
            v-if="detail.status === 'RELEASED'"
            content="已放行标本不得复测"
            placement="top"
          >
            <el-button type="warning" disabled>发起复测</el-button>
          </el-tooltip>
          <el-button
            v-else-if="retestable"
            type="warning"
            @click="openRetest(detail)"
          >
            发起复测
          </el-button>
          <el-tooltip
            v-else-if="detail.status !== 'RELEASED'"
            content="需先录全六项初检结果"
            placement="top"
          >
            <el-button type="warning" disabled>发起复测</el-button>
          </el-tooltip>
          <el-button
            v-if="detail.status === 'TESTING'"
            type="success"
            @click="releaseFromDetail"
          >
            放行并制备
          </el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 检验结果录入（初检） -->
    <el-dialog v-model="resultVisible" title="录入检验结果（初检 · 第 0 轮）" width="520px">
      <el-form label-width="92px">
        <el-form-item label="ALT">
          <el-input-number v-model="resultForm.altValue" :min="1" :max="200" :step="0.1" />
          <span style="margin-left: 8px; color: #8d6e63">U/L（&gt; 40 判不合格）</span>
        </el-form-item>
        <el-form-item label="血红蛋白">
          <el-input-number v-model="resultForm.hbValue" :min="60" :max="200" :step="0.5" />
          <span style="margin-left: 8px; color: #8d6e63">g/L</span>
        </el-form-item>
        <el-form-item label="HBV">
          <el-radio-group v-model="resultForm.hbvResult">
            <el-radio value="NEGATIVE">阴性</el-radio>
            <el-radio value="POSITIVE">阳性</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="HCV">
          <el-radio-group v-model="resultForm.hcvResult">
            <el-radio value="NEGATIVE">阴性</el-radio>
            <el-radio value="POSITIVE">阳性</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="HIV">
          <el-radio-group v-model="resultForm.hivResult">
            <el-radio value="NEGATIVE">阴性</el-radio>
            <el-radio value="POSITIVE">阳性</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="梅毒">
          <el-radio-group v-model="resultForm.syphilisResult">
            <el-radio value="NEGATIVE">阴性</el-radio>
            <el-radio value="POSITIVE">阳性</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="检验人">
          <el-input v-model="resultForm.operator" style="width: 160px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resultVisible = false">取消</el-button>
        <el-button type="primary" @click="submitResult">保存结果</el-button>
      </template>
    </el-dialog>

    <!-- 发起复测：项目 + 新结果 + 原因 + 检测人 -->
    <el-dialog v-model="retestVisible" title="发起复测（追加一轮，原结果不覆盖）" width="520px">
      <el-form v-if="detail" label-width="92px">
        <el-form-item label="复测项目">
          <el-select v-model="retestForm.itemCode" style="width: 260px">
            <el-option
              v-for="opt in ITEM_OPTIONS"
              :key="opt.code"
              :label="opt.name + (pendingItemCodes.has(opt.code) ? '（已有在办轮）' : '')"
              :value="opt.code"
              :disabled="pendingItemCodes.has(opt.code)"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="retestItem.numeric" label="新结果">
          <el-input-number
            v-model="retestForm.newValue"
            :min="1"
            :max="200"
            :step="retestForm.itemCode === 'ALT' ? 0.1 : 0.5"
          />
          <span style="margin-left: 8px; color: #8d6e63">{{ retestForm.itemCode === 'ALT' ? 'U/L' : 'g/L' }}</span>
        </el-form-item>
        <el-form-item v-else label="新结果">
          <el-radio-group v-model="retestForm.newResult">
            <el-radio value="NEGATIVE">阴性</el-radio>
            <el-radio value="POSITIVE">阳性</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="复测原因">
          <el-input
            v-model="retestForm.reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="例如：首次结果处于灰区 / 标本疑似脂血，换试剂复测"
          />
        </el-form-item>
        <el-form-item label="检测人">
          <el-input v-model="retestForm.operator" style="width: 160px" />
        </el-form-item>
        <div class="bc-panel-sub">
          同一项目存在待复核复测轮时不能重复发起；复测合格的报废标本将自动回到「检测中」，
          最终是否以复测结果为准，由复核人放行时裁定。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="retestVisible = false">取消</el-button>
        <el-button type="warning" @click="submitRetest">提交复测</el-button>
      </template>
    </el-dialog>

    <!-- 放行复核：逐轮采用 / 拒绝 + 理由 -->
    <el-dialog v-model="releaseVisible" title="放行复核：逐轮裁定复测结果" width="640px">
      <div v-if="releaseTarget" class="bc-alert">
        标本 {{ releaseTarget.barcode }} · {{ releaseTarget.donorName }} · {{ releaseTarget.bloodType }}。
        放行只依据各项目<b>最后一次有效结果</b>，请对每轮复测给出处置与理由。
      </div>
      <el-form label-width="82px" style="margin-top: 10px">
        <el-form-item label="复核人">
          <el-input v-model="releaseForm.reviewer" style="width: 180px" placeholder="例如 林岚" />
        </el-form-item>
        <el-form-item
          v-for="d in releaseForm.decisions"
          :key="d.roundId"
          :label="`第${d.roundNo}轮 ${d.itemName}`"
        >
          <div class="decision-box">
            <div class="decision-head">
              <span>新结果 <b>{{ d.value }}</b>（{{ d.conclusion }}），检测人 {{ d.operator }}</span>
              <el-radio-group v-model="d.decision" size="small">
                <el-radio-button value="ADOPTED">采用</el-radio-button>
                <el-radio-button value="REJECTED">拒绝</el-radio-button>
              </el-radio-group>
            </div>
            <el-input
              v-model="d.reason"
              size="small"
              placeholder="必须填写采用或拒绝该复测结果的理由"
              maxlength="500"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="releaseVisible = false">取消</el-button>
        <el-button type="success" @click="submitRelease">确认放行并制备</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.round-reviewer {
  font-size: 12px;
  color: #8d6e63;
}
.round-decision-reason {
  font-size: 12px;
  color: #6d4c41;
}
.decision-box {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.decision-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
:deep(.row-pending-retest) {
  background-color: #fff7e6;
}
:deep(.round-current) {
  background-color: #f0f9eb;
}
:deep(.round-rejected) {
  color: #9e9e9e;
}
</style>
