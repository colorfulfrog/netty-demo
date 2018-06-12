#yx-tcpserver
##说明
* 项目中用到的跟数据库及redis相关的模型都继续放入yx-common中
* 跟其他项目无关的工具\模型则放入本项目中.

##设计思路
* channelHandler类似于filter,可以层层下传
* channelActive可以在未接收请求的情况下就返回详细信息,不过如何确定给哪个用户呢?