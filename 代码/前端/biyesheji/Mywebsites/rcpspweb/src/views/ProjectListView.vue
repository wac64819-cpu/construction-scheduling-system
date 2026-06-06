<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import axios from 'axios';

const router = useRouter();
const projects = ref([]);
const showModal = ref(false);
const isUploading = ref(false);
const activeProjectId = ref(localStorage.getItem('activeProjectId') || '');

// === 新增：创建模式和层级状态 ===
const creationMode = ref('excel'); // 'excel' 或 'scratch'
const hierarchyLevels = ref(['Phase', 'Zone', 'Building']); // 从零创建时的默认层级建议

const form = ref({ name: '', file: null });

const goHome = () => { router.push('/'); };

const selectProject = (id, event) => {
  if (event) event.stopPropagation();
  activeProjectId.value = id;
  localStorage.setItem('activeProjectId', id);
  window.dispatchEvent(new Event('project-selected'));
};

const goToDashboard = (id, event) => {
  if (event) event.stopPropagation();
  router.push(`/dashboard/${id}`);
};

const goToModel = (id) => {
  router.push(`/model/${id}`);
};

const goToDisplay = (id, event) => {
  if (event) event.stopPropagation();
  router.push(`/display/${id}`); // 跳转到新的展示界面
};

const fetchProjects = async () => {
  try {
    const res = await axios.get('http://localhost:8080/api/projects');
    projects.value = res.data;
  } catch (err) { console.error(err); }
};

const deleteProject = async (id, event) => {
  if (event) event.stopPropagation();
  if (!confirm("Delete?")) return;
  try {
    await axios.delete(`http://localhost:8080/api/projects/${id}`);
    projects.value = projects.value.filter(p => p.id !== id);
    if (activeProjectId.value === id) {
      activeProjectId.value = '';
      localStorage.removeItem('activeProjectId');
      window.dispatchEvent(new Event('project-selected'));
    }
  } catch (err) { alert(err.message); }
};

const handleFileChange = (e) => { form.value.file = e.target.files[0]; };

// === 新增：管理层级输入框的函数 ===
const addHierarchyLevel = () => {
  hierarchyLevels.value.push('');
};
const removeHierarchyLevel = (index) => {
  hierarchyLevels.value.splice(index, 1);
};

// === 修改：处理不同模式的提交 ===
const handleCreate = async () => {
  if (!form.value.name) return alert("Project Name is required.");

  isUploading.value = true;
  try {
    let res;
    if (creationMode.value === 'excel') {
      // 模式 1：Excel 导入 (原有逻辑)
      if (!form.value.file) {
        isUploading.value = false;
        return alert("Please select an Excel file.");
      }
      const formData = new FormData();
      formData.append('name', form.value.name);
      formData.append('file', form.value.file);
      
      res = await axios.post('http://localhost:8080/api/projects', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
    } else {
      // 模式 2：从零创建空项目 (新逻辑)
      const validLevels = hierarchyLevels.value.map(l => l.trim()).filter(l => l !== '');
      if (validLevels.length === 0) {
        isUploading.value = false;
        return alert("Please define at least one Location Hierarchy level.");
      }

      const payload = {
        name: form.value.name,
        hierarchyLevels: validLevels
      };
      
      // 注意：这里调用了一个新的后端接口 /empty
      res = await axios.post('http://localhost:8080/api/projects/empty', payload);
    }

    showModal.value = false;
    selectProject(res.data.id, null);
    fetchProjects();
    // 清理表单
    form.value = { name: '', file: null };
  } catch (err) {
    if (err.response?.status === 409) alert("Name already exists!");
    else alert("Error: " + err.message);
  } finally {
    isUploading.value = false;
  }
};

onMounted(fetchProjects);
</script>

<template>
  <div class="project-list-view">
    <div class="header">
      <div class="header-left">
        <button class="btn-back" @click="goHome">← Home</button>
        <h1>Projects</h1>
      </div>
      <button class="btn-primary" @click="showModal = true">+ New Project</button>
    </div>

    <div class="grid">
      <!-- 列表渲染保持不变 -->
      <div
        v-for="p in projects"
        :key="p.id"
        class="card"
        :class="{ 'is-active': activeProjectId === p.id }"
        @click="goToModel(p.id)"
      >
        <button class="delete-btn" @click="(e) => deleteProject(p.id, e)" title="Delete">×</button>
        <h3>{{ p.name }}</h3>
        <p class="date">Created: {{ p.createdAt || 'Unknown' }}</p>
        <div class="card-footer">
          <span v-if="activeProjectId === p.id" class="status-badge">Selected</span>
          <div class="btn-group">
            <button class="action-btn select-btn" @click="(e) => selectProject(p.id, e)" v-if="activeProjectId !== p.id">Select</button>
            <button class="action-btn run-btn" @click="(e) => goToDashboard(p.id, e)">▶ Run</button>
             <!-- ✅ 新增 Display 按钮 -->
            <button 
              v-if="p.latestSchedule" 
              class="action-btn display-btn" 
              @click="(e) => goToDisplay(p.id, e)"
            >
               Display
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 弹窗 -->
    <div v-if="showModal" class="modal-overlay">
      <div class="modal">
        <h2>Create New Project</h2>
        
        <!-- 模式切换按钮 -->
        <div class="mode-selector">
          <button 
            :class="['mode-btn', { active: creationMode === 'excel' }]" 
            @click="creationMode = 'excel'">
            Import Excel
          </button>
          <button 
            :class="['mode-btn', { active: creationMode === 'scratch' }]" 
            @click="creationMode = 'scratch'">
            Build from Web
          </button>
        </div>

        <div class="form-group">
          <label>Project Name</label>
          <input v-model="form.name" type="text" placeholder="e.g. Office Tower Phase 1" />
        </div>

        <!-- 模式 1 界面：上传文件 -->
        <div v-if="creationMode === 'excel'" class="form-group">
          <label>Upload Excel Model</label>
          <input type="file" @change="handleFileChange" accept=".xlsx, .xls" />
          <p class="hint">Supported format: Standard Excel with Location Hierarchy</p>
        </div>

        <!-- 模式 2 界面：定义层级 -->
        <div v-if="creationMode === 'scratch'" class="form-group">
          <label>Define Location Hierarchy (Top to Bottom)</label>
          <p class="hint">Define your spatial breakdown. e.g., Phase -> Zone -> Building</p>
          
          <div class="hierarchy-list">
            <div v-for="(level, index) in hierarchyLevels" :key="index" class="hierarchy-item">
              <span class="level-num">L{{ index + 1 }}</span>
              <input v-model="hierarchyLevels[index]" type="text" placeholder="e.g. Zone" class="level-input" />
              <button class="remove-level-btn" @click="removeHierarchyLevel(index)" :disabled="hierarchyLevels.length === 1">×</button>
            </div>
            <button class="add-level-btn" @click="addHierarchyLevel">+ Add Level</button>
          </div>
        </div>

        <div class="actions">
          <button @click="showModal = false" class="btn-secondary" :disabled="isUploading">Cancel</button>
          <button class="btn-primary" @click="handleCreate" :disabled="isUploading">
            {{ isUploading ? 'Creating...' : 'Create Project' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 原有基础样式保持不变 */
.project-list-view { padding: 40px; background-color: #f8f9fa; min-height: 100vh; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 30px; }
.header-left { display: flex; align-items: center; gap: 15px; }
.btn-back { background: white; border: 1px solid #ccc; padding: 8px 16px; border-radius: 6px; cursor: pointer; }
.btn-primary { background: #0052cc; color: white; padding: 10px 20px; border-radius: 6px; border: none; cursor: pointer; font-weight: bold;}
.btn-secondary { background: white; border: 1px solid #ccc; padding: 10px 20px; border-radius: 6px; cursor: pointer; color: #333;}
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 20px; }
.display-btn {
  background-color: #e8f5e9; color: #2e7d32; border-color: #2e7d32;
}
.display-btn:hover { background-color: #2e7d32; color: white; }
/* 卡片样式 */
.card { background: white; padding: 20px; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.05); cursor: pointer; transition: all 0.2s; border: 2px solid transparent; position: relative; display: flex; flex-direction: column; justify-content: space-between; min-height: 160px; }
.card:hover { transform: translateY(-3px); box-shadow: 0 5px 15px rgba(0,0,0,0.1); }
.card.is-active { border-color: #0052cc; background: #f0f7ff; }
.delete-btn { position: absolute; top: 10px; right: 10px; background: transparent; border: none; font-size: 20px; color: #999; cursor: pointer; width: 30px; height: 30px; border-radius: 50%; }
.delete-btn:hover { background-color: #ffeaea; color: #ff4d4f; }
.card-footer { margin-top: 15px; display: flex; justify-content: space-between; align-items: center; }
.btn-group { display: flex; gap: 8px; margin-left: auto; }
.action-btn { padding: 6px 12px; border-radius: 4px; font-size: 12px; font-weight: 600; cursor: pointer; border: 1px solid transparent; transition: all 0.2s; }
.select-btn { background-color: white; border-color: #ddd; color: #555; }
.select-btn:hover { border-color: #0052cc; color: #0052cc; }
.run-btn { background-color: #e3f2fd; color: #0052cc; border-color: #0052cc; }
.run-btn:hover { background-color: #0052cc; color: white; }
.status-badge { font-size: 12px; color: #0052cc; font-weight: bold; background: #dbeafe; padding: 4px 8px; border-radius: 4px; }

/* 弹窗及新增模式切换样式 */
.modal-overlay { position: fixed; top:0; left:0; right:0; bottom:0; background: rgba(0,0,0,0.5); display: flex; justify-content: center; align-items: center; z-index: 100; }
.modal { background: white; padding: 30px; border-radius: 12px; width: 450px; }

.mode-selector { display: flex; background: #f4f5f7; border-radius: 6px; padding: 4px; margin-bottom: 20px;}
.mode-btn { flex: 1; padding: 8px; border: none; background: transparent; cursor: pointer; font-weight: 600; color: #6b778c; border-radius: 4px; transition: 0.2s;}
.mode-btn.active { background: white; color: #0052cc; box-shadow: 0 1px 3px rgba(0,0,0,0.1);}

.form-group { margin-bottom: 20px; }
.form-group label { display: block; margin-bottom: 8px; font-weight: 600; color: #172b4d;}
.form-group input[type="text"], .form-group input[type="file"] { width: 100%; padding: 10px; border: 1px solid #dfe1e6; border-radius: 6px; box-sizing: border-box;}
.hint { font-size: 12px; color: #6b778c; margin-top: 4px; }

/* 层级列表 UI */
.hierarchy-list { display: flex; flex-direction: column; gap: 8px; margin-top: 10px; background: #f9fafc; padding: 15px; border-radius: 6px; border: 1px dashed #dfe1e6;}
.hierarchy-item { display: flex; align-items: center; gap: 10px; }
.level-num { font-size: 12px; font-weight: bold; color: #888; background: #e0e0e0; padding: 4px 8px; border-radius: 4px; }
.level-input { flex: 1; padding: 8px; border: 1px solid #ccc; border-radius: 4px; }
.remove-level-btn { background: transparent; border: none; color: #ff5630; font-size: 18px; cursor: pointer; font-weight: bold; padding: 0 5px;}
.remove-level-btn:disabled { color: #ccc; cursor: not-allowed; }
.add-level-btn { margin-top: 10px; align-self: flex-start; background: transparent; border: 1px dashed #0052cc; color: #0052cc; padding: 6px 12px; border-radius: 4px; cursor: pointer; font-weight: bold;}
.add-level-btn:hover { background: #e9f2ff; }

.actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 30px; }
</style>