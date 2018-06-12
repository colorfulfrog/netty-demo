package com.yxhl.tcp.handler;

import com.yxhl.domain.ConfigDO;
import com.yxhl.enums.ResultCodeEnum;
import com.yxhl.persistence.mapper.dao.ConstantDao;
import com.yxhl.protobuf.*;
import com.yxhl.tcp.manager.BizOrderManager;
import com.yxhl.util.LogUtil;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by alan on 16/4/13.
 * 用户查询巴士或快车\接驳车的位置
 */
@ChannelHandler.Sharable
@Service("userQueryLocHandler")
public class UserQueryLocHandler extends BaseHandler {

    private final static Logger logger = LoggerFactory.getLogger(UserQueryLocHandler.class);

    @Autowired
    private BizOrderManager bizOrderManagerTcp;

    @Autowired
    private ConstantDao constantRedisDao;

    /**
     * channelActive表示当前通道已经可用不过还未从scoket中接受到数据, 不过此时已经可以向通道发送响应信息。
     * 不过如果需要直接发送,则需要记录socketId及对应的账户\订单信息
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {

    }

    /**
     * channelRead表示从scoket中读取到数据, 一般来说就是客户端向服务器端发起的调用信息，
     * 服务器端程序根据调用信息进行不同的逻辑处理并在比方法中将响应信息返回给客户端。
     *
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        try {
            if (filter(msg)) {
                logger.info("enter UserQueryLocHandler...." + msg.toString());
                BizOrderResponse.Builder bizOrderResponseBuilder = BizOrderResponse.newBuilder();
                BizOrderRequest bizOrderRequest = ((TcpRequest) msg).getBizOrder();

                checkNotNull(bizOrderRequest.getOrderSerial(),
                        ResultCodeEnum.CHECK_NULL_ERROR.getType());
                //打点
                LogUtil.setBizId(bizOrderRequest.getMobile(), bizOrderRequest.getOrderSerial() + "");
                logger.debug("user query location for car-request:" + bizOrderRequest.toString());

                // 查询位置,需要改动成同时兼容巴士和快车\接驳车的模式
                bizOrderManagerTcp.queryLocation(bizOrderResponseBuilder, bizOrderRequest);

                TcpResponse.Builder tcpResponseBuilder = genNormalResp(msg);
                tcpResponseBuilder.setBizOrderResp(bizOrderResponseBuilder)
                        .setIsSucc(true);

                logger.info("response:" + tcpResponseBuilder.build());
                ctx.write(tcpResponseBuilder.build());
            } else {
                logger.debug("skip user query location handler,msg=" + msg.toString());
                ctx.fireChannelRead(msg);
            }
        } catch (Exception e) {
            logger.error("user query location error,msg=" + msg + ",error:", e);

            ConfigDO configDO = constantRedisDao.queryConfigByKey(e.getMessage());
            if (null == configDO) {
                configDO = constantRedisDao.queryConfigByKey(ResultCodeEnum.UNKNOWN_ERROR.getType());
            }
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

        if (ServiceType.USER_QUERY_LOC.equals(((TcpRequest) msg).getServiceType())) {
            return true;
        }

        return false;
    }
}
