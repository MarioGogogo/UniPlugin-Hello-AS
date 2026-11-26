"use weex:vue";

if (typeof Promise !== 'undefined' && !Promise.prototype.finally) {
  Promise.prototype.finally = function(callback) {
    const promise = this.constructor
    return this.then(
      value => promise.resolve(callback()).then(() => value),
      reason => promise.resolve(callback()).then(() => {
        throw reason
      })
    )
  }
};

if (typeof uni !== 'undefined' && uni && uni.requireGlobal) {
  const global = uni.requireGlobal()
  ArrayBuffer = global.ArrayBuffer
  Int8Array = global.Int8Array
  Uint8Array = global.Uint8Array
  Uint8ClampedArray = global.Uint8ClampedArray
  Int16Array = global.Int16Array
  Uint16Array = global.Uint16Array
  Int32Array = global.Int32Array
  Uint32Array = global.Uint32Array
  Float32Array = global.Float32Array
  Float64Array = global.Float64Array
  BigInt64Array = global.BigInt64Array
  BigUint64Array = global.BigUint64Array
};


(()=>{var _=Object.create;var c=Object.defineProperty;var b=Object.getOwnPropertyDescriptor;var h=Object.getOwnPropertyNames;var m=Object.getPrototypeOf,v=Object.prototype.hasOwnProperty;var x=(e,r)=>()=>(r||e((r={exports:{}}).exports,r),r.exports);var P=(e,r,o,n)=>{if(r&&typeof r=="object"||typeof r=="function")for(let t of h(r))!v.call(e,t)&&t!==o&&c(e,t,{get:()=>r[t],enumerable:!(n=b(r,t))||n.enumerable});return e};var f=(e,r,o)=>(o=e!=null?_(m(e)):{},P(r||!e||!e.__esModule?c(o,"default",{value:e,enumerable:!0}):o,e));var p=x((k,w)=>{w.exports=Vue});var U=f(p());function g(e,r,...o){uni.__log__?uni.__log__(e,r,...o):console[e].apply(console,[...o,r])}var d=(e,r)=>{let o=e.__vccOpts||e;for(let[n,t]of r)o[n]=t;return o};var l=f(p()),y={container:{"":{flex:1,flexDirection:"column",backgroundColor:"#f5f7fa",width:"750rpx",height:100}},"webview-container":{"":{width:"750rpx",height:"800rpx",backgroundColor:"#ffffff"}},webview:{"":{flex:1}}},O={data(){return{webUrl:"",pageTitle:"",pageUrl:"",progress:0,sendData:"",logs:[],cachedPayload:null}},onLoad(e){e.url&&(this.webUrl=decodeURIComponent(e.url))},methods:{addLog(e){g("log","at pages/webview/x5-wechat.nvue:36",e)},onPageStart(e){this.addLog("\u7CFB\u7EDF","\u9875\u9762\u5F00\u59CB\u52A0\u8F7D"),this.pageUrl=e.url},onPageFinish(e){this.addLog("\u7CFB\u7EDF","\u9875\u9762\u52A0\u8F7D\u5B8C\u6210"),this.pageTitle=e.title,this.progress=100},onPageError(e){this.addLog("\u9519\u8BEF","\u9875\u9762\u52A0\u8F7D\u5931\u8D25")},onJsMessage(e){g("log","at pages/webview/x5-wechat.nvue:52","H5",`\u6536\u5230\u6D88\u606F\uFF1A${JSON.stringify(e.detail)}`)},onProgress(e){this.progress=e.detail.progress,g("log","at pages/webview/x5-wechat.nvue:57","\u52A0\u8F7D\u8FDB\u5EA6\uFF1A"+e.detail.progress+"%")}}};function S(e,r,o,n,t,s){return(0,l.openBlock)(),(0,l.createElementBlock)("scroll-view",{scrollY:!0,showScrollbar:!0,enableBackToTop:!0,bubble:"true",style:{flexDirection:"column"}},[(0,l.createElementVNode)("div",{class:"container"},[(0,l.createElementVNode)("X5WebView",{ref:"webView",class:"webview",src:t.webUrl,onOnpagestart:r[0]||(r[0]=(...a)=>s.onPageStart&&s.onPageStart(...a)),onOnpagefinish:r[1]||(r[1]=(...a)=>s.onPageFinish&&s.onPageFinish(...a)),onOnpageerror:r[2]||(r[2]=(...a)=>s.onPageError&&s.onPageError(...a)),onOnjsmessage:r[3]||(r[3]=(...a)=>s.onJsMessage&&s.onJsMessage(...a)),onOnprogress:r[4]||(r[4]=(...a)=>s.onProgress&&s.onProgress(...a))},null,40,["src"])])])}var i=d(O,[["render",S],["styles",[y]]]);var u=plus.webview.currentWebview();if(u){let e=parseInt(u.id),r="pages/webview/x5-wechat",o={};try{o=JSON.parse(u.__query__)}catch(t){}i.mpType="page";let n=Vue.createPageApp(i,{$store:getApp({allowDefault:!0}).$store,__pageId:e,__pagePath:r,__pageQuery:o});n.provide("__globalStyles",Vue.useCssStyles([...__uniConfig.styles,...i.styles||[]])),n.mount("#root")}})();
