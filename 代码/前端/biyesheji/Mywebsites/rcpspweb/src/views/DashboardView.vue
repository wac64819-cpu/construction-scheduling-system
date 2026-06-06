<script setup>
import { ref, nextTick, computed, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import axios from 'axios';
import * as echarts from 'echarts';

const router = useRouter();
const route = useRoute();
const projectId = route.params.projectId;

const loading = ref(false);
const resultData = ref(null);
const selectedSolutionIndex = ref(0);
const projectName = ref('');

// 存储从后端拉取的完整项目数据（包含 Settings 和 TradeData，用于前端计算成本）
const fullProjectData = ref(null);

// 项目摘要数据
const projectSummary = ref({
  activities: [],
  constraints:[],
  locationColumns:[]
});

let paretoChart = null;
let ganttChart = null;

// 添加控制弹窗状态 (如果你加了 View Model 弹窗功能)
const showModelModal = ref(false);

const goBack = () => { router.push('/projects'); };

// === 1. 初始化：获取项目名称和完整配置 ===
const loadProjectInfo = async () => {
  if (!projectId) return;
  try {
    const res = await axios.get(`http://localhost:8080/api/projects/${projectId}`);
    fullProjectData.value = res.data;
    projectName.value = res.data.name;
  } catch (e) {
    console.error("Failed to load project", e);
    alert("Failed to load project details. Is backend running?");
    router.push('/projects');
  }
};

onMounted(() => {
  loadProjectInfo();
  window.addEventListener('resize', () => {
    paretoChart && paretoChart.resize();
    ganttChart && ganttChart.resize();
  });
});

// === 2. 核心逻辑：运行优化 ===
const runOptimization = async () => {
  loading.value = true;
  try {
    const response = await axios.post('http://localhost:8080/api/schedule', {
      projectId: projectId,
      maxEvaluations: 500
    });
    const data = response.data;
    resultData.value = data;
    
    if (data.projectInfo) {
      projectSummary.value = data.projectInfo;
    }
    
    await nextTick();
    
    if (data.solutions && data.solutions.length > 0) {
      selectedSolutionIndex.value = 0;
      initParetoChart(data.solutions);
      updateGanttChart(data.solutions[0], 0);
      setTimeout(() => {
        paretoChart && paretoChart.resize();
        ganttChart && ganttChart.resize();
      }, 100);
    } else {
      alert("No solutions found!");
    }
  } catch (e) {
    
    console.error("AXIOS ERROR", e);
    console.log("AXIOS ERROR RESPONSE", e.response);
    
    const code = e.response?.data?.code;
    const msg = e.response?.data?.message || e.message;

    switch (code) {
      case "LOGICAL_DEADLOCK":
        alert("🚨 逻辑死锁错误 (Logical Deadlock)！\n\n检测到活动之间存在循环依赖。例如 A 等待 B，B 又等待 A。\n请返回 Constraints 页面，检查 Activity Precedence 配置！");
        break;
      case "TEMPORAL_INCONSISTENCY":
        alert("🚨 时间窗口冲突 (Time Window Inconsistency)！\n\n检测到搭接时间 (Gap) 存在矛盾或负环导致的时间倒挂。\n请返回 Constraints 页面，检查逻辑约束与空间流向约束的搭接天数设定！");
        break;
      default:
        alert("⚠️ ：\n" + msg);
        break;
    }
  } finally {
    loading.value = false;
  }
};

// === 3. 【核心新增】分析当前选中的解，生成数据看板明细 ===
const solutionAnalysis = computed(() => {
  if (!resultData.value || !fullProjectData.value) return null;
  const sol = resultData.value.solutions[selectedSolutionIndex.value];
  if (!sol) return null;

  const settings = fullProjectData.value.settings || { indirectCostPerDay: 1000, idleCostPerDay: 200, outsourcingCostMultiplier: 1.5 };
  const trades = fullProjectData.value.tradeData ||[];
  const activities = projectSummary.value.activities ||[];

  const makespan = Math.ceil(sol.makespan);
  if (makespan === 0) return null;

  // 每日总人数统计
  const dailyWorkers = new Array(makespan).fill(0);
  
  // 每种工种每日消耗量
  const tradeDailyUsage = {};
  trades.forEach(t => tradeDailyUsage[t.name] = new Array(makespan).fill(0));

  const taskDetails =[];

  // 3.1 遍历任务，填充分组
  sol.tasks.forEach(task => {
    // ✅ 修复属性名对应错误：a.activityName 和 a.crewSize
    const act = activities.find(a => a.activityName === task.activity);
    const tradeName = act ? act.trade : 'Unknown';
    const crew = act ? act.crewSize : 0; 
    
    taskDetails.push({ ...task, tradeName, crew });

    const startDay = Math.floor(task.start);
    const endDay = Math.ceil(task.end);

    for (let d = startDay; d < endDay; d++) {
      if (d < makespan) {
        dailyWorkers[d] += crew;
        if (tradeDailyUsage[tradeName]) {
          tradeDailyUsage[tradeName][d] += crew;
        }
      }
    }
  });

  // 3.2 计算直接人力成本与外包明细
  let laborCost = 0;
  const laborDetails =[];
  
  for (let d = 0; d < makespan; d++) {
    let dayCost = 0;
    let dayOwn = 0;
    let dayOutsourced = 0;
    const activeTasks =[];

    // 找出当天正在施工的任务
    sol.tasks.forEach(task => {
      if (d >= Math.floor(task.start) && d < Math.ceil(task.end)) {
        // ✅ 解决问题一：把 location 加入到任务展示中，并清除尖括号
        const cleanLoc = task.location.replace(/[<>]/g, '');
        activeTasks.push(`${task.activity} @ ${cleanLoc}`);
      }
    });

    trades.forEach(t => {
      const used = tradeDailyUsage[t.name][d];
      if (used > 0) {
        const own = Math.min(used, t.capacity);
        const outsourced = Math.max(0, used - t.capacity);
        
        dayOwn += own;
        dayOutsourced += outsourced;
        
        const cost = (own * t.standardRate) + (outsourced * t.standardRate * settings.outsourcingCostMultiplier);
        dayCost += cost;
      }
    });

    laborCost += dayCost;
    if (dayCost > 0) {
      laborDetails.push({
        day: d + 1,
        tasksStr: activeTasks.join(', '),
        own: dayOwn,
        outsourced: dayOutsourced,
        cost: dayCost
      });
    }
  }

  // 3.3 计算闲置成本与明细
  let idleCost = 0;
  const idleDetails =[];
  
  trades.forEach(t => {
    const myTasks = taskDetails.filter(tsk => tsk.tradeName === t.name).sort((a,b) => a.start - b.start);
    let maxEnd = -1;
    myTasks.forEach(tsk => {
      if (maxEnd >= 0) {
        const gap = tsk.start - maxEnd;
        if (gap > 0.001) {
          const cost = gap * settings.idleCostPerDay;
          idleCost += cost;
          idleDetails.push({
            trade: t.name,
            from: maxEnd.toFixed(1),
            to: tsk.start.toFixed(1),
            days: gap.toFixed(1),
            cost: cost
          });
        }
      }
      maxEnd = Math.max(maxEnd, tsk.end);
    });
  });

  const indirectCost = makespan * settings.indirectCostPerDay;
  const totalCost = indirectCost + laborCost + idleCost;

  // ✅ 新增：按施工队汇总 Direct Labor 和 Continuity Penalty
  const tradeCostBreakdown =[];
  trades.forEach(t => {
    // 提取该施工队参与的所有 Activity 并去重
    const myActs =[...new Set(taskDetails.filter(tsk => tsk.tradeName === t.name).map(tsk => tsk.activity))];
    const actStr = myActs.join(', ');

    // 汇总 Labor Cost
    let tLabor = 0;
    for(let d = 0; d < makespan; d++) {
      const used = tradeDailyUsage[t.name][d];
      if (used > 0) {
        const own = Math.min(used, t.capacity);
        const out = Math.max(0, used - t.capacity);
        tLabor += (own * t.standardRate) + (out * t.standardRate * settings.outsourcingCostMultiplier);
      }
    }

    // 汇总 Idle Cost
    let tIdle = idleDetails.filter(id => id.trade === t.name).reduce((sum, id) => sum + id.cost, 0);

    // 如果该施工队有成本产生，推入数组
    if (tLabor > 0 || tIdle > 0) {
      tradeCostBreakdown.push({
        trade: t.name,
        activitiesStr: actStr,
        labor: tLabor,
        idle: tIdle
      });
    }
  });



  return {
    makespan: sol.makespan,
    peak: sol.peakWorkforce,
    fluctuation: sol.resourceFluctuation,
    dailyWorkers,
    maxDailyWorker: Math.max(...dailyWorkers),
    tradeDailyUsage, // 传给 Gantt 图使用
    costs: {
      total: totalCost,
      indirect: indirectCost,
      labor: laborCost,
      idle: idleCost
    },
    details: {
      indirectStr: `Day 1 to Day ${makespan} @ $${settings.indirectCostPerDay}/day`,
      labor: laborDetails,
      idle: idleDetails
    },
        // ✅ 新增返回字段
    tradeCostBreakdown: tradeCostBreakdown
  };
});

// === 4. 表头逻辑 ===
const locationHeaders = computed(() => {
  const cols = projectSummary.value.locationColumns ||[];
  if (cols.length === 0) return[];
  const normalizedCols = cols.map(c => {
    if (c.hierarchy) return c;
    const h =[];
    if(c.constructionPhase) h.push(c.constructionPhase);
    if(c.superZone) h.push(c.superZone);
    if(c.zone) h.push(c.zone);
    if(c.building) h.push(c.building);
    return { ...c, hierarchy: h };
  });
  if (normalizedCols.length === 0) return[];
  const depth = normalizedCols[0].hierarchy.length;
  const headerRows =[];
  for (let level = 0; level < depth - 1; level++) {
    const groups =[];
    let currentVal = normalizedCols[0].hierarchy[level];
    let currentParentPath = normalizedCols[0].hierarchy.slice(0, level).join('_');
    let currentGroup = { name: currentVal, colspan: 1 };
    for (let i = 1; i < normalizedCols.length; i++) {
      const val = normalizedCols[i].hierarchy[level];
      const parentPath = normalizedCols[i].hierarchy.slice(0, level).join('_');
      if (val !== currentGroup.name || parentPath !== currentParentPath) {
        groups.push(currentGroup);
        currentGroup = { name: val, colspan: 1 };
        currentVal = val;
        currentParentPath = parentPath;
      } else {
        currentGroup.colspan++;
      }
    }
    groups.push(currentGroup);
    headerRows.push(groups);
  }
  return headerRows;
});

const getLeafName = (col) => col.hierarchy ? col.hierarchy[col.hierarchy.length - 1] : (col.building || 'N/A');
const getLocationKey = (loc) => loc.hierarchy ? `<${loc.hierarchy.join(', ')}>` : `<${loc.constructionPhase}, ${loc.superZone}, ${loc.zone}, ${loc.building}>`;

const getBorderClass = (colIndex) => {
  const cols = projectSummary.value.locationColumns;
  if (colIndex === cols.length - 1) return '';
  const getH = (c) => c.hierarchy ||[c.constructionPhase, c.superZone, c.zone, c.building].filter(Boolean);
  const curr = getH(cols[colIndex]);
  const next = getH(cols[colIndex + 1]);
  if (curr[0] !== next[0]) return 'border-thick-right';
  if (curr.length > 1 && curr[1] !== next[1]) return 'border-med-right';
  if (curr.length > 2 && curr[2] !== next[2]) return 'border-thin-right';
  return '';
};

const getColorByActivity = (name) => {
  const colors =['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272', '#fc8452', '#9a60b4'];
  let hash = 0;
  for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash);
  return colors[Math.abs(hash) % colors.length];
};

// === 5. ECharts 绘图逻辑 ===

const initParetoChart = (solutions) => {
  const chartDom = document.getElementById('pareto-chart');
  if(!chartDom) return;
  if (paretoChart) paretoChart.dispose();
  paretoChart = echarts.init(chartDom);
  
  const dataPoints = solutions.map((s, index) => ({
      value:[s.makespan, s.peakWorkforce, s.resourceFluctuation, s.workContinuity, s.totalCost],
      originalIndex: index,
      lineStyle: { width: 1.5, opacity: 0.3, color: '#458BE0' }
  }));

  const option = {
    title: { text: 'Multi-Objective Pareto Front', left: 'center', top: -5 },
    tooltip: {
      padding: 10,
      backgroundColor: '#222',
      borderColor: '#777',
      borderWidth: 1,
      textStyle: { color: '#fff' },
      formatter: (obj) => {
        const v = obj.data.value;
        const id = obj.data.originalIndex;
        return `<b>Solution #${id}</b><br>
                Makespan: ${v[0].toFixed(1)} days<br>
                Peak Workers: ${v[1].toFixed(0)}<br>
                Fluctuation: ${v[2].toFixed(1)}<br>
                Idle Days: ${v[3].toFixed(1)}<br>
                Total Cost: $${v[4].toFixed(0)}`;
      }
    },
    parallelAxis:[
      { dim: 0, name: 'Duration\n(Days)' },
      { dim: 1, name: 'Peak\nWorkers' },
      { dim: 2, name: 'Fluctuation\n(Var)' },
      { dim: 3, name: 'Idle\nDays' },
      { dim: 4, name: 'Total\nCost ($)' }
    ],
    parallel: { left: '5%', 
                right: '8%',
                bottom: 20, 
                top: 60,
                
        parallelAxisDefault: {
        min: (value) => Math.floor(value.min ),
        max: (value) => Math.ceil(value.max ),
      }
      },
    series:[{
      name: 'Solutions',
      type: 'parallel',
      data: dataPoints
    }]
  };
  paretoChart.setOption(option);

  paretoChart.on('click', (params) => {
    if (!params.data) return;
    const clickedIndex = params.data.originalIndex;
    selectedSolutionIndex.value = clickedIndex;
    updateGanttChart(resultData.value.solutions[clickedIndex], clickedIndex);
    
    const updatedData = dataPoints.map(item => {
      const isSelected = item.originalIndex === clickedIndex;
      return {
        ...item,
        lineStyle: {
          color: isSelected ? '#ff5630' : '#458BE0',
          width: isSelected ? 4 : 1.5,
          opacity: isSelected ? 1 : 0.15
        }
      };
    });
    paretoChart.setOption({ series: [{ data: updatedData }] });
  });
  
  paretoChart.dispatchAction({ type: 'highlight', seriesIndex: 0, dataIndex: 0 });
};

const updateGanttChart = (solution, index) => {
  const chartDom = document.getElementById('gantt-chart');
  if(!chartDom) return;
  if(ganttChart) ganttChart.dispose();
  ganttChart = echarts.init(chartDom);
  
  const tasks = solution.tasks;
  const categoryNames = tasks.map(t => `${t.activity} | ${t.location.replace(/[<>]/g,'')}`);
  
  const data = tasks.map((t, i) => {
    // ✅ 修复属性名对应错误：a.activityName 和 a.crewSize
    const act = projectSummary.value.activities.find(a => a.activityName === t.activity);
    const tradeName = act ? act.trade : 'Unknown';
    const crew = act ? act.crewSize : 0;

    return {
      name: t.activity,
      value:[i, t.start, t.end, t.end - t.start],
      itemStyle: { color: getColorByActivity(t.activity) },
      extraInfo: {
        location: t.location.replace(/[<>]/g,''),
        tradeName: tradeName,
        crew: crew,
        startDay: Math.floor(t.start)
      }
    };
  });

  const option = {
    title: { text: `Schedule (Solution #${index})`, left: 'center', top: 5 },
    tooltip: {
      formatter: (p) => {
        const info = p.data.extraInfo;
        let usageStr = '';
        if (solutionAnalysis.value) {
            const tradeData = fullProjectData.value.tradeData?.find(t => t.name === info.tradeName);
            const totalUsed = solutionAnalysis.value.tradeDailyUsage[info.tradeName]?.[info.startDay] || 0;
            const cap = tradeData ? tradeData.capacity : 0;
            const own = Math.min(totalUsed, cap);
            const out = Math.max(0, totalUsed - cap);
            usageStr = `<br/><span style="color:#aaa;font-size:11px">Trade Day-Load: Own ${own}, Out ${out} (Cap:${cap})</span>`;
        }

        // ✅ 解决问题二：展示完整的 Loc 和正确的需要人数
        return `<b>${p.name}</b><br/>
                Loc: ${info.location}<br/>
                Trade: ${info.tradeName} (Need ${info.crew} workers)<br/>
                Time: Day ${p.value[1].toFixed(1)} - ${p.value[2].toFixed(1)}
                ${usageStr}`;
      }
    },
    grid: { containLabel: true, left: 10, right: 30, top: 50, bottom: 20 },
    xAxis: { min: 0, scale: true, position: 'top', splitLine: { show: true } },
    yAxis: { type: 'category', data: categoryNames, axisLabel: { interval: 0, fontSize: 10, width: 200, overflow: 'truncate' } },
    dataZoom:[ { type: 'slider', yAxisIndex: 0, width: 20, right: 0, top: 50, bottom: 20 }, { type: 'inside', yAxisIndex: 0 } ],
    series:[{ type: 'custom', renderItem: renderGanttItem, itemStyle: { opacity: 0.8, borderRadius: 3 }, encode: { x: [1, 2], y: 0 }, data: data }]
  };
  ganttChart.setOption(option);
};

const renderGanttItem = (params, api) => {
  const categoryIndex = api.value(0);
  const start = api.coord([api.value(1), categoryIndex]);
  const end = api.coord([api.value(2), categoryIndex]);
  const height = api.size([0, 1])[1] * 0.6;
  const rectShape = echarts.graphic.clipRectByRect({ x: start[0], y: start[1] - height / 2, width: end[0] - start[0], height: height }, { x: params.coordSys.x, y: params.coordSys.y, width: params.coordSys.width, height: params.coordSys.height });
  return rectShape && { type: 'rect', transition: ['shape'], shape: rectShape, style: api.style() };
};
</script>

<template>
  <div class="dashboard-container">
    <nav class="navbar">
      <div class="nav-left"><span class="page-title">Dashboard</span></div>
      <div class="nav-center">
        <div class="project-info-badge">
          <span class="label">Project:</span>
          <span class="value">{{ projectName || 'Loading...' }}</span>
        </div>
      </div>
      <div class="nav-right">
        <button class="nav-btn secondary" @click="goBack">Back</button>
        <button class="nav-btn primary" @click="runOptimization" :disabled="loading">
          {{ loading ? 'Calculating...' : 'Run Optimization' }}
        </button>
      </div>
    </nav>

    <main class="content-wrapper">
      <!-- 左侧边栏：替换原有的 Constraints 为 Data Dashboard -->
      <aside class="sidebar">
        
        <!-- 1. 指标概览卡片 -->
        <div class="info-group" v-if="solutionAnalysis">
          <h3>Solution #{{ selectedSolutionIndex }} Metrics</h3>
          <div class="metrics-grid">
            <div class="metric-box">
              <span class="m-val">{{ solutionAnalysis.makespan.toFixed(1) }}</span>
              <span class="m-lbl">Total Days</span>
            </div>
            <div class="metric-box">
              <span class="m-val">{{ solutionAnalysis.peak.toFixed(0) }}</span>
              <span class="m-lbl">Peak Workers</span>
            </div>
            <div class="metric-box">
              <span class="m-val">${{ solutionAnalysis.costs.total.toLocaleString() }}</span>
              <span class="m-lbl">Total Cost</span>
            </div>
          </div>
        </div>

        <!-- 2. 每日人员消耗微图 (Sparkline) -->
        <div class="info-group" v-if="solutionAnalysis">
          <h3>Daily Workforce</h3>
          <div class="sparkline-container">
            <div v-for="(w, idx) in solutionAnalysis.dailyWorkers" :key="idx" class="spark-bar-wrapper">
                <div class="spark-val">{{ w }}</div>
                <div class="spark-bar" :style="{ height: (w / solutionAnalysis.maxDailyWorker * 100) + '%' }"></div>
                <div class="spark-lbl">{{ idx + 1 }}</div>
            </div>
          </div>
        </div>

        <!-- 3. 成本明细面板 -->
        <div class="info-group" v-if="solutionAnalysis">
          <h3>Cost Breakdown</h3>
          <div class="cost-accordion">
            
            <!-- 间接成本 -->
            <details class="cost-item">
              <summary>
                <div class="cost-header">
                  <span class="c-title">Indirect Overhead</span>
                  <span class="c-val">${{ solutionAnalysis.costs.indirect.toLocaleString() }}</span>
                </div>
              </summary>
              <div class="cost-content">
                {{ solutionAnalysis.details.indirectStr }}
              </div>
            </details>

            <!-- 劳务成本 -->
            <details class="cost-item" open>
              <summary>
                <div class="cost-header">
                  <span class="c-title">Direct Labor (Incl. Outsource)</span>
                  <span class="c-val">${{ solutionAnalysis.costs.labor.toLocaleString() }}</span>
                </div>
              </summary>
              <div class="cost-content scroll-content">
                <table class="detail-table">
                  <tr><th>Day</th><th>Tasks</th><th>Own</th><th>Out</th><th>Cost</th></tr>
                  <tr v-for="(ld, i) in solutionAnalysis.details.labor" :key="i">
                    <td>{{ ld.day }}</td>
                    <td class="trunc" :title="ld.tasksStr">{{ ld.tasksStr }}</td>
                    <td class="txt-blue">{{ ld.own }}</td>
                    <td class="txt-red">{{ ld.outsourced }}</td>
                    <td>${{ ld.cost.toFixed(0) }}</td>
                  </tr>
                </table>
              </div>
            </details>

            <!-- 闲置成本 -->
            <details class="cost-item">
              <summary>
                <div class="cost-header">
                  <span class="c-title">Continuity Penalty (Idle)</span>
                  <span class="c-val">${{ solutionAnalysis.costs.idle.toLocaleString() }}</span>
                </div>
              </summary>
              <div class="cost-content scroll-content">
                <table class="detail-table">
                  <tr v-if="solutionAnalysis.details.idle.length===0"><td colspan="4">No idle times!</td></tr>
                  <tr v-else><th>Trade</th><th>From</th><th>To</th><th>Cost</th></tr>
                  <tr v-for="(id, i) in solutionAnalysis.details.idle" :key="i">
                    <td>{{ id.trade }}</td><td>Day {{ id.from }}</td><td>Day {{ id.to }}</td>
                    <td class="txt-red">${{ id.cost.toFixed(0) }}</td>
                  </tr>
                </table>
              </div>
            </details>

          </div>
        </div>

                <!-- 4. ✅ 新增：按施工队汇总的成本面板 -->
        <div class="info-group" v-if="solutionAnalysis">
          <h3>Trade Cost Summary</h3>
          <div class="cost-item">
            <div class="cost-content scroll-content" style="border-top: none; padding: 10px;">
              <table class="detail-table">
                <tr>
                  <th style="width: 25%">Trade</th>
                  <th style="width: 35%">Activities</th>
                  <th style="width: 20%">Labor ($)</th>
                  <th style="width: 20%">Idle ($)</th>
                </tr>
                <tr v-for="(tc, i) in solutionAnalysis.tradeCostBreakdown" :key="i">
                  <td class="fw-bold">{{ tc.trade }}</td>
                  <!-- 鼠标悬停显示完整内容 -->
                  <td class="trunc" :title="tc.activitiesStr" style="max-width: 80px;">
                    {{ tc.activitiesStr }}
                  </td>
                  <td class="txt-blue">${{ tc.labor.toFixed(0) }}</td>
                  <td class="txt-red">${{ tc.idle.toFixed(0) }}</td>
                </tr>
              </table>
            </div>
          </div>
        </div>



        <div v-if="!solutionAnalysis" style="padding:20px; color:#999; text-align:center;">
          Click "Run Optimization" to generate dashboard.
        </div>
      </aside>
      
      <section class="visualizer">
        <div v-if="!resultData && !loading" class="empty-state">
          <div class="empty-content">
            <h2>Ready to Optimize</h2>
            <p>Click "Run Optimization" to start algorithm.</p>
          </div>
        </div>
        <div v-if="loading" class="loading-overlay">
          <div class="spinner"></div><p>Running NSGA-II Algorithm...</p>
        </div>
        <div v-show="resultData && !loading" class="charts-container">
          <div class="chart-box"><div id="pareto-chart" style="width: 100%; height: 320px;"></div></div>
          <div class="chart-box"><div id="gantt-chart" style="width: 100%; height: 500px;"></div></div>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
/* 基础布局 */
.dashboard-container { display: flex; flex-direction: column; height: 100vh; background-color: #f8f7f4; font-family: 'Inter', sans-serif; }
.navbar { height: 60px; background: white; border-bottom: 1px solid #eaeaea; display: flex; justify-content: space-between; align-items: center; padding: 0 30px; flex-shrink: 0; z-index: 50; }
.nav-center { flex: 1; display: flex; justify-content: center; }
.project-info-badge { background: #e3f2fd; color: #0052cc; padding: 6px 16px; border-radius: 20px; display: flex; gap: 8px; align-items: center; border: 1px solid #bbdefb; }
.project-info-badge .label { font-size: 12px; opacity: 0.8; text-transform: uppercase; }
.project-info-badge .value { font-size: 14px; font-weight: bold; }
.page-title { font-weight: 600; color: #666; font-size: 18px; }
.nav-btn { padding: 8px 16px; border-radius: 6px; border: none; font-weight: 500; cursor: pointer; margin-left: 10px; transition: 0.2s; }
.nav-btn.primary { background: #000; color: white; }
.nav-btn.primary:disabled { background: #888; cursor: not-allowed; }
.nav-btn.secondary { background: white; border:1px solid #ccc; color: #333; }
.nav-btn.secondary:hover { background: #eee; }

/* 内容区 */
.content-wrapper { display: flex; flex: 1; overflow: hidden; }

/* 侧边栏（新 Dashboard） */
.sidebar { width: 450px; background: white; border-right: 1px solid #eaeaea; padding: 20px; overflow-y: auto; flex-shrink: 0; }
.info-group { margin-bottom: 25px; }
.info-group h3 { font-size: 15px; color: #172b4d; margin-bottom: 15px; border-left: 4px solid #0052cc; padding-left: 8px; }

/* 概览指标卡片 */
.metrics-grid { display: flex; gap: 10px; }
.metric-box { flex: 1; background: #f4f5f7; padding: 15px 10px; border-radius: 8px; text-align: center; border: 1px solid #dfe1e6;}
.m-val { display: block; font-size: 22px; font-weight: 800; color: #0052cc; margin-bottom: 5px; }
.m-lbl { font-size: 11px; color: #5e6c84; text-transform: uppercase; letter-spacing: 0.5px;}

/* 每日人数迷你柱状图 */
.sparkline-container { display: flex; gap: 2px; height: 80px; align-items: flex-end; overflow-x: auto; padding-bottom: 5px; border-bottom: 1px solid #eee;}
.spark-bar-wrapper { display: flex; flex-direction: column; align-items: center; justify-content: flex-end; height: 100%; min-width: 20px; }
.spark-bar { width: 14px; background-color: #458BE0; border-radius: 2px 2px 0 0; transition: height 0.3s; }
.spark-bar:hover { background-color: #ff5630; }
.spark-val { font-size: 9px; color: #666; margin-bottom: 2px; }
.spark-lbl { font-size: 9px; color: #aaa; margin-top: 2px; }

/* 成本手风琴折叠面板 */
.cost-accordion { display: flex; flex-direction: column; gap: 10px; }
.cost-item { background: #fff; border: 1px solid #dfe1e6; border-radius: 6px; overflow: hidden; }
.cost-item summary { list-style: none; cursor: pointer; padding: 12px 15px; background: #f9fafc; user-select: none; }
.cost-item summary::-webkit-details-marker { display: none; }
.cost-header { display: flex; justify-content: space-between; align-items: center; }
.c-title { font-weight: 600; font-size: 13px; color: #172b4d; }
.c-val { font-weight: bold; font-size: 14px; color: #0052cc; }
.cost-content { padding: 15px; font-size: 12px; color: #42526e; border-top: 1px solid #dfe1e6; }
.scroll-content { max-height: 200px; overflow-y: auto; padding: 0; }

/* 详情表格 */
.detail-table { width: 100%; border-collapse: collapse; text-align: left; }
.detail-table th { background: #f4f5f7; padding: 6px 10px; position: sticky; top: 0; font-size: 11px; color: #6b778c;}
.detail-table td { padding: 6px 10px; border-bottom: 1px solid #eee; }
.trunc { max-width: 100px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.txt-blue { color: #0052cc; font-weight: bold; }
.txt-red { color: #de350b; font-weight: bold; }

/* 可视化区域 */
.visualizer { flex: 1; padding: 20px; overflow-y: auto; }
.chart-box { background: white; border-radius: 12px; padding: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.05); margin-bottom: 20px; border: 1px solid #eaeaea;}
.empty-state { height: 100%; display: flex; align-items: center; justify-content: center; color: #999; }
.loading-overlay { position: absolute; top: 0; left: 0; right: 0; bottom: 0; background: rgba(255,255,255,0.8); display: flex; flex-direction: column; align-items: center; justify-content: center; z-index: 50; }
.spinner { width: 40px; height: 40px; border: 4px solid #f3f3f3; border-top: 4px solid #0d0c22; border-radius: 50%; animation: spin 1s linear infinite; margin-bottom: 15px; }
@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
</style>
