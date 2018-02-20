package com.glureau.wolfram30.chat.util

import android.content.Context
import android.net.ConnectivityManager
import android.widget.Toast

/**
 * Created by Alessandro Barreto on 23/06/2016.
 */
object Util {

    val URL_STORAGE_REFERENCE = "gs://war30-chat.appspot.com"
    val FOLDER_STORAGE_IMG = "images"

    fun initToast(c: Context, message: String) {
        Toast.makeText(c, message, Toast.LENGTH_SHORT).show()
    }

    fun checkConnection(context: Context): Boolean {
        val isConnected: Boolean
        val conectivtyManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        isConnected = (conectivtyManager.activeNetworkInfo != null
                && conectivtyManager.activeNetworkInfo.isAvailable
                && conectivtyManager.activeNetworkInfo.isConnected)
        return isConnected
    }

    fun local(latitudeFinal: String, longitudeFinal: String): String {
        return "https://maps.googleapis.com/maps/api/staticmap?center=$latitudeFinal,$longitudeFinal&zoom=18&size=280x280&markers=color:red|$latitudeFinal,$longitudeFinal"
    }

}
