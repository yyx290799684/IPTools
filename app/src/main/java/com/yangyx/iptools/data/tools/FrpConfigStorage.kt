package com.yangyx.iptools.data.tools

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object FrpConfigStorage {

    private const val PREF_NAME = "frp_config_preferences"
    private const val KEY_SERVER_CONFIG = "key_frp_server_config"
    private const val KEY_CLIENT_CONFIG = "key_frp_client_config"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun loadServerConfig(context: Context): FrpServerConfig {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_SERVER_CONFIG, null) ?: return FrpServerConfig()
        return try {
            val obj = JSONObject(jsonStr)
            FrpServerConfig(
                bindPort = obj.optInt("bindPort", 0),
                authToken = obj.optString("authToken", ""),
                dashboardPort = obj.optInt("dashboardPort", 0),
                maxPoolCount = obj.optInt("maxPoolCount", 0)
            )
        } catch (e: Exception) {
            FrpServerConfig()
        }
    }

    fun saveServerConfig(context: Context, config: FrpServerConfig) {
        try {
            val obj = JSONObject().apply {
                put("bindPort", config.bindPort)
                put("authToken", config.authToken)
                put("dashboardPort", config.dashboardPort)
                put("maxPoolCount", config.maxPoolCount)
            }
            getPrefs(context).edit().putString(KEY_SERVER_CONFIG, obj.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadClientConfig(context: Context): FrpClientConfig {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_CLIENT_CONFIG, null) ?: return FrpClientConfig(proxies = emptyList())
        return try {
            val obj = JSONObject(jsonStr)
            val serverAddr = obj.optString("serverAddr", "")
            val serverPort = obj.optInt("serverPort", 0)
            val authToken = obj.optString("authToken", "")
            val proxiesArray = obj.optJSONArray("proxies") ?: JSONArray()
            val proxiesList = mutableListOf<FrpProxyConfig>()

            for (i in 0 until proxiesArray.length()) {
                val pObj = proxiesArray.getJSONObject(i)
                val pTypeStr = pObj.optString("type", "TCP")
                val pType = try { FrpProxyType.valueOf(pTypeStr) } catch (_: Exception) { FrpProxyType.TCP }

                proxiesList.add(
                    FrpProxyConfig(
                        id = pObj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = pObj.optString("name", ""),
                        type = pType,
                        localIp = pObj.optString("localIp", ""),
                        localPort = pObj.optInt("localPort", 0),
                        remotePort = pObj.optInt("remotePort", 0),
                        socksUser = pObj.optString("socksUser", ""),
                        socksPass = pObj.optString("socksPass", ""),
                        isEnabled = pObj.optBoolean("isEnabled", true)
                    )
                )
            }

            FrpClientConfig(
                serverAddr = serverAddr,
                serverPort = serverPort,
                authToken = authToken,
                proxies = proxiesList
            )
        } catch (e: Exception) {
            FrpClientConfig(proxies = emptyList())
        }
    }

    fun saveClientConfig(context: Context, config: FrpClientConfig) {
        try {
            val proxiesArray = JSONArray()
            config.proxies.forEach { proxy ->
                val pObj = JSONObject().apply {
                    put("id", proxy.id)
                    put("name", proxy.name)
                    put("type", proxy.type.name)
                    put("localIp", proxy.localIp)
                    put("localPort", proxy.localPort)
                    put("remotePort", proxy.remotePort)
                    put("socksUser", proxy.socksUser)
                    put("socksPass", proxy.socksPass)
                    put("isEnabled", proxy.isEnabled)
                }
                proxiesArray.put(pObj)
            }

            val obj = JSONObject().apply {
                put("serverAddr", config.serverAddr)
                put("serverPort", config.serverPort)
                put("authToken", config.authToken)
                put("proxies", proxiesArray)
            }

            getPrefs(context).edit().putString(KEY_CLIENT_CONFIG, obj.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
