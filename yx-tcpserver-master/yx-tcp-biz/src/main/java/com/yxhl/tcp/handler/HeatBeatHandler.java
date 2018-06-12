package com.yxhl.tcp.handler;

import com.yxhl.domain.ConfigDO;
import com.yxhl.enums.ResultCodeEnum;
import com.yxhl.persistence.mapper.dao.ConstantDao;
import com.yxhl.protobuf.ServiceType;
import com.yxhl.protobuf.TcpRequest;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by alan on 16/4/13.
 * 处理是否登录
 */
@ChannelHandler.Sharable
@Service("heartBeatHandler")
public class HeatBeatHandler extends BaseHandler {

    private static final Logger logger = LoggerFactory.getLogger(HeatBeatHandler.class);

    @Autowired
    private ConstantDao constantRedisDao;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        logger.debug("enter loginHandler..." + msg.toString());
        try {
            // 如果是客户端发过来的心跳请求
            if (filter(msg)) {
                logger.debug("handling heart beat request..." + msg.toString());
            } else {
                logger.debug("skip heart beat request,msg=" + msg.toString());
                ctx.fireChannelRead(msg);
            }
        } catch (Exception e) {
            logger.error("handling heart beat error,", e);
            ConfigDO configDO = constantRedisDao.queryConfigByKey(e.getMessage());
            if (null == configDO) {
                configDO = constantRedisDao.queryConfigByKey(ResultCodeEnum.UNKNOWN_ERROR.getType());
            }

            // 返回结果
            ctx.writeAndFlush(genErrResp(configDO, msg));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("channel " + ctx.channel().remoteAddress() + " exception: " + cause.getMessage());
//        ctx.close();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public boolean filter(Object msg) {
        if (!(msg instanceof TcpRequest)) {
            return false;
        }

        if (ServiceType.PING.equals(((TcpRequest) msg).getServiceType())) {
            return true;
        }

        return false;
    }
}
