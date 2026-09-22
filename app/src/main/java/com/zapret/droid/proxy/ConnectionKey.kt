package com.zapret.droid.proxy

data class ConnectionKey(
    val srcIp: String,
    val srcPort: Int,
    val dstIp: String,
    val dstPort: Int,
    val protocol: Int  // 6=TCP, 17=UDP
)
