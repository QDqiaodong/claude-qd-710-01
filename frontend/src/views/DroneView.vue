<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <h3>植保无人机</h3>
        <span class="panel-sub">载荷 / 喷幅 / 累计飞行小时与作业亩数 · 在库的机可派机</span>
        <div class="panel-extra">
          <el-select v-model="filterStatus" clearable placeholder="状态筛选" style="width: 150px" @change="load">
            <el-option v-for="s in droneStatuses" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
          <el-button size="small" @click="load">刷新</el-button>
          <el-button size="small" type="primary" @click="createVisible = true">新机入库</el-button>
        </div>
      </div>

      <div class="summary-bar" style="margin-bottom: 12px">
        <div class="sb-item"><span>机身总数</span><b>{{ drones.length }}</b></div>
        <div class="sb-item"><span>在库</span><b>{{ count('IDLE') }}</b></div>
        <div class="sb-item"><span>作业中</span><b>{{ count('WORKING') }}</b></div>
        <div class="sb-item"><span>维修 / 停用</span><b>{{ count('MAINT') + count('DISABLED') }}</b></div>
        <div class="sb-item"><span>累计作业亩数</span><b>{{ totalArea.toFixed(1) }}</b></div>
        <div class="sb-item"><span>累计飞行小时</span><b>{{ totalHours.toFixed(1) }}</b></div>
      </div>

      <div class="card-grid">
        <div v-for="d in drones" :key="d.id" class="unit-card">
          <div class="uc-head">
            <span class="uc-icon">🚁</span>
            <div>
              <div class="uc-id">{{ d.bodyNo }}</div>
              <div class="uc-sub">{{ d.model }} · {{ d.droneTypeName }}</div>
            </div>
            <span class="mini-tag" style="margin-left: auto" :style="statusStyle(d.status)">
              {{ d.statusName }}
            </span>
          </div>

          <div class="kv-grid">
            <div class="kv"><span>最大载荷</span><b>{{ d.maxPayload }} L</b></div>
            <div class="kv"><span>喷幅</span><b>{{ d.sprayWidth }} m</b></div>
            <div class="kv"><span>累计作业亩数</span><b>{{ d.totalAreaMu }}</b></div>
            <div class="kv"><span>累计飞行小时</span><b>{{ d.totalFlightHours }}</b></div>
            <div class="kv"><span>电池组数</span><b>{{ d.batteryPacks }} 组</b></div>
            <div class="kv"><span>购入日期</span><b>{{ d.purchaseDate || '—' }}</b></div>
          </div>

          <div class="tag-line">
            <span class="mini-tag">在手作业单 {{ d.openOrders }}</span>
            <span v-if="d.currentPlotNo" class="mini-tag">正在喷：{{ d.currentPlotNo }}</span>
            <span class="mini-tag">累计 {{ d.sortCount }} 架次</span>
          </div>

          <div class="card-actions">
            <el-select v-model="statusMap[d.id]" placeholder="切换状态" size="small" style="width: 120px" @change="changeStatus(d)">
              <el-option v-for="s in allowedStatus(d.status)" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
            <el-button size="small" type="primary" :disabled="d.status !== 'IDLE'" @click="openDispatch(d)">派机</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 派机：给这台机配地块 + 飞手，直接生成一张待飞作业单 -->
    <el-dialog v-model="dispatchVisible" :title="dispatchDrone ? '派机 · ' + dispatchDrone.bodyNo : '派机'" width="560">
      <el-form :model="dispatchForm" label-width="110px">
        <el-form-item label="作业单号">
          <el-input v-model="dispatchForm.orderNo" placeholder="如 JO-24092801" />
        </el-form-item>
        <el-form-item label="地块">
          <el-select v-model="dispatchForm.plotId" filterable placeholder="选择地块" style="width: 100%">
            <el-option v-for="p in plotOptions" :key="p.id" :label="p.plotNo + ' · ' + p.farmerName + ' · ' + p.areaMu + '亩'" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="飞手">
          <el-select v-model="dispatchForm.pilotId" filterable placeholder="选择飞手" style="width: 100%">
            <el-option v-for="p in pilotOptions" :key="p.id" :label="p.pilotNo + ' · ' + p.name + ' · ' + p.licenseName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="药剂配方"><el-input v-model="dispatchForm.formula" /></el-form-item>
        <el-form-item label="亩用量(L/亩)"><el-input-number v-model="dispatchForm.dosePerMu" :min="0.1" :max="3" :step="0.1" :precision="2" /></el-form-item>
        <el-form-item label="计划亩数"><el-input-number v-model="dispatchForm.plannedMu" :min="1" :precision="2" /></el-form-item>
        <el-form-item label="作业日期">
          <el-date-picker v-model="dispatchForm.jobDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="天气"><el-input v-model="dispatchForm.weather" placeholder="晴 / 多云 / 阴" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dispatchVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDispatch">生成作业单</el-button>
      </template>
    </el-dialog>

    <!-- 新机入库 -->
    <el-dialog v-model="createVisible" title="新机入库" width="520">
      <el-form :model="form" label-width="110px">
        <el-form-item label="机身编号"><el-input v-model="form.bodyNo" placeholder="如 DP-13" /></el-form-item>
        <el-form-item label="型号"><el-input v-model="form.model" placeholder="如 大疆 T70" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.droneType" style="width: 100%">
            <el-option v-for="t in types" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="最大载荷(L)"><el-input-number v-model="form.maxPayload" :min="1" :precision="2" /></el-form-item>
        <el-form-item label="喷幅(m)"><el-input-number v-model="form.sprayWidth" :min="1" :precision="2" /></el-form-item>
        <el-form-item label="电池组数"><el-input-number v-model="form.batteryPacks" :min="1" :max="20" /></el-form-item>
        <el-form-item label="购入日期">
          <el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { droneApi, pilotApi, plotApi } from '../api'

const plant = inject('plant')
const drones = ref([])
const filterStatus = ref('')
const statusMap = reactive({})
const createVisible = ref(false)
const dispatchVisible = ref(false)
const dispatchDrone = ref(null)
const plotOptions = ref([])
const pilotOptions = ref([])

const droneStatuses = [
  { value: 'IDLE', label: '在库' },
  { value: 'WORKING', label: '作业中' },
  { value: 'MAINT', label: '维修' },
  { value: 'DISABLED', label: '停用' }
]
const types = [
  { value: 'MULTI', label: '多旋翼' },
  { value: 'SINGLE', label: '单旋翼' },
  { value: 'SPRAY', label: '植保专用机' }
]

const form = ref({ bodyNo: '', model: '', droneType: 'MULTI', maxPayload: 40, sprayWidth: 7, batteryPacks: 6, purchaseDate: null })
const dispatchForm = ref({ orderNo: '', plotId: null, pilotId: null, formula: '', dosePerMu: 1.2, plannedMu: 100, jobDate: '', weather: '' })

const totalArea = computed(() => drones.value.reduce((sum, d) => sum + Number(d.totalAreaMu || 0), 0))
const totalHours = computed(() => drones.value.reduce((sum, d) => sum + Number(d.totalFlightHours || 0), 0))

function count(status) {
  return drones.value.filter((d) => d.status === status).length
}

function statusStyle(status) {
  const map = { IDLE: '#2e7d32', WORKING: '#7b1fa2', MAINT: '#ef6c00', DISABLED: '#c62828' }
  return { color: '#fff', background: map[status] || '#6b5f7d', borderColor: 'transparent' }
}

function allowedStatus(current) {
  // 作业中由作业单起飞推进，这里只能手切在库 / 维修 / 停用
  return droneStatuses.filter((s) => s.value !== 'WORKING' && s.value !== current)
}

async function load() {
  drones.value = await droneApi.list(filterStatus.value ? { status: filterStatus.value } : {})
  drones.value.forEach((d) => {
    statusMap[d.id] = ''
  })
  plant.reloadOverview()
}

async function changeStatus(drone) {
  const target = statusMap[drone.id]
  if (!target) return
  await droneApi.setStatus(drone.id, target)
  ElMessage.success(drone.bodyNo + ' 已切到' + droneStatuses.find((s) => s.value === target).label)
  await load()
}

async function openDispatch(drone) {
  dispatchDrone.value = drone
  dispatchForm.value = {
    orderNo: 'JO-' + String(new Date().getFullYear()).slice(2) + String(new Date().getMonth() + 1).padStart(2, '0')
      + String(new Date().getDate()).padStart(2, '0') + String(Math.floor(Math.random() * 90) + 10),
    plotId: null,
    pilotId: null,
    formula: '吡蚜酮 25% + 磷酸二氢钾',
    dosePerMu: 1.2,
    plannedMu: 100,
    jobDate: '',
    weather: '晴'
  }
  plotOptions.value = await plotApi.options()
  pilotOptions.value = await pilotApi.options()
  dispatchVisible.value = true
}

async function submitDispatch() {
  const f = dispatchForm.value
  if (!f.orderNo || !f.plotId || !f.pilotId || !f.formula || !f.jobDate) {
    ElMessage.warning('单号、地块、飞手、配方、作业日期都要填')
    return
  }
  await droneApi.dispatch(dispatchDrone.value.id, f)
  ElMessage.success('派机成功，作业单已生成')
  dispatchVisible.value = false
  await load()
}

async function submitCreate() {
  const payload = { ...form.value }
  if (!payload.bodyNo || !payload.model) {
    ElMessage.warning('机身编号与型号都要填')
    return
  }
  await droneApi.create(payload)
  ElMessage.success('新机已入库')
  createVisible.value = false
  await load()
}

onMounted(load)
</script>
