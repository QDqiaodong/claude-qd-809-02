import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 20000,
  headers: { 'Content-Type': 'application/json' }
})

// 统一解包：成功直接拿 ApiResponse，失败抛出后端给的中文提示
http.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const message = error.response?.data?.message || error.message || '请求失败'
    return Promise.reject(new Error(message))
  }
)

function qs(params) {
  const p = {}
  Object.keys(params || {}).forEach((k) => {
    if (params[k] !== undefined && params[k] !== null && params[k] !== '') p[k] = params[k]
  })
  return { params: p }
}

/* ===================== 采血与献血者 ===================== */
export const getDonors = (bloodType) => http.get('/collections/donors', qs({ bloodType }))
export const createDonor = (data) => http.post('/collections/donors', data)
export const getStations = (bloodType) => http.get('/collections/stations', qs({ bloodType }))
export const getCollections = (bloodType, donorId) =>
  http.get('/collections', qs({ bloodType, donorId }))
export const collectBlood = (data) => http.post('/collections/collect', data)
export const finishCollect = (id) => http.post(`/collections/${id}/finish`)
export const abnormalCollect = (id) => http.post(`/collections/${id}/abnormal`)

/* ===================== 标本检验与成分制备 ===================== */
export const getSamples = (bloodType) => http.get('/lab/samples', qs({ bloodType }))
export const inputResult = (id, data) => http.post(`/lab/samples/${id}/result`, data)
export const retestSample = (id, data) => http.post(`/lab/samples/${id}/retest`, data)
export const releaseSample = (id, reviewer, decisions) =>
  http.post(`/lab/samples/${id}/release`, { reviewer, decisions })
export const moveSample = (id, status, reviewer) =>
  http.post('/lab/samples/' + id + '/move', null, qs({ status, reviewer }))
export const getPreparations = (bloodType) => http.get('/lab/preparations', qs({ bloodType }))
export const getPrepSummary = () => http.get('/lab/preparations/summary')

/* ===================== 血液库存 ===================== */
export const getMatrix = (bloodType) => http.get('/inventory/matrix', qs({ bloodType }))
export const getCellBags = (bloodType, component) =>
  http.get('/inventory/cells', qs({ bloodType, component }))
export const adjustInventory = (data) => http.post('/inventory/adjust', data)
export const scrapExpired = () => http.post('/inventory/scrap-expired')
export const getScraps = (bloodType) => http.get('/inventory/scraps', qs({ bloodType }))
export const getInventorySummary = () => http.get('/inventory/summary')

/* ===================== 用血申请 / 配血 / 发血 ===================== */
export const getRequests = (bloodType) => http.get('/transfusion/requests', qs({ bloodType }))
export const createRequest = (data) => http.post('/transfusion/requests', data)
export const getMatches = (bloodType) => http.get('/transfusion/matches', qs({ bloodType }))
export const createMatch = (data) => http.post('/transfusion/matches', data)
export const setMatchResult = (id, result, operator) =>
  http.post(`/transfusion/matches/${id}/result`, { result, operator })
export const issueBlood = (id, operator) =>
  http.post(`/transfusion/matches/${id}/issue`, { operator })
export const recoveryMatch = (id) => http.post(`/transfusion/matches/${id}/recovery`)
export const getIssues = () => http.get('/transfusion/issues')
export const getHospitalSummary = () => http.get('/transfusion/issues/by-hospital')

/* ===================== 输血不良反应追溯台 ===================== */
export const getReactions = (status) => http.get('/reactions', qs({ status }))
export const getReaction = (id) => http.get(`/reactions/${id}`)
export const getReactionTrace = (matchId) => http.get(`/reactions/trace/${matchId}`)
export const reportReaction = (data) => http.post('/reactions/report', data)
export const reviewReaction = (id, data) => http.post(`/reactions/${id}/review`, data)

/* ===================== 概览与词典 ===================== */
export const getOverview = () => http.get('/stats/overview')
export const getDict = () => http.get('/stats/dict')
export const getInventorySummaryStats = () => http.get('/stats/inventory-summary')
export const getHospitalSummaryStats = () => http.get('/stats/hospital-summary')

/* ===================== 日期小工具 ===================== */
export function fmtDate(value) {
  if (!value) return '--'
  const d = value instanceof Date ? value : new Date(value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function fmtTime(value) {
  if (!value) return '--'
  const d = value instanceof Date ? value : new Date(value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export default http
