// src/main.js
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router' // 引入路由

const app = createApp(App)

app.use(createPinia()) // 状态管理：项目数据与调度结果跨视图共享
app.use(router) // 使用路由
app.mount('#app')
