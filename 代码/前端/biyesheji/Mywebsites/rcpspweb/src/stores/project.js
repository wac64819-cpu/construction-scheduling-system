import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import axios from 'axios'

const API_BASE = 'http://localhost:8080/api/projects'

// 后端数据可能缺失部分配置，这里给出默认值兜底
export const DEFAULT_SETTINGS = {
  indirectCostPerDay: 1000,
  idleCostPerDay: 200,
  maxOutsourcingRatio: 0.5,
  outsourcingCostMultiplier: 1.5,
  maxEvaluations: 25000,
  populationSize: 100,
  mutationProbability: 0.01,
  crossoverProbability: 0.9,
}

/**
 * 项目状态仓库
 *
 * 把「当前项目 + 配置项 + 调度结果」从各视图里抽出来集中管理，
 * 避免项目配置页、调度看板、结果展示页各自重复请求同一份数据。
 */
export const useProjectStore = defineStore('project', () => {
  // ---- state ----
  const currentProject = ref(null)
  const settings = ref({ ...DEFAULT_SETTINGS })
  const scheduleResult = ref(null)
  const isLoading = ref(false)
  const isSaving = ref(false)
  const error = ref(null)

  // ---- getters ----
  const projectId = computed(() => currentProject.value?.id ?? null)
  const projectName = computed(() => currentProject.value?.name ?? '')
  const hasScheduleResult = computed(() => scheduleResult.value !== null)
  const solutionCount = computed(() => scheduleResult.value?.solutions?.length ?? 0)

  // ---- actions ----

  /**
   * 拉取项目详情，并用后端返回的 settings 覆盖默认值
   * @returns {Promise<boolean>} 是否成功
   */
  async function loadProject(id) {
    isLoading.value = true
    error.value = null
    try {
      const { data } = await axios.get(`${API_BASE}/${id}`)
      currentProject.value = data
      if (data?.settings) {
        Object.assign(settings.value, data.settings)
      }
      return true
    } catch (err) {
      error.value = err.message || 'Unknown error'
      return false
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 保存当前配置。配置挂在项目对象上一并提交，后端按整体更新处理
   * @returns {Promise<boolean>} 是否成功
   */
  async function saveProject(id) {
    if (!currentProject.value) {
      error.value = 'Project not loaded'
      return false
    }
    isSaving.value = true
    error.value = null
    try {
      currentProject.value.settings = settings.value
      await axios.put(`${API_BASE}/${id}`, currentProject.value)
      return true
    } catch (err) {
      error.value = err.message || 'Unknown error'
      return false
    } finally {
      isSaving.value = false
    }
  }

  /** 调度求解完成后写入结果，供看板与结果页复用 */
  function setScheduleResult(result) {
    scheduleResult.value = result
  }

  function clearScheduleResult() {
    scheduleResult.value = null
  }

  function resetSettings() {
    settings.value = { ...DEFAULT_SETTINGS }
  }

  function clearError() {
    error.value = null
  }

  return {
    // state
    currentProject,
    settings,
    scheduleResult,
    isLoading,
    isSaving,
    error,
    // getters
    projectId,
    projectName,
    hasScheduleResult,
    solutionCount,
    // actions
    loadProject,
    saveProject,
    setScheduleResult,
    clearScheduleResult,
    resetSettings,
    clearError,
  }
})
