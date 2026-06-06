<script setup>
import { useRouter } from 'vue-router';

const router = useRouter();

const handleGetStarted = () => {
  // 1. 读取全局选中的项目 ID
  const activeId = localStorage.getItem('activeProjectId');
  
  if (activeId) {
    // 2. 如果已选中，直接进入该项目的运行界面
    router.push(`/dashboard/${activeId}`);
  } else {
    // 3. 如果没选，去项目列表页让用户选
    // 你可以加一个 alert("Please select a project first")，或者直接跳转让用户自己悟
    router.push('/projects');
  }
};
</script>

<template>
  <section class="hero">
    <!-- 粉色胶囊标签 -->
    <div class="pill-badge">
      RCPSP
    </div>
    <!-- 大标题 -->
    <h1 class="headline">
      Construction resource scheduling system
    </h1>
    <!-- 副标题 -->
    <p class="sub-headline">
      More efficient solutions!
    </p>
    <!-- 按钮：绑定了 handleGetStarted 逻辑 -->
    <button class="cta-button" @click="handleGetStarted">Get started</button>
  </section>
</template>

<style scoped>
.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  margin-top: 80px;
  text-align: center;
  padding: 50px 20px;
  position: relative;
  margin: 80px 20px 0;
  /* 渐变遮罩打底 */
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.15), rgba(0, 0, 0, 0.3));
  backdrop-filter: blur(3px); /* 轻量毛玻璃 */
  border-radius: 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
}
/* 粉色胶囊 */
.pill-badge {
  background-color: #458BE0;
  color: #ffffff;
  font-size: 14px;
  font-weight: 500;
  padding: 10px 24px;
  border-radius: 50px;
  margin-bottom: 30px;
  font-family: 'Inter', sans-serif;
  animation: fadeIn 1s ease-in-out;
  box-shadow: 0 4px 12px rgba(69, 139, 224, 0.2);
}
/* 核心大标题 */
.headline {
  font-family: 'Source Serif 4', serif;
  font-size: 72px;
  line-height: 1.1;
  font-weight: 400;
  margin-bottom: 30px;
  color: #ffffff;
  letter-spacing: -1px;
  -webkit-text-stroke: 0.5px rgba(0, 0, 0, 0.3);
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.5);
}
/* 副标题 */
.sub-headline {
  font-family: 'Inter', sans-serif;
  font-size: 20px;
  line-height: 1.6;
  color: #ffffff;
  max-width: 600px;
  margin-bottom: 40px;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.4);
}
/* 黑色大按钮 */
.cta-button {
  background-color: #000000;
  color: white;
  border: none;
  font-size: 16px;
  font-weight: 600;
  padding: 16px 32px;
  border-radius: 30px;
  cursor: pointer;
  transition: all 0.2s;
}
.cta-button:hover {
  transform: translateY(-2px);
  background-color: #000000;
  box-shadow: 0 8px 16px rgba(69, 139, 224, 0.3);
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
@media (max-width: 768px) {
  .headline {
    font-size: 48px;
    -webkit-text-stroke: 0.3px rgba(0, 0, 0, 0.3);
  }
  .sub-headline {
    font-size: 16px;
  }
  .hero {
    padding: 30px 15px;
    margin: 40px 10px 0;
    backdrop-filter: blur(2px);
  }
}
</style>
