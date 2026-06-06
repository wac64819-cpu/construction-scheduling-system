<script setup>
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import axios from 'axios';

const route = useRoute();
const router = useRouter();
const projectId = route.params.projectId;

const isLoading = ref(true);
const isSaving = ref(false);

// 初始化默认值，防止后端数据缺失
const settings = ref({
  indirectCostPerDay: 1000,
  idleCostPerDay: 200,
  maxOutsourcingRatio: 0.5,
  outsourcingCostMultiplier: 1.5,
  maxEvaluations: 25000,
  populationSize: 100,
  mutationProbability: 0.01,
  crossoverProbability: 0.9
});

// 完整项目对象
const project = ref(null);

const loadSettings = async () => {
  try {
    const res = await axios.get(`http://localhost:8080/api/projects/${projectId}`);
    project.value = res.data;
    
    // 如果后端有 settings 就覆盖默认值
    if (res.data.settings) {
      // 使用 Object.assign 保证响应式
      Object.assign(settings.value, res.data.settings);
    }
    isLoading.value = false;
  } catch (err) {
    alert("Failed to load settings: " + err.message);
  }
};

const saveSettings = async () => {
  isSaving.value = true;
  try {
    // 更新 project 对象中的 settings
    project.value.settings = settings.value;
    
    await axios.put(`http://localhost:8080/api/projects/${projectId}`, project.value);
    alert("Settings saved successfully!");
  } catch (err) {
    alert("Save failed: " + err.message);
  } finally {
    isSaving.value = false;
  }
};

// Exit 逻辑
const exitProject = () => {
  if (confirm("Exit without saving?")) {
    router.push('/projects');
  }
};

onMounted(loadSettings);
</script>

<template>
  <div class="page-container">
    <div class="toolbar">
      <div class="left">
        <button class="btn-secondary exit-btn" @click="exitProject">← Exit</button>
      </div>
      <div class="center">
        <h2>Project Configuration</h2>
      </div>
      <div class="right">
        <button class="btn-save" @click="saveSettings" :disabled="isSaving">
          {{ isSaving ? 'Saving...' : 'Save Settings' }}
        </button>
      </div>
    </div>

    <div class="content-scroll" v-if="!isLoading">
      <div class="settings-grid">
        
        <!-- 卡片 1: 成本与资源设置 -->
        <div class="card">
          <div class="card-header">
            <h3> Cost & Resource Parameters</h3>
            <p class="subtitle">Define global cost factors and resource constraints.</p>
          </div>
          <div class="card-body">
            
            <div class="form-group">
              <label>Daily Indirect Cost ($/Day)</label>
              <div class="input-desc">Fixed overhead cost for every day of the project duration.</div>
              <input type="number" v-model.number="settings.indirectCostPerDay" />
            </div>

            <div class="form-group">
              <label>Idle Cost Penalty ($/Day)</label>
              <div class="input-desc">Cost incurred when a trade crew is idle between locations (Continuity penalty).</div>
              <input type="number" v-model.number="settings.idleCostPerDay" />
            </div>

            <div class="divider"></div>

            <div class="form-group">
              <label>Max Outsourcing Ratio (0.0 - 1.0)</label>
              <div class="input-desc">
                If capacity is 10 and ratio is 0.5, max 5 extra workers can be hired (Total 15).
              </div>
              <input type="number" step="0.1" min="0" max="5" v-model.number="settings.maxOutsourcingRatio" />
            </div>

            <div class="form-group">
              <label>Excess Cost Multiplier (x)</label>
              <div class="input-desc">
                Multiplier for outsourcing/overtime rate. 1.5 means 1.5x Standard Rate.
              </div>
              <input type="number" step="0.1" min="1" v-model.number="settings.outsourcingCostMultiplier" />
            </div>

          </div>
        </div>

        <!-- 卡片 2: 遗传算法设置 -->
        <div class="card">
          <div class="card-header">
            <h3> Genetic Algorithm (NSGA-II)</h3>
            <p class="subtitle">Fine-tune the optimization engine performance.</p>
          </div>
          <div class="card-body">
            
            <div class="form-group">
              <label>Max Iterations</label>
              <div class="input-desc">Total number of solutions to evaluate. Higher = Better results but slower.</div>
              <input type="number" step="1000" v-model.number="settings.maxEvaluations" />
            </div>

            <div class="form-group">
              <label>Population Size</label>
              <div class="input-desc">Number of individuals in one generation. (Recommended: 50-200)</div>
              <input type="number" step="10" v-model.number="settings.populationSize" />
            </div>

            <div class="form-group">
              <label>Crossover Probability (0.0 - 1.0)</label>
              <div class="input-desc">Likelihood of creating offspring from two parents.</div>
              <input type="number" step="0.05" max="1" v-model.number="settings.crossoverProbability" />
            </div>

            <div class="form-group">
              <label>Mutation Probability (0.0 - 1.0)</label>
              <div class="input-desc">Likelihood of random changes. Keep low (e.g., 0.01 - 0.05).</div>
              <input type="number" step="0.01" max="1" v-model.number="settings.mutationProbability" />
            </div>

          </div>
        </div>

      </div>
    </div>
    
    <div v-else class="loading">Loading Configuration...</div>
  </div>
</template>

<style scoped>
/* 布局容器 */
.page-container { padding: 20px; height: 100vh; display: flex; flex-direction: column; background: #f4f5f7; }
.toolbar { display: flex; justify-content: space-between; margin-bottom: 20px; align-items: center; background: white; padding: 10px 20px; border-bottom: 1px solid #ddd; }
.content-scroll { flex: 1; overflow-y: auto; padding: 10px; }

/* Grid 布局 */
.settings-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

/* 卡片样式 */
.card {
  background: white;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  overflow: hidden;
  border: 1px solid #eee;
}

.card-header {
  background: #f9fafc;
  padding: 15px 20px;
  border-bottom: 1px solid #eee;
}

.card-header h3 { margin: 0; font-size: 16px; color: #172b4d; }
.subtitle { margin: 5px 0 0; font-size: 12px; color: #6b778c; }

.card-body { padding: 20px; }

/* 表单元素 */
.form-group { margin-bottom: 20px; }
.form-group label { display: block; font-weight: 600; font-size: 14px; color: #333; margin-bottom: 4px; }
.input-desc { font-size: 12px; color: #666; margin-bottom: 8px; line-height: 1.4; }
.form-group input { 
  width: 100%; padding: 8px 12px; border: 1px solid #dfe1e6; border-radius: 4px; font-size: 14px; box-sizing: border-box;
}
.form-group input:focus { border-color: #0052cc; outline: none; }

.divider { height: 1px; background: #eee; margin: 20px 0; }

/* 按钮 */
.btn-save { background: #0052cc; color: white; padding: 8px 20px; border-radius: 4px; border: none; cursor: pointer; font-weight: bold; transition: 0.2s; }
.btn-save:hover { background: #0065ff; }
.btn-save:disabled { background: #ccc; cursor: not-allowed; }

.btn-secondary { background: white; border: 1px solid #ccc; padding: 8px 16px; border-radius: 4px; cursor: pointer; color: #42526e; }
.exit-btn:hover { color: #de350b; border-color: #ffbdad; background-color: #fff0f0; }

.loading { text-align: center; margin-top: 50px; color: #666; }
</style>