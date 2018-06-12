package com.yxhl.tcp.handler;

import com.yxhl.domain.ConfigDO;
import com.yxhl.protobuf.TcpRequest;
import com.yxhl.protobuf.TcpResponse;
import io.netty.channel.ChannelHandlerAdapter;

/**
 * Created by alan on 16/4/13.
 * 接口
 */
public abstract class BaseHandler extends ChannelHandlerAdapter {

    /**
     * @param msg
     * @return 判断是否当前handler是否可以进入, 还是直接进入到下一个handler
     */
    abstract boolean filter(Object msg);

    /**
     * 处理返回给客户端的异常结果数据
     *
     * @param configDO
     * @param msg
     * @return
     */
    TcpResponse genErrResp(ConfigDO configDO, Object msg) {

        // 组建异常响应结果
        TcpResponse.Builder responseBuilder = TcpResponse.newBuilder();

        if (msg instanceof TcpRequest) {
            responseBuilder.setServiceType(((TcpRequest) msg).getServiceType());
        }

        responseBuilder.setIsSucc(false)
                .setResultCode(configDO.getConfigKey())
                .setResultMsg(configDO.getValue());

        return responseBuilder.build();
    }

    /**
     * 返回正常场景的数据
     *
     * @return
     */
    TcpResponse.Builder genNormalResp(Object msg) {
        TcpResponse.Builder responseBuilder = TcpResponse.newBuilder();

        if (msg instanceof TcpRequest) {
            responseBuilder.setServiceType(((TcpRequest) msg).getServiceType());
        }

        responseBuilder.setIsSucc(true);
        return responseBuilder;
    }
}
