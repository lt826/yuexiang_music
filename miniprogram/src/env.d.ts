/// <reference types="@dcloudio/types" />
/// <reference types="vite/client" />

/** 编译时常量：vite.config.ts 编译时探测本机局域网 IP 注入（见 injectBaseUrl 插件） */
declare const __MP_BASE_URL__: string

declare module '*.vue' {
  import { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}
