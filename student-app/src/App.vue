<script setup lang="ts">
import { onLaunch, onShow, onHide } from "@dcloudio/uni-app";
onLaunch(() => {
  console.log("App Launch");
});
onShow(() => {
  console.log("App Show");
});
onHide(() => {
  console.log("App Hide");
});
</script>
<style>
/* 全局按钮样式：uni-app 的 <button type> 仅接受 form 语义取值，统一改用 class 控制配色 */
.btn-primary {
  background-color: #3f9b3f;
  color: #ffffff;
}
.btn-primary[disabled] {
  background-color: #a9cfa9;
  color: #ffffff;
}
.btn-warn {
  background-color: #e6a23c;
  color: #ffffff;
}
</style>
