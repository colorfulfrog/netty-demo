package com.yxhl.tcp.manager;

import com.yxhl.domain.TaskDO;
import com.yxhl.protobuf.TaskRequest;
import com.yxhl.protobuf.TaskResponse;

import java.io.IOException;

/**
 * Created by alan on 16/4/18.
 * 任务相关接口
 */
public interface TaskManager {
    /**
     * 获取任务详情,内中分别判断巴士和快车\接驳车
     *
     * @param taskResponseBuilder
     * @param taskRequest
     */
    void getTaskDetail(TaskResponse.Builder taskResponseBuilder, TaskRequest taskRequest) throws IOException;

    /**
     * 推送添加(未调用)
     * @param taskResponseBuilder
     * @param taskDO
     * @throws IOException
     */
    public void getTaskDetail2(TaskResponse.Builder taskResponseBuilder, TaskDO taskDO) throws IOException;
}
