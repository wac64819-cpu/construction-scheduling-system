import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import axios from 'axios'

import SettingView from '../SettingView.vue'

// ---- 路由与网络层替换 ----
// SettingView 通过 useRoute 取 projectId、通过 useRouter 跳转，
// 组件测试不应真的启动路由，所以直接把这两个 hook 替换成桩函数。
const pushMock = vi.fn()

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { projectId: 'p-001' } }),
  useRouter: () => ({ push: pushMock }),
}))

// axios 整个模块替换为 mock，避免测试依赖后端服务
vi.mock('axios')

const BACKEND_SETTINGS = {
  indirectCostPerDay: 1500,
  idleCostPerDay: 300,
  maxOutsourcingRatio: 0.4,
  outsourcingCostMultiplier: 1.8,
  maxEvaluations: 30000,
  populationSize: 120,
  mutationProbability: 0.02,
  crossoverProbability: 0.85,
}

const mountView = () => mount(SettingView)

describe('SettingView 项目配置页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('alert', vi.fn())
    vi.stubGlobal('confirm', vi.fn(() => true))
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('首次渲染时显示加载提示，不渲染配置表单', () => {
    axios.get.mockReturnValue(new Promise(() => {})) // 保持 pending，停在加载态

    const wrapper = mountView()

    expect(wrapper.find('.loading').exists()).toBe(true)
    expect(wrapper.find('.loading').text()).toContain('Loading')
    expect(wrapper.find('.settings-grid').exists()).toBe(false)
  })

  it('接口返回后渲染表单，并用后端配置覆盖默认值', async () => {
    axios.get.mockResolvedValue({
      data: { name: '样例项目', settings: BACKEND_SETTINGS },
    })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('.loading').exists()).toBe(false)

    const inputs = wrapper.findAll('.form-group input')
    expect(inputs).toHaveLength(8)
    // 未在 settings 中出现的展示字段
    expect(wrapper.text()).toContain('Project Configuration')
    // 后端返回 1500，默认值是 1000，断言已被覆盖
    expect(inputs[0].element.value).toBe('1500')
    expect(inputs[4].element.value).toBe('30000')
  })

  it('点击保存时，以修改后的配置发起 PUT 请求', async () => {
    axios.get.mockResolvedValue({
      data: { name: '样例项目', settings: BACKEND_SETTINGS },
    })
    axios.put.mockResolvedValue({ data: { ok: true } })

    const wrapper = mountView()
    await flushPromises()

    // 模拟用户把间接成本从 1500 改成 2000
    const firstInput = wrapper.findAll('.form-group input')[0]
    await firstInput.setValue('2000')
    await wrapper.find('.btn-save').trigger('click')
    await flushPromises()

    expect(axios.put).toHaveBeenCalledTimes(1)
    const [url, payload] = axios.put.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/projects/p-001')
    expect(payload.settings.indirectCostPerDay).toBe(2000)
    // 其余配置保持不变
    expect(payload.settings.populationSize).toBe(120)
  })

  it('保存请求未返回时，按钮禁用并显示 Saving...', async () => {
    axios.get.mockResolvedValue({ data: { settings: BACKEND_SETTINGS } })
    axios.put.mockReturnValue(new Promise(() => {})) // 保持 pending

    const wrapper = mountView()
    await flushPromises()

    const btn = wrapper.find('.btn-save')
    expect(btn.text()).toBe('Save Settings')

    await btn.trigger('click')
    await wrapper.vm.$nextTick()

    expect(btn.text()).toBe('Saving...')
    expect(btn.attributes('disabled')).toBeDefined()
  })

  it('加载失败时提示错误，且不渲染表单', async () => {
    axios.get.mockRejectedValue(new Error('Network Error'))

    const wrapper = mountView()
    await flushPromises()

    expect(window.alert).toHaveBeenCalledWith(
      expect.stringContaining('Failed to load settings')
    )
    expect(wrapper.find('.settings-grid').exists()).toBe(false)
    expect(wrapper.find('.loading').exists()).toBe(true)
  })

  it('点击 Exit 并确认后跳转到项目列表', async () => {
    axios.get.mockResolvedValue({ data: { settings: BACKEND_SETTINGS } })

    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('.exit-btn').trigger('click')

    expect(window.confirm).toHaveBeenCalled()
    expect(pushMock).toHaveBeenCalledWith('/projects')
  })
})
