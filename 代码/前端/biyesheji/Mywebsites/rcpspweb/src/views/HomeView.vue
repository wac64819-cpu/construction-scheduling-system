<!-- src/views/HomeView.vue -->
<script setup>
import Navbar from '../components/Navbar.vue';
import HeroSection from '../components/HeroSection.vue';
</script>

<template>
  <div class="home-container">
    <Navbar />
    <HeroSection />
  </div>
</template>

<style scoped>
/* 把原来 App.vue 里的背景图样式搬到这里 */
.home-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  /* 背景图路径可能需要根据你的实际情况调整，比如 @/assets/bg.jpg */
  background-image: url('@/assets/bg.jpg'); 
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  background-attachment: fixed;
}
</style>