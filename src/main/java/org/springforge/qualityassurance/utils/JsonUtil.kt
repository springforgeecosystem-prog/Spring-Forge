package org.springforge.qualityassurance.utils

import com.google.gson.Gson

object JsonUtil {
    private val gson = Gson()

    fun toJson(obj: Any): String = gson.toJson(obj)

    fun <T> fromJson(json: String, clazz: Class<T>): T = gson.fromJson(json, clazz)
}
