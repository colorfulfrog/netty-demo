package com.yxhl.tcp.handler;

import com.yxhl.domain.BizOrderDO;
import com.yxhl.domain.ConfigDO;
import com.yxhl.domain.DriverDO;
import com.yxhl.domain.TaskDO;
import com.yxhl.enums.ResultCodeEnum;
import com.yxhl.exception.YxBizException;
import com.yxhl.persistence.mapper.BizOrderMapper;
import com.yxhl.persistence.mapper.DriverMapper;
import com.yxhl.persistence.mapper.TaskMapper;
import com.yxhl.persistence.mapper.dao.ConstantDao;
import com.yxhl.protobuf.ClientType;
import com.yxhl.protobuf.ServiceType;
import com.yxhl.protobuf.TcpRequest;
import com.yxhl.util.LogUtil;
import com.yxhl.util.LoginCheck;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.yxhl.enums.ResultCodeEnum.LOGIN_INVALID;
import static com.yxhl.enums.ResultCodeEnum.REQUEST_ERROR;
import static com.yxhl.tcp.server.YxBizServer.allChannels;

/**
 * Created by alan on 16/4/13.
 * 处理是否登录
 */
@ChannelHandler.Sharable
@Service("loginHandler")
public class LoginHandler extends BaseHandler {

    private static final Logger logger = LoggerFactory.getLogger(LoginHandler.class);

    @Autowired
    private LoginCheck loginCheck;

    @Autowired
    private ConstantDao constantRedisDao;

    @Autowired
    private BizOrderMapper bizOrderMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private DriverMapper driverMapper;
/*    @Autowired
    private ChannelMapper channelMapper;*/


//    @Override
//    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
//        //ChannelManager.getInstance().removeChannel((SocketChannel) ctx.channel());
//    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        allChannels.add(ctx.channel());
        //String id = ctx.channel().id().asLongText();
       // ChannelService.addGatewayChannel(id, (io.netty.channel.socket.SocketChannel) ctx.channel());
        logger.info("Now Connected Channels number:" + allChannels.size());

        // A closed Channel is automatically removed from ChannelGroup,
        // so there is no need to do "channels.remove(ctx.channel());"
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        logger.debug("enter loginHandler..." + msg.toString());
        try {
            // 如果已登录,则继续下一个handler,否则会抛出异常,直接release
            if (filter(msg)) {
                TcpRequest request = (TcpRequest) msg;
                //channel存入表中
              //  insertChannel(ctx, request);

                //得到需要的唯一主键,存入map
//                ClientType clientType = request.getClientType();
//                if (clientType == ClientType.USER) {
//                    //查处订单信息
//                    BizOrderDO bizOrderDO = bizOrderMapper.queryBizOrderByOrderSerial(request.getBizOrder().getOrderSerial());
//                    String usermobile=bizOrderDO.getMobile();
//                    if(StringUtils.isNotEmpty(usermobile))
//                        ChannelManager.getInstance().addChannel(usermobile,(SocketChannel)ctx.channel());
//                }
//
//                if (clientType == ClientType.DRIVER) {
//                    String drivermobile = null;
//                    //查出任务信息
//                    TaskDO taskDO = taskMapper.findTaskById(request.getTask().getTaskId());
//                    if (taskDO == null){
//                        DriverDO driverDO = driverMapper.queryDriverById(request.getTask().getDriverId()); //司机上传经纬度修改
//                        drivermobile = driverDO.getMobile();
//                    } else {
//                        drivermobile = taskDO.getDriverMobile();
//                    }
//                    if(StringUtils.isNotEmpty(drivermobile))
//                        ChannelManager.getInstance().addChannel(drivermobile,(SocketChannel)ctx.channel());
//                }


                logger.debug("start handling login biz..." + msg.toString());
                ctx.fireChannelRead(request);
            } else {
                logger.error("request is not tcpRequest type,please check! msg=" + msg.toString());
                throw new YxBizException(ResultCodeEnum.REQUEST_TYPE_ERROR.getType());
            }
        } catch (Exception e) {
            logger.error("handling login error,", e);
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
       // ctx.close();
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

        TcpRequest request = (TcpRequest) msg;

        // 判断serviceTyp是否为空
        ServiceType serviceType = request.getServiceType();
        checkArgument(serviceType.getNumber() != ServiceType.ST_DEFAULT_VALUE, "SERVICE_TYPE_IS_NULL");

        // ping直接返回OK
        if (ServiceType.PING_VALUE == request.getServiceTypeValue()) {
            return true;
        }

        String token = request.getToken(), account = null;
        Long taskId = null;

        ClientType clientType = request.getClientType();
        checkNotNull(clientType, "CLIENT_TYPE_IS_NULL");

        // 判断token是否存在,如果不存在,则直接抛异常,否则走到下一步
        if (clientType == ClientType.USER) {
            checkNotNull(request.getBizOrder(), REQUEST_ERROR.getType());
            account = request.getBizOrder().getMobile();
            //taskId = request.getBizOrder().getTaskId();
        }

        if (clientType == ClientType.DRIVER) {
            checkNotNull(request.getTask(), REQUEST_ERROR.getType());
            account = request.getTask().getDriverId() + "";
            //taskId = request.getTask().getTaskId();
        }

        // 判断token clientType account是否为空
        checkArgument(StringUtils.isNoneBlank(token, account),
                LOGIN_INVALID.getType());


        // add loginfo
        LogUtil.setBizIdWithClear(account, taskId + "", serviceType.name());

        // 判断是否登录了
        checkArgument(loginCheck.loginInfoCheck(account, token, clientType.name()),
                LOGIN_INVALID.getType());


        return true;

    }

    /**
     * 把channel存起来
     */
//    private void insertChannel(ChannelHandlerContext ctx, TcpRequest tcpRequest) {
//        ChannelDO channelDO = new ChannelDO();
//        if (tcpRequest.getClientType() == ClientType.DRIVER) {
//            channelDO.setKeyWord(tcpRequest.getBizOrder().getTaskId() + "");
//        }
//        if (tcpRequest.getClientType() == ClientType.USER) {
//            channelDO.setKeyWord(tcpRequest.getBizOrder().getMobile());
//        }
//        channelDO.setServiceType(tcpRequest.getServiceType().name())
//                .setChannelId(ctx.channel().id().asLongText())
//                .setStatus(1)
//                .setVersion(1);
//        channelMapper.insert(channelDO);
//
//    }
}
