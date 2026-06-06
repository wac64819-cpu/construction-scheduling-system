<script setup>
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import axios from 'axios';
import Navbar from '@/components/Navbar.vue'; // ✅ 引入 Navbar 组件

const route = useRoute();
const router = useRouter();
const projectId = route.params.projectId;

const trades = ref([]);
const loading = ref(true);

// 加载数据
const loadData = async () => {
  try {
    const res = await axios.get(`http://localhost:8080/api/projects/${projectId}`);
    trades.value = res.data.tradeData || [];
    loading.value = false;
  } catch (err) {
    alert("Failed to load project data");
  }
};

// 保存数据
const saveData = async () => {
  try {
    const res = await axios.get(`http://localhost:8080/api/projects/${projectId}`);
    const project = res.data;
    project.tradeData = trades.value;
    await axios.put(`http://localhost:8080/api/projects/${projectId}`, project);
    alert("Trade data saved!");
  } catch (err) {
    alert("Save failed: " + err.message);
  }
};

// 新增工种
const addTrade = () => {
  trades.value.push({
    id: Date.now().toString(),
    name: 'Trade',
    capacity: 6,
    standardRate: 300.0
  });
};

const deleteTrade = (index) => {
  trades.value.splice(index, 1);
};

const exitProject = () => {
  if (confirm("Exit without saving? Unsaved changes will be lost.")) {
    router.push('/projects');
  }
};

onMounted(loadData);
</script>

<template>
  <div class="page-wrapper">
    <!-- Navbar -->
    <Navbar />

    <div class="page-container">
      <div class="toolbar">
        <div class="left">
          <button class="btn-secondary exit-btn" @click="exitProject">← Exit</button>
        </div>
        <div class="center">
          <h2>Trade & Resources</h2>
        </div>
        <div class="right">
          <button class="btn-secondary" @click="addTrade">+ Add Trade</button>
          <button class="btn-save" @click="saveData">Save</button>
        </div>
      </div>

      <div class="content">
        <table class="simple-table">
          <thead>
            <tr>
              <th style="width: 40%">Trade Name</th>
              <th style="width: 25%">Capacity (Workers)</th>
              <th style="width: 25%">Standard Rate ($/Day)</th>
              <th style="width: 10%">Action</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(t, idx) in trades" :key="idx">
              <td><input v-model="t.name" class="input-cell" placeholder="e.g. Electrician" /></td>
              <td><input type="number" v-model.number="t.capacity" class="input-cell" min="1" /></td>
              <td><input type="number" v-model.number="t.standardRate" class="input-cell" min="0" step="0.5" /></td>
              <td style="text-align: center;">
                <button class="del-btn" @click="deleteTrade(idx)">×</button>
              </td>
            </tr>
          </tbody>
        </table>

        <div v-if="trades.length === 0" class="empty-hint">
          No trades defined. Click "+ Add Trade" to start.
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-wrapper {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f4f5f7;
}

.page-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 20px;
  overflow: hidden; /* 内容区滚动 */
}

.toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 20px;
  align-items: center;
  background: white;
  padding: 15px 20px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
}

.content {
  flex: 1;
  background: white;
  padding: 20px;
  border-radius: 8px;
  overflow-y: auto; /* 表格可滚动 */
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
}

.simple-table {
  width: 100%;
  border-collapse: collapse;
}

.simple-table th {
  background: #f9fafb;
  color: #555;
  font-weight: 600;
  padding: 12px;
  text-align: left;
  border-bottom: 2px solid #eee;
}

.simple-table td {
  border-bottom: 1px solid #eee;
  padding: 8px;
}

.input-cell {
  width: 100%;
  border: 1px solid transparent;
  padding: 8px;
  border-radius: 4px;
  font-size: 14px;
  background: transparent;
}
.input-cell:focus { border-color: #0052cc; background: #fff; outline: none; }
.input-cell:hover { background: #f9f9f9; }

.btn-save { background: #0052cc; color: white; padding: 8px 20px; border-radius: 4px; border:none; cursor: pointer; font-weight: bold; }
.btn-secondary { background: white; border: 1px solid #ccc; padding: 8px 16px; border-radius: 4px; cursor: pointer; margin-right: 10px; color: #333; }
.btn-secondary:hover { background-color: #f0f0f0; }
.del-btn { background: transparent; border: none; color: #ff5630; cursor: pointer; font-size: 20px; font-weight: bold; transition: 0.2s; }
.del-btn:hover { transform: scale(1.2); }
.exit-btn:hover { color: #de350b; border-color: #ffbdad; background-color: #fff0f0; }

.left, .right { display: flex; align-items: center; }
.center { flex: 2; text-align: center; color: #172b4d; }

.empty-hint { text-align: center; color: #999; margin-top: 40px; font-style: italic; }
</style>