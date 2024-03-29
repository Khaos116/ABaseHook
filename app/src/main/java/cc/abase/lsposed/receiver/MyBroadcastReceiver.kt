package cc.abase.lsposed.receiver

import android.content.*
import cc.abase.lsposed.bean.LogInfoBan
import cc.abase.lsposed.config.AppConfig
import cc.abase.lsposed.ext.launchError
import cc.abase.lsposed.livedata.AppLiveData
import cc.abase.lsposed.utils.MyUtils
import de.robv.android.xposed.XposedBridge
import kotlinx.coroutines.Dispatchers

/**
 * Author:Khaos
 * Date:2023/8/31
 * Time:17:15
 */


// 创建一个自定义的广播接收器类
class MyBroadcastReceiver : BroadcastReceiver() {
  companion object {
    const val KEY_RECEIVER_ACTION = "KEY_RECEIVER_ACTION"
    private const val KEY_RECEIVER_DATA = "KEY_RECEIVER_DATA"

    //不同进程同步消息
    fun sendBroadcastWithData(context: Context, bean: LogInfoBan) {
      launchError(Dispatchers.IO) {
        val encJson1 = encodeBroadcastData(MyUtils.toJson(bean))
        val encSize1 = MyUtils.calculateStringSize(encJson1)
        bean.dataSize = "压缩后:$encSize1"
        if (((encJson1.toByteArray(Charsets.UTF_8).size) / 1024) > 250) {//测试发现发送280会闪退，所以暂定250KB
          bean.responseBody = "响应数据太大无法正常传送，请查看打印日志，压缩后数据大小:${encSize1}"
        }
        val encJson2 = encodeBroadcastData(MyUtils.toJson(bean))
        //val encSize2 = MyUtils.calculateStringSize(encJson2)
        //XposedBridge.log("压缩后数据大小:$encSize2")
        if (((encJson2.toByteArray(Charsets.UTF_8).size) / 1024) > 250) {
          XposedBridge.log("压缩后数据大于250K，所以不发送广播")
        } else {
          context.sendBroadcast(Intent(KEY_RECEIVER_ACTION).also { it.putExtra(KEY_RECEIVER_DATA, encJson2) })
        }
      }
    }

    private fun encodeBroadcastData(s: String): String {
      return MyUtils.gzip(s)
    }

    private fun decodeBroadcastData(s: String): String {
      return MyUtils.unGzip(s)
    }
  }

  override fun onReceive(context: Context, intent: Intent) {
    if (context.packageName != AppConfig.myPackageName) return
    // 在这里处理接收到的广播
    when (intent.action) {
      KEY_RECEIVER_ACTION -> {
        intent.getStringExtra(KEY_RECEIVER_DATA)?.let { s ->
          launchError(Dispatchers.IO) {
            val decodeStr = decodeBroadcastData(s)//解压
            val info = MyUtils.fromJson(decodeStr, LogInfoBan::class.java)//解析
            val requestParams = MyUtils.unescapeJson(MyUtils.jsonFormat(info.requestParams))//JSON格式化+去转义
            val responseBody = if (info.responseBody.contains(MyUtils.gzipTag)) {
              MyUtils.unescapeJson(MyUtils.jsonFormat(MyUtils.unGzip(info.responseBody)))//GZIP解压缩+JSON格式化+去转义
            } else {
              MyUtils.unescapeJson(MyUtils.jsonFormat(info.responseBody))//JSON格式化+去转义
            }
            info.requestParams = requestParams
            info.responseBody = responseBody
            val list = AppLiveData.logAllLiveData.value
            AppLiveData.logNewLiveData.postValue(info.copy(requestParams = "", responseBody = ""))
            if (list == null) {
              AppLiveData.logAllLiveData.postValue(mutableListOf(info))
            } else {
              list.add(0, info)
              AppLiveData.logAllLiveData.postValue(list.takeLast(1000).toMutableList())//保留最新100条
            }
          }
        }
      }
    }
  }
}