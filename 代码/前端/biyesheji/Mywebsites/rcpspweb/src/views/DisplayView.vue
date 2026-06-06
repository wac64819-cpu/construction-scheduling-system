<script setup>
import { ref, nextTick, computed, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import axios from 'axios';
import * as echarts from 'echarts';

const router = useRouter();
const route = useRoute();
const projectId = route.params.projectId;

const loading = ref(true);
const resultData = ref(null);
const selectedSolutionIndex = ref(0);
const projectName = ref('');
const fullProjectData = ref(null);
const projectSummary = ref({ activities: [], constraints: [], locationColumns:[] });

let paretoChart = null;
let ganttChart = null;

const goBack = () => { router.push('/projects'); };

// === 1. 加载保存的数据 ===
const loadSavedData = async () => {
  if (!projectId) return;
  try {
    const res = await axios.get(`http://localhost:8080/api/projects/${projectId}`);
    fullProjectData.value = res.data;
    projectName.value = res.data.name;
    
    if (!res.data.latestSchedule) {
      alert("No saved schedule found. Please run optimization first.");
      router.push('/projects');
      return;
    }
    
    resultData.value = res.data.latestSchedule;
    if (resultData.value.projectInfo) {
      projectSummary.value = resultData.value.projectInfo;
    }
    
    await nextTick();
    if (resultData.value.solutions && resultData.value.solutions.length > 0) {
      selectedSolutionIndex.value = 0;
      initParetoChart(resultData.value.solutions);
      updateGanttChart(resultData.value.solutions[0], 0);
      
      setTimeout(() => {
        paretoChart && paretoChart.resize();
        ganttChart && ganttChart.resize();
      }, 100);
    }
  } catch (e) {
    console.error("Failed to load saved schedule", e);
    alert("Failed to load project details.");
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadSavedData();
  window.addEventListener('resize', () => {
    paretoChart && paretoChart.resize();
    ganttChart && ganttChart.resize();
  });
});

// === 2. 分析当前选中的解，生成数据看板与报表所需的明细 ===
const solutionAnalysis = computed(() => {
  if (!resultData.value || !fullProjectData.value) return null;
  const sol = resultData.value.solutions[selectedSolutionIndex.value];
  if (!sol) return null;
  
  const settings = fullProjectData.value.settings || { indirectCostPerDay: 1000, idleCostPerDay: 200, outsourcingCostMultiplier: 1.5 };
  const trades = fullProjectData.value.tradeData ||[];
  const activities = projectSummary.value.activities ||[];
  const makespan = Math.ceil(sol.makespan);
  
  if (makespan === 0) return null;
  
  const dailyWorkers = new Array(makespan).fill(0);
  const tradeDailyUsage = {};
  trades.forEach(t => tradeDailyUsage[t.name] = new Array(makespan).fill(0));
  
  const taskDetails =[];
  sol.tasks.forEach(task => {
    const act = activities.find(a => a.activityName === task.activity);
    const tradeName = act ? act.trade : 'Unknown';
    const crew = act ? act.crewSize : 0;
    taskDetails.push({ ...task, tradeName, crew });
    
    const startDay = Math.floor(task.start + 0.001);
    const endDay = Math.ceil(task.end - 0.001);
    
    for (let d = startDay; d < endDay; d++) {
      if (d < makespan) {
        dailyWorkers[d] += crew;
        if (tradeDailyUsage[tradeName]) {
          tradeDailyUsage[tradeName][d] += crew;
        }
      }
    }
  });
  
  let laborCost = 0;
  const laborDetails =[];
  for (let d = 0; d < makespan; d++) {
    let dayCost = 0; let dayOwn = 0; let dayOutsourced = 0;
    const activeTasks =[];
    sol.tasks.forEach(task => {
      const startDay = Math.floor(task.start + 0.001);
      const endDay = Math.ceil(task.end - 0.001);
      if (d >= startDay && d < endDay) {
        const cleanLoc = task.location.replace(/[<>]/g, '');
        activeTasks.push(`${task.activity} @ ${cleanLoc}`);
      }
    });
    
    trades.forEach(t => {
      const used = tradeDailyUsage[t.name][d];
      if (used > 0) {
        const own = Math.min(used, t.capacity);
        const outsourced = Math.max(0, used - t.capacity);
        dayOwn += own; dayOutsourced += outsourced;
        const cost = (own * t.standardRate) + (outsourced * t.standardRate * settings.outsourcingCostMultiplier);
        dayCost += cost;
      }
    });
    laborCost += dayCost;
    if (dayCost > 0) {
      laborDetails.push({ day: d + 1, tasksStr: activeTasks.join(', '), own: dayOwn, outsourced: dayOutsourced, cost: dayCost });
    }
  }
  
  let idleCost = 0;
  const idleDetails =[];
  trades.forEach(t => {
    const myTasks = taskDetails.filter(tsk => tsk.tradeName === t.name).sort((a,b) => a.start - b.start);
    let maxEnd = -1.0;
    myTasks.forEach(tsk => {
      if (maxEnd >= 0) {
        const gap = tsk.start - maxEnd;
        if (gap > 0.001) {
          const cost = gap * settings.idleCostPerDay;
          idleCost += cost;
          idleDetails.push({ trade: t.name, from: maxEnd.toFixed(1), to: tsk.start.toFixed(1), days: gap.toFixed(1), cost: cost });
        }
      }
      maxEnd = Math.max(maxEnd, tsk.end);
    });
  });
  
  const indirectCost = makespan * settings.indirectCostPerDay;
  const totalCost = indirectCost + laborCost + idleCost;
  
  const tradeCostBreakdown = [];
  trades.forEach(t => {
    const myActs =[...new Set(taskDetails.filter(tsk => tsk.tradeName === t.name).map(tsk => tsk.activity))];
    const actStr = myActs.join(', ');
    let tLabor = 0;
    for(let d = 0; d < makespan; d++) {
      const used = tradeDailyUsage[t.name][d];
      if (used > 0) {
        const own = Math.min(used, t.capacity);
        const out = Math.max(0, used - t.capacity);
        tLabor += (own * t.standardRate) + (out * t.standardRate * settings.outsourcingCostMultiplier);
      }
    }
    let tIdle = idleDetails.filter(id => id.trade === t.name).reduce((sum, id) => sum + id.cost, 0);
    if (tLabor > 0 || tIdle > 0) {
      tradeCostBreakdown.push({ trade: t.name, activitiesStr: actStr, labor: tLabor, idle: tIdle });
    }
  });
  
  return {
    makespan: sol.makespan, peak: sol.peakWorkforce, fluctuation: sol.resourceFluctuation,
    dailyWorkers, maxDailyWorker: Math.max(...dailyWorkers), tradeDailyUsage,
    costs: { total: totalCost, indirect: indirectCost, labor: laborCost, idle: idleCost },
    details: { indirectStr: `Total ${makespan} days @ $${settings.indirectCostPerDay}/day`, labor: laborDetails, idle: idleDetails },
    tradeCostBreakdown: tradeCostBreakdown
  };
});

// === 图形与颜色处理 ===
const getColorByActivity = (name) => {
  const colors =['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272', '#fc8452', '#9a60b4'];
  let hash = 0;
  for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash);
  return colors[Math.abs(hash) % colors.length];
};

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
      padding: 10, backgroundColor: '#222', borderColor: '#777', borderWidth: 1, textStyle: { color: '#fff' },
      formatter: (obj) => {
        const v = obj.data.value; const id = obj.data.originalIndex;
        return `<b>Solution #${id}</b><br>Makespan: ${v[0].toFixed(1)} days<br>Peak Workers: ${v[1].toFixed(0)}<br>Fluctuation: ${v[2].toFixed(1)}<br>Idle Days: ${v[3].toFixed(1)}<br>Total Cost: $${v[4].toFixed(0)}`;
      }
    },
    parallelAxis:[
      { dim: 0, name: 'Duration\n(Days)' }, { dim: 1, name: 'Peak\nWorkers' }, { dim: 2, name: 'Fluctuation\n(Var)' }, { dim: 3, name: 'Idle\nDays' }, { dim: 4, name: 'Total\nCost ($)' }
    ],
        parallel: { left: '5%', 
                right: '8%',
                bottom: 20, 
                top: 60,
                
        parallelAxisDefault: {
        min: (value) => Math.floor(value.min),
        max: (value) => Math.ceil(value.max),
      }
      },
    series: [{ name: 'Solutions', type: 'parallel', data: dataPoints }]
  };
  paretoChart.setOption(option);
  paretoChart.on('click', (params) => {
    if (!params.data) return;
    const clickedIndex = params.data.originalIndex;
    selectedSolutionIndex.value = clickedIndex;
    updateGanttChart(resultData.value.solutions[clickedIndex], clickedIndex);
    const updatedData = dataPoints.map(item => {
      const isSelected = item.originalIndex === clickedIndex;
      return { ...item, lineStyle: { color: isSelected ? '#ff5630' : '#458BE0', width: isSelected ? 4 : 1.5, opacity: isSelected ? 1 : 0.15 } };
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
    const act = projectSummary.value.activities.find(a => a.activityName === t.activity);
    const tradeName = act ? act.trade : 'Unknown';
    const crew = act ? act.crewSize : 0;
    return {
      name: t.activity,
      value:[i, t.start, t.end, t.end - t.start],
      itemStyle: { color: getColorByActivity(t.activity) },
      extraInfo: { location: t.location.replace(/[<>]/g,''), tradeName: tradeName, crew: crew, startDay: Math.floor(t.start + 0.001) }
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
            const own = Math.min(totalUsed, cap); const out = Math.max(0, totalUsed - cap);
            usageStr = `<br/><span style="color:#aaa;font-size:11px">Trade Day-Load: Own ${own}, Out ${out} (Cap:${cap})</span>`;
        }
        return `<b>${p.name}</b><br/>Loc: ${info.location}<br/>Trade: ${info.tradeName} (Need ${info.crew} workers)<br/>Time: Day ${p.value[1].toFixed(1)} - ${p.value[2].toFixed(1)}${usageStr}`;
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

// ✅ TXT 导出逻辑
const exportToTXT = () => {
  if (!solutionAnalysis.value || !resultData.value) return;

  const btn = document.getElementById('txt-btn');
  if (btn) {
    btn.innerText = 'Generating TXT...';
    btn.disabled = true;
  }

  try {
    const solIndex = selectedSolutionIndex.value;
    const sol = resultData.value.solutions[solIndex];
    const analysis = solutionAnalysis.value;

    let txt = `==========================================================\n`;
    txt += `  PROJECT SCHEDULE & COST REPORT \n`;
    txt += `==========================================================\n\n`;
    txt += `Project Name : ${projectName.value || 'Unknown Project'}\n`;
    txt += `Solution ID  : #${solIndex}\n\n`;

    // 1. Key Metrics
    txt += `[ 1. KEY METRICS ]\n`;
    txt += `----------------------------------------------------------\n`;
    txt += `- Total Duration       : ${analysis.makespan.toFixed(1)} Days\n`;
    txt += `- Peak Workers         : ${analysis.peak.toFixed(0)}\n`;
    if (analysis.fluctuation) txt += `- Resource Fluctuation : ${analysis.fluctuation.toFixed(1)}\n`;
    txt += `- Total Estimated Cost : $${analysis.costs.total.toLocaleString()}\n\n`;

    // 2. Trade Cost Summary
    txt += `[ 2. TRADE COST SUMMARY ]\n`;
    txt += `----------------------------------------------------------\n`;
    analysis.tradeCostBreakdown.forEach(tc => {
      txt += `- Trade: ${tc.trade.padEnd(15)} | Labor: $${tc.labor.toFixed(0).padEnd(8)} | Idle Penalty: $${tc.idle.toFixed(0).padEnd(8)} | Acts: ${tc.activitiesStr}\n`;
    });
    txt += `\n`;

    // 3. Detailed Cost Breakdown
    txt += `[ 3. DETAILED COST BREAKDOWN ]\n`;
    txt += `----------------------------------------------------------\n`;
    txt += `- Indirect Overhead    : $${analysis.costs.indirect.toLocaleString()} (${analysis.details.indirectStr})\n`;
    txt += `- Direct Labor Cost    : $${analysis.costs.labor.toLocaleString()} (Includes Outsourced Cost)\n`;
    txt += `- Continuity Penalty   : $${analysis.costs.idle.toLocaleString()} (Idle Days Penalty)\n\n`;

    // 4. Idle Detail (If any)
    if (analysis.details.idle && analysis.details.idle.length > 0) {
      txt += `[ 4. IDLE TIMES PENALTY DETAILS ]\n`;
      txt += `----------------------------------------------------------\n`;
      analysis.details.idle.forEach(id => {
        txt += `- Trade: ${id.trade.padEnd(15)} | From Day ${id.from} to Day ${id.to} (${id.days} days) | Penalty: $${id.cost.toFixed(0)}\n`;
      });
      txt += `\n`;
    }

    // 5. Tasks Start and End Times
    txt += `[ 5. TASK SCHEDULE (START & END TIMES) ]\n`;
    txt += `----------------------------------------------------------\n`;
    const sortedTasks =[...sol.tasks].sort((a, b) => a.start - b.start);
    sortedTasks.forEach(task => {
      const cleanLoc = task.location.replace(/[<>]/g, '');
      txt += `Activity: ${task.activity.padEnd(20)} | Location: ${cleanLoc.padEnd(25)} | Start: Day ${task.start.toFixed(1).padStart(6)} | End: Day ${task.end.toFixed(1).padStart(6)}\n`;
    });

    // 创建 Blob 并触发下载
    const blob = new Blob([txt], { type: 'text/plain;charset=utf-8' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    const safeProjectName = projectName.value.replace(/\s+/g, '_');
    link.download = `${safeProjectName}_Schedule_Sol_${solIndex}.txt`;
    
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

  } catch (err) {
    console.error("TXT Export failed", err);
    alert("Failed to generate TXT.");
  } finally {
    if (btn) {
      btn.innerText = 'Export TXT';
      btn.disabled = false;
    }
  }
};
</script>

<template>
  <div class="dashboard-container">
    <!-- UI 导航栏 -->
    <nav class="navbar">
      <div class="nav-left"><span class="page-title">Display Saved Schedule</span></div>
      <div class="nav-center">
        <div class="project-info-badge">
          <span class="label">Project:</span>
          <span class="value">{{ projectName || 'Loading...' }}</span>
        </div>
      </div>
      <div class="nav-right">
        <button class="nav-btn secondary" @click="goBack">Back to Projects</button>
        <!-- 导出 TXT 按钮 -->
        <button id="txt-btn" class="nav-btn primary" @click="exportToTXT" :disabled="loading">
          Export TXT
        </button>
      </div>
    </nav>
    
    <!-- 互动界面 (Dashboard) -->
    <main class="content-wrapper">
      <aside class="sidebar">
        <!-- 1. 指标概览卡片 -->
        <div class="info-group" v-if="solutionAnalysis">
          <h3>Solution #{{ selectedSolutionIndex }} Metrics</h3>
          <div class="metrics-grid">
            <div class="metric-box"><span class="m-val">{{ solutionAnalysis.makespan.toFixed(1) }}</span><span class="m-lbl">Total Days</span></div>
            <div class="metric-box"><span class="m-val">{{ solutionAnalysis.peak.toFixed(0) }}</span><span class="m-lbl">Peak Workers</span></div>
            <div class="metric-box"><span class="m-val">${{ solutionAnalysis.costs.total.toLocaleString() }}</span><span class="m-lbl">Total Cost</span></div>
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
        
        <!-- 2. Trade Cost Summary -->
        <div class="info-group" v-if="solutionAnalysis">
          <h3>Trade Cost Summary</h3>
          <div class="cost-item">
            <div class="cost-content scroll-content" style="border-top: none; padding: 10px;">
              <table class="detail-table">
                <tr><th style="width: 25%">Trade</th><th style="width: 35%">Activities</th><th style="width: 20%">Labor ($)</th><th style="width: 20%">Idle ($)</th></tr>
                <tr v-for="(tc, i) in solutionAnalysis.tradeCostBreakdown" :key="i">
                  <td class="fw-bold">{{ tc.trade }}</td>
                  <td class="trunc" :title="tc.activitiesStr" style="max-width: 80px;">{{ tc.activitiesStr }}</td>
                  <td class="txt-blue">${{ tc.labor.toFixed(0) }}</td><td class="txt-red">${{ tc.idle.toFixed(0) }}</td>
                </tr>
              </table>
            </div>
          </div>
        </div>
        
        <!-- 3. 成本明细折叠面板 -->
        <div class="info-group" v-if="solutionAnalysis">
          <h3>Detailed Cost Breakdown</h3>
          <div class="cost-accordion">
            <details class="cost-item">
              <summary><div class="cost-header"><span class="c-title">Indirect Overhead</span><span class="c-val">${{ solutionAnalysis.costs.indirect.toLocaleString() }}</span></div></summary>
              <div class="cost-content">{{ solutionAnalysis.details.indirectStr }}</div>
            </details>
            <details class="cost-item" open>
              <summary><div class="cost-header"><span class="c-title">Direct Labor (Incl. Outsource)</span><span class="c-val">${{ solutionAnalysis.costs.labor.toLocaleString() }}</span></div></summary>
              <div class="cost-content scroll-content">
                <table class="detail-table">
                  <tr><th>Day</th><th>Tasks</th><th>Own</th><th>Out</th><th>Cost</th></tr>
                  <tr v-for="(ld, i) in solutionAnalysis.details.labor" :key="i">
                    <td>{{ ld.day }}</td><td class="trunc" :title="ld.tasksStr">{{ ld.tasksStr }}</td>
                    <td class="txt-blue">{{ ld.own }}</td><td class="txt-red">{{ ld.outsourced }}</td>
                    <td>${{ ld.cost.toFixed(0) }}</td>
                  </tr>
                </table>
              </div>
            </details>
            <details class="cost-item">
              <summary><div class="cost-header"><span class="c-title">Continuity Penalty (Idle)</span><span class="c-val">${{ solutionAnalysis.costs.idle.toLocaleString() }}</span></div></summary>
              <div class="cost-content scroll-content">
                <table class="detail-table">
                  <tr v-if="solutionAnalysis.details.idle.length===0"><td colspan="4">No idle times!</td></tr>
                  <tr v-else><th>Trade</th><th>From</th><th>To</th><th>Cost</th></tr>
                  <tr v-for="(id, i) in solutionAnalysis.details.idle" :key="i">
                    <td>{{ id.trade }}</td><td>Day {{ id.from }}</td><td>Day {{ id.to }}</td><td class="txt-red">${{ id.cost.toFixed(0) }}</td>
                  </tr>
                </table>
              </div>
            </details>
          </div>
        </div>
      </aside>
      
      <section class="visualizer">
        <div v-if="loading" class="loading-overlay">
          <div class="spinner"></div><p>Loading data...</p>
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
/* ================= 原有 UI 样式 ================= */
.dashboard-container { display: flex; flex-direction: column; height: 100vh; background-color: #f8f7f4; font-family: 'Inter', sans-serif; }
.navbar { height: 60px; background: white; border-bottom: 1px solid #eaeaea; display: flex; justify-content: space-between; align-items: center; padding: 0 30px; flex-shrink: 0; z-index: 50; }
.nav-center { flex: 1; display: flex; justify-content: center; }
.project-info-badge { background: #e3f2fd; color: #0052cc; padding: 6px 16px; border-radius: 20px; display: flex; gap: 8px; align-items: center; border: 1px solid #bbdefb; }
.project-info-badge .label { font-size: 12px; opacity: 0.8; text-transform: uppercase; }
.project-info-badge .value { font-size: 14px; font-weight: bold; }
.page-title { font-weight: 600; color: #666; font-size: 18px; }
.nav-btn { padding: 8px 16px; border-radius: 6px; border: none; font-weight: 500; cursor: pointer; margin-left: 10px; transition: 0.2s; }
.nav-btn.primary { background: #0052cc; color: white; }
.nav-btn.primary:disabled { background: #888; cursor: not-allowed; }
.nav-btn.secondary { background: white; border:1px solid #ccc; color: #333; }
.nav-btn.secondary:hover { background: #eee; }
.content-wrapper { display: flex; flex: 1; overflow: hidden; }
.sidebar { width: 450px; background: white; border-right: 1px solid #eaeaea; padding: 20px; overflow-y: auto; flex-shrink: 0; }
.info-group { margin-bottom: 25px; }
.info-group h3 { font-size: 15px; color: #172b4d; margin-bottom: 15px; border-left: 4px solid #0052cc; padding-left: 8px; }
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
.cost-accordion { display: flex; flex-direction: column; gap: 10px; }
.cost-item { background: #fff; border: 1px solid #dfe1e6; border-radius: 6px; overflow: hidden; }
.cost-item summary { list-style: none; cursor: pointer; padding: 12px 15px; background: #f9fafc; user-select: none; }
.cost-header { display: flex; justify-content: space-between; align-items: center; }
.c-title { font-weight: 600; font-size: 13px; color: #172b4d; }
.c-val { font-weight: bold; font-size: 14px; color: #0052cc; }
.cost-content { padding: 15px; font-size: 12px; color: #42526e; border-top: 1px solid #dfe1e6; }
.scroll-content { max-height: 200px; overflow-y: auto; padding: 0; }
.detail-table { width: 100%; border-collapse: collapse; text-align: left; }
.detail-table th { background: #f4f5f7; padding: 6px 10px; position: sticky; top: 0; font-size: 11px; color: #6b778c;}
.detail-table td { padding: 6px 10px; border-bottom: 1px solid #eee; }
.trunc { max-width: 100px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.txt-blue { color: #0052cc; font-weight: bold; }
.txt-red { color: #de350b; font-weight: bold; }
.visualizer { flex: 1; padding: 20px; overflow-y: auto; background-color: #f8f7f4; }
.chart-box { background: white; border-radius: 12px; padding: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.05); margin-bottom: 20px; border: 1px solid #eaeaea;}
.loading-overlay { position: absolute; top: 0; left: 0; right: 0; bottom: 0; background: rgba(255,255,255,0.8); display: flex; flex-direction: column; align-items: center; justify-content: center; z-index: 50; }
.spinner { width: 40px; height: 40px; border: 4px solid #f3f3f3; border-top: 4px solid #0d0c22; border-radius: 50%; animation: spin 1s linear infinite; margin-bottom: 15px; }
@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
</style>