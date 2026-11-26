<template>
	<view class="page">
		<view class="section">
			<view class="section-title">① 环境检测</view>
			<view class="row">
				<button type="primary" size="mini" @click="checkSupport">检测打印支持</button>
				<button type="default" size="mini" @click="getServices">打印服务列表</button>
			</view>
		</view>

		<view class="section">
			<view class="section-title">② 打印图片</view>
			<view class="row">
				<button type="primary" size="mini" @click="chooseImage">从相册选择图片打印</button>
			</view>
			<view class="row">
				<input class="input" v-model="imageUrl" placeholder="网络图片 URL，如 https://xxx/a.png" />
				<button type="warn" size="mini" @click="printNetworkImage">打印</button>
			</view>
			<view class="tip">支持：本地路径 / file:// / content:// / http(s):// / base64 / assets://</view>
		</view>

		<view class="section">
			<view class="section-title">③ 打印 PDF</view>
			<view class="row">
				<input class="input" v-model="pdfUrl" placeholder="PDF URL，如 https://xxx/a.pdf" />
				<button type="warn" size="mini" @click="downloadAndPrintPdf">下载并打印</button>
			</view>
			<view class="tip">也可直接传 filePath 为本地 PDF 绝对路径</view>
		</view>

		<view class="section">
			<view class="section-title">④ 打印网页 / HTML</view>
			<view class="row">
				<input class="input" v-model="webUrl" placeholder="网页 URL，如 https://www.baidu.com" />
				<button type="warn" size="mini" @click="printWebPage">打印网页</button>
			</view>
			<textarea class="textarea" v-model="htmlContent" placeholder="HTML 内容（与 URL 二选一）"></textarea>
			<button type="warn" size="mini" @click="printHtml">打印 HTML</button>
		</view>

		<view class="section">
			<view class="section-title">⑤ 任务管理</view>
			<view class="row">
				<button type="default" size="mini" @click="getJobs">查看任务</button>
			</view>
			<view class="row">
				<input class="input" v-model="cancelJobId" placeholder="要取消的 jobId" />
				<button type="warn" size="mini" @click="cancelJob">取消任务</button>
			</view>
		</view>

		<view class="log">
			<view class="section-title">打印日志（含任务状态回调）</view>
			<scroll-view scroll-y class="log-scroll">
				<text class="log-line" v-for="(line, i) in logs" :key="i">{{ line }}</text>
			</scroll-view>
		</view>
	</view>
</template>

<script>
	export default {
		data() {
			return {
				printer: null,
				imageUrl: '',
				pdfUrl: '',
				webUrl: 'https://www.baidu.com',
				htmlContent: '<h1>测试打印</h1><p>Hello UniPrinter!</p>',
				cancelJobId: '',
				logs: []
			}
		},
		onReady() {
			this.printer = uni.requireNativePlugin('UniPrinterModule')
			this.log('插件已加载')
		},
		methods: {
			// ---------------- 工具 ----------------
			log(msg) {
				const time = new Date().toLocaleTimeString()
				this.logs.unshift('[' + time + '] ' + msg)
				if (this.logs.length > 100) {
					this.logs.pop()
				}
			},
			// plus 路径（如 _doc/xxx）转换为 file:/// 绝对路径
			toRealPath(p) {
				if (typeof plus !== 'undefined') {
					return plus.io.convertLocalFileSystemURL(p)
				}
				return p
			},
			// 打印任务的统一回调：会收到多次状态更新
			handlePrintCallback(res) {
				if (res.code === 0) {
					this.log('[任务 ' + res.jobId + '] ' + res.state + ' - ' + res.message)
					if (res.isFinal) {
						this.log('==> 任务结束，不再回调')
					}
				} else {
					this.log('错误: ' + res.message)
				}
			},

			// ---------------- 环境检测 ----------------
			checkSupport() {
				this.printer.checkSupport((res) => {
					this.log('支持: ' + res.supported + '，打印服务数: ' + res.printServiceCount)
					this.log('提示: ' + res.message)
				})
			},
			getServices() {
				this.printer.getPrintServices((res) => {
					if (res.code !== 0) {
						this.log('查询失败: ' + res.message)
						return
					}
					if (res.count === 0) {
						this.log('未安装任何打印服务，请安装 Mopria 等打印服务后重试')
						return
					}
					res.services.forEach((s) => {
						this.log('打印服务: ' + s.label + (s.enabled ? '（已启用）' : '（未启用）'))
					})
				})
			},

			// ---------------- 打印图片 ----------------
			chooseImage() {
				uni.chooseImage({
					count: 1,
					success: (res) => {
						const path = this.toRealPath(res.tempFilePaths[0])
						this.log('选择的图片: ' + path)
						this.printer.printImage({
							imagePath: path,
							jobName: '相册图片打印',
							pageSize: 'A4',
							fitMode: 'FIT'
						}, this.handlePrintCallback)
					},
					fail: (err) => {
						this.log('选择图片失败: ' + JSON.stringify(err))
					}
				})
			},
			printNetworkImage() {
				if (!this.imageUrl) {
					this.log('请先输入图片 URL')
					return
				}
				this.printer.printImage({
					imagePath: this.imageUrl,
					jobName: '网络图片打印'
				}, this.handlePrintCallback)
			},

			// ---------------- 打印 PDF ----------------
			downloadAndPrintPdf() {
				if (!this.pdfUrl) {
					this.log('请先输入 PDF URL')
					return
				}
				uni.showLoading({ title: '下载中...' })
				uni.downloadFile({
					url: this.pdfUrl,
					success: (res) => {
						uni.hideLoading()
						if (res.statusCode !== 200) {
							this.log('下载失败: HTTP ' + res.statusCode)
							return
						}
						const path = this.toRealPath(res.tempFilePath)
						this.log('PDF 已下载: ' + path)
						this.printer.printPdf({
							filePath: path,
							jobName: 'PDF打印',
							pageSize: 'A4'
						}, this.handlePrintCallback)
					},
					fail: (err) => {
						uni.hideLoading()
						this.log('下载失败: ' + JSON.stringify(err))
					}
				})
			},

			// ---------------- 打印网页 / HTML ----------------
			printWebPage() {
				if (!this.webUrl) {
					this.log('请先输入网页 URL')
					return
				}
				this.printer.printWebPage({
					url: this.webUrl,
					jobName: '网页打印',
					loadTimeoutMs: 30000
				}, this.handlePrintCallback)
			},
			printHtml() {
				if (!this.htmlContent) {
					this.log('请先输入 HTML 内容')
					return
				}
				this.printer.printWebPage({
					html: this.htmlContent,
					jobName: 'HTML打印'
				}, this.handlePrintCallback)
			},

			// ---------------- 任务管理 ----------------
			getJobs() {
				this.printer.getPrintJobs((res) => {
					if (res.code !== 0) {
						this.log('查询失败: ' + res.message)
						return
					}
					if (res.count === 0) {
						this.log('当前没有打印任务')
						return
					}
					res.jobs.forEach((j) => {
						this.log('任务: ' + j.id + ' | ' + j.label + ' | ' + j.state)
					})
				})
			},
			cancelJob() {
				if (!this.cancelJobId) {
					this.log('请先输入 jobId')
					return
				}
				this.printer.cancelPrintJob({
					jobId: this.cancelJobId
				}, (res) => {
					this.log(res.message)
				})
			}
		}
	}
</script>

<style>
	.page {
		padding: 20rpx;
	}

	.section {
		margin-bottom: 30rpx;
		padding: 20rpx;
		background: #ffffff;
		border-radius: 12rpx;
	}

	.section-title {
		font-size: 28rpx;
		font-weight: bold;
		margin-bottom: 16rpx;
	}

	.row {
		display: flex;
		align-items: center;
		margin-bottom: 12rpx;
	}

	.input {
		flex: 1;
		height: 64rpx;
		border: 1rpx solid #dddddd;
		border-radius: 8rpx;
		padding: 0 16rpx;
		font-size: 26rpx;
		margin-right: 12rpx;
	}

	.textarea {
		width: 100%;
		height: 160rpx;
		border: 1rpx solid #dddddd;
		border-radius: 8rpx;
		padding: 12rpx;
		font-size: 26rpx;
		margin-bottom: 12rpx;
		box-sizing: border-box;
	}

	.tip {
		font-size: 22rpx;
		color: #999999;
		margin-top: 8rpx;
	}

	.log {
		padding: 20rpx;
		background: #1e1e1e;
		border-radius: 12rpx;
	}

	.log .section-title {
		color: #cccccc;
	}

	.log-scroll {
		height: 400rpx;
	}

	.log-line {
		display: block;
		font-size: 22rpx;
		color: #7ecb20;
		margin-bottom: 6rpx;
		word-break: break-all;
	}
</style>
