<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <h3>异常架次停飞处置</h3>
        <span class="panel-sub">异常架次登记后自动待处置，值班主管可继续观察 / 停飞机身 / 解除（解除必填结论）</span>
        <div class="panel-extra">
          <el-select v-model="filterDroneId" clearable filterable placeholder="按无人机筛选" style="width: 200px" @change="load">
            <el-option v-for="d in droneOptions" :key="d.id"
              :label="d.bodyNo + ' · ' + d.model + (d.grounded ? '（停飞中）' : '')" :value="d.id" />
          </el-select>
          <el-select v-model="filterStatus" clearable placeholder="状态筛选" style="width: 150px" @change="load">
            <el-option v-for="s in statuses" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </div>

      <div class="summary-bar" style="margin-bottom: 12px">
        <div class="sb-item" v-for="s in statuses" :key="s.value"
          :class="{ 'is-alert-card': s.value === 'GROUNDED' }">
          <span>{{ s.label }}</span><b>{{ count(s.value) }}</b>
        </div>
      </div>

      <el-table :data="list" border size="small" :row-class-name="rowClass">
        <el-table-column prop="handlingNo" label="处置单号" width="130" />
        <el-table-column label="异常架次" width="170">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goLog(row)">
              {{ row.orderNo }} #{{ row.seqNo }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="机身" width="150">
          <template #default="{ row }">
            <el-button link :type="row.status === 'GROUNDED' ? 'danger' : 'primary'" size="small" @click="goDrone(row)">
              {{ row.droneBodyNo }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="plotNo" label="地块" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small" effect="dark">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="registrarName" label="登记人" width="90" />
        <el-table-column prop="supervisorName" label="值班主管" width="90">
          <template #default="{ row }">{{ row.supervisorName || '—' }}</template>
        </el-table-column>
        <el-table-column label="异常描述 / 结论" min-width="260">
          <template #default="{ row }">
            <div style="color: #c62828">{{ row.abnormalDesc }}</div>
            <div v-if="row.handleRemark" class="muted" style="margin-top: 2px">处置：{{ row.handleRemark }}</div>
            <div v-if="row.conclusion" style="color: #2e7d32; margin-top: 2px">结论：{{ row.conclusion }}</div>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">{{ fmt(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'RESOLVED'" size="small" type="primary" @click="openDetail(row)">
              去处置
            </el-button>
            <el-button v-else size="small" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 处置抽屉：异常情况 + 待改派待飞单 + 三个处置动作 + 留痕时间线 -->
    <el-drawer v-model="detailVisible" size="560px" :title="detail ? '异常处置 · ' + detail.handlingNo : '异常处置'">
      <div v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="处置单号">{{ detail.handlingNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag(detail.status)" size="small" effect="dark">{{ detail.statusName }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="异常架次">
            <el-button link type="primary" size="small" @click="goLog(detail)">{{ detail.orderNo }} #{{ detail.seqNo }}</el-button>
          </el-descriptions-item>
          <el-descriptions-item label="关联机身">
            <el-button link :type="detail.status === 'GROUNDED' ? 'danger' : 'primary'" size="small" @click="goDrone(detail)">
              {{ detail.droneBodyNo }} · {{ detail.droneModel }}
            </el-button>
            <span style="margin-left: 6px; color: #ef6c00">当前：{{ detail.droneStatusName }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="地块 / 承包户">{{ detail.plotNo }} · {{ detail.farmerName }}</el-descriptions-item>
          <el-descriptions-item label="起飞时间">{{ fmt(detail.takeoffAt) }}</el-descriptions-item>
          <el-descriptions-item label="登记人">{{ detail.registrarName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="登记时间">{{ fmt(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="值班主管">{{ detail.supervisorName || '待主管处理' }}</el-descriptions-item>
          <el-descriptions-item label="观察 / 停飞时间">
            {{ detail.observedAt ? fmt(detail.observedAt) : '—' }}<span v-if="detail.groundedAt"> / {{ fmt(detail.groundedAt) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="异常描述" :span="2">
            <span style="color: #c62828">{{ detail.abnormalDesc }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.handleRemark" label="处置说明" :span="2">{{ detail.handleRemark }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.conclusion" label="处置结论" :span="2">
            <span style="color: #2e7d32">{{ detail.conclusion }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.resolvedAt" label="解除时间" :span="2">{{ fmt(detail.resolvedAt) }}</el-descriptions-item>
        </el-descriptions>

        <!-- 停飞后受影响的待飞单：明确提示改派 -->
        <el-alert v-if="detail.groundedOrders.length" type="error" :closable="false" show-icon style="margin: 12px 0"
          :title="`机身 ${detail.droneBodyNo} 已停飞，以下 ${detail.groundedOrders.length} 张待飞作业单不得再用该机，请尽快改派：`">
          <div v-for="o in detail.groundedOrders" :key="o.id" style="margin-top: 4px">
            <el-button link type="warning" size="small" @click="goReassign(o)">
              {{ o.orderNo }} · {{ o.plotNo }} · {{ o.farmerName }} · {{ o.jobDate }} · 飞手 {{ o.pilotName || '—' }} → 去改派
            </el-button>
          </div>
        </el-alert>

        <!-- 处置动作区（已解除只展示结论） -->
        <div v-if="detail.status !== 'RESOLVED'" class="action-box">
          <div class="drawer-title">值班处置</div>
          <el-form label-width="92px" size="small">
            <el-form-item label="值班主管">
              <el-select v-model="action.supervisorId" filterable placeholder="选择值班主管（三级 / 教员）" style="width: 100%">
                <el-option v-for="p in supervisors" :key="p.id"
                  :label="p.pilotNo + ' · ' + p.name + ' · ' + p.licenseName" :value="p.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="处置说明">
              <el-input v-model="action.remark" type="textarea" :rows="2"
                placeholder="继续观察填观察要点；停飞填故障现象与停飞原因" />
            </el-form-item>
            <el-form-item label="处置结论">
              <el-input v-model="action.conclusion" type="textarea" :rows="2"
                placeholder="仅解除时必填：检修结果与能否复飞的结论" />
            </el-form-item>
            <el-form-item>
              <el-button :disabled="detail.status === 'GROUNDED'" @click="doObserve">继续观察</el-button>
              <el-button type="warning" :disabled="detail.status === 'GROUNDED'" @click="doGround">停飞机身</el-button>
              <el-button type="success" @click="doResolve">解除</el-button>
            </el-form-item>
          </el-form>
          <div class="muted" style="font-size: 12px">
            停飞后机身立即转为维修中，未起飞作业单不得再选该机；解除前必须填写处置结论，
            机身上仍有其他未结束处置时不能恢复在库。
          </div>
        </div>

        <div class="drawer-title">处置留痕</div>
        <el-timeline>
          <el-timeline-item v-for="a in detail.actions" :key="a.id"
            :type="timelineType(a.action)" :timestamp="fmt(a.createdAt)" placement="top">
            <b>{{ a.actionName }}</b>
            <span class="muted" style="margin-left: 8px">{{ a.supervisorName || '系统' }}</span>
            <div v-if="a.remark" style="margin-top: 2px">{{ a.remark }}</div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { droneApi, handlingApi } from '../api'

const route = useRoute()
const router = useRouter()
const list = ref([])
const filterStatus = ref('')
const filterDroneId = ref(null)
const droneOptions = ref([])
const detailVisible = ref(false)
const detail = ref(null)
const supervisors = ref([])
const action = reactive({ supervisorId: null, remark: '', conclusion: '' })

const statuses = [
  { value: 'PENDING', label: '待处置' },
  { value: 'OBSERVING', label: '继续观察' },
  { value: 'GROUNDED', label: '已停飞' },
  { value: 'RESOLVED', label: '已解除' }
]

const fmt = (value) => (value ? String(value).replace('T', ' ').slice(0, 16) : '—')

function count(status) {
  return list.value.filter((item) => item.status === status).length
}

function statusTag(status) {
  return { PENDING: 'danger', OBSERVING: 'warning', GROUNDED: 'info', RESOLVED: 'success' }[status] || ''
}

function timelineType(act) {
  return { REGISTER: 'primary', OBSERVE: 'warning', GROUND: 'danger', RESOLVE: 'success' }[act] || ''
}

function rowClass({ row }) {
  if (row.status === 'GROUNDED') return 'row-grounded'
  if (row.status === 'PENDING') return 'row-abnormal'
  return ''
}

function buildParams() {
  const params = {}
  if (filterStatus.value) params.status = filterStatus.value
  if (filterDroneId.value) params.droneId = filterDroneId.value
  return params
}

async function load() {
  list.value = await handlingApi.list(buildParams())
}

async function openDetail(row) {
  await refreshDetail(row.id)
  if (!supervisors.value.length) supervisors.value = await handlingApi.supervisorOptions()
  detailVisible.value = true
}

async function refreshDetail(id) {
  detail.value = await handlingApi.detail(id)
  action.remark = detail.value.handleRemark || ''
  action.conclusion = detail.value.conclusion || ''
  action.supervisorId = detail.value.supervisorId || null
}

async function doObserve() {
  if (!action.supervisorId) return ElMessage.warning('请先选择值班主管')
  if (!action.remark.trim()) return ElMessage.warning('继续观察必须填写处置说明')
  await handlingApi.observe(detail.value.id, { supervisorId: action.supervisorId, remark: action.remark.trim() })
  ElMessage.success('已登记继续观察，处置保持未结束')
  await afterAction()
}

async function doGround() {
  if (!action.supervisorId) return ElMessage.warning('请先选择值班主管')
  if (!action.remark.trim()) return ElMessage.warning('停飞机身必须填写处置说明')
  try {
    await ElMessageBox.confirm(
      `停飞后机身 ${detail.value.droneBodyNo} 立即转为维修中，所有未起飞作业单不得再选用该机，确认停飞？`,
      '停飞确认', { type: 'warning', confirmButtonText: '确认停飞', cancelButtonText: '再想想' })
  } catch (e) {
    return
  }
  await handlingApi.ground(detail.value.id, { supervisorId: action.supervisorId, remark: action.remark.trim() })
  ElMessage.success('机身已停飞；受影响待飞单请在下方提示中改派')
  await afterAction()
}

async function doResolve() {
  if (!action.supervisorId) return ElMessage.warning('请先选择值班主管')
  if (!action.conclusion.trim()) return ElMessage.warning('解除前必须填写处置结论')
  await handlingApi.resolve(detail.value.id, {
    supervisorId: action.supervisorId, conclusion: action.conclusion.trim()
  })
  ElMessage.success('处置已解除；如无其他未结束处置，机身已恢复在库')
  await afterAction()
}

async function afterAction() {
  // 详情始终按 id 重取，避免列表带状态筛选时（如停飞后被筛掉）抽屉关掉
  await refreshDetail(detail.value.id)
  list.value = await handlingApi.list(buildParams())
}

/** 进入飞行日志（架次侧入口） */
function goLog(row) {
  detailVisible.value = false
  router.push({ path: '/logs', query: { jobOrderId: row.jobOrderId, flightLogId: row.flightLogId } })
}

/** 进入无人机详情（机身侧入口） */
function goDrone(row) {
  detailVisible.value = false
  router.push({ path: '/drones', query: { droneId: row.droneId } })
}

/** 待飞单改派：跳到作业单页并直接打开改派弹窗 */
function goReassign(order) {
  detailVisible.value = false
  router.push({ path: '/orders', query: { editOrderId: order.id } })
}

onMounted(async () => {
  droneOptions.value = await droneApi.options()
  // 从飞行日志 / 无人机详情带参进入
  if (route.query.droneId) filterDroneId.value = Number(route.query.droneId)
  if (route.query.status) filterStatus.value = String(route.query.status)
  await load()
  supervisors.value = await handlingApi.supervisorOptions()
  if (route.query.flightLogId) {
    const linked = list.value.find((item) => String(item.flightLogId) === String(route.query.flightLogId))
    if (linked) openDetail(linked)
  }
})
</script>

<style scoped>
.action-box {
  margin-top: 6px;
  padding: 10px 12px;
  background: #faf7fc;
  border: 1px solid var(--dp-border);
  border-radius: 8px;
}

:global(.el-table .row-grounded) {
  --el-table-tr-bg-color: #fff3e0;
}
</style>
