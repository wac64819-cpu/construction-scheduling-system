<script setup>
import { ref, onMounted, onUnmounted } from 'vue';

// 完全替换：从localStorage读取初始值，而非路由参数
const currentProjectId = ref(localStorage.getItem('activeProjectId') || null);

// 新增：更新选中项目ID的函数
const updateActiveProject = () => {
  currentProjectId.value = localStorage.getItem('activeProjectId') || null;
};

// 新增：监听自定义事件（项目列表页选择项目时触发）
window.addEventListener('project-selected', updateActiveProject);

// 新增：监听storage事件（防止多标签页数据不同步）
window.addEventListener('storage', updateActiveProject);

// 新增：挂载时初始化一次
onMounted(updateActiveProject);

// 新增：卸载组件时移除事件监听（避免内存泄漏）
onUnmounted(() => {
  window.removeEventListener('project-selected', updateActiveProject);
  window.removeEventListener('storage', updateActiveProject);
});
</script>

<template>
  <nav class="navbar">
    <!-- 左侧菜单 -->
    <div class="nav-left">
      <ul class="menu">
        <!-- 1. Model 按钮：通常指向项目列表，方便切换项目 -->
        <!-- 如果你想让它指向当前项目的 Model页，可以改为 :to="currentProjectId ? `/model/${currentProjectId}` : '/projects'" -->
        <li>
          <router-link to="/projects" active-class="active-link">
            Model <span class="arrow"></span>
          </router-link>
        </li>

        <!-- 2. Constraints 按钮：只有在选中项目时才可用 -->
        <li>
          <router-link 
            v-if="currentProjectId" 
            :to="`/constraints/${currentProjectId}`"
            active-class="active-link"
          >
            Constraints
          </router-link>
          <span v-else class="disabled-link" title="Please select a project first">
            Constraints
          </span>
        </li>

        <!-- 3. Trade 按钮：只有在选中项目时才可用 -->
        <li>
          <router-link 
            v-if="currentProjectId" 
            :to="`/trade/${currentProjectId}`"
            active-class="active-link"
          >
            Trade <span class="arrow"></span>
          </router-link>
          <span v-else class="disabled-link" title="Please select a project first">
            Trade <span class="arrow"></span>
          </span>
        </li>

        <li>
          <router-link 
            v-if="currentProjectId" 
            :to="`/settings/${currentProjectId}`"
            active-class="active-link"
          >
            Setting
          </router-link>
          <span v-else class="disabled-link" title="Please select a project first">
            Setting
          </span>
        </li>


      
      </ul>
    </div>

    <!-- 中间 Logo -->
    <div class="nav-center">
      <a href="#" class="logo">Mike Zhou</a>
    </div>

    <!-- 右侧 操作区 -->
    <div class="nav-right">


    </div>
  </nav>
</template>

<style scoped>
/* 导航栏整体布局 */
.navbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 40px;
  height: 80px;
  color: #333;
}

/* 左侧菜单容器 */
.nav-left {
  background-color: #ffffff;
  padding: 4px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.menu {
  display: flex;
  list-style: none;
  margin: 0;
  padding: 0;
  gap: 2px;
}

.menu li {
  display: flex;
  align-items: center;
}

/* 菜单链接样式 */
.menu li a {
  display: block;
  text-decoration: none;
  color: #333;
  font-size: 14px;
  font-weight: 600;
  padding: 10px 18px;
  border-radius: 6px;
  transition: background-color 0.2s ease, color 0.2s ease;
}

/* 悬停效果 */
.menu li a:hover {
  background-color: #f0f0f0;
  color: #000;
}

/* --- 新增：选中状态的高亮样式 --- */
.active-link {
  background-color: #e9f2ff !important; /* 浅蓝色背景 */
  color: #0052cc !important;          /* 深蓝色文字 */
}

/* --- 新增：禁用状态的样式 --- */
.disabled-link {
  display: block;
  padding: 10px 18px;
  color: #ccc;        /* 灰色文字 */
  font-size: 14px;
  font-weight: 600;
  cursor: not-allowed; /* 禁止手势 */
  border-radius: 6px;
}

/* 箭头小图标 */
.arrow {
  font-size: 8px;
  margin-left: 4px;
  vertical-align: middle;
  color: #666;
}

/* --- Logo 和 右侧区域 --- */
.nav-center {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
}

.logo {
  font-family: 'Pacifico', cursive;
  font-size: 28px;
  color: #0d0c22;
  text-shadow: 0 2px 4px rgba(255, 255, 255, 0.8);
  transition: opacity 0.2s;
  text-decoration: none;
}

.logo:hover {
  opacity: 0.7;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.search-box {
  position: relative;
  display: flex;
  align-items: center;
}

.search-box input {
  padding: 10px 10px 10px 40px;
  border-radius: 20px;
  border: none;
  background: white;
  width: 140px;
  outline: none;
  font-size: 14px;
  transition: width 0.3s;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.1);
}

.search-box input:focus {
  width: 200px;
  background: white;
}

.search-icon {
  position: absolute;
  left: 12px;
  font-size: 12px;
  color: #999;
}

.login-link {
  color: #0d0c22;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.8);
  padding: 8px 16px;
  border-radius: 20px;
  text-decoration: none;
}

.signup-btn {
  background-color: #0d0c22;
  color: white;
  padding: 10px 20px;
  border-radius: 20px;
  font-weight: 600;
  font-size: 14px;
  text-decoration: none;
}

.signup-btn:hover {
  background-color: #333;
}
</style>