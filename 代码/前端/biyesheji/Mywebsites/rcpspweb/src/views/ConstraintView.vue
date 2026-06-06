<script setup>
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import axios from 'axios';

// ===== 引入全局 Navbar 组件 =====
import Navbar from '../components/Navbar.vue';

const route = useRoute();
const router = useRouter();
const projectId = route.params.projectId;

const activeTab = ref('precedence'); // 'precedence' | 'activity' | 'location'
const project = ref(null);
const hierarchyLevels = ref([]); // e.g. ["CP", "SZ", "Zone"]
const activityNames = ref([]); // e.g. ["Earthwork", "Foundation"]

// 核心数据结构 (对应后端的 ProjectConstraints)
const constraints = ref({
  precedenceConstraints: [], // Activity Precedence
  activityConstraints:[],   // Activity Constraints
  locationConstraints:[]    // Location Precedence
});

// 加载数据
const loadData = async () => {
  try {
    const res = await axios.get(`http://localhost:8080/api/projects/${projectId}`);
    project.value = res.data;
    
    // 初始化数据，防止 null
    if (res.data.constraints) {
      constraints.value.precedenceConstraints = res.data.constraints.precedenceConstraints ||[];
      constraints.value.activityConstraints = res.data.constraints.activityConstraints ||[];
      constraints.value.locationConstraints = res.data.constraints.locationConstraints ||[];
    }
    
    // 初始化下拉选项
    hierarchyLevels.value = res.data.model.hierarchyLevels ||[];
    if(res.data.model.activities) {
        activityNames.value = res.data.model.activities.map(a => a.name);
    }
  } catch (err) {
    alert("Error loading project data: " + err.message);
  }
};

// 保存数据
const saveData = async () => {
  try {
    project.value.constraints = constraints.value;
    await axios.put(`http://localhost:8080/api/projects/${projectId}`, project.value);
    alert("Constraints saved successfully!");
  } catch (err) {
    alert("Save failed: " + err.message);
  }
};

// --- 1. Activity Precedence (原 Logic Constraints) ---
const addPrecedence = () => {
  constraints.value.precedenceConstraints.push({
    predecessor: '',
    successor: '',
    scopeMode: 'GLOBAL',
    scopeLevels:[], // Set<Integer>
    dayGap: 0,
    predType: 'FS',
    relation: 'GE'
  });
};

// --- 2. Activity Constraints (原 Resource Constraints) ---
const addActConstraint = () => {
  constraints.value.activityConstraints.push({
    activityName: '',
    type: 'EXCLUSIVE',
    scopeMode: 'PARTIAL',
    scopeLevels:[] // Set<Integer>
  });
};

// --- 3. Location Precedence (原 Location Priority) ---
const addLocConstraint = () => {
  // 根据层级深度，初始化 Pattern 数组，默认为 "*" (通配符)
  const defaultPattern = new Array(hierarchyLevels.value.length).fill("*");
  constraints.value.locationConstraints.push({
    applyActivities:[], // Set<String>
    // 两个 Pattern，对应 Source 和 Target，必须是独立数组
    loc1Patterns: [...defaultPattern],
    loc2Patterns: [...defaultPattern],
    dayGap: 0,
    predType: 'FS',
    relation: 'GE'
  });
};

// Exit 逻辑 (局部退出按钮，可保留以增强直观性，也可以依赖 Navbar 的跳转)
const exitProject = () => {
  if (confirm("Exit without saving? Unsaved changes will be lost.")) {
    router.push('/projects');
  }
};

onMounted(loadData);
</script>

<template>
  <!-- 最外层使用 Flex 布局，占满屏幕高度 -->
  <div class="page-layout">
    
    <!-- 顶部插入全局导航栏 -->
    <Navbar />

    <!-- 原本的页面内容容器，减去 Navbar 高度 -->
    <div class="page-container">
      <div class="toolbar">
        <div class="left">
          <button class="btn-secondary exit-btn" @click="exitProject">← Exit</button>
        </div>
        <div class="center">
          <div class="tabs">
            <!-- 修改了按钮的文本名称 -->
            <button :class="{active: activeTab==='precedence'}" @click="activeTab='precedence'">Activity Precedence</button>
            <button :class="{active: activeTab==='activity'}" @click="activeTab='activity'">Activity Constraints</button>
            <button :class="{active: activeTab==='location'}" @click="activeTab='location'">Location Precedence</button>
          </div>
        </div>
        <div class="right">
          <button class="btn-save" @click="saveData">Save</button>
        </div>
      </div>

      <div class="content" v-if="project">
        
        <!-- TAB 1: Activity Precedence -->
        <div v-if="activeTab==='precedence'" class="tab-pane">
          <!-- 修改了添加按钮的文本 -->
          <button class="btn-add" @click="addPrecedence">+ Add Activity Precedence</button>
          <div class="table-scroll">
              <table class="c-table">
              <thead>
                  <tr>
                  <th style="width: 15%">Predecessor</th>
                  <th style="width: 15%">Successor</th>
                  <th style="width: 8%">Type</th>
                  <th style="width: 8%">Gap</th>
                  <th style="width: 10%">Scope Mode</th>
                  <th>Scope Dimensions (Check to apply)</th>
                  <th style="width: 5%">Del</th>
                  </tr>
              </thead>
              <tbody>
                  <tr v-for="(c, i) in constraints.precedenceConstraints" :key="i">
                  <td>
                      <select v-model="c.predecessor" class="full-width">
                      <option disabled value="">Select...</option>
                      <option v-for="n in activityNames" :key="n" :value="n">{{n}}</option>
                      </select>
                  </td>
                  <td>
                      <select v-model="c.successor" class="full-width">
                      <option disabled value="">Select...</option>
                      <option v-for="n in activityNames" :key="n" :value="n">{{n}}</option>
                      </select>
                  </td>
                  <td>
                      <select v-model="c.predType">
                      <option value="FS">FS</option><option value="SS">SS</option>
                      <option value="FF">FF</option><option value="SF">SF</option>
                      </select>
                  </td>
                  <td><input type="number" v-model.number="c.dayGap" class="tiny-input"/></td>
                  <td>
                      <select v-model="c.scopeMode">
                      <option value="GLOBAL">Global</option>
                      <option value="PARTIAL">Partial</option>
                      <option value="EACH">Each</option>
                      </select>
                  </td>
                  <td>
                      <div v-if="c.scopeMode === 'PARTIAL'" class="scope-selector">
                      <label v-for="(lvl, idx) in hierarchyLevels" :key="idx" class="chk-label">
                          <input type="checkbox" :value="idx" v-model="c.scopeLevels"/> {{lvl}}
                      </label>
                      </div>
                      <span v-else class="text-gray">{{ c.scopeMode }} (Auto)</span>
                  </td>
                  <td><button class="del-btn" @click="constraints.precedenceConstraints.splice(i,1)">×</button></td>
                  </tr>
              </tbody>
              </table>
          </div>
        </div>

        <!-- TAB 2: Activity Constraints -->
        <div v-if="activeTab==='activity'" class="tab-pane">
           <!-- 修改了添加按钮的文本 -->
           <button class="btn-add" @click="addActConstraint">+ Add Activity Constraint</button>
           <div class="table-scroll">
              <table class="c-table">
                  <thead>
                      <tr>
                          <th style="width: 20%">Activity</th>
                          <th style="width: 15%">Type</th>
                          <th style="width: 15%">Scope Mode</th>
                          <th>Scope Dimensions</th>
                          <th style="width: 5%">Del</th>
                      </tr>
                  </thead>
                  <tbody>
                      <tr v-for="(ac, i) in constraints.activityConstraints" :key="i">
                          <td>
                              <select v-model="ac.activityName" class="full-width">
                                  <option v-for="n in activityNames" :key="n" :value="n">{{n}}</option>
                              </select>
                          </td>
                          <td>
                              <select v-model="ac.type">
                                  <option value="EXCLUSIVE">EXCLUSIVE (No Parallel)</option>
                              </select>
                          </td>
                          <td>
                              <select v-model="ac.scopeMode">
                                  <option value="GLOBAL">Global</option>
                                  <option value="PARTIAL">Partial</option>
                                  <option value="EACH">Each</option>
                              </select>
                          </td>
                          <td>
                              <div v-if="ac.scopeMode === 'PARTIAL'" class="scope-selector">
                                  <label v-for="(lvl, idx) in hierarchyLevels" :key="idx" class="chk-label">
                                      <input type="checkbox" :value="idx" v-model="ac.scopeLevels"/> {{lvl}}
                                  </label>
                              </div>
                               <span v-else class="text-gray">{{ ac.scopeMode }} (Auto)</span>
                          </td>
                          <td><button class="del-btn" @click="constraints.activityConstraints.splice(i,1)">×</button></td>
                      </tr>
                  </tbody>
              </table>
           </div>
        </div>
        
        <!-- TAB 3: Location Precedence -->
        <div v-if="activeTab==='location'" class="tab-pane">
           <div class="info-box">
               Define directional workflow. e.g., "CP2 must start after CP1 finishes".
               Use "*" for any value.
           </div>
           <!-- 修改了添加按钮的文本 -->
           <button class="btn-add" @click="addLocConstraint">+ Add Location Precedence</button>
           <div class="table-scroll">
              <table class="c-table">
                  <thead>
                      <tr>
                          <th style="width: 20%">Apply To (Activities)</th>
                          <th style="width: 30%">If Location A matches...</th>
                          <th style="width: 30%">Then Location B matches...</th>
                          <th style="width: 15%">Constraint</th>
                          <th style="width: 5%">Del</th>
                      </tr>
                  </thead>
                  <tbody>
                      <tr v-for="(lc, i) in constraints.locationConstraints" :key="i">
                          <!-- 1. Apply Activities (Multi-select) -->
                          <td>
                              <div class="multi-select-box">
                                  <label class="block-chk">
                                      <input type="checkbox" value="ALL" v-model="lc.applyActivities"/> <b>ALL</b>
                                  </label>
                                  <div class="separator"></div>
                                  <label v-for="n in activityNames" :key="n" class="block-chk">
                                      <input type="checkbox" :value="n" v-model="lc.applyActivities"/> {{n}}
                                  </label>
                              </div>
                          </td>
                          <!-- 2. Location A Pattern (Dynamic Inputs) -->
                          <td>
                              <div class="pattern-grid">
                                  <div v-for="(lvlName, lvlIdx) in hierarchyLevels" :key="'a-'+lvlIdx" class="pattern-item">
                                      <span class="lbl">{{lvlName}}:</span>
                                      <input v-model="lc.loc1Patterns[lvlIdx]" placeholder="*" class="pat-input"/>
                                  </div>
                              </div>
                          </td>
                          <!-- 3. Location B Pattern (Dynamic Inputs) -->
                          <td>
                              <div class="pattern-grid">
                                  <div v-for="(lvlName, lvlIdx) in hierarchyLevels" :key="'b-'+lvlIdx" class="pattern-item">
                                      <span class="lbl">{{lvlName}}:</span>
                                      <input v-model="lc.loc2Patterns[lvlIdx]" placeholder="*" class="pat-input"/>
                                  </div>
                              </div>
                          </td>
                          <!-- 4. Logic -->
                          <td>
                              <div class="logic-col">
                                  <span>Type:</span>
                                  <select v-model="lc.predType" class="tiny-sel">
                                      <option value="FS">FS</option>
                                      <option value="SS">SS</option>
                                      <option value="SF">SF</option>
                                      <option value="FF">FF</option>

                                  </select>
                                  <span>Gap:</span>
                                  <input type="number" v-model.number="lc.dayGap" class="tiny-input"/>
                              </div>
                          </td>
                          <td><button class="del-btn" @click="constraints.locationConstraints.splice(i,1)">×</button></td>
                      </tr>
                  </tbody>
              </table>
           </div>
        </div>

      </div>
    </div>
  </div>
</template>

<style scoped>
/* ===== 新增外层布局样式 ===== */
.page-layout {
  display: flex;
  flex-direction: column;
  height: 100vh; /* 占满整个屏幕高度 */
  overflow: hidden; /* 禁用外层滚动 */
}

/* ===== 原有容器样式调整 ===== */
.page-container { 
  display: flex; 
  flex-direction: column; 
  height: calc(100vh - 80px); /* 减去 Navbar 高度 */
  padding: 20px; 
  background: #f4f5f7; 
}

.toolbar { display: flex; justify-content: space-between; margin-bottom: 15px; align-items: center; background: white; padding: 10px 20px; border-bottom: 1px solid #ddd; border-radius: 4px;}
.content { flex: 1; overflow: hidden; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); display: flex; flex-direction: column;}
.tab-pane { display: flex; flex-direction: column; height: 100%; }
.table-scroll { overflow-y: auto; flex: 1; border: 1px solid #eee; }

/* 按钮样式 */
.btn-save { background: #0052cc; color: white; padding: 8px 16px; border-radius: 4px; border:none; cursor: pointer; font-weight: bold;}
.btn-secondary { background: white; border: 1px solid #ccc; padding: 8px 16px; border-radius: 4px; cursor: pointer; color: #42526e;}
.btn-add { margin-bottom: 15px; background: #ebecf0; border:none; padding: 8px 16px; cursor: pointer; border-radius: 4px; font-weight: 600; color: #091e42; align-self: flex-start; }
.btn-add:hover { background: #dfe1e6; }
.del-btn { background: transparent; border: none; color: #ff5630; cursor: pointer; font-size: 20px; font-weight: bold;}
.exit-btn:hover { color: #de350b; border-color: #ffbdad; background-color: #fff0f0; }

/* Tabs */
.tabs { display: flex; gap: 20px; }
.tabs button {
  padding: 10px 0;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  font-size: 15px;
  color: #5e6c84;
  font-weight: 500;
}
.tabs button.active { color: #0052cc; border-bottom-color: #0052cc; font-weight: bold; }

/* 表格通用样式 */
.c-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.c-table th { background: #f4f5f7; text-align: left; padding: 10px; border-bottom: 2px solid #dfe1e6; color: #5e6c84; font-weight: 600; position: sticky; top: 0; z-index: 5;}
.c-table td { border-bottom: 1px solid #eee; padding: 10px; vertical-align: top; }
.c-table tr:hover { background-color: #fafbfc; }

/* 输入控件 */
.full-width { width: 100%; padding: 6px; border: 1px solid #dfe1e6; border-radius: 3px; }
.tiny-input { width: 50px; padding: 4px; border: 1px solid #dfe1e6; border-radius: 3px; }
.tiny-sel { padding: 4px; border: 1px solid #dfe1e6; border-radius: 3px; }
.text-gray { color: #999; font-style: italic; }

/* Checkbox 组样式 */
.scope-selector { display: flex; gap: 10px; flex-wrap: wrap; }
.chk-label { display: flex; align-items: center; gap: 4px; cursor: pointer; background: #f4f5f7; padding: 2px 6px; border-radius: 3px;}

/* Location Tab 特有样式 */
.info-box { background: #e9f2ff; color: #0052cc; padding: 10px; border-radius: 4px; margin-bottom: 15px; font-size: 13px; }

/* Multi-select box (Apply To) */
.multi-select-box {
    max-height: 120px;
    overflow-y: auto;
    border: 1px solid #dfe1e6;
    padding: 5px;
    border-radius: 3px;
    background: #fff;
}
.block-chk { display: block; margin-bottom: 4px; font-size: 13px; cursor: pointer; }
.separator { height: 1px; background: #eee; margin: 4px 0; }

/* Pattern Inputs (Grid Layout) */
.pattern-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(100px, 1fr));
    gap: 8px;
    background: #f9f9f9;
    padding: 8px;
    border-radius: 4px;
    border: 1px solid #eee;
}
.pattern-item { display: flex; flex-direction: column; }
.lbl { font-size: 11px; color: #6b778c; margin-bottom: 2px; font-weight: bold; }
.pat-input { width: 100%; padding: 4px; font-size: 13px; border: 1px solid #dfe1e6; border-radius: 3px; }
.pat-input:focus { border-color: #4c9aff; outline: none; }

/* Logic Column */
.logic-col { display: flex; flex-direction: column; gap: 5px; font-size: 13px; }

.left, .right { display: flex; align-items: center; gap: 10px; }
.center { flex: 2; display: flex; justify-content: center; }
</style>