package com.yxhl.tcp.server;

import com.yxhl.protobuf.TcpRequest;
import com.yxhl.tcp.constants.TcpServerConstants;
import com.yxhl.tcp.handler.*;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Created by alan on 16/4/22.
 */
@Service("bizChannelInitializer")
public class YxBizChannelInitializer extends ChannelInitializer<SocketChannel> {

    @Autowired
    private DriverQueryTaskHandler driverQueryTaskHandler;

    @Autowired
    private LoginHandler loginHandler;

    @Autowired
    private UserQueryLocHandler userQueryLocHandler;

    @Autowired
    private UploadLocationHandler uploadLocationHandler;

    @Autowired
    private ProtobufVarint32LengthFieldPrepender protobufLengthEncoder;

    @Autowired
    private ProtobufEncoder protobufEncoder;

    @Autowired
    private IdleHandler idleHandler;

    @Autowired
    private HeatBeatHandler heartBeatHandler;

    private int port;

    public int getPort() {
        return port;
    }

    public YxBizChannelInitializer setPort(int port) {
        this.port = port;
        return this;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        //ios不需要压缩-解压
        if (port == TcpServerConstants.ANDORID_PORT) {
            // 压缩解压
            ch.pipeline()
                    .addLast("gzipDecoder", ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP))
                    .addLast("gzipEncoder", ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP));
        }
        // add log print info
        ch.pipeline()
                .addLast("logPrint", new LoggingHandler(LogLevel.INFO))
                // 再处理protobuf与对象的转换
                .addLast("protobufFrameDecoder", new ProtobufVarint32FrameDecoder())

                // 服务端负责请求处理,客户端则需要负责响应处理
                .addLast("protobufDecoder", new ProtobufDecoder(TcpRequest.getDefaultInstance()))

                .addLast("protobufLengthEncoder", protobufLengthEncoder)
                .addLast("protobufEncoder", protobufEncoder)

                /** 处理心跳相关 **/
                // 超时处理：参数分别为读超时时间、写超时时间、读和写都超时时间、时间单位
                // 如果获取到IdleState.ALL_IDLE则定时向客户端发送心跳包；
                // 客户端在业务逻辑的Handler里面，如果接到心跳包，则向服务器发送一个心跳反馈；
                // 服务端如果长时间没有接受到客户端的信息，即IdleState.READER_IDLE被触发，则关闭当前的channel。
                .addLast("idleStateHandler", new IdleStateHandler(250, 120, 30, TimeUnit.SECONDS))
                .addLast("idleHandler", idleHandler)

                /** 业务处理的handler都写在下面 **/
                .addLast("heartBeatHandler", heartBeatHandler)
                .addLast("loginHandler", loginHandler)
                .addAfter("loginHandler", "uploadLocHandler", uploadLocationHandler)
                .addAfter("loginHandler", "userQueryLocHandler", userQueryLocHandler)
                .addAfter("loginHandler", "driverQueryTaskHandler", driverQueryTaskHandler);

    }
}
