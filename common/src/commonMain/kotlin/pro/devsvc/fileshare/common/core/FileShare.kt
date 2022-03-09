package pro.devsvc.fileshare.common.core

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBufUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.handler.codec.MessageToMessageDecoder
import io.netty.handler.codec.MessageToMessageEncoder
import io.netty.util.CharsetUtil
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.util.*


object FileShare {

    private val log = LoggerFactory.getLogger(javaClass)

    var id = ""
    private val serverGroup = NioEventLoopGroup()
    private val serverBootstrap = Bootstrap()

    private val clientGroup = NioEventLoopGroup()
    private val clientBootstrap = Bootstrap()

    val devices = mutableListOf<Device>()

    private fun init() {
        id = UUID.randomUUID().toString()
        serverBootstrap.group(serverGroup).channel(NioDatagramChannel::class.java)
            .option(ChannelOption.SO_BROADCAST, true)
            .handler(object: MessageToMessageEncoder<String>() {
                val addr = InetSocketAddress("255.255.255.255", 9555)
                val charset = Charset.forName("utf-8")
                override fun encode(ctx: ChannelHandlerContext?, msg: String?, out: MutableList<Any>) {
                    val bytes = ByteBufUtil.encodeString(ctx!!.alloc(), CharBuffer.wrap(msg), charset)
                    out.add(DatagramPacket(bytes, addr))
                }
            })
    }

    fun start() {
        init()
        broadCast()
        startListen()
    }

    private fun broadCast() {
        val ch = serverBootstrap.bind(0).sync().channel()
        GlobalScope.launch {
            while (true) {
                ch.writeAndFlush(id)
                delay(5000)
            }
        }
    }

    private fun startListen() {
        clientBootstrap.group(clientGroup).channel(NioDatagramChannel::class.java)
            .option(ChannelOption.SO_BROADCAST, true)
            .handler(object: MessageToMessageDecoder<DatagramPacket>() {
                override fun decode(ctx: ChannelHandlerContext?, datagramPacket: DatagramPacket, out: MutableList<Any>) {
                    val senderAddr = datagramPacket.sender()
                    val data = datagramPacket.content()
                    val msg = data.slice(0, data.readableBytes()).toString(CharsetUtil.UTF_8)
                    println("$senderAddr: $msg")
                    if (msg != id) {
                        val device = Device(msg, senderAddr.address.hostAddress, 9666, senderAddr.hostName)
                        log.info("device: {}", device)
                        if (!devices.contains(device)) {
                            devices.add(device)
                        }
                    }
                }
            })
            .localAddress(InetSocketAddress(9555))
        val ch2 = clientBootstrap.bind().sync().channel()
        ch2.closeFuture()
    }
}

data class Device (
    val id: String,
    val ip: String,
    val port: Int,
    val name: String = ""
)
