package io.stridelab.privatealpha

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Link availability only; a connected link is not proof that Supabase is reachable. */
internal class ConnectivityMonitor(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private fun connected(): Boolean = manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    private val mutable = MutableStateFlow(connected())
    val online = mutable.asStateFlow()
    private val callback = object: ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { mutable.value = connected() }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { mutable.value = connected() }
        override fun onLost(network: Network) { mutable.value = connected() }
    }
    init { manager.registerDefaultNetworkCallback(callback) }
}
