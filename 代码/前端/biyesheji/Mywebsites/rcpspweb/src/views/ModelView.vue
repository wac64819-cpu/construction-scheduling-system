<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import axios from 'axios';
import { useRouter } from 'vue-router';
// ===== 引入 Navbar 组件 =====
import Navbar from '../components/Navbar.vue';

const router = useRouter();
const route = useRoute();
const projectId = route.params.projectId;

// ================= 核心状态 =================
const isLoading = ref(true);
const project = ref({
  model: {
    hierarchyLevels: [], // ["CP", "SZ", "Zone"...]
    locationTree:[], // 树根节点
    activities:[] // 任务列表
  },
  constraints:[],
  tradeData:[]
});
const isEditingTitle = ref(false);
const tempTitle = ref('');

// 开始编辑标题
const startEditTitle = () => {
  tempTitle.value = project.value.name;
  isEditingTitle.value = true;
};

// 确认修改标题
const confirmEditTitle = () => {
  if (!tempTitle.value.trim()) return alert("Project name cannot be empty");
  project.value.name = tempTitle.value;
  isEditingTitle.value = false;
};

// ================= 数据加载与保存 =================
const loadProject = async () => {
  try {
    const res = await axios.get(`http://localhost:8080/api/projects/${projectId}`);
    project.value = res.data;
    isLoading.value = false;
  } catch (err) {
    alert("Failed to load project");
  }
};



const availableTrades = computed(() => {
  const trades = new Set();
  
  // 1. 读取全局定义的 TradeData (TradeView 中新增的)
  if (project.value.tradeData && project.value.tradeData.length > 0) {
    project.value.tradeData.forEach(t => {
      if (t.name) trades.add(t.name);
    });
  }
  
  // 2. 读取 Excel 导入时自带的 Trade (确保旧数据不丢失)
  if (project.value.model.activities && project.value.model.activities.length > 0) {
    project.value.model.activities.forEach(act => {
      if (act.trade) trades.add(act.trade);
    });
  }
  
  return Array.from(trades).sort();
});

const saveProject = async () => {
  try {
    await axios.put(`http://localhost:8080/api/projects/${projectId}`, project.value);
    alert("Project saved successfully!");
  } catch (err) {
    if (err.response && err.response.status === 409) {
      alert("Save Failed: Project Name already exists! Please choose another name.");
      startEditTitle();
    } else {
      alert("Save failed: " + err.message);
    }
  }
};

// 放弃修改
const discardChanges = async () => {
  if (confirm("Are you sure you want to discard all unsaved changes? The project will revert to the last saved state.")) {
    isLoading.value = true;
    await loadProject();
  }
};

onMounted(loadProject);

// ================= 计算属性：表格渲染逻辑 =================
const leafNodes = computed(() => {
  const leaves =[];
  const traverse = (nodes) => {
    if (!nodes) return;
    nodes.forEach(node => {
      if (!node.children || node.children.length === 0) {
        leaves.push(node);
      } else {
        traverse(node.children);
      }
    });
  };
  traverse(project.value.model.locationTree);
  return leaves;
});

// ================= 计算属性：表格渲染逻辑 =================
const headerRows = computed(() => {
  // 1. 获取目标层级深度 (例如 Phase -> Zone -> Building 就是 3)
  const levels = project.value.model.hierarchyLevels ||[];
  const targetDepth = levels.length > 0 ? levels.length : 1;
  
  // 2. 始终生成 targetDepth 数量的行，千万不要过滤掉空行！
  const rows = Array.from({ length: targetDepth }, () =>[]);
  
  if (!project.value.model.locationTree || project.value.model.locationTree.length === 0) {
    return rows; // 如果树是空的，直接返回空行框架，以支撑右侧的 Hierarchy 列
  }

  const processNode = (nodes, level) => {
    if (!nodes) return;
    nodes.forEach(node => {
      // 计算 colspan: 底层叶子节点数量
      const getLeafCount = (n) => {
        if (!n.children || n.children.length === 0) return 1;
        return n.children.reduce((sum, child) => sum + getLeafCount(child), 0);
      };
      const colspan = getLeafCount(node);
      
      // ✅ 核心修复：计算 rowspan
      // 如果当前节点还没有子节点，它就必须向下跨越所有剩余的空层级，把表格撑开
      const hasChildren = node.children && node.children.length > 0;
      const rowspan = hasChildren ? 1 : (targetDepth - level);
      
      rows[level].push({
        name: node.name,
        colspan: colspan,
        rowspan: rowspan, // 加入算好的 rowspan
        nodeRef: node,
        isLeaf: !hasChildren
      });
      
      if (hasChildren) {
        processNode(node.children, level + 1);
      }
    });
  };
  
  processNode(project.value.model.locationTree, 0);
  
  // ✅ 核心修复：直接返回 rows，不要使用 .filter 去除空行
  return rows; 
});

// ================= 交互逻辑：Location 操作 =================
const showLocModal = ref(false);
const locForm = ref({ parentId: '', name: '', type: 'Zone' });

const allNodesFlat = computed(() => {
  const list =[];
  list.push({ id: 'ROOT', name: 'ROOT (Top Level)', pathStr: 'ROOT', depth: -1 });
  const traverse = (nodes, pathPrefix, currentDepth) => {
    if (!nodes) return;
    nodes.forEach(node => {
      const currentPath = pathPrefix ? `${pathPrefix} > ${node.name}` : node.name;
      if (currentDepth < project.value.model.hierarchyLevels.length - 1) {
          list.push({
            id: node.id,
            name: node.name,
            pathStr: currentPath,
            rawNode: node,
            depth: currentDepth
          });
      }
      traverse(node.children, currentPath, currentDepth + 1);
    });
  };
  traverse(project.value.model.locationTree, '', 0);
  return list;
});

const nextLevelType = computed(() => {
  if (!locForm.value.parentId) return 'Unknown';
  const parent = allNodesFlat.value.find(n => n.id === locForm.value.parentId);
  if (!parent) return 'Unknown';
  const nextDepth = parent.depth + 1;
  const levels = project.value.model.hierarchyLevels;
  if (nextDepth < levels.length) return levels[nextDepth];
  return 'Leaf Node';
});

// 退出功能
const exitProject = () => {
  if (confirm("Exit without saving? Unsaved changes will be lost.")) {
    router.push('/projects');
  }
};

// 新建 Location
const handleAddLocation = () => {
  const { parentId, name } = locForm.value;
  if (!name) return alert("Name is required");
  const typeName = nextLevelType.value;
  const newId = `loc_${Date.now()}_${Math.floor(Math.random() * 1000)}`;
  const newNode = { id: newId, name: name, type: typeName, children:[] };
  if (parentId === 'ROOT') {
    project.value.model.locationTree.push(newNode);
  } else {
    const findAndPush = (nodes) => {
      for (let node of nodes) {
        if (node.id === parentId) {
          if (!node.children) node.children =[];
          node.children.push(newNode);
          return true;
        }
        if (node.children && findAndPush(node.children)) return true;
      }
      return false;
    };
    findAndPush(project.value.model.locationTree);
  }
  project.value.model.activities.forEach(act => {
    if (!act.quantities) act.quantities = {};
    act.quantities[newId] = 0;
  });
  showLocModal.value = false;
  locForm.value = { parentId: '', name: '', type: 'Zone' };
};

// 删除 Location
const deleteLocation = (targetNode) => {
  if (!confirm(`Delete location "${targetNode.name}" and all its sub-locations?`)) return;
  const recursiveDelete = (nodes) => {
    const idx = nodes.findIndex(n => n.id === targetNode.id);
    if (idx !== -1) {
      nodes.splice(idx, 1);
      return true;
    }
    for (let node of nodes) {
      if (node.children && recursiveDelete(node.children)) return true;
    }
    return false;
  };
  if (!recursiveDelete(project.value.model.locationTree)) {
    console.error("Node not found for deletion");
  }
};

// ================= 交互逻辑：Activity 操作 =================
const addActivity = () => {
  project.value.model.activities.push({
    id: `act_${Date.now()}`, name: 'New Activity', trade: '', crew: 1, dailyProd: 0, quantities: {}
  });
};

const deleteActivity = (index) => {
  if (confirm("Delete this activity row?")) {
    project.value.model.activities.splice(index, 1);
  }
};
</script>

<template>
  <!-- 整个页面的最外层容器 -->
  <div class="page-layout">
    
    <!-- 1. 顶部插入全局导航栏 -->
    <Navbar />

    <!-- 2. 下方的工作区 (原先的 model-view 容器) -->
    <div class="model-view" v-if="!isLoading">
      
      <!-- 顶部控制栏 -->
      <div class="toolbar">
        <div class="left">
          <button class="btn-secondary exit-btn" @click="exitProject">← Exit</button>
          <div class="divider"></div>
          <button class="btn-secondary" @click="addActivity">+ Activity</button>
          <button class="btn-secondary" @click="showLocModal = true">+ Location</button>
        </div>
        
        <div class="center">
          <div v-if="isEditingTitle" class="title-edit-box">
            <input v-model="tempTitle" class="title-input" @keyup.enter="confirmEditTitle" placeholder="Enter project name" />
            <button class="btn-tiny ok" @click="confirmEditTitle">✓</button>
            <button class="btn-tiny cancel" @click="isEditingTitle = false">✕</button>
          </div>
          <div v-else class="title-display" @click="startEditTitle" title="Click to rename">
            <span class="proj-name">{{ project.name }}</span>
            <span class="edit-icon">✎</span>
          </div>
        </div>
        
        <div class="right">
          <button class="btn-secondary" @click="discardChanges">Discard</button>
          <button class="btn-save" @click="saveProject">Save</button>
        </div>
      </div>

      <!-- 核心表格区域 -->
      <div class="table-wrapper">
        <table>
          <thead>
            <tr v-for="(row, rowIndex) in headerRows" :key="'hrow-'+rowIndex">
              <template v-if="rowIndex === 0">
                <th :rowspan="headerRows.length" class="fixed-col" style="width: 50px;">#</th>
                <th :rowspan="headerRows.length" class="fixed-col" style="width: 200px;">Activity</th>
                <th :rowspan="headerRows.length" class="fixed-col" style="width: 100px;">Trade</th>
                <th :rowspan="headerRows.length" class="fixed-col" style="width: 60px;">Crew</th>
                <th :rowspan="headerRows.length" class="fixed-col" style="width: 80px;">Prod/Day</th>
              </template>
              
              <!-- 中间 Location 动态节点 -->
              <th v-for="cell in row" :key="cell.nodeRef.id" :colspan="cell.colspan" :rowspan="cell.rowspan" class="loc-th">
                <div class="th-content">
                  <input v-model="cell.nodeRef.name" class="th-input" />
                  <span class="th-del" @click="deleteLocation(cell.nodeRef)" title="Delete Location">×</span>
                </div>
              </th>

              <!-- ✅ 新增：最右侧 Location Hierarchy 列 -->
              <th class="fixed-col-right loc-hierarchy-th">
                <!-- 只在第一行显示“Location Hierarchy”标题字样 -->
                <div v-if="rowIndex === 0" class="hierarchy-title">Location Hierarchy</div>
                <!-- 每一行显示对应的层级名称 -->
                <div class="hierarchy-name">
                  {{ project.model.hierarchyLevels[rowIndex] || `Level ${rowIndex + 1}` }}
                </div>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(act, idx) in project.model.activities" :key="act.id">
              <td class="text-center">{{ idx + 1 }} <span class="row-del" @click="deleteActivity(idx)">×</span></td>
              <td><input v-model="act.name" class="cell-input" /></td>
              <td>
                <!-- ✅ 核心修复：下拉框使用 availableTrades 数据源 -->
                <select v-model="act.trade" class="cell-input">
                  <option value="">Select...</option>
                  <option v-for="trade in availableTrades" :key="trade" :value="trade">{{ trade }}</option>
                </select>
              </td>
              <td><input type="number" v-model.number="act.crew" class="cell-input text-center" /></td>
              <td><input type="number" v-model.number="act.dailyProd" class="cell-input text-center" /></td>
              
              <!-- 遍历所有叶子节点数量 -->
              <td v-for="leaf in leafNodes" :key="leaf.id" class="qty-cell">
                <input type="number" v-model.number="act.quantities[leaf.id]" class="cell-input text-center" placeholder="-" />
              </td>

              <!-- ✅ 新增：最右侧的空白/占位单元格 (为了配合右侧固定表头) -->
              <td class="fixed-col-right loc-hierarchy-td"></td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 弹窗部分 -->
      <div v-if="showLocModal" class="modal-overlay">
        <div class="modal">
          <h3>Add New Location</h3>
          <div class="form-item">
            <label>Parent Location</label>
            <select v-model="locForm.parentId">
              <option disabled value="">Select Parent...</option>
              <option v-for="opt in allNodesFlat" :key="opt.id" :value="opt.id">{{ opt.pathStr }}</option>
            </select>
          </div>
          <div class="form-item">
            <label>Name</label>
            <input v-model="locForm.name" placeholder="Node Name" />
          </div>
          <div class="form-item" v-if="locForm.parentId">
            <label>Type (Auto-detected)</label>
            <input :value="nextLevelType" disabled class="disabled-input"/>
          </div>
          <div class="actions">
            <button @click="showLocModal = false" class="btn-secondary">Cancel</button>
            <button @click="handleAddLocation" class="btn-save">Add</button>
          </div>
        </div>
      </div>
      
    </div>
    
    <!-- 加载中提示 -->
    <div v-else class="loading">Loading Project Model...</div>
    
  </div>
</template>

<style scoped>
/* ===== 新增外层布局样式 ===== */
.page-layout {
  display: flex;
  flex-direction: column;
  height: 100vh; /* 占满整个屏幕高度 */
  overflow: hidden; /* 禁用外层滚动，滚动条交给内部表格 */
}

/* Navbar 是引入的组件，它的样式由自己控制，但我们需要确保下方视图减去它的高度 */
.model-view { 
  display: flex; 
  flex-direction: column; 
  /* 关键修改：减去 Navbar 的高度 (假设 Navbar height 是 80px) */
  height: calc(100vh - 80px); 
  background: #f4f5f7; 
}

/* ... 以下为你原有的所有样式，完全保持不变 ... */
.toolbar { display: flex; justify-content: space-between; align-items: center; padding: 10px 20px; background: white; border-bottom: 1px solid #ddd; }
.left, .right { display: flex; align-items: center; gap: 10px; flex: 1; }
.right { justify-content: flex-end; } 
.center { flex: 2; display: flex; justify-content: center; }
.proj-name { font-size: 18px; font-weight: bold; }
.divider { width: 1px; height: 24px; background-color: #ddd; margin: 0 10px; }

.btn-secondary { padding: 6px 12px; background: white; border: 1px solid #dfe1e6; border-radius: 4px; color: #42526e; cursor: pointer; font-weight: 500; transition: 0.2s; }
.btn-secondary:hover { background: #ebecf0; color: #0052cc; }
.btn-secondary:first-child:hover { color: #de350b; }
.btn-save { padding: 8px 16px; background: #0052cc; color: white; border: none; border-radius: 4px; cursor: pointer; font-weight: bold; }
.btn-save:hover { background: #0065ff; }

.table-wrapper { flex: 1; overflow: auto; padding: 20px; position: relative; max-width: 100vw; }
table { border-collapse: separate; border-spacing: 0; background: white; border: 1px solid #dfe1e6; width: max-content; min-width: 100%; }
th { background: #f4f5f7; border-right: 1px solid #dfe1e6; border-bottom: 1px solid #dfe1e6; position: sticky; top: 0; z-index: 10; font-size: 12px; color: #5e6c84; }
.fixed-col { background: #ebecf0; z-index: 20; left: 0; }
.loc-th { min-width: 80px; padding: 4px; text-align: center; }

/* ===== 新增：右侧固定列表头与单元格样式 ===== */
.fixed-col-right {
  position: sticky;
  right: 0;
  z-index: 20;
  background-color: #f4f5f7;
  border-left: 2px solid #ccc; /* 明显的左边框，区分滚动内容 */
  border-right: none;
}
/* 解决表头 Z-index 穿透问题 */
thead th.fixed-col-right {
  z-index: 30;
  background-color: #ebecf0;
}
.loc-hierarchy-th {
  min-width: 140px;
  vertical-align: middle;
  padding: 8px;
}
.loc-hierarchy-td {
  background-color: #f9f9f9;
}
.hierarchy-title {
  font-size: 10px;
  color: #6b778c;
  text-transform: uppercase;
  margin-bottom: 4px;
}
.hierarchy-name {
  font-size: 13px;
  font-weight: 700;
  color: #172b4d;
}
/* =========================================== */

.cell-input { width: 100%; height: 32px; border: none; padding: 0 4px; font-family: inherit; }
.cell-input:focus { background: #e9f2ff; outline: 2px solid #4c9aff; z-index: 2; position: relative; }
.text-center { text-align: center; }
td { border-right: 1px solid #dfe1e6; border-bottom: 1px solid #dfe1e6; padding: 0; }
.qty-cell { min-width: 60px; }

.th-content { display: flex; align-items: center; justify-content: center; }
.th-input { border: none; background: transparent; text-align: center; font-weight: bold; width: 100%; color: #172b4d; }
.th-input:focus { background: white; }
.th-del { cursor: pointer; color: #ff5630; opacity: 0; margin-left: 4px; font-size: 16px; }
.loc-th:hover .th-del { opacity: 1; }
.row-del { cursor: pointer; color: #ccc; margin-left: 5px; font-size: 14px; }
.row-del:hover { color: red; }

.modal-overlay { position: fixed; top:0; left:0; right:0; bottom:0; background: rgba(0,0,0,0.5); z-index: 100; display: flex; justify-content: center; align-items: center; }
.modal { background: white; width: 400px; padding: 24px; border-radius: 8px; box-shadow: 0 0 20px rgba(0,0,0,0.2); }
.form-item { margin-bottom: 16px; }
.form-item label { display: block; font-weight: 600; margin-bottom: 6px; font-size: 14px; }
.form-item select, .form-item input { width: 100%; padding: 8px; border: 1px solid #dfe1e6; border-radius: 4px; }
.hint { font-size: 12px; color: #6b778c; margin-top: 4px; }
.actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 24px; }
.btn-primary { background: #0052cc; color: white; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer; }
.loading { display: flex; justify-content: center; align-items: center; height: 100%; font-size: 18px; color: #666; }
.disabled-input { background-color: #f4f5f7; color: #6b778c; cursor: not-allowed; }

/* 标题样式 */
.title-display { display: flex; align-items: center; cursor: pointer; padding: 4px 12px; border-radius: 6px; transition: background 0.2s; }
.title-display:hover { background-color: #e9f2ff; }
.edit-icon { margin-left: 8px; font-size: 14px; color: #6b778c; opacity: 0; }
.title-display:hover .edit-icon { opacity: 1; }
.title-edit-box { display: flex; align-items: center; gap: 4px; }
.title-input { font-size: 16px; padding: 4px 8px; border: 1px solid #0052cc; border-radius: 4px; outline: none; width: 200px; }
.btn-tiny { width: 24px; height: 24px; border-radius: 4px; border: none; cursor: pointer; display: flex; align-items: center; justify-content: center; font-size: 12px; }
.btn-tiny.ok { background: #36b37e; color: white; }
.btn-tiny.cancel { background: #ff5630; color: white; }
</style>
