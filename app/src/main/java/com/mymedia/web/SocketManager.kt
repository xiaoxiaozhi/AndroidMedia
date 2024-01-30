package com.mymedia.web

import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.lang.Exception
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import javax.inject.Inject

/**
 *   webSocket.send("Welcome to the server!"); //给客户端发送信息
 *   broadcast("new connection: " + handshake?.resourceDescriptor);给所有的客户端发送信息
 */
class SocketManager @Inject constructor(private val threadPools: ExecutorService) {

    private val server: WebSocketServer by lazy {
        object : WebSocketServer(InetSocketAddress(1819)) {

            override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
                println("WebSocketServer------onOpen,")
                println("new connection:-----${handshake?.resourceDescriptor}")
                conn?.apply {
                    Log.i(
                        this@SocketManager.javaClass.simpleName,
                        "客户端IP" + conn.remoteSocketAddress.address.hostAddress
                    )
                }
            }

            override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
                println("WebSocketServer------onError connections size=${connections.size}")//TODO 客户端关闭，会自己调整连接数吗

            }

            override fun onMessage(conn: WebSocket?, message: String?) {
                println("WebSocketServer------onError")
            }

            override fun onError(conn: WebSocket?, ex: Exception?) {
                println("WebSocketServer------onError=${ex?.message}")
            }

            override fun onStart() {
                println("WebSocketServer------onStart")
                this.draft
                println("WebSocketServer-----ip=${this.address.hostString}---port=${this.address.port}")
            }
        }
    }

    init {
        threadPools.execute {
            server.start()
            server.connections
        }
    }

    public fun sendFrame(bytes: ByteArray) {
        if (server.connections.isNotEmpty()) {
            server.broadcast(bytes)
        } else {
            Log.i("SocketManager", "server.connections size < 0")
        }
    }

    public fun senText() {
        if (server.connections.isNotEmpty()) {
            server.broadcast(8 as ByteArray)
//            server.broadcast("Welcome to the server!")
        }

    }

}