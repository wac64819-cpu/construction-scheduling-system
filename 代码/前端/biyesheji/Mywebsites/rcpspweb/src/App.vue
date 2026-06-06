<!-- src/App.vue -->
<script setup>
// 不需要引入 Navbar 或 HeroSection 了，交给路由去处理
</script>

<template>
  <!-- <router-view> 是一个占位符，Vue 会根据网址自动把它替换成 HomeView 或 DashboardView -->
  <router-view />
</template>

<style>
/* 全局重置样式保持不变 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'Inter', sans-serif;
  /* 移除原来的背景图设置，因为背景图现在归 HomeView 管理 */
}
</style>
