package com.yxhl.tcp.manager;

import com.yxhl.domain.BizOrderDO;
import com.yxhl.protobuf.BizOrderRequest;
import com.yxhl.protobuf.BizOrderResponse;

/**
 * Created by alan on 16/4/18.
 * 订单相关接口,与yx-platform中接口定义和实现完全不同.
 */
public interface BizOrderManager {

    /**
     * @param bizOrderResponseBuilder
     * @param bizOrderRequest
     */
    void queryLocation(BizOrderResponse.Builder bizOrderResponseBuilder, BizOrderRequest bizOrderRequest);

    public void queryLocation2(BizOrderResponse.Builder bizOrderResponseBuilder, BizOrderDO bizOrderDO);
}
