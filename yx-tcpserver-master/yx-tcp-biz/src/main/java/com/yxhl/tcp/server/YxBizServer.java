package com.yxhl.tcp.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by alan on 16/4/12.
 * tcp服务主入口
 */
@Service("yxBizServer")
public class YxBizServer {

    private final static Logger logger = LoggerFactory.getLogger(YxBizServer.class);

    @Autowired
    private YxBizChannelInitializer bizChannelInitializer;

    // 所有的channel都放在这里,用于监控及关闭
    public static final ChannelGroup allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    /**
     * 端口
     */
    private int port;

    public int getPort() {
        return port;
    }

    public YxBizServer setPort(int port) {
        this.port = port;
        return this;
    }

    private EventLoopGroup bossGroup;

    private EventLoopGroup workerGroup;

    public YxBizServer(int port) {
        this.port = port;
    }

    public YxBizServer() {
    }

    /**
     * 启动服务
     *
     * @throws Exception
     */
    public void run() throws Exception {
        logger.info("start yx netty tcp server......" + port);
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();

            bizChannelInitializer.setPort(port);

            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(bizChannelInitializer)
                    // 减少网络延迟
                    .option(ChannelOption.TCP_NODELAY, true)
                    // 加入内存池,不过此内存池不依赖gc,故必须每次都手动release对应的连接
                    //如果使用内存池，完成ByteBuf的解码工作之后必须显式的
                    // 调用ReferenceCountUtil.release(msg)对接收缓冲区ByteBuf进行内存释放，
                    // 否则它会被认为仍然在使用中，这样会导致内存泄露。
                    .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            // A ChannelFuture represents an I/O operation which has not yet occurred.
            ChannelFuture future = bootstrap.bind(port).sync();

            future.channel().closeFuture().sync();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    /**
     * 停止服务
     */
    public void stop() {
        logger.info("stopping yx netty tcp server......");
        if (null != workerGroup) {
            workerGroup.shutdownGracefully();
            logger.info("stopped workerGroup thread pool.");
        }

        if (null != bossGroup) {
            bossGroup.shutdownGracefully();
            logger.info("stopped bossGroup thread pool.");
        }

        allChannels.close().awaitUninterruptibly();

        try {
            Thread.sleep(5000l);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        logger.info("stopped yx netty tcp server!!!");
    }
}
